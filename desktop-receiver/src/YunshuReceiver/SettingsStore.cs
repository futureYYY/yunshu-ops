using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace YunshuReceiver;

public sealed class SettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true,
        Converters = { new System.Text.Json.Serialization.JsonStringEnumConverter() }
    };

    public string RootDirectory { get; }

    public SettingsStore(string? rootDirectory = null)
    {
        RootDirectory = rootDirectory ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "YunshuReceiver");
    }

    private string SettingsPath => Path.Combine(RootDirectory, "settings.bin");

    public ReceiverSettings Load()
    {
        try
        {
            if (!File.Exists(SettingsPath))
            {
                return NewDefaults();
            }

            var protectedBytes = File.ReadAllBytes(SettingsPath);
            var plainBytes = ProtectedData.Unprotect(protectedBytes, null, DataProtectionScope.CurrentUser);
            var settings = JsonSerializer.Deserialize<ReceiverSettings>(plainBytes, JsonOptions) ?? NewDefaults();
            settings.PairedDevices ??= new List<PairedDevice>();
            if (string.IsNullOrWhiteSpace(settings.ReceiverId)) settings.ReceiverId = Guid.NewGuid().ToString("N");
            if (string.IsNullOrWhiteSpace(settings.ReceiverName)) settings.ReceiverName = Environment.MachineName;
            if (string.IsNullOrWhiteSpace(settings.OutputDirectory)) settings.OutputDirectory = ReceiverSettings.DefaultOutputDirectory();
            return settings;
        }
        catch
        {
            return NewDefaults();
        }
    }

    public void Save(ReceiverSettings settings)
    {
        Directory.CreateDirectory(RootDirectory);
        var json = JsonSerializer.SerializeToUtf8Bytes(settings, JsonOptions);
        var protectedBytes = ProtectedData.Protect(json, null, DataProtectionScope.CurrentUser);
        var temp = SettingsPath + ".tmp";
        File.WriteAllBytes(temp, protectedBytes);
        File.Move(temp, SettingsPath, true);
    }

    public string StagingDirectory => Path.Combine(RootDirectory, "staging");

    private static ReceiverSettings NewDefaults() => new()
    {
        ReceiverId = Guid.NewGuid().ToString("N"),
        ReceiverName = Environment.MachineName,
        OutputDirectory = ReceiverSettings.DefaultOutputDirectory()
    };
}
