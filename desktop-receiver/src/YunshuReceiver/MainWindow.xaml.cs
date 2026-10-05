using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Forms = System.Windows.Forms;

namespace YunshuReceiver;

public partial class MainWindow : Window
{
    private readonly ReceiverService _service;
    private bool _isRefreshingAddressSelector;

    public MainWindow(ReceiverService service)
    {
        InitializeComponent();
        _service = service;
        _service.StateChanged += ServiceOnStateChanged;
        Loaded += (_, _) => RefreshView();
    }

    public void RefreshPairing()
    {
        _service.RefreshPairing();
        RefreshView();
    }

    private void ServiceOnStateChanged(object? sender, EventArgs e)
    {
        Dispatcher.Invoke(RefreshView);
    }

    private void RefreshView()
    {
        var snapshot = _service.Snapshot;
        var running = snapshot.IsRunning;
        StatusDot.Fill = new SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString(running ? "#16856F" : "#B7791F"));
        StatusText.Text = running ? "接收器运行中" : "接收器已暂停";
        ToggleButton.Content = running ? "停止接收" : "启动接收";
        EndpointText.Text = running ? $"局域网地址：{snapshot.Host}:{snapshot.Port}" : "局域网地址：未启动";
        ExpiryText.Text = snapshot.Invite is null
            ? "二维码有效期：接收器启动后生成"
            : $"二维码有效期：{snapshot.Invite.ExpiresAtUtc.ToLocalTime():HH:mm} 前";
        OutputDirectoryText.Text = snapshot.OutputDirectory;
        DeviceCountText.Text = $"{snapshot.PairedDevices.Count} 台手机已配对";
        DeviceList.ItemsSource = snapshot.PairedDevices.Select(x =>
            $"{x.DeviceName}    ·    最近连接 {FormatLastSeen(x.LastSeenAtUtc)}").ToList();
        TransferGrid.ItemsSource = snapshot.Transfers;
        _isRefreshingAddressSelector = true;
        try
        {
            AddressSelector.ItemsSource = snapshot.AddressOptions;
            AddressSelector.SelectedItem = snapshot.AddressOptions.FirstOrDefault(option =>
                string.Equals(option.Address.ToString(), snapshot.Host, StringComparison.Ordinal));
        }
        finally
        {
            _isRefreshingAddressSelector = false;
        }
        AddressHintText.Text = snapshot.IsHotspotAddress
            ? "已选择 Windows 移动热点。打开电脑热点后，让手机连接此热点，再扫描刷新后的二维码。"
            : "当前无线网络未见手机访问时，选择 Windows 移动热点；手机改连电脑热点后刷新二维码。";
        if (snapshot.Invite is not null)
        {
            QrImage.Source = QrCodeService.Render(snapshot.Invite.Uri);
        }
    }

    private async void ToggleButton_OnClick(object sender, RoutedEventArgs e)
    {
        try
        {
            if (_service.Snapshot.IsRunning) await _service.StopAsync();
            else await _service.StartAsync();
        }
        catch (Exception error)
        {
            System.Windows.MessageBox.Show(error.Message, "云枢智维", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Error);
        }
    }

    private void RefreshButton_OnClick(object sender, RoutedEventArgs e) => RefreshPairing();

    private void HotspotSettingsButton_OnClick(object sender, RoutedEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo("ms-settings:network-mobilehotspot") { UseShellExecute = true });
        }
        catch (Exception error)
        {
            System.Windows.MessageBox.Show(
                $"打开 Windows 热点设置出现问题：{error.Message}",
                "云枢智维",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
        }
    }

    private void AddressSelector_OnSelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (_isRefreshingAddressSelector || AddressSelector.SelectedItem is not NetworkAddressOption option) return;
        _service.SetAdvertisedHost(option.Address.ToString());
    }

    private void CopyButton_OnClick(object sender, RoutedEventArgs e)
    {
        var invite = _service.Snapshot.Invite;
        if (invite is null) return;
        System.Windows.Clipboard.SetText(invite.Uri);
        System.Windows.MessageBox.Show("连接信息已复制。请在手机 App 的“扫码连接电脑”中使用。", "云枢智维", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
    }

    private void FirewallButton_OnClick(object sender, RoutedEventArgs e)
    {
        var snapshot = _service.Snapshot;
        if (!snapshot.IsRunning)
        {
            System.Windows.MessageBox.Show("请先启动接收器，再启用局域网访问。", "云枢智维", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
            return;
        }

        var result = FirewallAccess.AllowReceiver(snapshot.Port);
        System.Windows.MessageBox.Show(
            result.Message,
            "局域网访问",
            System.Windows.MessageBoxButton.OK,
            result.Success ? System.Windows.MessageBoxImage.Information : System.Windows.MessageBoxImage.Warning);
    }

    private void SelectDirectoryButton_OnClick(object sender, RoutedEventArgs e)
    {
        using var dialog = new Forms.FolderBrowserDialog
        {
            Description = "选择 Excel 交付文件保存目录",
            UseDescriptionForTitle = true,
            SelectedPath = _service.Settings.OutputDirectory,
            ShowNewFolderButton = true
        };
        if (dialog.ShowDialog() == Forms.DialogResult.OK)
        {
            _service.SetOutputDirectory(dialog.SelectedPath);
            RefreshView();
        }
    }

    private void OpenDirectoryButton_OnClick(object sender, RoutedEventArgs e)
    {
        var directory = _service.Settings.OutputDirectory;
        Directory.CreateDirectory(directory);
        Process.Start(new ProcessStartInfo("explorer.exe", $"\"{directory}\"") { UseShellExecute = true });
    }

    private void TransferGrid_OnMouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        var source = e.OriginalSource as DependencyObject;
        var row = source is null
            ? null
            : ItemsControl.ContainerFromElement(TransferGrid, source) as DataGridRow;
        if (row?.Item is not TransferRecord transfer) return;

        OpenDeliveredWorkbook(transfer);
    }

    private static void OpenDeliveredWorkbook(TransferRecord transfer)
    {
        if (string.IsNullOrWhiteSpace(transfer.SavedPath))
        {
            System.Windows.MessageBox.Show(
                $"“{transfer.FileName}”未记录有效的保存路径。请在交付目录中查找该文件。",
                "打开交付文件",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
            return;
        }

        string fullPath;
        try
        {
            fullPath = Path.GetFullPath(transfer.SavedPath);
        }
        catch (Exception error) when (error is ArgumentException or NotSupportedException or PathTooLongException)
        {
            System.Windows.MessageBox.Show(
                $"“{transfer.FileName}”的保存路径无效，暂时无法打开。请检查交付目录设置。",
                "打开交付文件",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
            return;
        }

        if (!File.Exists(fullPath))
        {
            System.Windows.MessageBox.Show(
                $"未找到“{transfer.FileName}”。该文件可能已被移动、重命名或删除。\n\n记录路径：{fullPath}",
                "打开交付文件",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
            return;
        }

        try
        {
            Process.Start(new ProcessStartInfo(fullPath) { UseShellExecute = true });
        }
        catch (System.ComponentModel.Win32Exception)
        {
            System.Windows.MessageBox.Show(
                "此电脑未关联可打开 .xlsx 文件的程序。请安装或设置 Microsoft Excel、WPS Office 后重试。",
                "打开交付文件",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
        }
        catch (Exception error)
        {
            System.Windows.MessageBox.Show(
                $"打开“{transfer.FileName}”时出现问题：{error.Message}",
                "打开交付文件",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Warning);
        }
    }

    private void RevokeButton_OnClick(object sender, RoutedEventArgs e)
    {
        if (_service.Pairing.Devices.Count == 0) return;
        var answer = System.Windows.MessageBox.Show("解除后，所有手机需要重新扫码配对。确定继续吗？", "解除配对", System.Windows.MessageBoxButton.YesNo, System.Windows.MessageBoxImage.Warning);
        if (answer == System.Windows.MessageBoxResult.Yes) _service.RevokeAllDevices();
    }

    private static string FormatLastSeen(DateTime? value) => value is null ? "尚未连接" : value.Value.ToLocalTime().ToString("MM-dd HH:mm");
}
