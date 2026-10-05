# 云枢智维桌面接收器

Windows 端轻量接收器，用于把云枢智维 Android App 生成的 `.xlsx` 交付文件，直接发送到指定电脑目录。电脑端不依赖中心服务器，手机和电脑在同一局域网内即可完成配对和传输。

## 已实现能力

- 首次启动选择 Excel 交付目录，默认位于 `文档\云枢智维\Excel交付`。
- 系统托盘常驻，关闭窗口不会停止接收服务。
- 一次性二维码配对，二维码 10 分钟有效；扫码成功后自动失效。
- 默认选择实际在线的 WLAN/以太网地址，并在“手机连接地址”中提供 Windows 移动热点 `192.168.137.1` 入口；支持企业 WLAN 常见的 `100.64.0.0/10` 地址段。
- 单实例运行保护，避免重复启动使监听端口切换为随机端口。
- 内置“启用局域网访问”按钮，可在 Windows 管理员确认后为当前接收端口创建仅限本程序的入站规则。
- 配对设备使用随机 Bearer Token，电脑端只保存 Token 的 DPAPI 加密配置和哈希，不保存明文 Token。
- `.xlsx` 分片上传，默认每片 4 MB；支持查询已收分片和断点续传。
- 每片校验、整文件 SHA-256 校验、Excel ZIP 结构校验。
- 重名文件自动生成 `文件 (1).xlsx`，不会覆盖已有交付件。
- 最近 20 条接收回执、来源设备、保存目录和接收时间可在桌面端查看。

## 运行环境

- Windows 10/11 x64。
- 首次运行点击“启用局域网访问”，在 Windows 权限提示中确认；企业 WLAN 显示为“公用网络”时也适用该规则。
- 手机和电脑连接同一局域网；访客 Wi-Fi、AP 隔离网络可能阻止直连。
- 当前协议使用局域网 HTTP。局域网演示与内网现场可用；生产部署建议置于 VPN/专网，或叠加 HTTPS 与证书固定后再上线。

## Windows 上运行

1. 关闭手机 Wi-Fi，打开手机热点，让电脑连接该手机热点。
2. 解压并打开 `云枢智维桌面接收器.exe`；首次也可双击 `start-hotspot-receiver.cmd`，它会清理旧版托盘实例并启动当前窗口。
3. 在“交付目录”选择例如 `D:\机房交付\Excel`，点击“启用局域网访问”，完成 Windows 权限提示后刷新二维码。
4. 打开云枢智维 App，进入“设置 → 已连接电脑 → 扫码连接电脑”，扫描电脑端二维码；App 显示“连接成功”即配对完成。
5. 手机完成识别后，点击“发送到电脑”。电脑端“最近交付”出现“已保存”文件后，双击该行即可用本机默认 Excel 或 WPS 打开对应工作簿。

若电脑与手机使用办公 WLAN，且该网络启用了设备隔离，可在“手机连接地址”选择“Windows 移动热点”，让手机连接这台电脑开启的移动热点，再刷新二维码并扫码。

## 从源码发布

在 Windows PowerShell 中，在项目根目录执行：

```powershell
.\scripts\build-release.ps1
```

发布件位于 `release\win-x64\云枢智维桌面接收器.exe`。脚本使用 .NET 8 SDK 生成自包含单文件，不要求目标电脑预装 .NET。

## 协议与 Android 对接

协议定义见 [docs/protocol-v1.md](docs/protocol-v1.md)，Android 适配方案见 [docs/android-adaptation-plan.md](docs/android-adaptation-plan.md)。桌面端 API 仅开放以下路径：

- `GET /api/v1/health`
- `POST /api/v1/pair`
- `POST/GET/PUT/DELETE /api/v1/uploads/...`

生产版本应在 Android 端限制为受控局域网地址（RFC1918 与企业 WLAN 的 RFC6598 地址段），并在错误、超时和配对失效时给出可操作的提示。

## 项目结构

```text
src/YunshuReceiver/        WPF 桌面端、托盘和 Kestrel 接收服务
docs/protocol-v1.md        手机与电脑的 HTTP 协议
docs/android-adaptation-plan.md
scripts/build-release.ps1  Windows 发布脚本
tests/                     协议和文件规则测试
```
