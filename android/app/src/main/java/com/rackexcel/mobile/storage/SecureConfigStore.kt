package com.rackexcel.mobile.storage

import android.content.Context
import android.os.Build
import com.rackexcel.mobile.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted device-local storage for named model profiles. The payload keeps the
 * original preference keys and Keystore alias, so v2.0 single-config data is
 * migrated in place on first read.
 */
class SecureConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadProfiles(): ModelProfileCollection {
        val root = decryptRoot() ?: return defaultProfiles()
        val loaded = if (root.has(KEY_PROFILES)) parseProfiles(root) else migrateSingleConfig(root)
        return ensureBundledProfiles(loaded)
    }

    fun saveProfiles(collection: ModelProfileCollection) {
        val default = defaultProfile()
        val normalized = ModelProfilePolicy.normalize(collection.profiles, collection.activeId, default)
        val payload = JSONObject()
            .put("schemaVersion", 3)
            .put(KEY_ACTIVE_PROFILE, normalized.activeId)
            .put(KEY_PROFILES, JSONArray().apply {
                normalized.profiles.forEach { profile ->
                    put(ModelProfileJsonCodec.toJson(profile))
                }
            })
            .toString()
            .toByteArray()
        encryptAndSave(payload)
    }

    /** Compatibility surface for code paths that require the active profile only. */
    fun load(): ModelConfig = loadProfiles().active.toConfig()

    /** Updates only the active profile while preserving all named alternatives. */
    fun save(config: ModelConfig) {
        val current = loadProfiles()
        val updated = current.profiles.map { profile ->
            if (profile.id == current.activeId) {
                profile.copy(
                    url = config.url,
                    model = config.model,
                    apiKey = config.apiKey,
                    concurrency = config.concurrency,
                )
            } else {
                profile
            }
        }
        saveProfiles(ModelProfileCollection(updated, current.activeId))
    }

    private fun parseProfiles(root: JSONObject): ModelProfileCollection {
        val fallback = defaultProfile()
        val array = root.optJSONArray(KEY_PROFILES) ?: JSONArray()
        val profiles = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue
                val profile = ModelProfileJsonCodec.fromJson(item)
                add(profile.copy(isBuiltIn = profile.isBuiltIn || id == DEFAULT_PROFILE_ID))
            }
        }
        return ModelProfilePolicy.normalize(
            profiles = profiles,
            activeId = root.optString(KEY_ACTIVE_PROFILE),
            fallback = fallback,
        )
    }

    private fun migrateSingleConfig(root: JSONObject): ModelProfileCollection {
        val default = defaultProfile()
        val migrated = default.copy(
            url = root.optString("url", default.url),
            model = root.optString("model", default.model),
            apiKey = root.optString("apiKey", default.apiKey),
            concurrency = root.optInt("concurrency", default.concurrency),
        )
        val collection = ModelProfilePolicy.normalize(listOf(migrated), migrated.id, default)
        saveProfiles(collection)
        return collection
    }

    private fun defaultProfiles(): ModelProfileCollection {
        val primary = primaryProfile()
        return ModelProfileCollection(listOf(primary, backupProfile()), primary.id)
    }

    private fun primaryProfile(): ModelProfile = ModelProfile(
        id = PRIMARY_PROFILE_ID,
        name = "主模型配置（待填写）",
        url = BuildConfig.DEFAULT_MODEL_URL,
        model = BuildConfig.DEFAULT_MODEL_NAME,
        apiKey = BuildConfig.DEFAULT_API_KEY,
        concurrency = 5,
        isBuiltIn = true
    )

    private fun backupProfile(): ModelProfile = ModelProfile(
        id = BACKUP_PROFILE_ID,
        name = "备用模型配置（待填写）",
        url = BuildConfig.FALLBACK_MODEL_URL,
        model = BuildConfig.FALLBACK_MODEL_NAME,
        apiKey = BuildConfig.FALLBACK_API_KEY,
        concurrency = 5,
        isBuiltIn = true
    )

    private fun ensureBundledProfiles(collection: ModelProfileCollection): ModelProfileCollection {
        val existing = collection.profiles
        val primary = primaryProfile()
        val backup = backupProfile()
        val hasPrimary = existing.any { it.id == primary.id }
        val merged = mutableListOf<ModelProfile>()
        if (!hasPrimary) {
            merged.add(primary)
        }
        merged.addAll(existing)
        if (existing.none { it.id == backup.id }) {
            merged.add(backup)
        }
        val activeId = if (hasPrimary) collection.activeId else primary.id
        return ModelProfileCollection(merged, activeId)
    }

    private fun defaultProfile(): ModelProfile = ModelProfile(
        id = DEFAULT_PROFILE_ID,
        name = "平台默认配置",
        url = BuildConfig.DEFAULT_MODEL_URL,
        model = BuildConfig.DEFAULT_MODEL_NAME,
        apiKey = BuildConfig.DEFAULT_API_KEY,
        concurrency = 5,
        isBuiltIn = true,
    )

    private fun decryptRoot(): JSONObject? {
        val cipherText = preferences.getString(KEY_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_IV, null)
        if (cipherText.isNullOrBlank() || iv.isNullOrBlank()) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
            JSONObject(cipher.doFinal(decode(cipherText)).decodeToString())
        }.getOrNull()
    }

    private fun encryptAndSave(payload: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(payload)
        preferences.edit()
            .putString(KEY_IV, encode(cipher.iv))
            .putString(KEY_CIPHERTEXT, encode(encrypted))
            .apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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

    private fun encode(value: ByteArray): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Base64.getEncoder().encodeToString(value)
        else android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Base64.getDecoder().decode(value)
        else android.util.Base64.decode(value, android.util.Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES = "rack_excel_secure_config"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_ALIAS = "rack_excel_model_config"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "activeProfileId"
        const val DEFAULT_PROFILE_ID = "platform-default"
        const val PRIMARY_PROFILE_ID = "platform-primary"
        const val BACKUP_PROFILE_ID = "platform-default"
    }
}
