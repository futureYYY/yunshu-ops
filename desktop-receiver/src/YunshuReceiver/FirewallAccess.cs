using System.Diagnostics;
using System.ComponentModel;

namespace YunshuReceiver;

public static class FirewallAccess
{
    public static (bool Success, string Message) AllowReceiver(int port)
    {
        if (port is < 1 or > 65_535)
        {
            return (false, "当前接收端口无效，请先刷新接收器。");
        }

        var executable = Environment.ProcessPath;
        if (string.IsNullOrWhiteSpace(executable))
        {
            return (false, "未找到当前接收器程序路径。");
        }

        // The receiver may use a fallback port when another local process briefly
        // owns the configured port. Restrict the rule to this executable instead
        // of tying a trusted local receiver to one transient port number.
        var ruleName = "云枢智维桌面接收器 TCP";
        var arguments =
            $"advfirewall firewall add rule name=\"{ruleName}\" " +
            $"dir=in action=allow protocol=TCP profile=any " +
            $"program=\"{executable}\" enable=yes";
        try
        {
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = "netsh.exe",
                Arguments = arguments,
                UseShellExecute = true,
                Verb = "runas",
                WindowStyle = ProcessWindowStyle.Hidden,
            });
            process?.WaitForExit(8_000);
            if (process is null || !process.HasExited)
            {
                return (false, "防火墙设置仍在等待系统确认，请完成管理员权限提示后重试。");
            }
            return process.ExitCode == 0
                ? (true, $"已允许云枢智维桌面接收器通过 Windows 防火墙（当前端口 {port}），手机可重新检测电脑连接。")
                : (false, "Windows 防火墙未接受本次设置，请在权限提示中选择“是”。");
        }
        catch (Win32Exception)
        {
            return (false, "未完成管理员权限确认，防火墙规则保持不变。");
        }
        catch (Exception error)
        {
            return (false, $"防火墙设置出现问题：{error.Message}");
        }
    }
}
