package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack

/** Pure, auditable three-state field-review transition for a single alarm. */
object AlarmReviewStateMachine {
    fun apply(
        alarm: AlarmRecord,
        coordinate: AlarmRackCoordinate?,
        input: AlarmReviewInput,
    ): AlarmReviewTransition {
        check(alarm.status != AlarmStatus.HANDLED) { "该告警已处理，如需再次处置请先在网管侧重新打开告警。" }
        val reviewer = input.reviewer.trim().ifBlank { "现场人员" }
        val reviewedAt = input.reviewedAtMillis.coerceAtLeast(alarm.occurredAtMillis)
        val evidence = input.evidencePhotoPaths.map(String::trim).filter(String::isNotBlank).distinct()
        val note = input.note.trim()
        val plan = when (input.decision) {
            AlarmReviewDecision.DEVICE_NOT_PRESENT -> ReviewPlan(
                status = AlarmStatus.HANDLED,
                action = AlarmFollowUpAction.CLOSE_INVALID_ALARM,
                writeback = coordinate?.let { AlarmAssetWriteback.RemoveDevice(it, note.ifBlank { "现场确认设备不存在或已下架" }) }
                    ?: AlarmAssetWriteback.NoChange,
                todoText = null,
            )
            AlarmReviewDecision.DEVICE_FAULT -> ReviewPlan(
                status = AlarmStatus.HANDLED,
                action = AlarmFollowUpAction.CREATE_FAULT_DISPOSAL_RECORD,
                writeback = coordinate?.let {
                    AlarmAssetWriteback.MarkDeviceFault(
                        coordinate = it,
                        status = "故障待处置",
                        note = note.ifBlank { "现场确认硬件故障" },
                    )
                } ?: AlarmAssetWriteback.NoChange,
                todoText = null,
            )
            AlarmReviewDecision.NEEDS_FURTHER_PROCESSING -> ReviewPlan(
                status = AlarmStatus.UNHANDLED,
                action = AlarmFollowUpAction.CREATE_FOLLOW_UP_TODO,
                writeback = AlarmAssetWriteback.NoChange,
                todoText = "告警 ${alarm.alarmId}：${note.ifBlank { "待检查线缆、端口或配置" }}",
            )
        }
        val record = AlarmProcessingRecord(
            reviewer = reviewer,
            reviewedAtMillis = reviewedAt,
            decision = input.decision,
            note = note,
            evidencePhotoPaths = evidence,
            followUpAction = plan.action,
            todoText = plan.todoText,
        )
        return AlarmReviewTransition(
            updatedAlarm = alarm.copy(status = plan.status, processing = alarm.processing + record),
            followUpAction = plan.action,
            writeback = plan.writeback,
        )
    }

    private data class ReviewPlan(
        val status: AlarmStatus,
        val action: AlarmFollowUpAction,
        val writeback: AlarmAssetWriteback,
        val todoText: String?,
    )
}

/** Applies a review write-back to immutable rack data; Excel export can use the returned rack list directly. */
object AlarmAssetWritebackPolicy {
    fun apply(racks: List<Rack>, transition: AlarmReviewTransition): AlarmWritebackResult = when (val writeback = transition.writeback) {
        AlarmAssetWriteback.NoChange -> AlarmWritebackResult(
            racks = racks,
            applied = false,
            message = "本次复核保留上架图不变。",
        )
        is AlarmAssetWriteback.RemoveDevice -> updateAt(racks, writeback.coordinate) { rack, index, _ ->
            rack.copy(devices = rack.devices.filterIndexed { deviceIndex, _ -> deviceIndex != index })
        }.let { update ->
            if (update.applied) AlarmWritebackResult(update.racks, true, "已从 ${writeback.coordinate.cabinetId} 的上架图与台账中移除该设备。")
            else AlarmWritebackResult(racks, false, "未找到待回写的设备，上架图保持不变。")
        }
        is AlarmAssetWriteback.MarkDeviceFault -> updateAt(racks, writeback.coordinate) { rack, index, device ->
            val incident = "告警 ${transition.updatedAlarm.alarmId}：${writeback.note}"
            val notes = listOf(device.notes.trim(), incident).filter(String::isNotBlank).distinct().joinToString("；")
            rack.copy(devices = rack.devices.mapIndexed { deviceIndex, current ->
                if (deviceIndex == index) current.copy(status = writeback.status, notes = notes) else current
            })
        }.let { update ->
            if (update.applied) AlarmWritebackResult(update.racks, true, "已将设备状态回写为${writeback.status}。")
            else AlarmWritebackResult(racks, false, "未找到待回写的设备，上架图保持不变。")
        }
    }

    private fun updateAt(
        racks: List<Rack>,
        coordinate: AlarmRackCoordinate,
        transform: (Rack, Int, Device) -> Rack,
    ): RackUpdate {
        val rackIndex = racks.indexOfFirst { it.cabinetId.trim().equals(coordinate.cabinetId.trim(), ignoreCase = true) }
        if (rackIndex < 0) return RackUpdate(racks, false)
        val rack = racks[rackIndex]
        val deviceIndex = rack.devices.getOrNull(coordinate.deviceIndex)
            ?.takeIf { it.matches(coordinate) }
            ?.let { coordinate.deviceIndex }
            ?: rack.devices.indexOfFirst { it.matches(coordinate) }
        if (deviceIndex < 0) return RackUpdate(racks, false)
        val updatedRack = transform(rack, deviceIndex, rack.devices[deviceIndex])
        return RackUpdate(racks.mapIndexed { index, current -> if (index == rackIndex) updatedRack else current }, true)
    }

    private fun Device.matches(coordinate: AlarmRackCoordinate): Boolean =
        bottomU == coordinate.bottomU && heightU == coordinate.heightU && type == coordinate.type

    private data class RackUpdate(val racks: List<Rack>, val applied: Boolean)
}

/** Keeps the operator in control of the review queue; a recorded answer never auto-advances. */
object AlarmReviewNavigationPolicy {
    const val AUTO_ADVANCE_AFTER_DECISION: Boolean = false

    fun nextUnresolvedAlarmId(
        alarms: Iterable<AlarmRecord>,
        currentAlarmId: String,
    ): String? = alarms
        .asSequence()
        .filter { it.status == AlarmStatus.UNHANDLED && it.alarmId != currentAlarmId }
        .sortedWith(compareBy<AlarmRecord> { severityRank(it.severity) }.thenByDescending { it.occurredAtMillis })
        .map(AlarmRecord::alarmId)
        .firstOrNull()

    private fun severityRank(value: AlarmSeverity): Int = when (value) {
        AlarmSeverity.CRITICAL -> 0
        AlarmSeverity.WARNING -> 1
        AlarmSeverity.INFO -> 2
    }
}
