using System.Text.Json.Serialization;

namespace YunshuReceiver;

public sealed class ReceiverSettings
{
    public string ReceiverId { get; set; } = Guid.NewGuid().ToString("N");
    public string ReceiverName { get; set; } = Environment.MachineName;
    public string OutputDirectory { get; set; } = DefaultOutputDirectory();
    public int ListenPort { get; set; } = 48120;
    public string? AdvertisedHost { get; set; }
    public long MaxFileBytes { get; set; } = 200L * 1024 * 1024;
    public List<PairedDevice> PairedDevices { get; set; } = new();

    public static string DefaultOutputDirectory()
    {
        var documents = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        if (string.IsNullOrWhiteSpace(documents))
        {
            documents = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }
        return Path.Combine(documents, "云枢智维", "Excel交付");
    }
}

public sealed class PairedDevice
{
    public string DeviceId { get; set; } = string.Empty;
    public string DeviceName { get; set; } = string.Empty;
    public string TokenHash { get; set; } = string.Empty;
    public DateTime PairedAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? LastSeenAtUtc { get; set; }
}

public sealed record PairingTicket(
    string ReceiverId,
    string ReceiverName,
    string Host,
    int Port,
    string PairingSecret,
    DateTime ExpiresAtUtc);

public sealed record PairRequest(string PairingSecret, string DeviceId, string DeviceName);

public sealed record PairResponse(
    string ReceiverId,
    string ReceiverName,
    string DeviceId,
    string AccessToken,
    DateTime ExpiresAtUtc,
    string ProtocolVersion);

/// <summary>
/// Wire payload for a desktop pairing response. The explicit UTC text and epoch
/// fields keep v1 compatible with Android date parsers across runtime versions.
/// </summary>
public sealed record PairResponsePayload(
    string ReceiverId,
    string ReceiverName,
    string DeviceId,
    string AccessToken,
    string ExpiresAtUtc,
    long ExpiresAtEpochMillis,
    string ProtocolVersion)
{
    public static PairResponsePayload From(PairResponse response)
    {
        var utc = response.ExpiresAtUtc.ToUniversalTime();
        return new PairResponsePayload(
            response.ReceiverId,
            response.ReceiverName,
            response.DeviceId,
            response.AccessToken,
            utc.ToString("O"),
            new DateTimeOffset(utc).ToUnixTimeMilliseconds(),
            response.ProtocolVersion);
    }
}

public sealed record InitUploadRequest(
    string FileName,
    long TotalBytes,
    string Sha256,
    string? TaskId = null,
    string? UploadId = null);

public sealed record UploadInitResponse(
    string UploadId,
    int ChunkSize,
    int[] ReceivedChunks,
    long TotalBytes,
    string FileName);

public sealed record UploadStatusResponse(
    string UploadId,
    int ChunkSize,
    int[] ReceivedChunks,
    long TotalBytes,
    string FileName,
    string Sha256);

public sealed record TransferReceipt(
    string UploadId,
    string FileName,
    string SavedPath,
    long Size,
    string Sha256,
    DateTime ReceivedAtUtc,
    string DeviceName);

public sealed record TransferRecord(
    DateTime ReceivedAtUtc,
    string FileName,
    string DeviceName,
    string Status,
    string SavedPath,
    long Size);

public sealed record PairingInvite(
    string Scheme,
    string ReceiverId,
    string ReceiverName,
    string Host,
    int Port,
    string PairingSecret,
    DateTime ExpiresAtUtc)
{
    [JsonIgnore]
    public string Uri =>
        $"yunshu-receiver://pair?v=1&host={System.Uri.EscapeDataString(Host)}&port={Port}" +
        $"&receiver_id={System.Uri.EscapeDataString(ReceiverId)}&receiver_name={System.Uri.EscapeDataString(ReceiverName)}" +
        $"&pairing_secret={System.Uri.EscapeDataString(PairingSecret)}&expires={new DateTimeOffset(ExpiresAtUtc).ToUnixTimeSeconds()}";
}

public sealed record ServiceSnapshot(
    bool IsRunning,
    string ReceiverName,
    string Host,
    int Port,
    string OutputDirectory,
    PairingInvite? Invite,
    IReadOnlyList<PairedDevice> PairedDevices,
    IReadOnlyList<TransferRecord> Transfers,
    string? ErrorMessage,
    IReadOnlyList<NetworkAddressOption> AddressOptions,
    bool IsHotspotAddress);

public sealed class UploadManifest
{
    public string UploadId { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public long TotalBytes { get; set; }
    public string Sha256 { get; set; } = string.Empty;
    public string? TaskId { get; set; }
    public string DeviceId { get; set; } = string.Empty;
    public string DeviceName { get; set; } = string.Empty;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public HashSet<int> ReceivedChunks { get; set; } = new();

    [JsonIgnore]
    public string SessionDirectory { get; set; } = string.Empty;
}

public sealed class ApiException : Exception
{
    public int StatusCode { get; }
    public string Code { get; }

    public ApiException(int statusCode, string code, string message)
        : base(message)
    {
        StatusCode = statusCode;
        Code = code;
    }
}
