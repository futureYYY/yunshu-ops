package com.rackexcel.mobile.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmSettingsPolicyTest {
    @Test
    fun defaults_use_mock_source_and_all_three_keys() {
        val defaults = AlarmSettingsDefaults.SETTINGS

        assertEquals(AlarmSourceMode.MOCK, defaults.sourceMode)
        assertEquals(false, defaults.demoMode)
        assertEquals(true, defaults.soundEnabled)
        assertEquals(true, defaults.vibrationEnabled)
        assertEquals("本机现场人员", defaults.reviewer)
        assertEquals(AlarmIdentityField.entries.toSet(), defaults.matchConfig.fields)
        assertEquals(AlarmMatchMode.ANY, defaults.matchConfig.mode)
    }

    @Test
    fun malformed_source_and_mode_values_fall_back_to_safe_defaults() {
        assertEquals(AlarmSourceMode.MOCK, AlarmSettingsPolicy.parseSourceMode("not-a-source"))
        assertEquals(AlarmSourceMode.MOCK, AlarmSettingsPolicy.parseSourceMode(null))
        assertEquals(AlarmMatchMode.ANY, AlarmSettingsPolicy.parseMatchMode("not-a-mode"))
        assertEquals(AlarmMatchMode.ANY, AlarmSettingsPolicy.parseMatchMode(null))
        assertEquals(AlarmSourceMode.NETWORK_MANAGER, AlarmSettingsPolicy.parseSourceMode("网管接口"))
        assertEquals(AlarmMatchMode.ALL, AlarmSettingsPolicy.parseMatchMode("all"))
    }

    @Test
    fun malformed_match_fields_restore_all_keys_but_blank_can_mean_intentional_none() {
        val all = AlarmIdentityField.entries.toSet()

        assertEquals(all, AlarmSettingsPolicy.parseMatchFields(null, keyPresent = false))
        assertEquals(all, AlarmSettingsPolicy.parseMatchFields("unknown", keyPresent = true))
        assertEquals(
            setOf(AlarmIdentityField.DEVICE_NAME, AlarmIdentityField.ASSET_ID),
            AlarmSettingsPolicy.parseMatchFields("DEVICE_NAME,unknown,资产编号", keyPresent = true),
        )
        assertTrue(AlarmSettingsPolicy.parseMatchFields("  ", keyPresent = true).isEmpty())
    }

    @Test
    fun reviewer_is_trimmed_control_chars_removed_and_bounded() {
        assertEquals("现场人员", AlarmSettingsPolicy.normalizeReviewer(" 现场\n人员\u0000 "))
        assertEquals("本机现场人员", AlarmSettingsPolicy.normalizeReviewer("   "))
        assertEquals(80, AlarmSettingsPolicy.normalizeReviewer("x".repeat(120)).length)
    }

    @Test
    fun normalization_drops_unknown_enum_values_from_an_in_memory_settings_object() {
        val normalized = AlarmSettingsPolicy.normalize(
            AlarmSettings(
                reviewer = "巡检员",
                matchConfig = AlarmMatchConfig(
                    fields = setOf(AlarmIdentityField.MANAGEMENT_IP),
                    mode = AlarmMatchMode.ALL,
                ),
            ),
        )

        assertEquals("巡检员", normalized.reviewer)
        assertEquals(setOf(AlarmIdentityField.MANAGEMENT_IP), normalized.matchConfig.fields)
        assertEquals(AlarmMatchMode.ALL, normalized.matchConfig.mode)
    }
}
