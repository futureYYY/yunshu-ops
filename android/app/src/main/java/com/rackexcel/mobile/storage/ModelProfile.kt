package com.rackexcel.mobile.storage

import org.json.JSONObject

/** A named, device-local connection definition for one multimodal model endpoint. */
data class ModelProfile(
    val id: String,
    val name: String,
    val url: String,
    val model: String,
    val apiKey: String,
    val concurrency: Int = 5,
    val isBuiltIn: Boolean = false,
    val lastCheckedAtMillis: Long? = null,
    val lastLatencyMillis: Long? = null,
    val lastCheckSucceeded: Boolean? = null,
) {
    fun toConfig(): ModelConfig = ModelConfig(
        url = url.trim(),
        model = model.trim(),
        apiKey = apiKey,
        concurrency = concurrency.coerceIn(1, 5),
    )
}

/** JSON codec kept separate from the encrypted store so old payload shapes remain readable. */
internal object ModelProfileJsonCodec {
    fun toJson(profile: ModelProfile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("url", profile.url)
        .put("model", profile.model)
        .put("apiKey", profile.apiKey)
        .put("concurrency", profile.concurrency)
        .put("builtIn", profile.isBuiltIn)
        .putOptional("lastCheckedAtMillis", profile.lastCheckedAtMillis)
        .putOptional("lastLatencyMillis", profile.lastLatencyMillis)
        .putOptional("lastCheckSucceeded", profile.lastCheckSucceeded)

    fun fromJson(payload: JSONObject): ModelProfile = ModelProfile(
        id = payload.optString("id").trim(),
        name = payload.optString("name"),
        url = payload.optString("url"),
        model = payload.optString("model"),
        apiKey = payload.optString("apiKey"),
        concurrency = payload.optInt("concurrency", 5),
        isBuiltIn = payload.optBoolean("builtIn", false),
        lastCheckedAtMillis = payload.optNullableLong("lastCheckedAtMillis"),
        lastLatencyMillis = payload.optNullableLong("lastLatencyMillis"),
        lastCheckSucceeded = payload.optNullableBoolean("lastCheckSucceeded"),
    )

    private fun JSONObject.putOptional(key: String, value: Long?): JSONObject = apply {
        if (value != null) put(key, value)
    }

    private fun JSONObject.putOptional(key: String, value: Boolean?): JSONObject = apply {
        if (value != null) put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }
}

data class ModelProfileCollection(
    val profiles: List<ModelProfile>,
    val activeId: String,
) {
    val active: ModelProfile
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

object ModelProfilePolicy {
    fun normalize(
        profiles: List<ModelProfile>,
        activeId: String,
        fallback: ModelProfile,
    ): ModelProfileCollection {
        val normalized = profiles
            .asSequence()
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map(::normalizeProfile)
            .toList()
            .ifEmpty { listOf(normalizeProfile(fallback)) }
        val active = normalized.firstOrNull { it.id == activeId } ?: normalized.first()
        return ModelProfileCollection(normalized, active.id)
    }

    fun remove(
        profiles: List<ModelProfile>,
        id: String,
        fallback: ModelProfile,
    ): List<ModelProfile> {
        val remaining = profiles.filterNot { it.id == id && !it.isBuiltIn }
        return normalize(remaining, activeId = "", fallback = fallback).profiles
    }

    private fun normalizeProfile(profile: ModelProfile): ModelProfile = profile.copy(
        name = profile.name.trim().ifBlank { "未命名模型配置" },
        url = profile.url.trim(),
        model = profile.model.trim(),
        concurrency = profile.concurrency.coerceIn(1, 5),
    )
}
