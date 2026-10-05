package com.rackexcel.mobile.alarm

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** JVM coverage for the SharedPreferences adapter without requiring a device or Robolectric. */
class AlarmSettingsStoreTest {
    @Test
    fun empty_preferences_load_documented_defaults() {
        val store = AlarmSettingsStore(fakePreferences())

        assertEquals(AlarmSettingsDefaults.SETTINGS, store.load())
    }

    @Test
    fun save_and_update_round_trip_all_alarm_settings() {
        val preferences = fakePreferences()
        val store = AlarmSettingsStore(preferences)
        val saved = store.save(
            AlarmSettings(
                sourceMode = AlarmSourceMode.NETWORK_MANAGER,
                demoMode = true,
                soundEnabled = false,
                vibrationEnabled = false,
                reviewer = "  张三  ",
                matchConfig = AlarmMatchConfig(
                    fields = setOf(AlarmIdentityField.MANAGEMENT_IP, AlarmIdentityField.ASSET_ID),
                    mode = AlarmMatchMode.ALL,
                ),
            ),
        )

        assertEquals("张三", saved.reviewer)
        assertEquals(saved, store.load())

        val changed = store.update { it.copy(demoMode = false, reviewer = "李四") }
        assertFalse(changed.demoMode)
        assertEquals("李四", store.load().reviewer)
        assertEquals(AlarmSourceMode.NETWORK_MANAGER, store.load().sourceMode)
        assertEquals(AlarmMatchMode.ALL, store.load().matchConfig.mode)
    }

    @Test
    fun malformed_persisted_values_are_recovered_without_throwing() {
        val preferences = fakePreferences(
            mutableMapOf(
                AlarmSettingsStore.KEY_SOURCE_MODE to "unsupported-source",
                AlarmSettingsStore.KEY_DEMO_MODE to 42,
                AlarmSettingsStore.KEY_SOUND_ENABLED to "false",
                AlarmSettingsStore.KEY_VIBRATION_ENABLED to Any(),
                AlarmSettingsStore.KEY_REVIEWER to "   ",
                AlarmSettingsStore.KEY_MATCH_FIELDS to "unknown-field",
                AlarmSettingsStore.KEY_MATCH_MODE to "unsupported-mode",
            ),
        )

        val loaded = AlarmSettingsStore(preferences).load()

        assertEquals(AlarmSourceMode.MOCK, loaded.sourceMode)
        assertFalse(loaded.demoMode)
        assertFalse(loaded.soundEnabled)
        assertTrue(loaded.vibrationEnabled)
        assertEquals(AlarmSettingsDefaults.REVIEWER, loaded.reviewer)
        assertEquals(AlarmSettingsDefaults.MATCH_FIELDS, loaded.matchConfig.fields)
        assertEquals(AlarmMatchMode.ANY, loaded.matchConfig.mode)
    }

    @Test
    fun reset_removes_only_alarm_keys_and_restores_defaults() {
        val preferences = fakePreferences(mutableMapOf("unrelated" to "keep"))
        val store = AlarmSettingsStore(preferences)
        store.save(AlarmSettings(demoMode = true, soundEnabled = false))

        assertEquals(AlarmSettingsDefaults.SETTINGS, store.reset())
        assertEquals("keep", preferences.getString("unrelated", null))
        assertEquals(AlarmSettingsDefaults.SETTINGS, store.load())
    }

    /** Small dynamic-proxy fake; avoids adding a test-only Android runtime dependency. */
    @Suppress("UNCHECKED_CAST")
    private fun fakePreferences(initial: MutableMap<String, Any?> = mutableMapOf()): SharedPreferences {
        val values = initial
        val loader = SharedPreferences::class.java.classLoader ?: javaClass.classLoader
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            loader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            val key = args?.getOrNull(0) as? String
            when (method.name) {
                "putString", "putBoolean", "putInt", "putLong", "putFloat", "putStringSet" -> {
                    values[key.orEmpty()] = args?.getOrNull(1)
                    editor
                }
                "remove" -> {
                    values.remove(key.orEmpty())
                    editor
                }
                "clear" -> {
                    values.clear()
                    editor
                }
                "commit" -> true
                "apply" -> null
                else -> defaultReturn(method.returnType)
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            loader,
            arrayOf(SharedPreferences::class.java),
        ) { proxy, method, args ->
            val key = args?.getOrNull(0) as? String
            when (method.name) {
                "getAll" -> HashMap(values)
                "getString" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is String) throw ClassCastException("not a String")
                    value as? String ?: args?.getOrNull(1) as? String
                }
                "getStringSet" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is Set<*>) throw ClassCastException("not a String set")
                    value as? Set<String> ?: args?.getOrNull(1) as? Set<String>
                }
                "getBoolean" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is Boolean) throw ClassCastException("not a Boolean")
                    value as? Boolean ?: (args?.getOrNull(1) as? Boolean ?: false)
                }
                "getInt" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is Int) throw ClassCastException("not an Int")
                    value as? Int ?: (args?.getOrNull(1) as? Int ?: 0)
                }
                "getLong" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is Long) throw ClassCastException("not a Long")
                    value as? Long ?: (args?.getOrNull(1) as? Long ?: 0L)
                }
                "getFloat" -> {
                    val value = values[key.orEmpty()]
                    if (value != null && value !is Float) throw ClassCastException("not a Float")
                    value as? Float ?: (args?.getOrNull(1) as? Float ?: 0f)
                }
                "contains" -> values.containsKey(key.orEmpty())
                "edit" -> editor
                "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                "toString" -> "FakeSharedPreferences"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> defaultReturn(method.returnType)
            }
        } as SharedPreferences
    }

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Short::class.javaPrimitiveType -> 0.toShort()
        Byte::class.javaPrimitiveType -> 0.toByte()
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
