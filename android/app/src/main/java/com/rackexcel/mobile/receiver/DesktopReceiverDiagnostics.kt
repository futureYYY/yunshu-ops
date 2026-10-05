package com.rackexcel.mobile.receiver

/**
 * Human-readable receiver errors shared by pairing, health checks, and delivery.
 * The message only exposes the LAN endpoint, never pairing secrets or access tokens.
 */
object DesktopReceiverDiagnostics {
    fun readableError(error: Throwable, endpoint: String? = null): String {
        val receiverError = error as? DesktopReceiverException
        return when (receiverError?.type) {
            DesktopReceiverErrorType.NETWORK -> networkMessage(endpoint)
            DesktopReceiverErrorType.UNAUTHORIZED,
            DesktopReceiverErrorType.PAIRING_EXPIRED -> "连接已过期，请在电脑端刷新二维码后重新连接。"
            DesktopReceiverErrorType.CONFLICT -> "电脑端拒绝了当前传输，请重新生成文件后再发送。"
            DesktopReceiverErrorType.UNSUPPORTED_FILE -> "桌面接收器仅接收 .xlsx 文件。"
            DesktopReceiverErrorType.VALIDATION -> receiverError.message ?: "交付文件校验未通过，请重新生成后重试。"
            DesktopReceiverErrorType.SERVER,
            DesktopReceiverErrorType.PROTOCOL -> receiverError.message ?: "桌面接收器响应异常，请检查接收器状态。"
            null -> error.message?.trim().takeUnless { it.isNullOrBlank() }
                ?: "请检查网络、图片质量和模型配置。"
        }
    }

    private fun networkMessage(endpoint: String?): String {
        val target = endpoint
            ?.trim()
            ?.takeIf { it.matches(Regex("[0-9.]+:[0-9]{1,5}")) }
            ?.let { "电脑 $it" }
            ?: "电脑端接收器"
        return "未收到${target}的响应。当前无线网络可能限制设备互访；请在电脑接收器的“手机连接地址”选择“Windows 移动热点”，开启电脑热点后让手机连接该热点，刷新二维码并重新扫码。"
    }
}
