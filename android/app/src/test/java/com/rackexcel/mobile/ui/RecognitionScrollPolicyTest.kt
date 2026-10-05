package com.rackexcel.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RecognitionScrollPolicyTest {
    @Test
    fun target_movesToTaskProgressWhenRecognitionStarts() {
        assertEquals(
            RecognitionScrollTarget.TASK_PROGRESS,
            RecognitionScrollPolicy.target(
                isRunning = true,
                exportPhase = ExportPhase.IDLE,
                hasResult = false,
            ),
        )
    }

    @Test
    fun target_movesToDeliveryWhenRecognitionFinishesOrWorkbookCompletes() {
        assertEquals(
            RecognitionScrollTarget.DELIVERY,
            RecognitionScrollPolicy.target(
                isRunning = false,
                exportPhase = ExportPhase.WAITING_CONFIRMATION,
                hasResult = false,
            ),
        )
        assertEquals(
            RecognitionScrollTarget.DELIVERY,
            RecognitionScrollPolicy.target(
                isRunning = false,
                exportPhase = ExportPhase.COMPLETED,
                hasResult = true,
            ),
        )
    }

    @Test
    fun target_movesToExcelWritingCardAfterConfirmation() {
        assertEquals(
            RecognitionScrollTarget.EXPORT_WRITING,
            RecognitionScrollPolicy.target(
                isRunning = true,
                exportPhase = ExportPhase.WRITING,
                hasResult = false,
            ),
        )
        assertEquals(2, RecognitionScrollPolicy.EXPORT_WRITING_INDEX)
    }

    @Test
    fun runningTimelineIndex_accountsForTheExpandedAdvancedSection() {
        assertEquals(6, RecognitionScrollPolicy.runningTimelineIndex(hasImages = true, advancedOptionsOpen = false))
        assertEquals(7, RecognitionScrollPolicy.runningTimelineIndex(hasImages = true, advancedOptionsOpen = true))
    }
}
