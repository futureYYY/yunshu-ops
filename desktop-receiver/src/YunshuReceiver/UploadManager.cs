using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;

namespace YunshuReceiver;

public sealed class UploadManager
{
    public const int ChunkSize = 4 * 1024 * 1024;
    private readonly SettingsStore _store;
    private readonly ReceiverSettings _settings;
    private readonly string _stagingRoot;
    private readonly object _gate = new();
    private readonly Dictionary<string, UploadManifest> _sessions = new(StringComparer.OrdinalIgnoreCase);

    public UploadManager(SettingsStore store, ReceiverSettings settings)
    {
        _store = store;
        _settings = settings;
        _stagingRoot = store.StagingDirectory;
        Directory.CreateDirectory(_stagingRoot);
        LoadExistingSessions();
    }

    public UploadManifest Begin(InitUploadRequest request, PairedDevice device)
    {
        if (request is null || string.IsNullOrWhiteSpace(request.FileName) || request.TotalBytes <= 0)
            throw new ApiException(400, "invalid_upload", "文件参数不完整");
        if (request.TotalBytes > _settings.MaxFileBytes)
            throw new ApiException(413, "file_too_large", $"文件不能超过 {_settings.MaxFileBytes / 1024 / 1024} MB");
        var fileName = SanitizeFileName(request.FileName);
        if (!fileName.EndsWith(".xlsx", StringComparison.OrdinalIgnoreCase))
            throw new ApiException(415, "xlsx_required", "只接收 .xlsx 文件");
        var sha = NormalizeSha(request.Sha256);

        lock (_gate)
        {
            if (!string.IsNullOrWhiteSpace(request.UploadId) && _sessions.TryGetValue(request.UploadId, out var existing) &&
                existing.DeviceId == device.DeviceId)
            {
                if (existing.TotalBytes != request.TotalBytes || !CryptographicEquals(existing.Sha256, sha))
                    throw new ApiException(409, "upload_mismatch", "续传参数与原任务不一致");
                return existing;
            }

            var id = Guid.NewGuid().ToString("N");
            var session = new UploadManifest
            {
                UploadId = id,
                FileName = fileName,
                TotalBytes = request.TotalBytes,
                Sha256 = sha,
                TaskId = request.TaskId,
                DeviceId = device.DeviceId,
                DeviceName = device.DeviceName,
                CreatedAtUtc = DateTime.UtcNow,
                SessionDirectory = Path.Combine(_stagingRoot, id)
            };
            Directory.CreateDirectory(session.SessionDirectory);
            SaveManifest(session);
            _sessions[id] = session;
            return session;
        }
    }

    public UploadManifest Get(string uploadId, PairedDevice device)
    {
        lock (_gate)
        {
            if (!_sessions.TryGetValue(uploadId, out var session) || session.DeviceId != device.DeviceId)
                throw new ApiException(404, "upload_not_found", "找不到上传任务");
            return session;
        }
    }

    public void PutChunk(string uploadId, int index, byte[] bytes, string? chunkSha, PairedDevice device)
    {
        if (bytes.Length > ChunkSize) throw new ApiException(413, "chunk_too_large", "分片超过 4 MB");
        lock (_gate)
        {
            var session = Get(uploadId, device);
            var chunkCount = GetChunkCount(session.TotalBytes);
            if (index < 0 || index >= chunkCount) throw new ApiException(400, "invalid_chunk", "分片序号无效");
            var expectedLength = (int)Math.Min(ChunkSize, session.TotalBytes - (long)index * ChunkSize);
            if (bytes.Length != expectedLength) throw new ApiException(400, "chunk_length_mismatch", "分片大小不正确");
            if (!string.IsNullOrWhiteSpace(chunkSha) && !CryptographicEquals(NormalizeSha(chunkSha), Convert.ToHexString(SHA256.HashData(bytes))))
                throw new ApiException(400, "chunk_checksum_failed", "分片校验失败");
            var path = ChunkPath(session, index);
            var temp = path + ".tmp";
            File.WriteAllBytes(temp, bytes);
            File.Move(temp, path, true);
            session.ReceivedChunks.Add(index);
            SaveManifest(session);
        }
    }

    public TransferReceipt Complete(string uploadId, PairedDevice device)
    {
        lock (_gate)
        {
            var session = Get(uploadId, device);
            var chunkCount = GetChunkCount(session.TotalBytes);
            if (session.ReceivedChunks.Count != chunkCount || Enumerable.Range(0, chunkCount).Any(i => !session.ReceivedChunks.Contains(i)))
                throw new ApiException(409, "chunks_missing", "文件尚未全部上传");

            Directory.CreateDirectory(_settings.OutputDirectory);
            var assembled = Path.Combine(session.SessionDirectory, "assembled.xlsx");
            using (var output = new FileStream(assembled, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                for (var index = 0; index < chunkCount; index++)
                {
                    using var input = File.OpenRead(ChunkPath(session, index));
                    input.CopyTo(output);
                }
            }

            var actualHash = ComputeFileHash(assembled);
            if (!CryptographicEquals(actualHash, session.Sha256))
                throw new ApiException(400, "file_checksum_failed", "文件校验失败，请重新发送");
            ValidateXlsx(assembled);

            var target = CollisionSafePath(_settings.OutputDirectory, session.FileName);
            File.Move(assembled, target);
            var receipt = new TransferReceipt(session.UploadId, Path.GetFileName(target), target, session.TotalBytes, actualHash, DateTime.UtcNow, session.DeviceName);
            _sessions.Remove(session.UploadId);
            TryDeleteDirectory(session.SessionDirectory);
            return receipt;
        }
    }

    public void Abort(string uploadId, PairedDevice device)
    {
        lock (_gate)
        {
            var session = Get(uploadId, device);
            _sessions.Remove(session.UploadId);
            TryDeleteDirectory(session.SessionDirectory);
        }
    }

    public void CleanupStale(TimeSpan maxAge)
    {
        lock (_gate)
        {
            var cutoff = DateTime.UtcNow - maxAge;
            foreach (var session in _sessions.Values.Where(s => s.CreatedAtUtc < cutoff).ToList())
            {
                _sessions.Remove(session.UploadId);
                TryDeleteDirectory(session.SessionDirectory);
            }
        }
    }

    public static int GetChunkCount(long bytes) => (int)((bytes + ChunkSize - 1) / ChunkSize);

    public static string SanitizeFileName(string value)
    {
        var normalized = value.Replace('\\', '/');
        var name = Path.GetFileName(normalized).Trim();
        // Windows file names are sanitized explicitly so protocol behavior is the same in WSL tests.
        foreach (var invalid in new[] { '<', '>', ':', '"', '/', '\\', '|', '?', '*' }) name = name.Replace(invalid, '_');
        return string.IsNullOrWhiteSpace(name) ? "云枢智维交付.xlsx" : name[..Math.Min(name.Length, 160)];
    }

    private void LoadExistingSessions()
    {
        foreach (var directory in Directory.EnumerateDirectories(_stagingRoot))
        {
            var path = Path.Combine(directory, "manifest.json");
            try
            {
                var session = JsonSerializer.Deserialize<UploadManifest>(File.ReadAllText(path));
                if (session is null || string.IsNullOrWhiteSpace(session.UploadId)) continue;
                session.SessionDirectory = directory;
                _sessions[session.UploadId] = session;
            }
            catch { }
        }
    }

    private void SaveManifest(UploadManifest session)
    {
        File.WriteAllText(Path.Combine(session.SessionDirectory, "manifest.json"), JsonSerializer.Serialize(session));
    }

    private static string ChunkPath(UploadManifest session, int index) => Path.Combine(session.SessionDirectory, $"chunk-{index:D6}.bin");
    private static string NormalizeSha(string value) => value.Trim().Replace("-", string.Empty).ToUpperInvariant();
    private static bool CryptographicEquals(string left, string right) =>
        left.Length == right.Length && CryptographicOperations.FixedTimeEquals(System.Text.Encoding.UTF8.GetBytes(left), System.Text.Encoding.UTF8.GetBytes(right));

    private static string CollisionSafePath(string directory, string fileName)
    {
        var baseName = Path.GetFileNameWithoutExtension(fileName);
        var extension = Path.GetExtension(fileName);
        var candidate = Path.Combine(directory, fileName);
        var index = 1;
        while (File.Exists(candidate)) candidate = Path.Combine(directory, $"{baseName} ({index++}){extension}");
        return candidate;
    }

    private static void ValidateXlsx(string path)
    {
        try
        {
            using var archive = ZipFile.OpenRead(path);
            if (archive.GetEntry("[Content_Types].xml") is null || archive.GetEntry("xl/workbook.xml") is null)
                throw new InvalidDataException();
        }
        catch
        {
            throw new ApiException(400, "invalid_xlsx", "文件不是有效的 Excel 工作簿");
        }
    }

    private static string ComputeFileHash(string path)
    {
        using var input = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(input));
    }

    private static void TryDeleteDirectory(string path)
    {
        try { if (Directory.Exists(path)) Directory.Delete(path, true); } catch { }
    }
}
