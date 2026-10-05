package com.rackexcel.mobile.storage

import android.content.Context

data class AppSettings(
    val filePrefix: String = "云枢智维",
    val autoSave: Boolean = true,
    val retainOriginalImages: Boolean = false,
)

class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        filePrefix = preferences.getString(KEY_PREFIX, "云枢智维").orEmpty().ifBlank { "云枢智维" },
        autoSave = preferences.getBoolean(KEY_AUTO_SAVE, true),
        retainOriginalImages = preferences.getBoolean(KEY_RETAIN_IMAGES, false),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_PREFIX, settings.filePrefix.trim().ifBlank { "云枢智维" })
            .putBoolean(KEY_AUTO_SAVE, settings.autoSave)
            .putBoolean(KEY_RETAIN_IMAGES, settings.retainOriginalImages)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "yunshu_app_settings"
        const val KEY_PREFIX = "file_prefix"
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_RETAIN_IMAGES = "retain_original_images"
    }
}
