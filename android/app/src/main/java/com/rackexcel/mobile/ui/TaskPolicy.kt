package com.rackexcel.mobile.ui

import com.rackexcel.mobile.excel.ImageOutcome
import com.rackexcel.mobile.excel.ImageProcessingState
import com.rackexcel.mobile.excel.ReviewPolicy

object TaskPolicy {
    const val DEFAULT_CONCURRENCY = 5
    const val MAX_CONCURRENCY = 5
    const val MAX_RETRIES = 3

    fun effectiveConcurrency(requested: Int, imageCount: Int): Int =
        requested.coerceIn(1, MAX_CONCURRENCY).coerceAtMost(imageCount.coerceAtLeast(1))

    fun repairStatus(retry: Int): String = if (retry in 1..MAX_RETRIES) {
        "遇到问题，自动修复中 · 第 $retry/$MAX_RETRIES 次"
    } else {
        "修复失败 · 建议补拍或单独重试"
    }

    fun shouldBlockDuplicateGenerate(isRunning: Boolean, hasResult: Boolean, imageCount: Int): Boolean =
        !isRunning && hasResult && imageCount > 0

    /**
     * Direct review always starts with work that requires a decision. When no
     * uncertainty exists, the first recognized cabinet remains inspectable.
     */
    fun preferredReviewOutcomeId(outcomes: Iterable<ImageOutcome>): String? {
        val items = outcomes.toList()
        return items.firstOrNull { it.rack != null && ReviewPolicy.requiresManualReview(it) }?.imageId
            ?: items.firstOrNull { it.rack != null }?.imageId
    }

    /** Timeline indexes: 0 采集, 1 质检, 2 识别, 3 修复, 4 交付. */
    fun recognitionTimelineStep(
        isRunning: Boolean,
        hasResult: Boolean,
        hasImages: Boolean,
        outcomeStates: Iterable<ImageProcessingState>,
        exportPhase: ExportPhase = ExportPhase.IDLE,
    ): Int = when {
        hasResult || exportPhase != ExportPhase.IDLE -> 4
        outcomeStates.any { it == ImageProcessingState.REPAIRING } -> 3
        outcomeStates.any { it == ImageProcessingState.ANALYZING } -> 2
        isRunning || hasImages -> 1
        else -> 0
    }
}
