using System.Drawing;
using System.Threading;
using System.Windows;
using Forms = System.Windows.Forms;

namespace YunshuReceiver;

public partial class App : System.Windows.Application
{
    private ReceiverService? _service;
    private MainWindow? _window;
    private Forms.NotifyIcon? _trayIcon;
    private Mutex? _singleInstance;
    private EventWaitHandle? _showWindowEvent;
    private RegisteredWaitHandle? _showWindowRegistration;
    private bool _ownsSingleInstance;
    private bool _exitRequested;

    private const string SingleInstanceName = "Local\\YunshuReceiver.Desktop.SingleInstance";
    private const string ShowWindowEventName = "Local\\YunshuReceiver.Desktop.ShowWindow";

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        _singleInstance = new Mutex(true, SingleInstanceName, out var created);
        _ownsSingleInstance = created;
        if (!created)
        {
            SignalExistingInstance();
            _singleInstance.Dispose();
            _singleInstance = null;
            Shutdown(0);
            return;
        }

        try
        {
            _showWindowEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowWindowEventName);
            _showWindowRegistration = ThreadPool.RegisterWaitForSingleObject(
                _showWindowEvent,
                (_, _) => Dispatcher.BeginInvoke(ShowWindow),
                null,
                Timeout.Infinite,
                executeOnlyOnce: false);
            _service = new ReceiverService(new SettingsStore());
            await _service.StartAsync();
            _window = new MainWindow(_service);
            _window.Closing += WindowClosing;
            _window.Show();
            CreateTrayIcon();
        }
        catch (Exception error)
        {
            System.Windows.MessageBox.Show(
                $"桌面接收器启动失败。\n\n{error.Message}",
                "云枢智维",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Error);
            Shutdown(-1);
        }
    }

    private void CreateTrayIcon()
    {
        _trayIcon = new Forms.NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "云枢智维桌面接收器",
            Visible = true,
        };
        _trayIcon.DoubleClick += (_, _) => ShowWindow();
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("显示接收器", null, (_, _) => ShowWindow());
        menu.Items.Add("刷新配对二维码", null, (_, _) => _window?.RefreshPairing());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("退出", null, (_, _) => ExitApplication());
        _trayIcon.ContextMenuStrip = menu;
    }

    private void WindowClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (_exitRequested) return;
        e.Cancel = true;
        _window?.Hide();
        _trayIcon?.ShowBalloonTip(1800, "云枢智维", "接收器仍在后台运行，可从系统托盘打开。", Forms.ToolTipIcon.Info);
    }

    private void ShowWindow()
    {
        if (_window is null) return;
        _window.Show();
        _window.WindowState = WindowState.Normal;
        _window.Activate();
        _window.Topmost = true;
        _window.Topmost = false;
        _window.Focus();
    }

    private static void SignalExistingInstance()
    {
        try
        {
            using var signal = EventWaitHandle.OpenExisting(ShowWindowEventName);
            signal.Set();
        }
        catch (WaitHandleCannotBeOpenedException)
        {
            // The existing process may be an older release without the wake-up event.
        }
        catch (UnauthorizedAccessException)
        {
            // A session boundary or security policy can prevent opening the event.
        }
    }

    internal void ExitApplication()
    {
        if (_exitRequested) return;
        _exitRequested = true;
        _trayIcon?.Dispose();
        _trayIcon = null;
        _service?.StopAsync().GetAwaiter().GetResult();
        Shutdown();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _showWindowRegistration?.Unregister(null);
        _showWindowRegistration = null;
        _showWindowEvent?.Dispose();
        _showWindowEvent = null;
        _trayIcon?.Dispose();
        _service?.DisposeAsync().AsTask().GetAwaiter().GetResult();
        if (_ownsSingleInstance)
        {
            try { _singleInstance?.ReleaseMutex(); } catch (ApplicationException) { }
        }
        _singleInstance?.Dispose();
        base.OnExit(e);
    }
}
