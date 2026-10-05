package com.rackexcel.mobile.alarm

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Device-local settings used by the alarm/asset-linkage feature.
 *
 * The store deliberately lives in the alarm package and owns a dedicated
 * SharedPreferences file.  This keeps the source selection and review policy
 * independent from Compose and from the model/receiver settings.  A future
 * network-manager adapter can read the same settings without changing the UI
 * contract.
 */
data class AlarmSettings(
    val sourceMode: AlarmSourceMode = AlarmSourceMode.MOCK,
    val demoMode: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val reviewer: String = AlarmSettingsDefaults.REVIEWER,
    val matchConfig: AlarmMatchConfig = AlarmSettingsDefaults.MATCH_CONFIG,
)

/** Stable defaults shared by the store, tests and future settings screens. */
object AlarmSettingsDefaults {
    const val REVIEWER: String = "本机现场人员"

    val MATCH_FIELDS: Set<AlarmIdentityField> = AlarmIdentityField.entries.toSet()
    val MATCH_CONFIG: AlarmMatchConfig
        get() = AlarmMatchConfig(fields = MATCH_FIELDS, mode = AlarmMatchMode.ANY)

    val SETTINGS: AlarmSettings
        get() = AlarmSettings(
            sourceMode = AlarmSourceMode.MOCK,
            demoMode = false,
            soundEnabled = true,
            vibrationEnabled = true,
            reviewer = REVIEWER,
            matchConfig = MATCH_CONFIG,
        )
}

/**
 * Pure normalization/parsing helpers.  SharedPreferences can contain values
 * from an older build or a manually edited backup; malformed values therefore
 * fall back to safe defaults rather than escaping into the UI or source layer.
 */
object AlarmSettingsPolicy {
    private val allFields: Set<AlarmIdentityField> = AlarmSettingsDefaults.MATCH_FIELDS

    fun normalize(settings: AlarmSettings): AlarmSettings {
        val fields = settings.matchConfig.fields.intersect(allFields)
        return settings.copy(
            reviewer = normalizeReviewer(settings.reviewer),
            matchConfig = AlarmMatchConfig(
                fields = fields,
                mode = settings.matchConfig.mode,
            ),
        )
    }

    fun normalizeReviewer(raw: String?): String = raw
        ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
        ?.trim()
        ?.take(MAX_REVIEWER_LENGTH)
        ?.ifBlank { AlarmSettingsDefaults.REVIEWER }
        ?: AlarmSettingsDefaults.REVIEWER

    fun parseSourceMode(raw: String?): AlarmSourceMode {
        val value = raw?.trim().orEmpty()
        return AlarmSourceMode.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.label == value
        } ?: when (value.lowercase(Locale.ROOT)) {
            "mock", "local", "本地", "模拟" -> AlarmSourceMode.MOCK
            "network", "networkmanager", "network_manager", "网管" -> AlarmSourceMode.NETWORK_MANAGER
            else -> AlarmSourceMode.MOCK
        }
    }

    fun parseMatchMode(raw: String?): AlarmMatchMode = AlarmMatchMode.entries.firstOrNull {
        it.name.equals(raw?.trim(), ignoreCase = true)
    } ?: when (raw?.trim()?.uppercase(Locale.ROOT)) {
        "全部", "ALL_KEYS" -> AlarmMatchMode.ALL
        else -> AlarmMatchMode.ANY
    }

    /**
     * Reads the canonical comma-separated enum names written by the store.
     * An absent key means first-run defaults (all keys); a present blank value
     * intentionally means that the operator disabled every key.  If a nonblank
     * value contains no recognized field, all keys are restored as a safe
     * fallback.  Recognized fields are retained when a legacy payload includes
     * a mixture of known and unknown names.
     */
    fun parseMatchFields(raw: String?, keyPresent: Boolean): Set<AlarmIdentityField> {
        if (!keyPresent) return allFields
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return emptySet()
        val recognized = text.split(',', ';', '|')
            .mapNotNull { token ->
                val value = token.trim()
                AlarmIdentityField.entries.firstOrNull {
                    it.name.equals(value, ignoreCase = true) || it.label == value
                }
            }
            .toSet()
        return recognized.ifEmpty { allFields }
    }

    fun parseBoolean(raw: String?, default: Boolean): Boolean = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "true", "1", "yes", "on", "是" -> true
        "false", "0", "no", "off", "否" -> false
        else -> default
    }

    private const val MAX_REVIEWER_LENGTH = 80
}

/**
 * Persistent alarm settings.  Both constructors are public so application
 * code can use a Context while JVM/instrumentation tests can inject a
 * SharedPreferences implementation.
 */
class AlarmSettingsStore {
    private val preferences: SharedPreferences

    constructor(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    constructor(preferences: SharedPreferences) {
        this.preferences = preferences
    }

    /** Returns normalized settings; malformed persisted values never throw. */
    @Synchronized
    fun load(): AlarmSettings {
        val fieldsKeyPresent = safeContains(KEY_MATCH_FIELDS)
        return AlarmSettingsPolicy.normalize(
            AlarmSettings(
                sourceMode = AlarmSettingsPolicy.parseSourceMode(safeGetString(KEY_SOURCE_MODE)),
                demoMode = safeGetBoolean(KEY_DEMO_MODE, AlarmSettingsDefaults.SETTINGS.demoMode),
                soundEnabled = safeGetBoolean(KEY_SOUND_ENABLED, AlarmSettingsDefaults.SETTINGS.soundEnabled),
                vibrationEnabled = safeGetBoolean(KEY_VIBRATION_ENABLED, AlarmSettingsDefaults.SETTINGS.vibrationEnabled),
                reviewer = AlarmSettingsPolicy.normalizeReviewer(safeGetString(KEY_REVIEWER)),
                matchConfig = AlarmMatchConfig(
                    fields = AlarmSettingsPolicy.parseMatchFields(
                        raw = safeGetString(KEY_MATCH_FIELDS),
                        keyPresent = fieldsKeyPresent,
                    ),
                    mode = AlarmSettingsPolicy.parseMatchMode(safeGetString(KEY_MATCH_MODE)),
                ),
            ),
        )
    }

    /** Saves and returns the normalized value that was persisted. */
    @Synchronized
    fun save(settings: AlarmSettings): AlarmSettings {
        val normalized = AlarmSettingsPolicy.normalize(settings)
        preferences.edit()
            .putString(KEY_SOURCE_MODE, normalized.sourceMode.name)
            .putBoolean(KEY_DEMO_MODE, normalized.demoMode)
            .putBoolean(KEY_SOUND_ENABLED, normalized.soundEnabled)
            .putBoolean(KEY_VIBRATION_ENABLED, normalized.vibrationEnabled)
            .putString(KEY_REVIEWER, normalized.reviewer)
            .putString(
                KEY_MATCH_FIELDS,
                normalized.matchConfig.fields
                    .sortedBy(AlarmIdentityField::ordinal)
                    .joinToString(",") { it.name },
            )
            .putString(KEY_MATCH_MODE, normalized.matchConfig.mode.name)
            .apply()
        return normalized
    }

    /** Atomically reads, transforms, normalizes and persists the settings. */
    @Synchronized
    fun update(transform: (AlarmSettings) -> AlarmSettings): AlarmSettings = save(transform(load()))

    fun setSourceMode(mode: AlarmSourceMode): AlarmSettings = update { it.copy(sourceMode = mode) }
    fun setDemoMode(enabled: Boolean): AlarmSettings = update { it.copy(demoMode = enabled) }
    fun setSoundEnabled(enabled: Boolean): AlarmSettings = update { it.copy(soundEnabled = enabled) }
    fun setVibrationEnabled(enabled: Boolean): AlarmSettings = update { it.copy(vibrationEnabled = enabled) }
    fun setReviewer(reviewer: String): AlarmSettings = update { it.copy(reviewer = reviewer) }
    fun setMatchConfig(config: AlarmMatchConfig): AlarmSettings = update { it.copy(matchConfig = config) }

    /** Clears only this store's keys and restores the documented defaults. */
    @Synchronized
    fun reset(): AlarmSettings {
        preferences.edit()
            .remove(KEY_SOURCE_MODE)
            .remove(KEY_DEMO_MODE)
            .remove(KEY_SOUND_ENABLED)
            .remove(KEY_VIBRATION_ENABLED)
            .remove(KEY_REVIEWER)
            .remove(KEY_MATCH_FIELDS)
            .remove(KEY_MATCH_MODE)
            .apply()
        return load()
    }

    private fun safeContains(key: String): Boolean = runCatching { preferences.contains(key) }.getOrDefault(false)

    private fun safeGetString(key: String): String? = runCatching {
        if (!preferences.contains(key)) null else preferences.getString(key, null)
    }.getOrNull()

    private fun safeGetBoolean(key: String, default: Boolean): Boolean = runCatching {
        if (!preferences.contains(key)) return@runCatching default
        preferences.getBoolean(key, default)
    }.getOrElse {
        // Older builds occasionally stored toggles as text.  Accept valid
        // textual booleans, but use the documented default for everything else.
        AlarmSettingsPolicy.parseBoolean(
            raw = runCatching { preferences.getString(key, null) }.getOrNull(),
            default = default,
        )
    }

    companion object {
        const val PREFERENCES: String = "yunshu_alarm_settings"
        const val KEY_SOURCE_MODE: String = "source_mode"
        const val KEY_DEMO_MODE: String = "demo_mode"
        const val KEY_SOUND_ENABLED: String = "sound_enabled"
        const val KEY_VIBRATION_ENABLED: String = "vibration_enabled"
        const val KEY_REVIEWER: String = "reviewer"
        const val KEY_MATCH_FIELDS: String = "match_fields"
        const val KEY_MATCH_MODE: String = "match_mode"
    }
}
