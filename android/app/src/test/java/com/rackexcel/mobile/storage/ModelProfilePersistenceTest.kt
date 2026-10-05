package com.rackexcel.mobile.storage

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelProfilePersistenceTest {
    @Test
    fun legacy_profile_payload_keeps_health_status_unknown() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("name", "旧配置")
            .put("url", "https://example.test/v1")
            .put("model", "vision-a")
            .put("apiKey", "key")
            .put("concurrency", 2)

        val profile = ModelProfileJsonCodec.fromJson(legacy)

        assertNull(profile.lastCheckedAtMillis)
        assertNull(profile.lastLatencyMillis)
        assertNull(profile.lastCheckSucceeded)
    }

    @Test
    fun profile_payload_round_trip_preserves_health_check_details() {
        val profile = ModelProfile(
            id = "现场模型",
            name = "现场模型",
            url = "https://example.test/v1",
            model = "vision-b",
            apiKey = "key",
            concurrency = 3,
            lastCheckedAtMillis = 1_723_456_789_000L,
            lastLatencyMillis = 486L,
            lastCheckSucceeded = true,
        )

        val restored = ModelProfileJsonCodec.fromJson(ModelProfileJsonCodec.toJson(profile))

        assertEquals(profile.lastCheckedAtMillis, restored.lastCheckedAtMillis)
        assertEquals(profile.lastLatencyMillis, restored.lastLatencyMillis)
        assertEquals(profile.lastCheckSucceeded, restored.lastCheckSucceeded)
    }
}
