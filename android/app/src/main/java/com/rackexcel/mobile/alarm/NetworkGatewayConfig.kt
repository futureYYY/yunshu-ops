package com.rackexcel.mobile.alarm

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.time.OffsetDateTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.TimeUnit

enum class NetworkAuthMode(val label: String) {
    BEARER("Bearer Token"),
    API_KEY("API Key"),
    BASIC("Basic Auth"),
    CUSTOM_HEADER("自定义请求头"),
    NONE("无认证"),
}

data class NetworkGatewayFieldMapping(
    val alarmId: String = "alarmId",
    val deviceName: String = "device.deviceName",
    val managementIp: String = "device.managementIp",
    val assetId: String = "device.assetId",
    val severity: String = "severity",
    val occurredAt: String = "occurredAt",
    val description: String = "description",
    val status: String = "status",
)

data class NetworkGatewayConfig(
    val id: String = "network-default",
    val name: String = "生产网管",
    val baseUrl: String = "",
    val apiVersion: String = "v1",
    val healthPath: String = "/health",
    val alarmsPath: String = "/alarms",
    val timeoutSeconds: Int = 15,
    val authMode: NetworkAuthMode = NetworkAuthMode.BEARER,
    val credential: String = "",
    val headerName: String = "X-API-Key",
    val fieldMapping: NetworkGatewayFieldMapping = NetworkGatewayFieldMapping(),
    val lastCheckAtMillis: Long? = null,
    val lastLatencyMillis: Long? = null,
    val lastCheckMessage: String = "尚未检测",
    val lastCheckSucceeded: Boolean? = null,
) {
    val healthUrl: String get() = NetworkGatewayConfigPolicy.joinUrl(this, healthPath)
    val alarmsUrl: String get() = NetworkGatewayConfigPolicy.joinUrl(this, alarmsPath)
}

object NetworkGatewayConfigPolicy {
    fun normalize(config: NetworkGatewayConfig): NetworkGatewayConfig {
        val base = config.baseUrl.trim().trimEnd('/')
        val api = config.apiVersion.trim().trim('/').take(40)
        return config.copy(
            name = config.name.trim().ifBlank { "未命名网管" }.take(80),
            baseUrl = base,
            apiVersion = api,
            healthPath = normalizePath(config.healthPath),
            alarmsPath = normalizePath(config.alarmsPath),
            timeoutSeconds = config.timeoutSeconds.coerceIn(5, 60),
            headerName = config.headerName.trim().ifBlank { "X-API-Key" }.take(80),
        )
    }

    fun joinUrl(config: NetworkGatewayConfig, path: String): String {
        val parts = listOf(config.baseUrl.trim().trimEnd('/'), config.apiVersion.trim().trim('/'), normalizePath(path).trimStart('/'))
            .filter { it.isNotBlank() }
        return parts.joinToString("/")
    }

    fun validate(config: NetworkGatewayConfig): String? {
        val value = normalize(config)
        if (value.baseUrl.isBlank()) return "请填写网关地址"
        if (!value.baseUrl.startsWith("http://") && !value.baseUrl.startsWith("https://")) return "网关地址必须以 http:// 或 https:// 开头"
        if (value.authMode != NetworkAuthMode.NONE && value.credential.isBlank()) return "请填写认证凭据"
        if (value.authMode == NetworkAuthMode.CUSTOM_HEADER && value.headerName.isBlank()) return "请填写自定义请求头名称"
        return null
    }

    private fun normalizePath(path: String): String = "/" + path.trim().trim('/').take(160)
}

data class NetworkGatewayCheckResult(
    val succeeded: Boolean,
    val message: String,
    val latencyMillis: Long,
)

/** Persists endpoint metadata separately from the model profile store. Credentials are encrypted at rest. */
class NetworkGatewayConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val delegate = GatewaySecretDelegate(preferences)

    fun load(): NetworkGatewayConfig = runCatching {
        val plain = preferences.getString(KEY_CONFIG, null)?.let(::JSONObject) ?: JSONObject()
        NetworkGatewayConfig(
            id = plain.optString("id", "network-default"), name = plain.optString("name", "生产网管"),
            baseUrl = plain.optString("baseUrl"), apiVersion = plain.optString("apiVersion", "v1"),
            healthPath = plain.optString("healthPath", "/health"), alarmsPath = plain.optString("alarmsPath", "/alarms"),
            timeoutSeconds = plain.optInt("timeoutSeconds", 15),
            authMode = NetworkAuthMode.entries.firstOrNull { it.name == plain.optString("authMode") } ?: NetworkAuthMode.BEARER,
            credential = delegate.loadSecret(), headerName = plain.optString("headerName", "X-API-Key"),
            fieldMapping = NetworkGatewayFieldMapping(
                alarmId = plain.optString("mapAlarmId", "alarmId"), deviceName = plain.optString("mapDeviceName", "device.deviceName"),
                managementIp = plain.optString("mapManagementIp", "device.managementIp"), assetId = plain.optString("mapAssetId", "device.assetId"),
                severity = plain.optString("mapSeverity", "severity"), occurredAt = plain.optString("mapOccurredAt", "occurredAt"),
                description = plain.optString("mapDescription", "description"), status = plain.optString("mapStatus", "status"),
            ), lastCheckAtMillis = plain.optLong("lastCheckAtMillis", -1L).takeIf { it >= 0 },
            lastLatencyMillis = plain.optLong("lastLatencyMillis", -1L).takeIf { it >= 0 },
            lastCheckMessage = plain.optString("lastCheckMessage", "尚未检测"),
            lastCheckSucceeded = if (plain.has("lastCheckSucceeded")) plain.optBoolean("lastCheckSucceeded") else null,
        )
    }.getOrElse { NetworkGatewayConfig() }

    fun save(config: NetworkGatewayConfig): NetworkGatewayConfig {
        val value = NetworkGatewayConfigPolicy.normalize(config)
        preferences.edit().putString(KEY_CONFIG, JSONObject().apply {
            put("id", value.id); put("name", value.name); put("baseUrl", value.baseUrl); put("apiVersion", value.apiVersion)
            put("healthPath", value.healthPath); put("alarmsPath", value.alarmsPath); put("timeoutSeconds", value.timeoutSeconds)
            put("authMode", value.authMode.name); put("headerName", value.headerName)
            put("mapAlarmId", value.fieldMapping.alarmId); put("mapDeviceName", value.fieldMapping.deviceName)
            put("mapManagementIp", value.fieldMapping.managementIp); put("mapAssetId", value.fieldMapping.assetId)
            put("mapSeverity", value.fieldMapping.severity); put("mapOccurredAt", value.fieldMapping.occurredAt)
            put("mapDescription", value.fieldMapping.description); put("mapStatus", value.fieldMapping.status)
            value.lastCheckAtMillis?.let { put("lastCheckAtMillis", it) }; value.lastLatencyMillis?.let { put("lastLatencyMillis", it) }
            put("lastCheckMessage", value.lastCheckMessage); value.lastCheckSucceeded?.let { put("lastCheckSucceeded", it) }
        }.toString()).apply()
        delegate.saveSecret(value.credential)
        return value
    }

    private class GatewaySecretDelegate(private val preferences: SharedPreferences) {
        fun loadSecret(): String = runCatching {
            val cipherText = preferences.getString(KEY_SECRET, null) ?: return ""
            val iv = preferences.getString(KEY_SECRET_IV, null) ?: return ""
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
                doFinal(decode(cipherText)).toString(StandardCharsets.UTF_8)
            }
        }.getOrDefault("")

        fun saveSecret(value: String) {
            if (value.isBlank()) {
                preferences.edit().remove(KEY_SECRET).remove(KEY_SECRET_IV).apply()
                return
            }
            runCatching {
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.ENCRYPT_MODE, key())
                    preferences.edit()
                        .putString(KEY_SECRET_IV, encode(iv))
                        .putString(KEY_SECRET, encode(doFinal(value.toByteArray(StandardCharsets.UTF_8))))
                        .apply()
                }
            }
        }

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
        private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
    }

    private companion object {
        const val PREFERENCES = "yunshu_network_gateway"
        const val KEY_CONFIG = "config"
        const val KEY_SECRET = "credential_ciphertext"
        const val KEY_SECRET_IV = "credential_iv"
        const val KEY_ALIAS = "yunshu_network_gateway_credential"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

object NetworkAlarmPayloadAdapter {
    fun decode(payload: String, mapping: NetworkGatewayFieldMapping = NetworkGatewayFieldMapping()): List<AlarmRecord> {
        val textPayload = payload.trim()
        val root = textPayload.takeUnless { it.startsWith("[") }?.let(::JSONObject)
        val values = when {
            textPayload.startsWith("[") -> JSONArray(textPayload)
            root?.optJSONArray("data") != null -> root.optJSONArray("data")!!
            root?.optJSONArray("alarms") != null -> root.optJSONArray("alarms")!!
            root?.optJSONArray("items") != null -> root.optJSONArray("items")!!
            root != null -> JSONArray().put(root)
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = text(item, mapping.alarmId, "alarmId", "alarm_id", "id")
                val deviceName = text(item, mapping.deviceName, "deviceName", "device_name", "name", "device.deviceName")
                val ip = text(item, mapping.managementIp, "managementIp", "management_ip", "ip", "device.managementIp")
                val asset = text(item, mapping.assetId, "assetId", "asset_id", "assetNo", "device.assetId")
                val description = text(item, mapping.description, "description", "message", "告警描述")
                if (id.isBlank() || description.isBlank() || listOf(deviceName, ip, asset).all(String::isBlank)) continue
                val time = parseTime(value(item, mapping.occurredAt) ?: value(item, "occurredAt") ?: value(item, "occurred_at") ?: value(item, "发生时间"))
                    ?: throw AlarmDataFormatException("网管告警第 ${index + 1} 项发生时间无效")
                add(AlarmRecord(id, AlarmDeviceIdentity(deviceName, ip, asset), AlarmSeverity.from(text(item, mapping.severity, "severity", "level", "告警等级")), time, description, AlarmStatus.from(text(item, mapping.status, "status", "state", "状态"))))
            }
        }
    }

    private fun value(obj: JSONObject, path: String): Any? {
        var current: Any? = obj
        for (segment in path.split('.')) {
            current = (current as? JSONObject)?.opt(segment)?.takeUnless { it == JSONObject.NULL } ?: return null
        }
        return current
    }
    private fun text(obj: JSONObject, vararg paths: String): String = paths.asSequence().mapNotNull { value(obj, it)?.toString()?.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun parseTime(value: Any?): Long? = when (value) {
        is Number -> value.toLong().let { if (it < 10_000_000_000L) it * 1000L else it }
        is String -> value.trim().toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it }
            ?: runCatching { Instant.parse(value).toEpochMilli() }.recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        else -> null
    }
}

class ConfiguredNetworkManagerGateway(private val config: NetworkGatewayConfig) : NetworkManagerAlarmGateway {
    private val client = OkHttpClient.Builder().callTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS).build()
    override suspend fun loadAlarms(): List<AlarmRecord> {
        val request = request(config.alarmsUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AlarmSourceException("网管告警接口返回 HTTP ${response.code}")
            return NetworkAlarmPayloadAdapter.decode(response.body?.string().orEmpty(), config.fieldMapping)
        }
    }
    override suspend fun updateAlarm(alarm: AlarmRecord): AlarmRecord {
        val url = config.alarmsUrl.trimEnd('/') + "/" + java.net.URLEncoder.encode(alarm.alarmId, StandardCharsets.UTF_8.name())
        val body = AlarmJsonCodec.toJsonObject(alarm).toString().toRequestBody("application/json".toMediaType())
        val request = request(url).put(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AlarmSourceException("网管告警回写返回 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            return if (text.isBlank()) alarm else NetworkAlarmPayloadAdapter.decode(text, config.fieldMapping).firstOrNull() ?: alarm
        }
    }
    suspend fun check(): NetworkGatewayCheckResult {
        val started = System.currentTimeMillis()
        return runCatching {
            val error = NetworkGatewayConfigPolicy.validate(config)
            if (error != null) throw AlarmSourceException(error)
            client.newCall(request(config.healthUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw AlarmSourceException("健康检查返回 HTTP ${response.code}")
            }
            NetworkGatewayCheckResult(true, "网管接口已连通 · 告警通道待命", System.currentTimeMillis() - started)
        }.getOrElse { NetworkGatewayCheckResult(false, it.message ?: "网管接口连接失败", System.currentTimeMillis() - started) }
    }
    private fun request(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when (config.authMode) {
            NetworkAuthMode.BEARER -> builder.header("Authorization", "Bearer ${config.credential}")
            NetworkAuthMode.API_KEY, NetworkAuthMode.CUSTOM_HEADER -> builder.header(config.headerName, config.credential)
            NetworkAuthMode.BASIC -> builder.header("Authorization", "Basic ${config.credential}")
            NetworkAuthMode.NONE -> Unit
        }
        return builder
    }
}
