package com.rackexcel.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelProfilePolicyTest {
    private val default = ModelProfile(
        id = "default",
        name = "平台默认配置",
        url = "https://example.test/v1",
        model = "vision-a",
        apiKey = "key-a",
        concurrency = 5,
        isBuiltIn = true,
    )

    @Test
    fun collection_keeps_named_active_profile_and_normalizes_concurrency() {
        val beta = default.copy(
            id = "beta",
            name = "现场备用模型",
            model = "vision-b",
            concurrency = 9,
            isBuiltIn = false,
        )

        val result = ModelProfilePolicy.normalize(listOf(default, beta), activeId = "beta", fallback = default)

        assertEquals("beta", result.activeId)
        assertEquals("现场备用模型", result.active.name)
        assertEquals(5, result.active.concurrency)
        assertEquals(listOf("平台默认配置", "现场备用模型"), result.profiles.map { it.name })
    }

    @Test
    fun collection_falls_back_when_selected_profile_has_been_deleted() {
        val result = ModelProfilePolicy.normalize(listOf(default), activeId = "missing", fallback = default)

        assertEquals("default", result.activeId)
        assertEquals("平台默认配置", result.active.name)
    }

    @Test
    fun custom_profile_can_be_removed_but_built_in_profile_is_retained() {
        val custom = default.copy(id = "custom", name = "测试模型", isBuiltIn = false)

        assertEquals(listOf(default), ModelProfilePolicy.remove(listOf(default, custom), "custom", default))
        assertTrue(ModelProfilePolicy.remove(listOf(default, custom), "default", default).any { it.id == "default" })
        assertFalse(ModelProfilePolicy.remove(listOf(default, custom), "custom", default).any { it.id == "custom" })
    }
}
