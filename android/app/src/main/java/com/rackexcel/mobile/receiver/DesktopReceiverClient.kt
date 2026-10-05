package com.rackexcel.mobile.receiver

import com.rackexcel.mobile.network.RetryExecutor
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

typealias ReceiverRetryListener = suspend (retryAttempt: Int, error: DesktopReceiverException) -> Unit

/**
 * HTTP client for the local Windows receiver protocol. This layer has no UI
 * dependencies and never logs QR secrets or Bearer tokens.
 */
class DesktopReceiverClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val endpointResolver: (host: String, port: Int) -> String = { host, port -> "http://$host:$port" },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun health(
        invite: ReceiverPairingInvite,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverHealth = health(invite.host, invite.port, onRetry)

    suspend fun health(
        connection: ReceiverConnection,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverHealth = health(connection.host, connection.port, onRetry)

    suspend fun pair(
        invite: ReceiverPairingInvite,
        deviceId: String,
        deviceName: String,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverConnection {
        validateEndpoint(invite.host, invite.port)
        if (invite.expiresAtEpochSeconds * 1_000L <= nowEpochMillis()) {
            throw DesktopReceiverException.validation("桌面接收器二维码已过期，请在电脑端刷新后重试")
        }
        val health = health(invite, onRetry)
        if (health.protocolVersion != PROTOCOL_VERSION || health.status.lowercase() != "ready") {
            throw DesktopReceiverException.protocol("桌面接收器尚未就绪")
        }
        if (health.receiverId != invite.receiverId) {
            throw DesktopReceiverException.protocol("桌面接收器标识与二维码不一致")
        }
        val safeDeviceId = deviceId.trim().takeIf { it.isNotEmpty() }
            ?: throw DesktopReceiverException.validation("手机设备标识无效")
        val body = JSONObject()
            .put("pairingSecret", invite.pairingSecret)
            .put("deviceId", safeDeviceId)
            .put("deviceName", deviceName.trim().ifBlank { "现场手机" })
            .toString()
        val response = requestJson(
            host = invite.host,
            port = invite.port,
            method = HttpMethod.POST,
            path = PAIR_PATH,
            body = body,
            onRetry = onRetry,
        )
        val root = jsonObject(response, "配对响应")
        val receiverId = root.requiredString("receiverId", "receiver_id")
        if (receiverId != invite.receiverId) {
            throw DesktopReceiverException.protocol("桌面接收器标识与二维码不一致")
        }
        val responseDeviceId = root.requiredString("deviceId", "device_id")
        if (responseDeviceId != safeDeviceId) {
            throw DesktopReceiverException.protocol("桌面接收器配对设备标识不一致")
        }
        val protocolVersion = root.requiredString("protocolVersion", "protocol_version")
        if (protocolVersion != PROTOCOL_VERSION) {
            throw DesktopReceiverException.protocol("桌面接收器协议版本不匹配")
        }
        val token = root.requiredString("accessToken", "access_token")
        val expiresAt = root.optionalLong("expiresAtEpochMillis", "expires_at_epoch_millis")
            ?: root.requiredString("expiresAtUtc", "expires_at_utc").toEpochMillis("桌面接收器配对期限")
        if (expiresAt <= nowEpochMillis()) {
            throw DesktopReceiverException.protocol("桌面接收器返回了已过期的配对期限")
        }
        return ReceiverConnection(
            receiverId = receiverId,
            receiverName = root.requiredString("receiverName", "receiver_name"),
            host = invite.host,
            port = invite.port,
            deviceId = safeDeviceId,
            accessToken = token,
            expiresAtEpochMillis = expiresAt,
            protocolVersion = protocolVersion,
            pairedAtEpochMillis = nowEpochMillis(),
        )
    }

    suspend fun calculateSha256(source: ReceiverUploadSource): String = withContext(Dispatchers.IO) {
        validateSource(source)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            source.openStream().use { input ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    total += read
                }
            }
        } catch (error: DesktopReceiverException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw DesktopReceiverException.validation("读取待发送 Excel 失败：${error.message ?: "文件不可读"}")
        }
        if (total != source.totalBytes) {
            throw DesktopReceiverException.validation("待发送 Excel 的大小已变化，请重新生成后再发送")
        }
        digest.digest().toHex()
    }

    suspend fun initUpload(
        connection: ReceiverConnection,
        fileName: String,
        totalBytes: Long,
        sha256: String,
        taskId: String,
        resumeUploadId: String? = null,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverUploadSession {
        validateConnection(connection)
        validateUploadMetadata(fileName, totalBytes, sha256)
        val body = JSONObject()
            .put("fileName", fileName)
            .put("totalBytes", totalBytes)
            .put("sha256", sha256.uppercase())
            .put("taskId", taskId)
            .apply { resumeUploadId?.takeIf(String::isNotBlank)?.let { put("uploadId", it) } }
            .toString()
        return parseUploadSession(
            jsonObject(
                requestJson(
                    host = connection.host,
                    port = connection.port,
                    method = HttpMethod.POST,
                    path = INIT_PATH,
                    body = body,
                    accessToken = connection.accessToken,
                    onRetry = onRetry,
                ),
                "上传初始化响应",
            ),
            expectedSha256 = null,
        ).also { session ->
            if (session.totalBytes != totalBytes || session.fileName != fileName) {
                throw DesktopReceiverException.protocol("桌面接收器返回的上传会话与待发送文件不一致")
            }
        }
    }

    suspend fun uploadStatus(
        connection: ReceiverConnection,
        uploadId: String,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverUploadSession {
        validateConnection(connection)
        val safeUploadId = uploadId.trim().takeIf { it.isNotEmpty() }
            ?: throw DesktopReceiverException.validation("上传会话标识无效")
        return parseUploadSession(
            jsonObject(
                requestJson(
                    host = connection.host,
                    port = connection.port,
                    method = HttpMethod.GET,
                    path = "$UPLOADS_PATH/$safeUploadId",
                    accessToken = connection.accessToken,
                    onRetry = onRetry,
                ),
                "上传状态响应",
            ),
            expectedSha256 = null,
        )
    }

    suspend fun uploadChunk(
        connection: ReceiverConnection,
        uploadId: String,
        index: Int,
        bytes: ByteArray,
        onRetry: ReceiverRetryListener? = null,
    ) {
        validateConnection(connection)
        if (index < 0 || bytes.isEmpty() || bytes.size > MAX_CHUNK_SIZE) {
            throw DesktopReceiverException.validation("Excel 上传分片无效")
        }
        requestRaw(
            host = connection.host,
            port = connection.port,
            method = HttpMethod.PUT,
            path = "$UPLOADS_PATH/${uploadId.trim()}/chunks/$index",
            body = bytes,
            accessToken = connection.accessToken,
            extraHeaders = mapOf(CHUNK_SHA_HEADER to MessageDigest.getInstance("SHA-256").digest(bytes).toHex()),
            onRetry = onRetry,
        )
    }

    suspend fun completeUpload(
        connection: ReceiverConnection,
        uploadId: String,
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverTransferReceipt {
        validateConnection(connection)
        val root = jsonObject(
            requestRaw(
                host = connection.host,
                port = connection.port,
                method = HttpMethod.POST,
                path = "$UPLOADS_PATH/${uploadId.trim()}/complete",
                body = ByteArray(0),
                accessToken = connection.accessToken,
                onRetry = onRetry,
            ),
            "接收回执",
        )
        return ReceiverTransferReceipt(
            uploadId = root.requiredString("uploadId", "upload_id"),
            fileName = root.requiredString("fileName", "file_name"),
            savedPath = root.requiredString("savedPath", "saved_path"),
            size = root.requiredLong("size"),
            sha256 = root.requiredString("sha256").uppercase(),
            receivedAtEpochMillis = root.requiredString("receivedAtUtc", "received_at_utc").toEpochMillis("接收时间"),
            deviceName = root.requiredString("deviceName", "device_name"),
        )
    }

    suspend fun abortUpload(
        connection: ReceiverConnection,
        uploadId: String,
        onRetry: ReceiverRetryListener? = null,
    ) {
        validateConnection(connection)
        requestRaw(
            host = connection.host,
            port = connection.port,
            method = HttpMethod.DELETE,
            path = "$UPLOADS_PATH/${uploadId.trim()}",
            accessToken = connection.accessToken,
            onRetry = onRetry,
        )
    }

    /**
     * Computes the whole-file digest before initializing the receiver session,
     * then streams one bounded chunk at a time. [onCheckpoint] is invoked after
     * initialization and each acknowledged chunk so the caller can resume later.
     */
    suspend fun upload(
        connection: ReceiverConnection,
        source: ReceiverUploadSource,
        taskId: String,
        sourceUri: String? = null,
        checkpoint: ReceiverUploadCheckpoint? = null,
        onCheckpoint: suspend (ReceiverUploadCheckpoint) -> Unit = {},
        onProgress: suspend (ReceiverUploadProgress) -> Unit = {},
        onRetry: ReceiverRetryListener? = null,
    ): ReceiverTransferReceipt {
        validateConnection(connection)
        validateSource(source)
        val sha256 = calculateSha256(source)
        val reusableCheckpoint = checkpoint?.takeIf {
            it.receiverId == connection.receiverId &&
                it.fileName == source.fileName &&
                it.totalBytes == source.totalBytes &&
                it.sha256.equals(sha256, ignoreCase = true)
        }
        suspend fun freshSession(resumeUploadId: String?): ReceiverUploadSession = initUpload(
            connection = connection,
            fileName = source.fileName,
            totalBytes = source.totalBytes,
            sha256 = sha256,
            taskId = taskId,
            resumeUploadId = resumeUploadId,
            onRetry = onRetry,
        )
        var session = try {
            freshSession(reusableCheckpoint?.uploadId)
        } catch (error: DesktopReceiverException) {
            // The desktop receiver clears stale staging sessions. A prior checkpoint
            // is then no longer resumable, so restart this file with a new session.
            if (reusableCheckpoint != null && error.httpStatus == 404) freshSession(null) else throw error
        }
        if (reusableCheckpoint != null && session.uploadId == reusableCheckpoint.uploadId) {
            session = try {
                uploadStatus(connection, session.uploadId, onRetry)
            } catch (error: DesktopReceiverException) {
                if (error.httpStatus == 404) freshSession(null) else throw error
            }
        }
        if (session.chunkSize !in 1..MAX_CHUNK_SIZE) {
            throw DesktopReceiverException.protocol("桌面接收器返回了不受支持的分片大小")
        }
        if (session.totalBytes != source.totalBytes || session.fileName != source.fileName) {
            throw DesktopReceiverException.protocol("桌面接收器上传会话与当前 Excel 不一致")
        }

        val totalChunks = chunkCount(source.totalBytes, session.chunkSize)
        val received = session.receivedChunks.filterTo(linkedSetOf()) { it in 0 until totalChunks }
        suspend fun publishCheckpoint() {
            onCheckpoint(
                ReceiverUploadCheckpoint(
                    taskId = taskId,
                    uploadId = session.uploadId,
                    fileName = source.fileName,
                    totalBytes = source.totalBytes,
                    sha256 = sha256,
                    receivedChunks = received.toSet(),
                    receiverId = connection.receiverId,
                    sourceUri = sourceUri ?: reusableCheckpoint?.sourceUri,
                    updatedAtEpochMillis = nowEpochMillis(),
                ),
            )
        }
        suspend fun publishProgress() {
            val confirmedBytes = received.sumOf { index -> chunkLength(source.totalBytes, session.chunkSize, index) }
            onProgress(
                ReceiverUploadProgress(
                    uploadId = session.uploadId,
                    totalChunks = totalChunks,
                    completedChunks = received.size,
                    sentBytes = confirmedBytes,
                    totalBytes = source.totalBytes,
                ),
            )
        }

        publishCheckpoint()
        publishProgress()
        try {
            source.openStream().use { input ->
                for (index in 0 until totalChunks) {
                    val expectedLength = chunkLength(source.totalBytes, session.chunkSize, index).toInt()
                    val chunk = readChunk(input, expectedLength)
                    if (index !in received) {
                        uploadChunk(connection, session.uploadId, index, chunk, onRetry)
                        received += index
                        publishCheckpoint()
                        publishProgress()
                    }
                }
                if (input.read() != -1) {
                    throw DesktopReceiverException.validation("待发送 Excel 的大小已变化，请重新生成后再发送")
                }
            }
        } catch (error: DesktopReceiverException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw DesktopReceiverException.validation("读取待发送 Excel 失败：${error.message ?: "文件不可读"}")
        }
        return completeUpload(connection, session.uploadId, onRetry)
    }

    private suspend fun health(
        host: String,
        port: Int,
        onRetry: ReceiverRetryListener?,
    ): ReceiverHealth {
        validateEndpoint(host, port)
        val root = jsonObject(
            requestJson(host, port, HttpMethod.GET, HEALTH_PATH, onRetry = onRetry),
            "桌面接收器状态响应",
        )
        val health = ReceiverHealth(
            receiverId = root.requiredString("receiver_id", "receiverId"),
            receiverName = root.requiredString("receiver_name", "receiverName"),
            protocolVersion = root.requiredString("protocol_version", "protocolVersion"),
            status = root.requiredString("status"),
        )
        if (health.protocolVersion != PROTOCOL_VERSION) {
            throw DesktopReceiverException.protocol("桌面接收器协议版本不匹配")
        }
        return health
    }

    private suspend fun requestJson(
        host: String,
        port: Int,
        method: HttpMethod,
        path: String,
        body: String? = null,
        accessToken: String? = null,
        onRetry: ReceiverRetryListener? = null,
    ): String = request(
        host = host,
        port = port,
        method = method,
        path = path,
        body = body?.toRequestBody(JSON_MEDIA_TYPE),
        accessToken = accessToken,
        onRetry = onRetry,
    )

    private suspend fun requestRaw(
        host: String,
        port: Int,
        method: HttpMethod,
        path: String,
        body: ByteArray? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        onRetry: ReceiverRetryListener? = null,
    ): String = request(
        host = host,
        port = port,
        method = method,
        path = path,
        body = body?.toRequestBody(BINARY_MEDIA_TYPE),
        accessToken = accessToken,
        extraHeaders = extraHeaders,
        onRetry = onRetry,
    )

    private suspend fun request(
        host: String,
        port: Int,
        method: HttpMethod,
        path: String,
        body: okhttp3.RequestBody? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        onRetry: ReceiverRetryListener? = null,
    ): String = withContext(Dispatchers.IO) {
        validateEndpoint(host, port)
        val baseUrl = endpointResolver(host, port).trimEnd('/')
        val url = "$baseUrl$path"
        val result = RetryExecutor(
            maxRetries = DesktopReceiverRetryPolicy.MAX_RETRIES,
            delayMillis = DesktopReceiverRetryPolicy::delayMillis,
        ).attempt(
            onRetry = { retry, error ->
                val receiverError = error as? DesktopReceiverException
                    ?: DesktopReceiverException.network(error)
                onRetry?.invoke(retry, receiverError)
            },
            shouldRetry = DesktopReceiverRetryPolicy::shouldRetry,
        ) {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
            accessToken?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
            extraHeaders.forEach { (name, value) -> builder.header(name, value) }
            when (method) {
                HttpMethod.GET -> builder.get()
                HttpMethod.POST -> builder.post(body ?: ByteArray(0).toRequestBody(BINARY_MEDIA_TYPE))
                HttpMethod.PUT -> builder.put(body ?: throw DesktopReceiverException.validation("上传分片缺少内容"))
                HttpMethod.DELETE -> builder.delete()
            }
            execute(builder.build())
        }
        result.value ?: throw (result.error as? DesktopReceiverException
            ?: DesktopReceiverException.network(result.error ?: IllegalStateException("请求桌面接收器失败")))
    }

    private fun execute(request: Request): String = try {
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw responseError(response.code, responseBody)
            responseBody
        }
    } catch (error: DesktopReceiverException) {
        throw error
    } catch (error: java.io.IOException) {
        throw DesktopReceiverException.network(error)
    }

    private fun responseError(status: Int, body: String): DesktopReceiverException {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")?.trim()
        }.getOrNull().orEmpty().take(240)
        val message = detail.ifBlank { "桌面接收器返回 HTTP $status" }
        return DesktopReceiverException.http(status, message)
    }

    private fun jsonObject(body: String, responseName: String): JSONObject = runCatching {
        // Some Windows HTTP stacks prefix UTF-8 JSON with a BOM. Android's
        // JSONObject treats that byte order mark as a non-JSON leading token.
        JSONObject(body.removePrefix("\uFEFF").trim())
    }.getOrElse {
        throw DesktopReceiverException.protocol("$responseName 格式无效")
    }

    private fun parseUploadSession(root: JSONObject, expectedSha256: String?): ReceiverUploadSession {
        val chunkSize = root.requiredInt("chunkSize", "chunk_size")
        if (chunkSize !in 1..MAX_CHUNK_SIZE) {
            throw DesktopReceiverException.protocol("桌面接收器返回了不受支持的分片大小")
        }
        val sha = root.optionalString("sha256")?.uppercase()
        if (expectedSha256 != null && sha != null && !sha.equals(expectedSha256, ignoreCase = true)) {
            throw DesktopReceiverException.protocol("桌面接收器上传状态与当前 Excel 校验值不一致")
        }
        return ReceiverUploadSession(
            uploadId = root.requiredString("uploadId", "upload_id"),
            chunkSize = chunkSize,
            receivedChunks = root.optionalIntArray("receivedChunks", "received_chunks").toSet(),
            totalBytes = root.requiredLong("totalBytes", "total_bytes"),
            fileName = root.requiredString("fileName", "file_name"),
            sha256 = sha,
        )
    }

    private fun validateConnection(connection: ReceiverConnection) {
        validateEndpoint(connection.host, connection.port)
        if (connection.receiverId.isBlank() || connection.accessToken.isBlank() || connection.protocolVersion != PROTOCOL_VERSION) {
            throw DesktopReceiverException.validation("已连接电脑配置无效，请重新扫码连接")
        }
        if (connection.expiresAtEpochMillis <= nowEpochMillis()) {
            throw DesktopReceiverException.validation("电脑连接已过期，请重新扫码连接")
        }
    }

    private fun validateEndpoint(host: String, port: Int) {
        if (!ReceiverPairingUriParser.isPrivateIpv4(host) || port !in 1..65_535) {
            throw DesktopReceiverException.validation("桌面接收器必须使用受控局域网 IPv4 地址")
        }
    }

    private fun validateSource(source: ReceiverUploadSource) {
        validateUploadMetadata(source.fileName, source.totalBytes, null)
    }

    private fun validateUploadMetadata(fileName: String, totalBytes: Long, sha256: String?) {
        if (!fileName.lowercase().endsWith(".xlsx")) {
            throw DesktopReceiverException.validation("桌面接收器只接收 .xlsx 交付文件")
        }
        if (totalBytes !in 1..MAX_FILE_SIZE) {
            throw DesktopReceiverException.validation("待发送 Excel 大小超出桌面接收器支持范围")
        }
        if (sha256 != null && !SHA256_REGEX.matches(sha256)) {
            throw DesktopReceiverException.validation("待发送 Excel 校验值无效")
        }
    }

    private fun readChunk(input: InputStream, expectedLength: Int): ByteArray {
        val result = ByteArray(expectedLength)
        var offset = 0
        while (offset < expectedLength) {
            val read = input.read(result, offset, expectedLength - offset)
            if (read < 0) throw DesktopReceiverException.validation("待发送 Excel 的大小已变化，请重新生成后再发送")
            if (read > 0) offset += read
        }
        return result
    }

    private fun chunkCount(totalBytes: Long, chunkSize: Int): Int =
        ((totalBytes + chunkSize - 1) / chunkSize).toInt()

    private fun chunkLength(totalBytes: Long, chunkSize: Int, index: Int): Long =
        minOf(chunkSize.toLong(), totalBytes - index.toLong() * chunkSize)

    private fun JSONObject.requiredString(primary: String, alternate: String? = null): String =
        optionalString(primary, alternate)?.takeIf { it.isNotBlank() }
            ?: throw DesktopReceiverException.protocol("桌面接收器响应缺少 $primary")

    private fun JSONObject.optionalString(primary: String, alternate: String? = null): String? =
        listOfNotNull(primary, alternate).firstNotNullOfOrNull { key ->
            optString(key).trim().takeIf { it.isNotEmpty() }
        }

    private fun JSONObject.requiredLong(primary: String, alternate: String? = null): Long {
        listOfNotNull(primary, alternate).forEach { key ->
            if (has(key)) return optLong(key)
        }
        throw DesktopReceiverException.protocol("桌面接收器响应缺少 $primary")
    }

    private fun JSONObject.optionalLong(primary: String, alternate: String? = null): Long? {
        listOfNotNull(primary, alternate).forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            val raw = opt(key)
            when (raw) {
                is Number -> return raw.toLong()
                is String -> raw.trim().toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.requiredInt(primary: String, alternate: String? = null): Int {
        listOfNotNull(primary, alternate).forEach { key ->
            if (has(key)) return optInt(key)
        }
        throw DesktopReceiverException.protocol("桌面接收器响应缺少 $primary")
    }

    private fun JSONObject.optionalIntArray(primary: String, alternate: String? = null): List<Int> {
        val array = listOfNotNull(primary, alternate)
            .firstNotNullOfOrNull { key -> optJSONArray(key) } ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                if (!array.isNull(index)) add(array.optInt(index))
            }
        }
    }

    private fun String.toEpochMillis(fieldName: String): Long = runCatching {
        Instant.parse(this).toEpochMilli()
    }.getOrElse { throw DesktopReceiverException.protocol("桌面接收器返回的$fieldName 无效") }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

    private enum class HttpMethod { GET, POST, PUT, DELETE }

    private companion object {
        const val PROTOCOL_VERSION = "1"
        const val HEALTH_PATH = "/api/v1/health"
        const val PAIR_PATH = "/api/v1/pair"
        const val INIT_PATH = "/api/v1/uploads/init"
        const val UPLOADS_PATH = "/api/v1/uploads"
        const val CHUNK_SHA_HEADER = "X-Chunk-Sha256"
        const val MAX_CHUNK_SIZE = 4 * 1024 * 1024
        const val MAX_FILE_SIZE = 200L * 1024 * 1024
        const val STREAM_BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "YunshuRackMobile/2.8 (Android)"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
    }
}
