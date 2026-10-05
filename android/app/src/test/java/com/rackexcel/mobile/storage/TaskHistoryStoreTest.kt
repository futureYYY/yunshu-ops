package com.rackexcel.mobile.storage

import com.rackexcel.mobile.excel.ExportSummary
import com.rackexcel.mobile.excel.TaskEfficiencyMetrics
import com.rackexcel.mobile.alarm.AlarmDeviceIdentity
import com.rackexcel.mobile.alarm.AlarmRecord
import com.rackexcel.mobile.alarm.AlarmSeverity
import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.ModelRiskCandidate
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RiskLevel
import com.rackexcel.mobile.task.TaskArchiveStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskHistoryStoreTest {
    @Test
    fun historyText_stripsRepeatedRetryPrefixesFromLegacySnapshots() {
        assertEquals(
            "请求模型失败: timeout",
            TaskHistoryText.normalizeRepairMessage("第 1 次：第 1 次：第 2 次：请求模型失败: timeout"),
        )
    }

    @Test
    fun historyPolicy_keepsEveryCompletedTaskAndSupportsSingleTaskRemoval() {
        val existing = (1..41).map { index -> historyItem("task-$index", index.toLong()) }
        val newest = historyItem("task-new", 99L)

        val updated = TaskHistoryPolicy.upsert(existing, newest)

        assertEquals(42, updated.size)
        assertEquals("task-new", updated.first().taskId)
        assertEquals(41, TaskHistoryPolicy.remove(updated, "task-17").size)
        assertEquals(null, TaskHistoryPolicy.remove(updated, "task-17").firstOrNull { it.taskId == "task-17" })
    }

    @Test
    fun historyJson_roundTripsDetailedReviewSnapshotsAndDuration() {
        val rack = Rack(
            cabinetId = "K03",
            imageName = "rack-k03.jpg",
            devices = listOf(
                Device("交换机", bottomU = 43, heightU = 1, confidence = 0.98, evidence = "右侧轨 U43"),
                Device("服务器", bottomU = 21, heightU = 2, confidence = 0.91, evidence = "右侧轨 U21-U22"),
            ),
            uncertain = listOf("U21 被线缆局部遮挡"),
            riskCandidates = listOf(
                ModelRiskCandidate(
                    category = "散热",
                    description = "设备区间较密集",
                    level = RiskLevel.MEDIUM,
                    recommendation = "现场核验温度",
                    evidence = "U20-U30",
                    confidence = 0.73,
                    dataSource = "照片识别",
                ),
            ),
        )
        val item = TaskHistoryItem(
            taskId = "task-001",
            roomName = "A 区机房",
            createdAtMillis = 1_000L,
            startedAtMillis = 1_000L,
            finishedAtMillis = 8_250L,
            cabinetIds = listOf("K03"),
            summary = ExportSummary(1, 1, 0, 1),
            resultName = "识别结果.xlsx",
            resultUri = "content://downloads/result",
            efficiency = TaskEfficiencyMetrics.calculate(1, 1, 2_000L, 500L, 4_000L),
            confirmationMode = "现场复核后确认",
            desktopDelivery = DesktopDeliveryReceipt(
                receiverId = "receiver-001",
                receiverName = "机房运维电脑",
                uploadId = "upload-001",
                fileName = "识别结果 (1).xlsx",
                savedPath = "D:\\机房交付\\Excel\\识别结果 (1).xlsx",
                sha256 = "A".repeat(64),
                sizeBytes = 24576L,
                receivedAtMillis = 9_000L,
            ),
            reviewItems = listOf(
                TaskReviewSnapshot(
                    imageId = "image-001",
                    imageName = "rack-k03.jpg",
                    cabinetId = "K03",
                    reviewImagePath = "/data/user/0/app/files/review_images/task-001/image-001.jpg",
                    state = "建议复核",
                    message = "识别完成，发现 1 项待人工复核。",
                    retryCount = 1,
                    repairEvents = listOf("第 1 次：模型响应格式复核"),
                    uncertain = rack.uncertain,
                    riskTexts = listOf("散热：设备区间较密集"),
                    rack = rack,
                ),
            ),
            alarmRecords = listOf(
                AlarmRecord(
                    alarmId = "ALM-001",
                    identity = AlarmDeviceIdentity(assetId = "asset-001"),
                    severity = AlarmSeverity.CRITICAL,
                    occurredAtMillis = 5_000L,
                    description = "演示设备告警",
                    taskId = "task-001",
                ),
            ),
        )

        val restored = TaskHistoryJson.decode(TaskHistoryJson.encode(listOf(item))).single()

        assertEquals(7_250L, restored.durationMillis)
        assertEquals(1_000L, restored.startedAtMillis)
        assertEquals(8_250L, restored.finishedAtMillis)
        assertEquals("K03", restored.reviewItems.single().cabinetId)
        assertEquals(listOf("U21 被线缆局部遮挡"), restored.reviewItems.single().uncertain)
        assertEquals(listOf("散热：设备区间较密集"), restored.reviewItems.single().riskTexts)
        assertEquals("服务器", restored.reviewItems.single().rack?.devices?.last()?.type)
        assertEquals(2, restored.reviewItems.single().rack?.devices?.last()?.heightU)
        assertEquals("散热", restored.reviewItems.single().rack?.riskCandidates?.single()?.category)
        assertEquals(listOf("模型响应格式复核"), restored.reviewItems.single().repairEvents)
        assertEquals("机房运维电脑", restored.desktopDelivery?.receiverName)
        assertEquals("upload-001", restored.desktopDelivery?.uploadId)
        assertEquals(24576L, restored.desktopDelivery?.sizeBytes)
        assertEquals("现场复核后确认", restored.confirmationMode)
        assertEquals(533_500L, restored.efficiency.netSavedMillis)
        assertEquals("ALM-001", restored.alarmRecords.single().alarmId)
        assertEquals("asset-001", restored.alarmRecords.single().identity.assetId)
    }

    @Test
    fun historyJson_readsLegacyEntriesWithReasonableDefaults() {
        val restored = TaskHistoryJson.decode(
            """[{"taskId":"legacy","roomName":"旧机房","createdAtMillis":2000,"cabinetIds":["K10"],"imageCount":1,"successCount":1,"failedCount":0,"pendingCount":0,"resultName":"legacy.xlsx","resultUri":"content://downloads/legacy"}]""",
        ).single()

        assertEquals(2_000L, restored.startedAtMillis)
        assertEquals(2_000L, restored.finishedAtMillis)
        assertEquals(0L, restored.durationMillis)
        assertEquals(emptyList(), restored.reviewItems)
        assertNull(restored.reviewItems.firstOrNull())
        assertNull(restored.desktopDelivery)
    }

    @Test
    fun historyJson_ignoresMalformedAlarmSnapshotWithoutDroppingTask() {
        val restored = TaskHistoryJson.decode(
            """[{"taskId":"alarm-legacy","createdAtMillis":2000,"cabinetIds":[],"imageCount":0,"successCount":0,"failedCount":0,"pendingCount":0,"alarmRecords":[{"alarmId":"broken"}]}]""",
        ).single()

        assertEquals("alarm-legacy", restored.taskId)
        assertEquals(emptyList(), restored.alarmRecords)
    }

    @Test
    fun historyJson_keepsExpiredDeliveryUnavailableUntilReexport() {
        val item = TaskHistoryItem(
            taskId = "review-edit",
            roomName = "现场机房",
            createdAtMillis = 3_000L,
            startedAtMillis = 3_000L,
            finishedAtMillis = 4_000L,
            cabinetIds = listOf("K03"),
            summary = ExportSummary(1, 1, 0, 1),
            resultName = null,
            resultUri = null,
        )

        val restored = TaskHistoryJson.decode(TaskHistoryJson.encode(listOf(item))).single()

        assertNull(restored.resultName)
        assertNull(restored.resultUri)
    }

    @Test
    fun archivePaths_sanitizeIdentifiersAndStayInsideTaskDirectories() {
        assertEquals(
            "review_images/task_2026_08/image_01.jpg",
            TaskArchiveStore.reviewRelativePath("task/2026:08", "image 01"),
        )
        assertEquals(
            "original_images/task_2026_08/image_01.png",
            TaskArchiveStore.originalRelativePath("task/2026:08", "image 01", "image/png"),
        )
    }

    private fun historyItem(taskId: String, createdAtMillis: Long) = TaskHistoryItem(
        taskId = taskId,
        roomName = "现场机房",
        createdAtMillis = createdAtMillis,
        cabinetIds = listOf("K03"),
        summary = ExportSummary(1, 1, 0, 0),
        resultName = "交付.xlsx",
        resultUri = "content://files/$taskId",
    )
}
