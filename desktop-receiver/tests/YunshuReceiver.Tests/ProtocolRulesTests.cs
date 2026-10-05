using System.Text.Json;
using System.Text;
using Xunit;

namespace YunshuReceiver.Tests;

public sealed class ProtocolRulesTests
{
    [Theory]
    [InlineData("云枢智维_K03_K04.xlsx", "云枢智维_K03_K04.xlsx")]
    [InlineData("..\\secret\\report.xlsx", "report.xlsx")]
    [InlineData("bad:name.xlsx", "bad_name.xlsx")]
    public void FileNameIsReducedToSafeXlsxName(string input, string expected)
    {
        Assert.Equal(expected, YunshuReceiver.UploadManager.SanitizeFileName(input));
    }

    [Theory]
    [InlineData(1, 1)]
    [InlineData(4194304, 1)]
    [InlineData(4194305, 2)]
    public void ChunkCountUsesFourMiBChunks(long bytes, int expected)
    {
        Assert.Equal(expected, YunshuReceiver.UploadManager.GetChunkCount(bytes));
    }

    [Fact]
    public void PairingUriUsesExplicitScheme()
    {
        var invite = new YunshuReceiver.PairingInvite("yunshu-receiver", "receiver", "现场电脑", "192.0.2.20", 48120, "secret", DateTime.UtcNow.AddMinutes(5));
        Assert.StartsWith("yunshu-receiver://pair?", invite.Uri, StringComparison.Ordinal);
        Assert.Contains("host=192.0.2.20", invite.Uri, StringComparison.Ordinal);
        Assert.Contains("port=48120", invite.Uri, StringComparison.Ordinal);
    }

    [Fact]
    public void ExplicitAddressSelectionOverridesTheDefaultAddress()
    {
        var options = new[]
        {
            new YunshuReceiver.NetworkAddressOption(System.Net.IPAddress.Parse("192.0.2.10"), "WLAN", false, 420),
            new YunshuReceiver.NetworkAddressOption(System.Net.IPAddress.Parse("192.168.137.1"), "Wi-Fi Direct", true, 180)
        };

        var resolved = YunshuReceiver.NetworkIdentity.ResolveAdvertisedAddress("192.168.137.1", options);

        Assert.True(resolved.IsWindowsHotspot);
        Assert.Equal("192.168.137.1", resolved.Address.ToString());
    }

    [Fact]
    public void PairResponsePayloadUsesStableCamelCaseAndBothExpiryForms()
    {
        var expiresAtUtc = new DateTime(2027, 8, 17, 0, 0, 0, DateTimeKind.Utc);
        var payload = YunshuReceiver.PairResponsePayload.From(new YunshuReceiver.PairResponse(
            "receiver-001", "DESKTOP-01", "android-001", "token", expiresAtUtc, "1"));
        var json = JsonSerializer.Serialize(payload, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        Assert.Equal("receiver-001", root.GetProperty("receiverId").GetString());
        Assert.Equal("android-001", root.GetProperty("deviceId").GetString());
        Assert.Equal("2027-08-17T00:00:00.0000000Z", root.GetProperty("expiresAtUtc").GetString());
        Assert.Equal(1818460800000L, root.GetProperty("expiresAtEpochMillis").GetInt64());
        Assert.Equal("1", root.GetProperty("protocolVersion").GetString());
    }

    [Fact]
    public async Task PairEndpointReturnsAJsonCompatibilityPayload()
    {
        var settingsDirectory = Path.Combine(Path.GetTempPath(), $"yunshu-receiver-test-{Guid.NewGuid():N}");
        await using var service = new YunshuReceiver.ReceiverService(new YunshuReceiver.SettingsStore(settingsDirectory));
        try
        {
            await service.StartAsync();
            var invite = Assert.IsType<YunshuReceiver.PairingInvite>(service.Snapshot.Invite);
            using var client = new HttpClient(new HttpClientHandler { UseProxy = false });
            using var payload = new StringContent(
                $$"""{"pairingSecret":"{{invite.PairingSecret}}","deviceId":"android-001","deviceName":"现场手机"}""",
                Encoding.UTF8,
                "application/json");
            using var response = await client.PostAsync($"http://127.0.0.1:{service.Snapshot.Port}/api/v1/pair", payload);
            var body = await response.Content.ReadAsStringAsync();
            Assert.True(response.IsSuccessStatusCode, $"HTTP {(int)response.StatusCode}: {body}");
            Assert.False(
                string.IsNullOrWhiteSpace(body),
                $"HTTP {(int)response.StatusCode}; content type {response.Content.Headers.ContentType}; port {service.Snapshot.Port}; paired {service.Pairing.Devices.Count}");
            using var document = JsonDocument.Parse(body);
            var root = document.RootElement;

            Assert.StartsWith("application/json", response.Content.Headers.ContentType?.MediaType, StringComparison.OrdinalIgnoreCase);
            Assert.Equal(invite.ReceiverId, root.GetProperty("receiverId").GetString());
            Assert.Equal("android-001", root.GetProperty("deviceId").GetString());
            Assert.True(root.GetProperty("expiresAtEpochMillis").GetInt64() > DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            Assert.False(string.IsNullOrWhiteSpace(root.GetProperty("expiresAtUtc").GetString()));
        }
        finally
        {
            if (Directory.Exists(settingsDirectory)) Directory.Delete(settingsDirectory, recursive: true);
        }
    }
}
