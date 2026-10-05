package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlarmMatchingPolicyTest {
    private val racks = listOf(
        Rack(
            cabinetId = "CAB-A",
            imageName = "CAB-A.jpg",
            devices = listOf(
                Device(
                    type = "交换机",
                    bottomU = 43,
                    heightU = 1,
                    displayName = "CAB-A 核心交换机",
                    managementIp = "192.0.2.11",
                    assetId = "DEMO-CAB-A-SW-01",
                ),
                Device(
                    type = "服务器",
                    bottomU = 21,
                    heightU = 2,
                    displayName = "CAB-A 应用服务器01",
                    managementIp = "192.0.2.21",
                    assetId = "DEMO-CAB-A-SRV-01",
                ),
            ),
            uncertain = emptyList(),
        ),
    )

    @Test
    fun match_prefers_assetId_and_returnsRackUCoordinate() {
        val result = AlarmMatchPolicy.match(
            alarm = AlarmRecord(
                alarmId = "ALM-001",
                identity = AlarmDeviceIdentity(assetId = "ty cab a sw 01"),
                severity = AlarmSeverity.CRITICAL,
                occurredAtMillis = 1_700_000_000_000L,
                description = "模拟端口中断",
            ),
            racks = racks,
        )

        val matched = assertIs<AlarmMatchResult.Matched>(result)
        assertEquals("CAB-A", matched.coordinate.cabinetId)
        assertEquals(43, matched.coordinate.bottomU)
        assertEquals(1, matched.coordinate.heightU)
        assertTrue(AlarmIdentityField.ASSET_ID in matched.matchedFields)
    }

    @Test
    fun match_canUseManagementIpWhenNameIsDifferent() {
        val result = AlarmMatchPolicy.match(
            alarm = AlarmRecord(
                alarmId = "ALM-002",
                identity = AlarmDeviceIdentity(deviceName = "网管别名", managementIp = "192.0.2.21"),
                severity = AlarmSeverity.WARNING,
                occurredAtMillis = 1_700_000_000_000L,
                description = "模拟状态告警",
            ),
            racks = racks,
            config = AlarmMatchConfig(fields = setOf(AlarmIdentityField.MANAGEMENT_IP)),
        )

        val matched = assertIs<AlarmMatchResult.Matched>(result)
        assertEquals("CAB-A", matched.coordinate.cabinetId)
        assertEquals(21, matched.coordinate.bottomU)
        assertTrue(AlarmIdentityField.MANAGEMENT_IP in matched.matchedFields)
    }

    @Test
    fun match_returnsExplainableUnmatchedWhenAssetIsAbsentFromRackDiagram() {
        val result = AlarmMatchPolicy.match(
            alarm = AlarmRecord(
                alarmId = "ALM-404",
                identity = AlarmDeviceIdentity(assetId = "DEMO-CAB-Z-SW-99"),
                severity = AlarmSeverity.INFO,
                occurredAtMillis = 1_700_000_000_000L,
                description = "模拟未匹配告警",
            ),
            racks = racks,
        )

        val unmatched = assertIs<AlarmMatchResult.Unmatched>(result)
        assertTrue(unmatched.reason.contains("未匹配"))
        assertTrue(unmatched.suggestion.contains("补采"))
    }

    @Test
    fun allKeyMode_requiresEveryConfiguredKeyToMatch() {
        val result = AlarmMatchPolicy.match(
            alarm = AlarmRecord(
                alarmId = "ALM-003",
                identity = AlarmDeviceIdentity(assetId = "DEMO-CAB-A-SW-01", managementIp = "192.0.2.99"),
                severity = AlarmSeverity.CRITICAL,
                occurredAtMillis = 1_700_000_000_000L,
                description = "模拟组合主键不一致",
            ),
            racks = racks,
            config = AlarmMatchConfig(
                fields = setOf(AlarmIdentityField.ASSET_ID, AlarmIdentityField.MANAGEMENT_IP),
                mode = AlarmMatchMode.ALL,
            ),
        )

        assertIs<AlarmMatchResult.Unmatched>(result)
    }
}
