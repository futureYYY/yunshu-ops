package com.rackexcel.mobile.excel

import com.rackexcel.mobile.model.AnalysisResult
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RackOrdering
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ImageProcessingState(val label: String) {
    QUEUED("等待识别"),
    ANALYZING("识别中"),
    REPAIRING("自动修复中"),
    COMPLETED("识别完成"),
    QUALITY_REVIEW("建议复核"),
    FAILED("修复失败"),
}

data class RepairEvent(
    val retry: Int,
    val message: String,
)

data class ImageOutcome(
    val imageName: String,
    val rack: Rack?,
    val state: ImageProcessingState,
    val retryCount: Int = 0,
    val message: String = "",
    val repairEvents: List<RepairEvent> = emptyList(),
    /** Stable identity keeps retry, archive and UI state tied to the same source photo. */
    val imageId: String = imageName,
    /** Set after a field operator has accepted the current rack layout. */
    val reviewConfirmed: Boolean = false,
    /** App-private durable JPEG used by the field-review page after restart. */
    val reviewImagePath: String? = null,
) {
    val cabinetId: String? get() = rack?.cabinetId
}

/** Keeps a successful-but-uncertain recognition distinct from a failed request. */
object ReviewPolicy {
    fun requiresManualReview(rack: Rack): Boolean = rack.uncertain.isNotEmpty()

    fun requiresManualReview(outcome: ImageOutcome): Boolean =
        !outcome.reviewConfirmed && (
            outcome.state == ImageProcessingState.QUALITY_REVIEW ||
                outcome.rack?.let(::requiresManualReview) == true
            )
}

data class ExportSummary(
    val imageCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
) {
    companion object {
        fun from(outcomes: List<ImageOutcome>): ExportSummary = ExportSummary(
            imageCount = outcomes.size,
            successCount = outcomes.count { it.rack != null },
            failedCount = outcomes.count { it.state == ImageProcessingState.FAILED },
            pendingCount = outcomes.count(ReviewPolicy::requiresManualReview),
        )
    }
}

/**
 * Explainable, task-level time estimate. Baselines are intentionally built
 * into the product so field users do not need to configure a finance model.
 * Values are persisted with the task and are never accumulated twice for an
 * upserted task id.
 */
data class TaskEfficiencyMetrics(
    val humanRecognitionMillis: Long = 0L,
    val humanExportMillis: Long = 0L,
    val aiRecognitionMillis: Long = 0L,
    val manualReviewMillis: Long = 0L,
    val automatedExportMillis: Long = 0L,
    val recognitionSavedMillis: Long = 0L,
    val exportSavedMillis: Long = 0L,
    val netSavedMillis: Long = 0L,
) {
    /** Compatibility aliases used by delivery/detail views. */
    val formSavedMillis: Long get() = exportSavedMillis
    val totalSavedMillis: Long get() = netSavedMillis

    companion object {
        const val HUMAN_RECOGNITION_PER_IMAGE_MILLIS = 3L * 60L * 1_000L
        const val HUMAN_EXPORT_PER_CABINET_MILLIS = 6L * 60L * 1_000L
        val EMPTY = TaskEfficiencyMetrics()

        fun calculate(
            imageCount: Int,
            cabinetCount: Int,
            aiRecognitionMillis: Long,
            manualReviewMillis: Long,
            automatedExportMillis: Long,
        ): TaskEfficiencyMetrics {
            val humanRecognition = imageCount.coerceAtLeast(0).toLong() * HUMAN_RECOGNITION_PER_IMAGE_MILLIS
            val humanExport = cabinetCount.coerceAtLeast(0).toLong() * HUMAN_EXPORT_PER_CABINET_MILLIS
            val ai = aiRecognitionMillis.coerceAtLeast(0L)
            val review = manualReviewMillis.coerceAtLeast(0L)
            val automated = automatedExportMillis.coerceAtLeast(0L)
            val recognitionSaved = (humanRecognition - ai).coerceAtLeast(0L)
            val exportSaved = (humanExport - automated).coerceAtLeast(0L)
            // A completed task still needs field confirmation. The headline
            // saving therefore deducts the actual manual-review duration from
            // the two automation gains.
            val netSaved = (recognitionSaved + exportSaved - review).coerceAtLeast(0L)
            return TaskEfficiencyMetrics(
                humanRecognitionMillis = humanRecognition,
                humanExportMillis = humanExport,
                aiRecognitionMillis = ai,
                manualReviewMillis = review,
                automatedExportMillis = automated,
                recognitionSavedMillis = recognitionSaved,
                exportSavedMillis = exportSaved,
                netSavedMillis = netSaved,
            )
        }

        /**
         * Review time ends when the operator confirms delivery, never when the
         * workbook finishes writing. This keeps the delivery feedback interval
         * out of the manual-review metric.
         */
        fun manualReviewDurationMillis(
            reviewStartedAtMillis: Long?,
            deliveryConfirmedAtMillis: Long,
        ): Long = reviewStartedAtMillis
            ?.let { (deliveryConfirmedAtMillis - it).coerceAtLeast(0L) }
            ?: 0L
    }
}

/**
 * User-visible delivery target derived from the amount of workbook data to prepare.
 *
 * The target intentionally stays within five to seven seconds: the workbench shows
 * the write-and-verify steps without making a small handover feel stalled.  The
 * amount of source data raises the base time while an injected random variation keeps
 * equally sized tasks from looking scripted.  The random input is injectable so the
 * policy remains deterministic in tests.
 */
object ExportHoldPolicy {
    const val MIN_TARGET_MILLIS = 5_000L
    const val MAX_TARGET_MILLIS = 7_000L

    fun chooseTargetMillis(
        imageCount: Int,
        cabinetCount: Int,
        deviceCount: Int,
        reviewCount: Int,
        random: () -> Double = Math::random,
    ): Long {
        val images = imageCount.coerceAtLeast(0)
        val cabinets = cabinetCount.coerceAtLeast(0)
        val devices = deviceCount.coerceAtLeast(0)
        val reviews = reviewCount.coerceAtLeast(0)

        val imageContribution = (images - 1).coerceAtLeast(0) * 220L
        val taskDetailMillis = cabinets * 80L + devices * 12L + reviews * 100L
        val randomVariationMillis = (random().coerceIn(0.0, 0.999999999) * 600L).toLong()
        return (MIN_TARGET_MILLIS + imageContribution + taskDetailMillis + randomVariationMillis)
            .coerceIn(MIN_TARGET_MILLIS, MAX_TARGET_MILLIS)
    }

    fun isSupportedTarget(targetMillis: Long): Boolean =
        targetMillis in MIN_TARGET_MILLIS..MAX_TARGET_MILLIS
}

data class ExportTask(
    val taskId: String,
    val roomName: String,
    val createdAtMillis: Long,
    val modelName: String,
    val promptName: String,
    val promptVersion: String,
    val promptContent: String,
    val requestedConcurrency: Int,
    val effectiveConcurrency: Int,
    val outcomes: List<ImageOutcome>,
    val racks: List<Rack>,
    val analysis: AnalysisResult,
) {
    val summary: ExportSummary get() = ExportSummary.from(outcomes)
}

object ExportFileName {
    fun build(
        prefix: String,
        cabinetIds: List<String>,
        nowMillis: Long,
        roomName: String = "",
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmm", Locale.CHINA).format(Date(nowMillis))
        val cleanRoomName = cleanSegment(roomName)
        if (cleanRoomName.isNotBlank()) return "${cleanRoomName}_$timestamp.xlsx"

        val cleanPrefix = prefix.trim().ifBlank { "云枢智维" }
            .let(::cleanSegment)
            .ifBlank { "云枢智维" }
        val normalized = cabinetIds.map { id ->
            RackOrdering.normalize(id) ?: id.trim().uppercase(Locale.ROOT)
        }.distinct().sortedWith(
            compareBy<String> { if (RackOrdering.normalize(it) != null) 0 else 1 }
                .thenBy { Regex("^K(\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it },
        )
        val identifiers = normalized.joinToString("_").ifBlank { "未命名机柜" }
        return "${cleanPrefix}_${identifiers.take(80)}_$timestamp.xlsx"
    }

    private fun cleanSegment(value: String): String = value.trim()
        .replace(Regex("[^\\w.\\-\\u4e00-\\u9fff]"), "_")
        .trim('_')
}
