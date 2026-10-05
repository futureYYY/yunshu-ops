package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlarmReviewStateMachineTest {
    private val alarm = AlarmRecord(
        alarmId = "ALM-CAB-A-001",
        identity = AlarmDeviceIdentity(assetId = "DEMO-CAB-A-SW-01"),
        severity = AlarmSeverity.CRITICAL,
        occurredAtMillis = 1_700_000_000_000L,
        description = "模拟设备离线",
    )
    private val rack = Rack(
        cabinetId = "CAB-A",
        imageName = "CAB-A.jpg",
        devices = listOf(
            Device(
                type = "交换机",
                bottomU = 43,
                heightU = 1,
                displayName = "CAB-A 核心交换机",
                assetId = "DEMO-CAB-A-SW-01",
            ),
        ),
        uncertain = emptyList(),
    )
    private val coordinate = AlarmRackCoordinate(
        cabinetId = "CAB-A",
        deviceIndex = 0,
        type = "交换机",
        bottomU = 43,
        heightU = 1,
        displayName = "CAB-A 核心交换机",
        assetId = "DEMO-CAB-A-SW-01",
    )

    @Test
    fun deviceAbsent_closesInvalidAlarmAndRemovesMatchedDeviceFromRack() {
        val transition = AlarmReviewStateMachine.apply(
            alarm = alarm,
            coordinate = coordinate,
            input = AlarmReviewInput(
                decision = AlarmReviewDecision.DEVICE_NOT_PRESENT,
                reviewer = "现场工程师A",
                reviewedAtMillis = 1_700_000_100_000L,
                note = "现场核对后确认已下架",
            ),
        )

        assertEquals(AlarmStatus.HANDLED, transition.updatedAlarm.status)
        assertEquals(AlarmFollowUpAction.CLOSE_INVALID_ALARM, transition.followUpAction)
        assertEquals("现场工程师A", transition.updatedAlarm.processing.single().reviewer)
        val writeback = AlarmAssetWritebackPolicy.apply(listOf(rack), transition)
        assertTrue(writeback.applied)
        assertTrue(writeback.racks.single().devices.isEmpty())
    }

    @Test
    fun deviceFault_marksAssetAndKeepsEvidenceInDisposalRecord() {
        val transition = AlarmReviewStateMachine.apply(
            alarm = alarm,
            coordinate = coordinate,
            input = AlarmReviewInput(
                decision = AlarmReviewDecision.DEVICE_FAULT,
                reviewer = "现场工程师B",
                reviewedAtMillis = 1_700_000_100_000L,
                evidencePhotoPaths = listOf("/data/user/0/demo/fault.jpg"),
            ),
        )

        assertEquals(AlarmStatus.HANDLED, transition.updatedAlarm.status)
        assertEquals(AlarmFollowUpAction.CREATE_FAULT_DISPOSAL_RECORD, transition.followUpAction)
        assertEquals(listOf("/data/user/0/demo/fault.jpg"), transition.updatedAlarm.processing.single().evidencePhotoPaths)
        val writeback = AlarmAssetWritebackPolicy.apply(listOf(rack), transition)
        assertEquals("故障待处置", writeback.racks.single().devices.single().status)
        assertTrue(writeback.racks.single().devices.single().notes.contains("ALM-CAB-A-001"))
    }

    @Test
    fun furtherProcessing_keepsAlarmOpenAndCreatesTodoWithoutChangingRack() {
        val transition = AlarmReviewStateMachine.apply(
            alarm = alarm,
            coordinate = coordinate,
            input = AlarmReviewInput(
                decision = AlarmReviewDecision.NEEDS_FURTHER_PROCESSING,
                reviewer = "现场工程师C",
                reviewedAtMillis = 1_700_000_100_000L,
                note = "待检查跳线和端口配置",
            ),
        )

        assertEquals(AlarmStatus.UNHANDLED, transition.updatedAlarm.status)
        assertEquals(AlarmFollowUpAction.CREATE_FOLLOW_UP_TODO, transition.followUpAction)
        assertIs<AlarmAssetWriteback.NoChange>(transition.writeback)
        val writeback = AlarmAssetWritebackPolicy.apply(listOf(rack), transition)
        assertFalse(writeback.applied)
        assertEquals(rack, writeback.racks.single())
    }
}
