package com.rackexcel.mobile.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class NetworkGatewayConfigTest {
    @Test
    fun normalizesGatewayAddressPathsAndTimeout() {
        val normalized = NetworkGatewayConfigPolicy.normalize(
            NetworkGatewayConfig(
                name = "生产网管",
                baseUrl = " https://gateway.example.com/// ",
                apiVersion = " /v1/ ",
                healthPath = "health",
                alarmsPath = " /alarms ",
                timeoutSeconds = 99,
            ),
        )

        assertEquals("https://gateway.example.com", normalized.baseUrl)
        assertEquals("v1", normalized.apiVersion)
        assertEquals("/health", normalized.healthPath)
        assertEquals("/alarms", normalized.alarmsPath)
        assertEquals(60, normalized.timeoutSeconds)
        assertEquals("https://gateway.example.com/v1/health", normalized.healthUrl)
        assertEquals("https://gateway.example.com/v1/alarms", normalized.alarmsUrl)
    }

    @Test
    fun payloadAdapterMapsCustomNetworkManagerFieldsToCanonicalAlarmRecords() {
        val mapping = NetworkGatewayFieldMapping(
            alarmId = "id",
            deviceName = "node.name",
            managementIp = "node.ip",
            assetId = "node.asset",
            severity = "level",
            occurredAt = "time",
            description = "message",
            status = "state",
        )
        val alarms = NetworkAlarmPayloadAdapter.decode(
            """{"data":[{"id":"A-1","node":{"name":"SW-01","ip":"192.0.2.1","asset":"ASSET-1"},"level":"严重","time":"2026-09-07T08:00:00Z","message":"端口异常","state":"未处理"}]}""",
            mapping,
        )

        assertEquals(1, alarms.size)
        assertEquals("A-1", alarms.single().alarmId)
        assertEquals("SW-01", alarms.single().identity.deviceName)
        assertEquals("192.0.2.1", alarms.single().identity.managementIp)
        assertEquals("ASSET-1", alarms.single().identity.assetId)
        assertEquals(AlarmSeverity.CRITICAL, alarms.single().severity)
        assertEquals("端口异常", alarms.single().description)
        assertTrue(alarms.single().occurredAtMillis > 0L)
    }

    @Test
    fun policyKeepsEmptyOptionalFieldsEmptyInsteadOfInventingValues() {
        val mapping = NetworkGatewayFieldMapping()
        val alarms = NetworkAlarmPayloadAdapter.decode(
            """[{"alarmId":"A-2","device":{"deviceName":"SW-02"},"severity":"提示","occurredAt":1000,"description":"提示"}]""",
            mapping,
        )

        assertEquals("SW-02", alarms.single().identity.deviceName)
        assertEquals("", alarms.single().identity.managementIp)
        assertEquals("", alarms.single().identity.assetId)
    }

    @Test
    fun configuredGatewayHealthCheckUsesConfiguredAuthenticationWithoutLoggingIt() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"))
            server.start()
            val config = NetworkGatewayConfig(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiVersion = "",
                healthPath = "/health",
                authMode = NetworkAuthMode.API_KEY,
                credential = "TOKEN_FIXTURE",
                headerName = "X-Test-Key",
            )

            val result = ConfiguredNetworkManagerGateway(config).check()

            assertTrue(result.succeeded)
            assertTrue(result.latencyMillis >= 0L)
            assertEquals("TOKEN_FIXTURE", server.takeRequest().getHeader("X-Test-Key"))
        }
    }
}
