package com.rackexcel.mobile.receiver

import java.io.IOException

/** A validated, one-time desktop receiver invitation decoded from its QR URI. */
data class ReceiverPairingInvite(
    val host: String,
    val port: Int,
    val receiverId: String,
    val receiverName: String,
    val pairingSecret: String,
    val expiresAtEpochSeconds: Long,
) {
    val baseUrl: String get() = "http://$host:$port"
}

class ReceiverPairingUriException(message: String) : IllegalArgumentException(message)

enum class DesktopReceiverErrorType {
    NETWORK,
    UNAUTHORIZED,
    PAIRING_EXPIRED,
    CONFLICT,
    UNSUPPORTED_FILE,
    VALIDATION,
    SERVER,
    PROTOCOL,
}

/**
 * Error returned by the desktop receiver transport. It deliberately omits
 * credentials and request bodies from its message and string representation.
 */
class DesktopReceiverException(
    message: String,
    val type: DesktopReceiverErrorType,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {
    val isRetryable: Boolean get() = DesktopReceiverRetryPolicy.shouldRetry(this)

    companion object {
        fun http(status: Int, message: String): DesktopReceiverException = DesktopReceiverException(
            message = message,
            type = typeForStatus(status),
            httpStatus = status,
        )

        fun network(cause: Throwable): DesktopReceiverException = DesktopReceiverException(
            message = "连接桌面接收器失败：${cause.message ?: "网络异常"}",
            type = DesktopReceiverErrorType.NETWORK,
            cause = cause,
        )

        fun protocol(message: String): DesktopReceiverException = DesktopReceiverException(
            message = message,
            type = DesktopReceiverErrorType.PROTOCOL,
        )

        fun validation(message: String): DesktopReceiverException = DesktopReceiverException(
            message = message,
            type = DesktopReceiverErrorType.VALIDATION,
        )

        private fun typeForStatus(status: Int): DesktopReceiverErrorType = when (status) {
            401, 403 -> DesktopReceiverErrorType.UNAUTHORIZED
            410 -> DesktopReceiverErrorType.PAIRING_EXPIRED
            409 -> DesktopReceiverErrorType.CONFLICT
            415 -> DesktopReceiverErrorType.UNSUPPORTED_FILE
            400, 404, 413, 422 -> DesktopReceiverErrorType.VALIDATION
            in 500..599 -> DesktopReceiverErrorType.SERVER
            else -> DesktopReceiverErrorType.PROTOCOL
        }
    }
}

data class ReceiverHealth(
    val receiverId: String,
    val receiverName: String,
    val protocolVersion: String,
    val status: String,
)

/**
 * A paired connection. [accessToken] must only come from the encrypted store
 * or the one-time pairing response. Its value is redacted from toString().
 */
data class ReceiverConnection(
    val receiverId: String,
    val receiverName: String,
    val host: String,
    val port: Int,
    val deviceId: String,
    val accessToken: String,
    val expiresAtEpochMillis: Long,
    val protocolVersion: String,
    val pairedAtEpochMillis: Long,
) {
    val baseUrl: String get() = "http://$host:$port"

    override fun toString(): String =
        "ReceiverConnection(receiverId=$receiverId, receiverName=$receiverName, host=$host, port=$port, " +
            "deviceId=$deviceId, accessToken=***, expiresAtEpochMillis=$expiresAtEpochMillis, " +
            "protocolVersion=$protocolVersion, pairedAtEpochMillis=$pairedAtEpochMillis)"
}

/** Token-free metadata that is safe to render in settings and task history. */
data class ConnectedComputer(
    val receiverId: String,
    val receiverName: String,
    val host: String,
    val port: Int,
    val deviceId: String,
    val expiresAtEpochMillis: Long,
    val protocolVersion: String,
    val pairedAtEpochMillis: Long,
)

fun ReceiverConnection.toConnectedComputer(): ConnectedComputer = ConnectedComputer(
    receiverId = receiverId,
    receiverName = receiverName,
    host = host,
    port = port,
    deviceId = deviceId,
    expiresAtEpochMillis = expiresAtEpochMillis,
    protocolVersion = protocolVersion,
    pairedAtEpochMillis = pairedAtEpochMillis,
)

data class ReceiverUploadSession(
    val uploadId: String,
    val chunkSize: Int,
    val receivedChunks: Set<Int>,
    val totalBytes: Long,
    val fileName: String,
    val sha256: String? = null,
)

data class ReceiverTransferReceipt(
    val uploadId: String,
    val fileName: String,
    val savedPath: String,
    val size: Long,
    val sha256: String,
    val receivedAtEpochMillis: Long,
    val deviceName: String,
)

data class ReceiverUploadSource(
    val fileName: String,
    val totalBytes: Long,
    val openStream: () -> java.io.InputStream,
)

data class ReceiverUploadCheckpoint(
    val taskId: String,
    val uploadId: String,
    val fileName: String,
    val totalBytes: Long,
    val sha256: String,
    val receivedChunks: Set<Int>,
    val receiverId: String,
    val sourceUri: String? = null,
    val updatedAtEpochMillis: Long,
)

data class ReceiverUploadProgress(
    val uploadId: String,
    val totalChunks: Int,
    val completedChunks: Int,
    val sentBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = if (totalBytes <= 0L) 0f else sentBytes.toFloat() / totalBytes.toFloat()
}
