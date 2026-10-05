package com.rackexcel.mobile.alarm

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmSourceAndJsonTest {
    @Test
    fun jsonCodec_roundTripsAlarmAndProcessingHistory() {
        val original = AlarmRecord(
            alarmId = "ALM-100",
            identity = AlarmDeviceIdentity(
                deviceName = "CAB-B 存储服务器01",
                managementIp = "192.0.2.41",
                assetId = "DEMO-CAB-B-SRV-01",
            ),
            severity = AlarmSeverity.WARNING,
            occurredAtMillis = 1_700_000_000_000L,
            description = "模拟磁盘告警",
            status = AlarmStatus.HANDLED,
            processing = listOf(
                AlarmProcessingRecord(
                    reviewer = "工程师A",
                    reviewedAtMillis = 1_700_000_100_000L,
                    decision = AlarmReviewDecision.DEVICE_FAULT,
                    note = "已完成现场确认",
                    evidencePhotoPaths = listOf("evidence.jpg"),
                ),
            ),
        )

        val restored = AlarmJsonCodec.decode(AlarmJsonCodec.encode(listOf(original))).single()

        assertEquals(original, restored)
    }

    @Test
    fun mockSource_injectionAndUpdatePersistInItsLocalStore() = runBlocking {
        val store = InMemoryAlarmRecordStore()
        val source = MockAlarmSource(store)
        val injected = AlarmRecord(
            alarmId = "ALM-101",
            identity = AlarmDeviceIdentity(assetId = "DEMO-CAB-C-SW-01"),
            severity = AlarmSeverity.INFO,
            occurredAtMillis = 1_700_000_000_000L,
            description = "演示注入告警",
        )

        source.injectAlarm(injected)
        source.updateAlarm(injected.copy(status = AlarmStatus.HANDLED))

        val saved = source.loadAlarms().single()
        assertEquals(AlarmStatus.HANDLED, saved.status)
        assertTrue(store.persistedPayload.isNotBlank())
    }

    @Test
    fun jsonFileStore_seedsOnceThenKeepsLocallyInjectedAlarmAcrossReload() {
        val directory = Files.createTempDirectory("alarm-json-store").toFile()
        try {
            val file = File(directory, "mock_alarms.json")
            val store = JsonFileAlarmRecordStore(file) { "{\"version\":1,\"alarms\":[]}" }
            assertTrue(store.loadAlarms().isEmpty())

            val record = AlarmRecord(
                alarmId = "ALM-LOCAL-1",
                identity = AlarmDeviceIdentity(managementIp = "192.0.2.1"),
                severity = AlarmSeverity.INFO,
                occurredAtMillis = 1_700_000_000_000L,
                description = "本地 JSON 持久化验证",
            )
            store.saveAlarms(listOf(record))

            assertEquals(listOf(record), JsonFileAlarmRecordStore(file).loadAlarms())
        } finally {
            directory.deleteRecursively()
        }
    }
}
