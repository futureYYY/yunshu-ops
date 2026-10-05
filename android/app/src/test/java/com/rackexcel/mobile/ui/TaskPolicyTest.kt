package com.rackexcel.mobile.ui

import com.rackexcel.mobile.excel.ImageOutcome
import com.rackexcel.mobile.excel.ImageProcessingState
import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskPolicyTest {
    @Test
    fun effectiveConcurrency_usesSelectionButNeverExceedsImageCount() {
        assertEquals(4, TaskPolicy.effectiveConcurrency(requested = 5, imageCount = 4))
        assertEquals(3, TaskPolicy.effectiveConcurrency(requested = 5, imageCount = 3))
        assertEquals(5, TaskPolicy.effectiveConcurrency(requested = 5, imageCount = 8))
    }

    @Test
    fun effectiveConcurrency_staysWithinSupportedRange() {
        assertEquals(1, TaskPolicy.effectiveConcurrency(requested = 0, imageCount = 1))
        assertEquals(1, TaskPolicy.effectiveConcurrency(requested = 9, imageCount = 0))
        assertEquals(1, TaskPolicy.effectiveConcurrency(requested = 1, imageCount = 9))
    }

    @Test
    fun repairStatus_isBoundedToThreeRepairAttempts() {
        assertEquals("遇到问题，自动修复中 · 第 1/3 次", TaskPolicy.repairStatus(1))
        assertEquals("遇到问题，自动修复中 · 第 3/3 次", TaskPolicy.repairStatus(3))
        assertEquals("修复失败 · 建议补拍或单独重试", TaskPolicy.repairStatus(4))
    }

    @Test
    fun duplicateGenerate_isBlockedOnlyAfterACompletedDelivery() {
        assertEquals(true, TaskPolicy.shouldBlockDuplicateGenerate(false, true, 2))
        assertEquals(false, TaskPolicy.shouldBlockDuplicateGenerate(true, true, 2))
        assertEquals(false, TaskPolicy.shouldBlockDuplicateGenerate(false, false, 2))
        assertEquals(false, TaskPolicy.shouldBlockDuplicateGenerate(false, true, 0))
    }

    @Test
    fun recognitionTimelineStep_movesToRecognitionBeforeFirstImageCompletes() {
        assertEquals(
            2,
            TaskPolicy.recognitionTimelineStep(
                isRunning = true,
                hasResult = false,
                hasImages = true,
                outcomeStates = listOf(ImageProcessingState.ANALYZING, ImageProcessingState.QUEUED),
            ),
        )
    }

    @Test
    fun recognitionTimelineStep_prioritizesRepairAndDelivery() {
        assertEquals(
            3,
            TaskPolicy.recognitionTimelineStep(
                isRunning = true,
                hasResult = false,
                hasImages = true,
                outcomeStates = listOf(ImageProcessingState.REPAIRING, ImageProcessingState.ANALYZING),
            ),
        )
        assertEquals(
            4,
            TaskPolicy.recognitionTimelineStep(
                isRunning = false,
                hasResult = true,
                hasImages = true,
                outcomeStates = listOf(ImageProcessingState.COMPLETED),
            ),
        )
    }

    @Test
    fun preferredReviewOutcome_prioritizesManualReviewThenFallsBackToRecognizedRack() {
        val normal = outcome("normal", reviewRequired = false)
        val review = outcome("review", reviewRequired = true)

        assertEquals("review", TaskPolicy.preferredReviewOutcomeId(listOf(normal, review)))
        assertEquals("normal", TaskPolicy.preferredReviewOutcomeId(listOf(normal)))
        assertEquals(null, TaskPolicy.preferredReviewOutcomeId(emptyList()))
    }

    private fun outcome(id: String, reviewRequired: Boolean) = ImageOutcome(
        imageName = "$id.jpg",
        imageId = id,
        rack = Rack(
            cabinetId = "K01",
            imageName = "$id.jpg",
            devices = listOf(Device("服务器", 1, 2, 0.9, "右侧轨 U1-U2")),
            uncertain = if (reviewRequired) listOf("U 位边界需要现场确认") else emptyList(),
        ),
        state = ImageProcessingState.COMPLETED,
    )
}
