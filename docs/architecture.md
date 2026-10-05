# 架构说明 / Architecture

## 1. 总览 / Overview

云枢智维（YunshuOps）由两个可独立运行、搭配使用的组件构成：

| 组件 | 平台 | 技术栈 |
| --- | --- | --- |
| `android/` | Android 8.0+ (minSdk 26) | Kotlin · Jetpack Compose · OkHttp · Android Keystore |
| `desktop-receiver/` | Windows 10/11 x64 | C# · .NET 8 · WPF + Windows Forms NotifyIcon · ASP.NET Core Kestrel · QRCoder |

数据流：

```
现场拍照/相册
      │
      ▼
 Android App ──► 多模态模型（OpenAI-compatible，用户自配端点）
      │                │
      │                ▼
      │        逐图 JSON 识别结果（柜号 / U 位 / 设备 / 风险项）
      ▼
 生成 .xlsx（三张工作表）
      │
      ▼  局域网 HTTP 分片上传（二维码一次性配对 + Bearer Token）
 Windows 桌面接收器 ──► 落地到指定目录（分片校验 / 整文件 SHA-256 / ZIP 结构校验）
```

## 2. Android 端分层 / Android layers

- `ui/` — Compose 页面与 ViewModel；工作台、智能识别、任务中心、设置四大作业页。
- `network/` — `VisionApiClient` / `VisionRequestBuilder`：OpenAI-compatible 多模态调用、并发限流、失败自修复。
- `storage/` — `SecureConfigStore`（AES-GCM + Android Keystore 加密保存多套模型配置）、任务与结果存储。
- `excel/` — 三张工作表的生成与版式（基于客户模板版式重建，输出不含模板样例数据）。
- `alarm/` — 告警联动演示：Mock 告警源 + 匹配策略（可替换为真实网管接口）。
- `receiver/` — 与桌面接收器的配对与上传客户端。

**模型配置不内置任何真实端点与密钥**：`BuildConfig.DEFAULT_*` / `FALLBACK_*` 默认为空，只接受构建期环境变量注入；运行时配置由用户在「设置 → 配置中心」填写，并以 Android Keystore 加密落盘。

## 3. 桌面接收器分层 / Desktop receiver layers

- `ReceiverService` — Kestrel HTTP 端点与路由，协议见 [`protocol-v1.md`](protocol-v1.md)。
- `PairingManager` — 一次性二维码配对（10 分钟有效）、随机 Bearer Token 签发与哈希保存。
- `UploadManager` — 4 MB 分片上传、断点续传、整文件 SHA-256 与 ZIP 结构校验、重名自动改名。
- `NetworkIdentity` — 选择实际在线网卡地址（含 Windows 移动热点 `192.168.137.1`）。
- `FirewallAccess` — 经管理员确认后为本程序创建入站规则。
- `SettingsStore` — DPAPI（`CurrentUser`）加密保存配置与 Token 哈希，不保存明文 Token。

## 4. 安全设计 / Security notes

- 手机与电脑在同一局域网直连，**不依赖中心服务器**。
- 配对凭证一次性、限时；Token 随机生成，桌面端只存哈希与 DPAPI 加密副本。
- 传输为局域网 HTTP；如需在生产环境部署，建议置于 VPN/专网，或自行叠加 HTTPS 与证书固定。
- 本仓库不包含任何凭据、私有端点或业务数据；发布前请运行 `tools/scan-secrets.sh`。

## 5. 已知边界 / Known limits

- 识别质量取决于现场照片质量与所选多模态模型能力；看不清的字段按「留空」处理，不臆测填充。
- 告警联动当前为演示模式（Mock 数据源），真实网管接口为可替换的二期适配层。
