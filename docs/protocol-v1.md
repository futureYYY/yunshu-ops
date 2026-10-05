# 云枢智维桌面接收器协议 v1

## 1. 配对

桌面端二维码携带一次性 URI：

```text
yunshu-receiver://pair?v=1&host=192.0.2.20&port=48120&receiver_id=...&receiver_name=...&pairing_secret=...&expires=...
```

手机扫码后向 `http://host:port/api/v1/pair` 发送：

```json
{
  "pairingSecret": "二维码中的 pairing_secret",
  "deviceId": "Android 安装实例的稳定随机 ID",
  "deviceName": "现场手机"
}
```

成功响应：

```json
{
  "receiverId": "电脑接收器 ID",
  "receiverName": "DESKTOP-01",
  "deviceId": "手机 ID",
  "accessToken": "仅本次响应返回的随机 Bearer Token",
  "expiresAtUtc": "2027-08-17T00:00:00Z",
  "protocolVersion": "1"
}
```

Android 端应将 `accessToken` 保存到 Android Keystore 保护的存储中，不能写入日志、剪贴板或普通明文偏好设置。桌面端只保存 Token 哈希。

## 2. 初始化上传

所有上传接口都带：`Authorization: Bearer <accessToken>`。

`POST /api/v1/uploads/init`

```json
{
  "fileName": "云枢智维_K03_K04.xlsx",
  "totalBytes": 123456,
  "sha256": "64 位大写十六进制 SHA-256",
  "taskId": "App 内任务 ID",
  "uploadId": "断点续传时填写上一次的 ID"
}
```

响应：

```json
{
  "uploadId": "服务端生成的 ID",
  "chunkSize": 4194304,
  "receivedChunks": [0, 1],
  "totalBytes": 123456,
  "fileName": "云枢智维_K03_K04.xlsx"
}
```

只接受 `.xlsx`，单文件上限默认 200 MB。

## 3. 分片和续传

- `GET /api/v1/uploads/{uploadId}`：查询已接收分片。
- `PUT /api/v1/uploads/{uploadId}/chunks/{index}`：请求体为原始二进制，最大 4 MB。
- 可带 `X-Chunk-Sha256`，电脑端会先校验分片再写入 staging。
- 手机端应把 `uploadId`、文件 SHA-256 和已确认分片持久化到任务详情。

网络超时、连接重置、HTTP 408/429/5xx 可重试，建议指数退避 1/2/4 秒，单分片最多 3 次；401/403/409/410/415/422 应停止并提示用户处理。

## 4. 完成和回执

`POST /api/v1/uploads/{uploadId}/complete` 会依次执行：

1. 检查 0 到 N-1 分片是否齐全；
2. 合并临时文件并校验整文件 SHA-256；
3. 检查 `[Content_Types].xml` 和 `xl/workbook.xml`；
4. 原子移动到电脑选择的目录；
5. 若同名则自动使用 `名称 (1).xlsx`。

成功响应：

```json
{
  "uploadId": "...",
  "fileName": "云枢智维_K03_K04 (1).xlsx",
  "savedPath": "D:\\机房交付\\Excel\\云枢智维_K03_K04 (1).xlsx",
  "size": 123456,
  "sha256": "...",
  "receivedAtUtc": "2026-08-17T08:30:00Z",
  "deviceName": "现场手机"
}
```

## 5. 传输边界

v1 是局域网直连方案，不上传中心服务器。二维码只负责发现地址和一次性授权；长期 Token 用于后续上传。当前实现默认 HTTP，因此应把电脑和手机放在受控专网/VPN 中。后续生产版可在 Kestrel 增加 HTTPS 证书、Android 证书固定和设备撤销审计。
