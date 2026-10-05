package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import java.security.MessageDigest

data class MockAlarmGenerationResult(
    val alarms: List<AlarmRecord>,
    /** A user-visible explanation only when the current task has too few devices for the full demo. */
    val notices: List<String> = emptyList(),
)

/**
 * Creates a repeatable offline demo from the currently recognized rack data.
 * There are deliberately no cabinet IDs or pre-authored device names here:
 * every matched alert is based on one of the supplied [Rack]/[Device] records.
 */
object MockAlarmGenerator {
    fun generate(
        racks: List<Rack>,
        taskId: String? = null,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): MockAlarmGenerationResult {
        val targets = racks.flatMap { rack -> rack.devices.map { device -> MockTarget(rack, device) } }
        // Keep the one-click demo focused: up to three actual devices demonstrate
        // red/orange/green severity accents, followed by one intentional unmatched case.
        val selected = targets.take(3)
        val severities = listOf(AlarmSeverity.CRITICAL, AlarmSeverity.WARNING, AlarmSeverity.INFO)
        val alarms = selected.mapIndexed { index, target ->
            val severity = severities[index]
            AlarmRecord(
                alarmId = "MOCK-ALARM-${severity.name}-${index + 1}",
                identity = AlarmDeviceIdentityResolver.forDevice(target.rack, target.device),
                severity = severity,
                occurredAtMillis = (occurredAtMillis - index * 60_000L).coerceAtLeast(0L),
                description = "演示告警：已按当前上架图自动关联到设备位置",
                taskId = taskId,
            )
        }.toMutableList()
        alarms += explicitUnmatchedAlarm(racks, taskId, occurredAtMillis - selected.size * 60_000L)

        val notices = buildList {
            when {
                targets.isEmpty() -> add("当前任务尚无可识别设备，仅生成未匹配告警；请先完成机柜识别后再演示联动。")
                targets.size == 1 -> add("当前任务仅有 1 台设备，已生成严重告警与未匹配告警；至少识别 2 台设备后可同时演示严重和一般高亮。")
            }
        }
        return MockAlarmGenerationResult(alarms = alarms, notices = notices)
    }

    private fun explicitUnmatchedAlarm(racks: List<Rack>, taskId: String?, occurredAtMillis: Long): AlarmRecord {
        val fingerprint = racks.joinToString("|") { rack ->
            "${rack.cabinetId}:${rack.devices.joinToString(",") { device -> "${device.bottomU}-${device.heightU}-${device.type}" }}"
        }.sha256Prefix()
        return AlarmRecord(
            alarmId = "MOCK-UNMATCHED-1",
            identity = AlarmDeviceIdentity(assetId = "__MOCK_UNMATCHED__$fingerprint"),
            severity = AlarmSeverity.INFO,
            occurredAtMillis = occurredAtMillis.coerceAtLeast(0L),
            description = "未匹配演示告警：当前上架图中未找到对应设备",
            taskId = taskId,
        )
    }

    private data class MockTarget(val rack: Rack, val device: Device)

    private fun String.sha256Prefix(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(10)
}
