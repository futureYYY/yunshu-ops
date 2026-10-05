package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockAlarmGeneratorTest {
    private val currentTaskRacks = listOf(
        Rack(
            cabinetId = "A-01",
            imageName = "a01.jpg",
            devices = listOf(
                Device(type = "交换机", bottomU = 43, heightU = 1, assetId = "DEMO-A01-SW-01"),
                Device(type = "服务器", bottomU = 21, heightU = 2, managementIp = "192.0.2.121"),
            ),
            uncertain = emptyList(),
        ),
        Rack(
            cabinetId = "B-07",
            imageName = "b07.jpg",
            devices = listOf(
                Device(type = "服务器", bottomU = 19, heightU = 2),
            ),
            uncertain = emptyList(),
        ),
    )

    @Test
    fun generator_usesCurrentDevicesForCriticalAndWarningAndAddsOneExplicitUnmatchedAlarm() {
        val generated = MockAlarmGenerator.generate(
            racks = currentTaskRacks,
            taskId = "TASK-DYNAMIC-001",
            occurredAtMillis = 1_700_000_000_000L,
        )

        assertEquals(4, generated.alarms.size)
        assertTrue(generated.alarms.all { it.taskId == "TASK-DYNAMIC-001" })
        val critical = generated.alarms.single { it.severity == AlarmSeverity.CRITICAL }
        val warning = generated.alarms.single { it.severity == AlarmSeverity.WARNING }
        assertEquals(1, generated.alarms.count { it.description.contains("未匹配演示告警") })

        val criticalMatch = assertIs<AlarmMatchResult.Matched>(AlarmMatchPolicy.match(critical, currentTaskRacks))
        val warningMatch = assertIs<AlarmMatchResult.Matched>(AlarmMatchPolicy.match(warning, currentTaskRacks))
        assertTrue(
            criticalMatch.coordinate.cabinetId != warningMatch.coordinate.cabinetId ||
                criticalMatch.coordinate.bottomU != warningMatch.coordinate.bottomU,
        )

        val unmatched = generated.alarms.single { it.description.contains("未匹配演示告警") }
        assertIs<AlarmMatchResult.Unmatched>(AlarmMatchPolicy.match(unmatched, currentTaskRacks))
    }

    @Test
    fun generator_canUseDeterministicDeviceNameWhenPhotosDidNotYieldAnyPrimaryKey() {
        val keylessRack = Rack(
            cabinetId = "无前缀柜号",
            imageName = "unknown.jpg",
            devices = listOf(
                Device(type = "服务器", bottomU = 5, heightU = 2),
                Device(type = "交换机", bottomU = 10, heightU = 1),
            ),
            uncertain = emptyList(),
        )

        val generated = MockAlarmGenerator.generate(listOf(keylessRack), occurredAtMillis = 1L)

        val critical = generated.alarms.single { it.severity == AlarmSeverity.CRITICAL }
        assertIs<AlarmMatchResult.Matched>(AlarmMatchPolicy.match(critical, listOf(keylessRack)))
    }
}
