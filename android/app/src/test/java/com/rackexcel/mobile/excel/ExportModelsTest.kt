package com.rackexcel.mobile.excel

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportModelsTest {

    @Test
    fun efficiencyMetrics_usesBuiltInBaselinesAndDeductsManualReviewFromNetSaving() {
        val metrics = TaskEfficiencyMetrics.calculate(
            imageCount = 5,
            cabinetCount = 3,
            aiRecognitionMillis = 522_000L,
            manualReviewMillis = 130_000L,
            automatedExportMillis = 43_500L,
        )

        assertEquals(900_000L, metrics.humanRecognitionMillis)
        assertEquals(1_080_000L, metrics.humanExportMillis)
        assertEquals(378_000L, metrics.recognitionSavedMillis)
        assertEquals(1_036_500L, metrics.exportSavedMillis)
        assertEquals(1_284_500L, metrics.netSavedMillis)
        assertEquals(metrics.exportSavedMillis, metrics.formSavedMillis)
        assertEquals(metrics.netSavedMillis, metrics.totalSavedMillis)
    }

    @Test
    fun manualReviewDuration_endsWhenDeliveryIsConfirmed_notAfterExcelWriting() {
        val reviewStartedAt = 1_000L
        val deliveryConfirmedAt = 3_500L
        val excelFinishedAt = 8_500L
        val reviewMillis = TaskEfficiencyMetrics.manualReviewDurationMillis(
            reviewStartedAt,
            deliveryConfirmedAt,
        )

        assertTrue(excelFinishedAt > deliveryConfirmedAt)
        assertEquals(
            2_500L,
            reviewMillis,
            "Excel writing through $excelFinishedAt must not inflate manual review time",
        )
        assertEquals(0L, TaskEfficiencyMetrics.manualReviewDurationMillis(null, deliveryConfirmedAt))
        assertEquals(0L, TaskEfficiencyMetrics.manualReviewDurationMillis(4_000L, deliveryConfirmedAt))
    }

    @Test
    fun efficiencyMetrics_clampsNegativeSavingsAndUnknownValues() {
        val metrics = TaskEfficiencyMetrics.calculate(
            imageCount = 0,
            cabinetCount = -1,
            aiRecognitionMillis = 1_000_000L,
            manualReviewMillis = 9_000_000L,
            automatedExportMillis = 8_000_000L,
        )

        assertEquals(0L, metrics.humanRecognitionMillis)
        assertEquals(0L, metrics.humanExportMillis)
        assertEquals(0L, metrics.recognitionSavedMillis)
        assertEquals(0L, metrics.exportSavedMillis)
        assertEquals(0L, metrics.netSavedMillis)
    }

    @Test
    fun exportHoldPolicy_scalesWithTaskSizeAndKeepsInjectedRandomDeterministic() {
        val small = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 1,
            cabinetCount = 1,
            deviceCount = 4,
            reviewCount = 0,
            random = { 0.0 },
        )
        val routine = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 4,
            cabinetCount = 3,
            deviceCount = 20,
            reviewCount = 1,
            random = { 0.5 },
        )
        val multiCabinet = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 7,
            cabinetCount = 5,
            deviceCount = 40,
            reviewCount = 2,
            random = { 0.5 },
        )

        assertTrue(small in 5_000L..7_000L)
        assertTrue(routine in 5_000L..7_000L)
        assertTrue(multiCabinet in 5_000L..7_000L)
        assertTrue(small < routine && routine <= multiCabinet)
    }

    @Test
    fun exportHoldPolicy_addsSmallRandomVariationAndClampsVeryLargeTasksAtOneMinute() {
        val lower = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 5,
            cabinetCount = 3,
            deviceCount = 24,
            reviewCount = 1,
            random = { 0.0 },
        )
        val upper = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 5,
            cabinetCount = 3,
            deviceCount = 24,
            reviewCount = 1,
            random = { 0.999999 },
        )
        val capped = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 100,
            cabinetCount = 100,
            deviceCount = 1_000,
            reviewCount = 50,
            random = { 0.0 },
        )

        assertTrue(lower in 5_000L..7_000L)
        assertTrue(upper in 5_000L..7_000L)
        assertEquals(7_000L, capped)
        assertTrue(upper - lower <= 1_000L)
    }

    @Test
    fun exportHoldPolicy_keepsEachImageCountBandWithinItsAdvertisedWindow() {
        val smallWithDenseDevices = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 2,
            cabinetCount = 2,
            deviceCount = 94,
            reviewCount = 2,
            random = { 0.999999 },
        )
        val routineWithDenseDevices = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 5,
            cabinetCount = 5,
            deviceCount = 235,
            reviewCount = 5,
            random = { 0.999999 },
        )
        val multiCabinetWithDenseDevices = ExportHoldPolicy.chooseTargetMillis(
            imageCount = 10,
            cabinetCount = 10,
            deviceCount = 470,
            reviewCount = 10,
            random = { 0.999999 },
        )

        assertTrue(smallWithDenseDevices in 5_000L..7_000L)
        assertTrue(routineWithDenseDevices in 5_000L..7_000L)
        assertTrue(multiCabinetWithDenseDevices in 5_000L..7_000L)
        assertTrue(ExportHoldPolicy.isSupportedTarget(5_000L))
        assertTrue(ExportHoldPolicy.isSupportedTarget(7_000L))
        assertEquals(false, ExportHoldPolicy.isSupportedTarget(4_999L))
        assertEquals(false, ExportHoldPolicy.isSupportedTarget(7_001L))
    }
    @Test
    fun export_name_uses_the_configured_prefix_and_sorted_cabinet_ids() {
        val fileName = ExportFileName.build("云枢智维", listOf("K10", "K03"), 0L)

        assertTrue(
            Regex("^云枢智维_K03_K10_\\d{12}\\.xlsx$").matches(fileName),
        )
    }

    @Test
    fun room_name_takes_priority_and_uses_a_compact_timestamp_suffix() {
        val fileName = ExportFileName.build(
            prefix = "云枢智维",
            cabinetIds = listOf("K10", "K03"),
            nowMillis = 0L,
            roomName = "A区通信机房",
        )

        assertTrue(Regex("^A区通信机房_\\d{12}\\.xlsx$").matches(fileName))
    }

    @Test
    fun review_summary_exposes_completed_failed_and_pending_image_counts() {
        val summary = ExportSummary.from(
            listOf(
                ImageOutcome("K03.jpg", rack("K03"), ImageProcessingState.COMPLETED),
                ImageOutcome("K04.jpg", null, ImageProcessingState.FAILED, retryCount = 3, message = "修复失败"),
                ImageOutcome("K10.jpg", null, ImageProcessingState.QUALITY_REVIEW, message = "建议补拍"),
            ),
        )

        assertEquals(3, summary.imageCount)
        assertEquals(1, summary.successCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.pendingCount)
    }

    @Test
    fun completed_recognition_with_uncertain_notes_enters_manual_review_queue() {
        val outcome = ImageOutcome(
            imageName = "K17.jpg",
            rack = Rack(
                cabinetId = "K17",
                imageName = "K17.jpg",
                devices = listOf(Device("服务器", 21, 2, 0.87, "右侧轨 U21-U22")),
                uncertain = listOf("U21-U22 设备下沿被线缆遮挡，需现场核对。"),
            ),
            state = ImageProcessingState.COMPLETED,
        )

        assertEquals(true, ReviewPolicy.requiresManualReview(outcome))
        assertEquals(1, ExportSummary.from(listOf(outcome)).pendingCount)
        assertEquals(1, ExportSummary.from(listOf(outcome)).successCount)
    }

    @Test
    fun manually_confirmed_recognition_leaves_the_review_queue_but_remains_successful() {
        val outcome = ImageOutcome(
            imageName = "K17.jpg",
            rack = Rack(
                cabinetId = "K17",
                imageName = "K17.jpg",
                devices = listOf(Device("服务器", 21, 2, 0.87, "右侧轨 U21-U22")),
                uncertain = listOf("U21-U22 设备下沿被线缆遮挡，需现场核对。"),
            ),
            state = ImageProcessingState.COMPLETED,
            reviewConfirmed = true,
        )

        assertEquals(false, ReviewPolicy.requiresManualReview(outcome))
        assertEquals(0, ExportSummary.from(listOf(outcome)).pendingCount)
        assertEquals(1, ExportSummary.from(listOf(outcome)).successCount)
    }

    private fun rack(id: String) = Rack(
        cabinetId = id,
        imageName = "$id.jpg",
        devices = listOf(Device("交换机", 43, 1, 0.9, "右侧轨 U43")),
        uncertain = emptyList(),
    )
}
