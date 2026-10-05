using System.Security.Cryptography;
using System.Text;

namespace YunshuReceiver;

public sealed class PairingManager
{
    private readonly SettingsStore _store;
    private readonly ReceiverSettings _settings;
    private readonly object _gate = new();
    private PairingTicket? _ticket;

    public PairingManager(SettingsStore store, ReceiverSettings settings)
    {
        _store = store;
        _settings = settings;
    }

    public PairingTicket GetTicket(string host, int port, bool forceRefresh = false)
    {
        lock (_gate)
        {
            if (!forceRefresh && _ticket is not null && _ticket.ExpiresAtUtc > DateTime.UtcNow.AddMinutes(1))
            {
                return _ticket with { Host = host, Port = port };
            }

            _ticket = new PairingTicket(
                _settings.ReceiverId,
                _settings.ReceiverName,
                host,
                port,
                Convert.ToBase64String(RandomNumberGenerator.GetBytes(24))
                    .Replace("+", "-").Replace("/", "_").TrimEnd('='),
                DateTime.UtcNow.AddMinutes(10));
            return _ticket;
        }
    }

    public PairResponse Pair(PairRequest request)
    {
        if (request is null || string.IsNullOrWhiteSpace(request.PairingSecret) ||
            string.IsNullOrWhiteSpace(request.DeviceId))
        {
            throw new ApiException(400, "invalid_request", "缺少配对参数");
        }

        lock (_gate)
        {
            if (_ticket is null || _ticket.ExpiresAtUtc <= DateTime.UtcNow ||
                !CryptographicEquals(_ticket.PairingSecret, request.PairingSecret))
            {
                throw new ApiException(410, "pairing_expired", "二维码已过期，请在电脑端刷新后重试");
            }

            var deviceId = NormalizeId(request.DeviceId);
            if (string.IsNullOrWhiteSpace(deviceId))
                throw new ApiException(400, "invalid_device", "设备标识无效");
            var deviceName = NormalizeName(request.DeviceName);
            var token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
                .Replace("+", "-").Replace("/", "_").TrimEnd('=');
            var existing = _settings.PairedDevices.FirstOrDefault(x => x.DeviceId == deviceId);
            if (existing is null)
            {
                existing = new PairedDevice { DeviceId = deviceId };
                _settings.PairedDevices.Add(existing);
            }

            existing.DeviceName = deviceName;
            existing.TokenHash = HashToken(token);
            existing.PairedAtUtc = DateTime.UtcNow;
            existing.LastSeenAtUtc = DateTime.UtcNow;
            _store.Save(_settings);
            _ticket = null;

            return new PairResponse(
                _settings.ReceiverId,
                _settings.ReceiverName,
                deviceId,
                token,
                DateTime.UtcNow.AddYears(1),
                "1");
        }
    }

    public PairedDevice? Authenticate(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return null;
        var hash = HashToken(token);
        lock (_gate)
        {
            var device = _settings.PairedDevices.FirstOrDefault(x => CryptographicEquals(x.TokenHash, hash));
            if (device is null) return null;
            device.LastSeenAtUtc = DateTime.UtcNow;
            _store.Save(_settings);
            return device;
        }
    }

    public IReadOnlyList<PairedDevice> Devices
    {
        get { lock (_gate) return _settings.PairedDevices.Select(Clone).ToList(); }
    }

    public void RevokeAll()
    {
        lock (_gate)
        {
            _settings.PairedDevices.Clear();
            _store.Save(_settings);
            _ticket = null;
        }
    }

    private static PairedDevice Clone(PairedDevice source) => new()
    {
        DeviceId = source.DeviceId,
        DeviceName = source.DeviceName,
        TokenHash = source.TokenHash,
        PairedAtUtc = source.PairedAtUtc,
        LastSeenAtUtc = source.LastSeenAtUtc
    };

    private static string NormalizeId(string value) =>
        new(value.Where(c => char.IsLetterOrDigit(c) || c is '-' or '_' or '.').Take(80).ToArray());

    private static string NormalizeName(string? value) =>
        string.IsNullOrWhiteSpace(value) ? "移动端" : value.Trim()[..Math.Min(value.Trim().Length, 80)];

    public static string HashToken(string value)
    {
        var digest = SHA256.HashData(Encoding.UTF8.GetBytes(value));
        return Convert.ToHexString(digest);
    }

    private static bool CryptographicEquals(string left, string right)
    {
        var a = Encoding.UTF8.GetBytes(left);
        var b = Encoding.UTF8.GetBytes(right);
        return a.Length == b.Length && CryptographicOperations.FixedTimeEquals(a, b);
    }
}
