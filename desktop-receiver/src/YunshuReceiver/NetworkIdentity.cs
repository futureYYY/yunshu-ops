using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace YunshuReceiver;

public static class NetworkIdentity
{
    public static string GetPreferredIPv4()
    {
        var candidate = GetAdvertisableIPv4Addresses().FirstOrDefault();
        return candidate?.Address.ToString() ?? IPAddress.Loopback.ToString();
    }

    public static IReadOnlyList<NetworkAddressOption> GetAdvertisableIPv4Addresses()
    {
        var candidates = new List<NetworkAddressOption>();
        foreach (var network in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (network.OperationalStatus != OperationalStatus.Up ||
                network.NetworkInterfaceType == NetworkInterfaceType.Loopback ||
                network.NetworkInterfaceType == NetworkInterfaceType.Tunnel)
            {
                continue;
            }

            try
            {
                var properties = network.GetIPProperties();
                var hasDefaultGateway = properties.GatewayAddresses.Any(gateway =>
                    gateway.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !IPAddress.Any.Equals(gateway.Address));
                var adapterScore = network.NetworkInterfaceType switch
                {
                    NetworkInterfaceType.Wireless80211 => 320,
                    NetworkInterfaceType.Ethernet => 240,
                    _ => 120,
                };
                if (hasDefaultGateway) adapterScore += 100;

                foreach (var address in properties.UnicastAddresses)
                {
                    if (address.Address.AddressFamily != AddressFamily.InterNetwork ||
                        IPAddress.IsLoopback(address.Address) ||
                        !IsLocalNetworkAddress(address.Address))
                    {
                        continue;
                    }

                    // Windows Mobile Hotspot uses a Wi-Fi Direct adapter. Keep that address
                    // selectable, but continue excluding unrelated virtual adapters.
                    var isWindowsHotspot = IsWindowsHotspot(network, address.Address);
                    if (IsVirtualOrTunnel(network) && !isWindowsHotspot)
                    {
                        continue;
                    }

                    var score = isWindowsHotspot ? 180 : adapterScore;
                    candidates.Add(new NetworkAddressOption(
                        address.Address,
                        network.Name,
                        isWindowsHotspot,
                        score));
                }
            }
            catch
            {
                // Network adapters can disappear while the receiver is starting.
            }
        }

        return candidates
            .GroupBy(candidate => candidate.Address)
            .Select(group => group.OrderByDescending(candidate => candidate.Score).First())
            .OrderByDescending(candidate => candidate.Score)
            .ThenBy(candidate => candidate.Address.ToString(), StringComparer.Ordinal)
            .ToList();
    }

    public static NetworkAddressOption ResolveAdvertisedAddress(
        string? selectedAddress,
        IReadOnlyList<NetworkAddressOption>? candidates = null)
    {
        candidates ??= GetAdvertisableIPv4Addresses();
        return candidates.FirstOrDefault(candidate =>
                   string.Equals(candidate.Address.ToString(), selectedAddress, StringComparison.Ordinal))
               ?? candidates.FirstOrDefault()
               ?? new NetworkAddressOption(IPAddress.Loopback, "本机回环地址", false, 0);
    }

    private static bool IsVirtualOrTunnel(NetworkInterface network)
    {
        var description = $"{network.Name} {network.Description}".ToUpperInvariant();
        string[] markers =
        [
            "WI-FI DIRECT",
            "WIFI DIRECT",
            "VETHERNET",
            "HYPER-V",
            "VIRTUAL",
            "VMWARE",
            "VIRTUALBOX",
            "WSL",
            "TAP-WINDOWS",
            "VPN",
            "TUNNEL",
            "ATRUST",
            "SANGFOR",
        ];
        return markers.Any(description.Contains);
    }

    private static bool IsWindowsHotspot(NetworkInterface network, IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        if (bytes is [192, 168, 137, 1]) return true;

        var description = $"{network.Name} {network.Description}".ToUpperInvariant();
        return description.Contains("WI-FI DIRECT") || description.Contains("WIFI DIRECT");
    }

    private static bool IsLocalNetworkAddress(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        if (bytes.Length != 4) return false;
        return bytes[0] == 10 ||
            (bytes[0] == 172 && bytes[1] is >= 16 and <= 31) ||
            (bytes[0] == 192 && bytes[1] == 168) ||
            // RFC 6598 is used by some enterprise WLANs as a local shared range.
            (bytes[0] == 100 && bytes[1] is >= 64 and <= 127);
    }

}

public sealed record NetworkAddressOption(
    IPAddress Address,
    string AdapterName,
    bool IsWindowsHotspot,
    int Score)
{
    public string DisplayName => IsWindowsHotspot
        ? $"Windows 移动热点 · {Address}"
        : $"局域网 · {AdapterName} · {Address}";
}
