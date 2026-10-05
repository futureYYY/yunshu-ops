using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace YunshuReceiver;

public sealed class ReceiverService : IAsyncDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly SettingsStore _store;
    private readonly ReceiverSettings _settings;
    private readonly PairingManager _pairing;
    private readonly UploadManager _uploads;
    private readonly List<TransferRecord> _transfers = new();
    private readonly object _stateGate = new();
    private WebApplication? _web;
    private string? _error;
    private int _port;

    public ReceiverService(SettingsStore store)
    {
        _store = store;
        _settings = store.Load();
        _pairing = new PairingManager(store, _settings);
        _uploads = new UploadManager(store, _settings);
        _port = _settings.ListenPort;
    }

    public event EventHandler? StateChanged;
    public PairingManager Pairing => _pairing;
    public ReceiverSettings Settings => _settings;

    public ServiceSnapshot Snapshot
    {
        get
        {
            var addressOptions = NetworkIdentity.GetAdvertisableIPv4Addresses();
            var activeAddress = NetworkIdentity.ResolveAdvertisedAddress(_settings.AdvertisedHost, addressOptions);
            var host = activeAddress.Address.ToString();
            PairingInvite? invite = null;
            if (_web is not null)
            {
                var ticket = _pairing.GetTicket(host, _port);
                invite = new PairingInvite("yunshu-receiver", ticket.ReceiverId, ticket.ReceiverName,
                    ticket.Host, ticket.Port, ticket.PairingSecret, ticket.ExpiresAtUtc);
            }
            lock (_stateGate)
            {
                return new ServiceSnapshot(
                    _web is not null,
                    _settings.ReceiverName,
                    host,
                    _port,
                    _settings.OutputDirectory,
                    invite,
                    _pairing.Devices,
                    _transfers.ToList(),
                    _error,
                    addressOptions,
                    activeAddress.IsWindowsHotspot);
            }
        }
    }

    public async Task StartAsync()
    {
        if (_web is not null) return;
        _error = null;
        Directory.CreateDirectory(_settings.OutputDirectory);
        _uploads.CleanupStale(TimeSpan.FromHours(24));

        var requestedPort = _settings.ListenPort;
        var app = BuildWebApplication(requestedPort);
        try
        {
            await app.StartAsync();
            _port = ReadBoundPort(app, requestedPort);
            _web = app;
            RaiseStateChanged();
        }
        catch (IOException) when (requestedPort != 0)
        {
            await app.DisposeAsync();
            app = BuildWebApplication(0);
            try
            {
                await app.StartAsync();
                _port = ReadBoundPort(app, 0);
                _web = app;
                RaiseStateChanged();
            }
            catch (Exception ex)
            {
                await app.DisposeAsync();
                _error = $"接收器启动失败：{ex.Message}";
                RaiseStateChanged();
                throw;
            }
        }
        catch (Exception ex)
        {
            await app.DisposeAsync();
            _error = $"接收器启动失败：{ex.Message}";
            RaiseStateChanged();
            throw;
        }
    }

    private WebApplication BuildWebApplication(int port)
    {
        var builder = WebApplication.CreateSlimBuilder(new WebApplicationOptions
        {
            ApplicationName = "YunshuReceiver",
            ContentRootPath = AppContext.BaseDirectory
        });
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options => options.ListenAnyIP(port));
        var app = builder.Build();
        MapRoutes(app);
        return app;
    }

    private static int ReadBoundPort(WebApplication app, int fallback)
    {
        var feature = app.Services.GetRequiredService<IServer>().Features.Get<IServerAddressesFeature>();
        var address = feature?.Addresses.FirstOrDefault();
        return System.Uri.TryCreate(address, UriKind.Absolute, out var uri) ? uri.Port : fallback;
    }

    public async Task StopAsync()
    {
        var app = _web;
        if (app is null) return;
        _web = null;
        if (app is not null)
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
        RaiseStateChanged();
    }

    public void SetOutputDirectory(string directory)
    {
        if (string.IsNullOrWhiteSpace(directory)) return;
        Directory.CreateDirectory(directory);
        _settings.OutputDirectory = Path.GetFullPath(directory);
        _store.Save(_settings);
        RaiseStateChanged();
    }

    public void RefreshPairing()
    {
        _pairing.GetTicket(GetActiveHost(), _port, forceRefresh: true);
        RaiseStateChanged();
    }

    public void SetAdvertisedHost(string host)
    {
        var candidates = NetworkIdentity.GetAdvertisableIPv4Addresses();
        var option = candidates.FirstOrDefault(candidate =>
            string.Equals(candidate.Address.ToString(), host, StringComparison.Ordinal));
        if (option is null) return;
        if (string.Equals(_settings.AdvertisedHost, option.Address.ToString(), StringComparison.Ordinal)) return;

        _settings.AdvertisedHost = option.Address.ToString();
        _store.Save(_settings);
        _pairing.GetTicket(option.Address.ToString(), _port, forceRefresh: true);
        RaiseStateChanged();
    }

    public void RevokeAllDevices()
    {
        _pairing.RevokeAll();
        RaiseStateChanged();
    }

    public async ValueTask DisposeAsync() => await StopAsync();

    private string GetActiveHost()
    {
        var candidates = NetworkIdentity.GetAdvertisableIPv4Addresses();
        return NetworkIdentity.ResolveAdvertisedAddress(_settings.AdvertisedHost, candidates).Address.ToString();
    }

    private void MapRoutes(WebApplication app)
    {
        app.MapGet("/api/v1/health", () => Results.Ok(new
        {
            receiver_id = _settings.ReceiverId,
            receiver_name = _settings.ReceiverName,
            protocol_version = "1",
            status = "ready"
        }));
        app.MapPost("/api/v1/pair", async (HttpContext context) =>
        {
            return await HandlePair(context);
        });
        app.MapPost("/api/v1/uploads/init", async (HttpContext context) =>
        {
            return await HandleInit(context);
        });
        app.MapGet("/api/v1/uploads/{uploadId}", async (HttpContext context, string uploadId) =>
        {
            return await HandleStatus(context, uploadId);
        });
        app.MapPut("/api/v1/uploads/{uploadId}/chunks/{index:int}", async (HttpContext context, string uploadId, int index) =>
        {
            return await HandleChunk(context, uploadId, index);
        });
        app.MapPost("/api/v1/uploads/{uploadId}/complete", async (HttpContext context, string uploadId) =>
        {
            return await HandleComplete(context, uploadId);
        });
        app.MapDelete("/api/v1/uploads/{uploadId}", async (HttpContext context, string uploadId) =>
        {
            return await HandleAbort(context, uploadId);
        });
    }

    private async Task<IResult> HandlePair(HttpContext context)
    {
        try
        {
            var request = await ReadJson<PairRequest>(context);
            var result = _pairing.Pair(request);
            RaiseStateChanged();
            return Results.Json(PairResponsePayload.From(result), JsonOptions);
        }
        catch (Exception ex) { return ErrorResult(ex); }
    }

    private async Task<IResult> HandleInit(HttpContext context)
    {
        try
        {
            var device = Authorize(context);
            var request = await ReadJson<InitUploadRequest>(context);
            var session = _uploads.Begin(request, device);
            return Results.Ok(new UploadInitResponse(session.UploadId, UploadManager.ChunkSize,
                session.ReceivedChunks.Order().ToArray(), session.TotalBytes, session.FileName));
        }
        catch (Exception ex) { return ErrorResult(ex); }
    }

    private Task<IResult> HandleStatus(HttpContext context, string uploadId)
    {
        try
        {
            var device = Authorize(context);
            var session = _uploads.Get(uploadId, device);
            return Task.FromResult<IResult>(Results.Ok(new UploadStatusResponse(uploadId, UploadManager.ChunkSize,
                session.ReceivedChunks.Order().ToArray(), session.TotalBytes, session.FileName, session.Sha256)));
        }
        catch (Exception ex) { return Task.FromResult<IResult>(ErrorResult(ex)); }
    }

    private async Task<IResult> HandleChunk(HttpContext context, string uploadId, int index)
    {
        try
        {
            var device = Authorize(context);
            var bytes = await ReadBytes(context, UploadManager.ChunkSize);
            _uploads.PutChunk(uploadId, index, bytes, context.Request.Headers["X-Chunk-Sha256"].FirstOrDefault(), device);
            return Results.Ok(new { upload_id = uploadId, chunk_index = index, received = true });
        }
        catch (Exception ex) { return ErrorResult(ex); }
    }

    private Task<IResult> HandleComplete(HttpContext context, string uploadId)
    {
        try
        {
            var device = Authorize(context);
            var receipt = _uploads.Complete(uploadId, device);
            lock (_stateGate)
            {
                _transfers.Insert(0, new TransferRecord(receipt.ReceivedAtUtc, receipt.FileName, receipt.DeviceName,
                    "已保存", receipt.SavedPath, receipt.Size));
                if (_transfers.Count > 20) _transfers.RemoveAt(_transfers.Count - 1);
            }
            RaiseStateChanged();
            return Task.FromResult<IResult>(Results.Ok(receipt));
        }
        catch (Exception ex) { return Task.FromResult<IResult>(ErrorResult(ex)); }
    }

    private Task<IResult> HandleAbort(HttpContext context, string uploadId)
    {
        try
        {
            var device = Authorize(context);
            _uploads.Abort(uploadId, device);
            return Task.FromResult<IResult>(Results.Ok(new { aborted = true }));
        }
        catch (Exception ex) { return Task.FromResult<IResult>(ErrorResult(ex)); }
    }

    private PairedDevice Authorize(HttpContext context)
    {
        var header = context.Request.Headers.Authorization.ToString();
        var token = header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..].Trim() : null;
        return _pairing.Authenticate(token) ?? throw new ApiException(401, "unauthorized", "电脑尚未与该手机配对");
    }

    private static async Task<T> ReadJson<T>(HttpContext context)
    {
        if (context.Request.ContentLength is long length && length > 256L * 1024L)
            throw new ApiException(413, "request_too_large", "请求内容过大");
        var value = await JsonSerializer.DeserializeAsync<T>(context.Request.Body, JsonOptions);
        return value ?? throw new ApiException(400, "invalid_json", "请求内容不是有效 JSON");
    }

    private static async Task<byte[]> ReadBytes(HttpContext context, int maxBytes)
    {
        if (context.Request.ContentLength is long length && length > maxBytes)
            throw new ApiException(413, "chunk_too_large", "分片超过 4 MB");
        await using var output = new MemoryStream();
        var buffer = new byte[64 * 1024];
        var total = 0;
        int read;
        while ((read = await context.Request.Body.ReadAsync(buffer)) > 0)
        {
            total += read;
            if (total > maxBytes) throw new ApiException(413, "chunk_too_large", "分片超过 4 MB");
            await output.WriteAsync(buffer.AsMemory(0, read));
        }
        return output.ToArray();
    }

    private static IResult ErrorResult(Exception exception)
    {
        var error = exception as ApiException ?? new ApiException(500, "internal_error", "接收器内部错误");
        return Results.Json(new { error = new { code = error.Code, message = error.Message } }, statusCode: error.StatusCode);
    }

    private void RaiseStateChanged() => StateChanged?.Invoke(this, EventArgs.Empty);
}
