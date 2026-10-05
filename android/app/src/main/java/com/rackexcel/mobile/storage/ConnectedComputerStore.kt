package com.rackexcel.mobile.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rackexcel.mobile.receiver.ConnectedComputer
import com.rackexcel.mobile.receiver.ReceiverConnection
import com.rackexcel.mobile.receiver.ReceiverPairingUriParser
import com.rackexcel.mobile.receiver.ReceiverUploadCheckpoint
import com.rackexcel.mobile.receiver.toConnectedComputer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps desktop receiver credentials separate from model credentials. The whole
 * payload, including the Bearer token, is AES-GCM encrypted with this app's
 * dedicated Android Keystore key and never exposed through metadata APIs.
 */
class ConnectedComputerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun deviceId(): String = state().deviceId

    @Synchronized
    fun loadConnection(): ReceiverConnection? = state().connection

    @Synchronized
    fun connectedComputer(): ConnectedComputer? = state().connection?.toConnectedComputer()

    @Synchronized
    fun saveConnection(connection: ReceiverConnection) {
        require(ReceiverPairingUriParser.isPrivateIpv4(connection.host) && connection.port in 1..65_535) {
            "桌面接收器地址无效"
        }
        require(connection.receiverId.isNotBlank() && connection.deviceId.isNotBlank()) {
            "桌面接收器配对信息无效"
        }
        require(connection.accessToken.isNotBlank()) { "桌面接收器访问令牌无效" }
        val current = state()
        val checkpoints = current.checkpoints.filterValues { it.receiverId == connection.receiverId }
        save(current.copy(connection = connection, checkpoints = checkpoints))
    }

    /** Disconnects the current desktop computer while retaining the stable Android installation ID. */
    @Synchronized
    fun disconnect() {
        val current = state()
        save(current.copy(connection = null, checkpoints = emptyMap()))
    }

    @Synchronized
    fun loadCheckpoint(taskId: String): ReceiverUploadCheckpoint? = state().checkpoints[taskId]

    @Synchronized
    fun saveCheckpoint(checkpoint: ReceiverUploadCheckpoint) {
        require(checkpoint.taskId.isNotBlank() && checkpoint.uploadId.isNotBlank()) { "上传检查点无效" }
        require(checkpoint.totalBytes > 0L && checkpoint.sha256.matches(SHA256_REGEX)) { "上传检查点校验值无效" }
        val current = state()
        val normalized = checkpoint.copy(
            sha256 = checkpoint.sha256.uppercase(),
            receivedChunks = checkpoint.receivedChunks.filter { it >= 0 }.toSet(),
        )
        save(current.copy(checkpoints = current.checkpoints + (normalized.taskId to normalized)))
    }

    @Synchronized
    fun clearCheckpoint(taskId: String) {
        val current = state()
        if (taskId in current.checkpoints) save(current.copy(checkpoints = current.checkpoints - taskId))
    }

    @Synchronized
    fun clearAllCheckpoints() {
        val current = state()
        if (current.checkpoints.isNotEmpty()) save(current.copy(checkpoints = emptyMap()))
    }

    private fun state(): StoreState {
        decrypt()?.let { return it }
        return StoreState(deviceId = UUID.randomUUID().toString(), connection = null, checkpoints = emptyMap())
            .also(::save)
    }

    private fun decrypt(): StoreState? {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_IV, null)
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, decode(iv)))
            decodeState(JSONObject(cipher.doFinal(decode(ciphertext)).decodeToString()))
        }.getOrNull()
    }

    private fun save(state: StoreState) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(encodeState(state).toString().encodeToByteArray())
        preferences.edit()
            .putString(KEY_IV, encode(cipher.iv))
            .putString(KEY_CIPHERTEXT, encode(encrypted))
            .apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encodeState(state: StoreState): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("deviceId", state.deviceId)
        .put("connection", state.connection?.let(::encodeConnection))
        .put("checkpoints", JSONArray().apply {
            state.checkpoints.values.sortedBy { it.updatedAtEpochMillis }.forEach { put(encodeCheckpoint(it)) }
        })

    private fun encodeConnection(connection: ReceiverConnection): JSONObject = JSONObject()
        .put("receiverId", connection.receiverId)
        .put("receiverName", connection.receiverName)
        .put("host", connection.host)
        .put("port", connection.port)
        .put("deviceId", connection.deviceId)
        .put("accessToken", connection.accessToken)
        .put("expiresAtEpochMillis", connection.expiresAtEpochMillis)
        .put("protocolVersion", connection.protocolVersion)
        .put("pairedAtEpochMillis", connection.pairedAtEpochMillis)

    private fun encodeCheckpoint(checkpoint: ReceiverUploadCheckpoint): JSONObject = JSONObject()
        .put("taskId", checkpoint.taskId)
        .put("uploadId", checkpoint.uploadId)
        .put("fileName", checkpoint.fileName)
        .put("totalBytes", checkpoint.totalBytes)
        .put("sha256", checkpoint.sha256)
        .put("receivedChunks", JSONArray(checkpoint.receivedChunks.sorted()))
        .put("receiverId", checkpoint.receiverId)
        .put("sourceUri", checkpoint.sourceUri)
        .put("updatedAtEpochMillis", checkpoint.updatedAtEpochMillis)

    private fun decodeState(root: JSONObject): StoreState {
        val deviceId = root.optString("deviceId").trim().takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val connection = root.optJSONObject("connection")?.let(::decodeConnection)
        val checkpoints = buildMap {
            val values = root.optJSONArray("checkpoints") ?: JSONArray()
            for (index in 0 until values.length()) {
                decodeCheckpoint(values.optJSONObject(index) ?: continue)?.let { checkpoint ->
                    put(checkpoint.taskId, checkpoint)
                }
            }
        }
        return StoreState(deviceId, connection, checkpoints)
    }

    private fun decodeConnection(value: JSONObject): ReceiverConnection? = runCatching {
        val connection = ReceiverConnection(
            receiverId = value.required("receiverId"),
            receiverName = value.required("receiverName"),
            host = value.required("host"),
            port = value.required("port").toInt(),
            deviceId = value.required("deviceId"),
            accessToken = value.required("accessToken"),
            expiresAtEpochMillis = value.required("expiresAtEpochMillis").toLong(),
            protocolVersion = value.required("protocolVersion"),
            pairedAtEpochMillis = value.required("pairedAtEpochMillis").toLong(),
        )
        if (!ReceiverPairingUriParser.isPrivateIpv4(connection.host) || connection.port !in 1..65_535) null else connection
    }.getOrNull()

    private fun decodeCheckpoint(value: JSONObject): ReceiverUploadCheckpoint? = runCatching {
        val chunks = value.optJSONArray("receivedChunks") ?: JSONArray()
        val checkpoint = ReceiverUploadCheckpoint(
            taskId = value.required("taskId"),
            uploadId = value.required("uploadId"),
            fileName = value.required("fileName"),
            totalBytes = value.required("totalBytes").toLong(),
            sha256 = value.required("sha256").uppercase(),
            receivedChunks = buildSet {
                for (index in 0 until chunks.length()) chunks.optInt(index).takeIf { it >= 0 }?.let(::add)
            },
            receiverId = value.required("receiverId"),
            sourceUri = value.optString("sourceUri").trim().takeIf { it.isNotEmpty() },
            updatedAtEpochMillis = value.required("updatedAtEpochMillis").toLong(),
        )
        checkpoint.takeIf { it.totalBytes > 0L && it.sha256.matches(SHA256_REGEX) }
    }.getOrNull()

    private fun JSONObject.required(key: String): String = optString(key).trim().takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing $key")

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private data class StoreState(
        val deviceId: String,
        val connection: ReceiverConnection?,
        val checkpoints: Map<String, ReceiverUploadCheckpoint>,
    )

    private companion object {
        const val PREFERENCES = "connected_desktop_receiver_v1"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_ALIAS = "rack_excel.connected_desktop_receiver.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
        const val SCHEMA_VERSION = 1
        val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
    }
}
