package com.rackexcel.mobile.ui

enum class RecognitionScrollTarget {
    NONE,
    TASK_PROGRESS,
    EXPORT_WRITING,
    DELIVERY,
}

/** Keeps automatic scrolling tied to meaningful task transitions, not recompositions. */
object RecognitionScrollPolicy {
    fun target(
        isRunning: Boolean,
        exportPhase: ExportPhase,
        hasResult: Boolean,
    ): RecognitionScrollTarget = when {
        hasResult || exportPhase == ExportPhase.COMPLETED ->
            RecognitionScrollTarget.DELIVERY
        exportPhase == ExportPhase.WRITING -> RecognitionScrollTarget.EXPORT_WRITING
        exportPhase != ExportPhase.IDLE -> RecognitionScrollTarget.DELIVERY
        isRunning -> RecognitionScrollTarget.TASK_PROGRESS
        else -> RecognitionScrollTarget.NONE
    }

    /** Index of the timeline after the capture controls and optional advanced section. */
    fun runningTimelineIndex(hasImages: Boolean, advancedOptionsOpen: Boolean): Int = when {
        !hasImages -> 2
        advancedOptionsOpen -> 7
        else -> 6
    }

    /** Delivery summary (0), timeline (1), then the Excel writing card (2). */
    const val EXPORT_WRITING_INDEX = 2
}
