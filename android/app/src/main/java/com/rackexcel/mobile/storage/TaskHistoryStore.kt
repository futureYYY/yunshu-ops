package com.rackexcel.mobile.storage

import android.content.Context
import com.rackexcel.mobile.alarm.AlarmJsonCodec
import com.rackexcel.mobile.alarm.AlarmRecord
import com.rackexcel.mobile.excel.ExportSummary
import com.rackexcel.mobile.excel.TaskEfficiencyMetrics
import com.rackexcel.mobile.model.ActionHints
import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.ModelRiskCandidate
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RackMetadata
import com.rackexcel.mobile.model.RiskLevel
import org.json.JSONArray
import org.json.JSONObject

/**
 * A compact, local snapshot used to reopen a completed task after the app has
 * been restarted. Source content URIs are deliberately excluded; only files
 * written into the app-private archive can be referenced here.
 */
data class TaskReviewSnapshot(
    val imageId: String,
    val imageName: String,
    val cabinetId: String?,
    val reviewImagePath: String?,
    val state: String,
    val reviewConfirmed: Boolean = false,
    val message: String = "",
    val retryCount: Int = 0,
    val repairEvents: List<String> = emptyList(),
    val uncertain: List<String> = emptyList(),
    val riskTexts: List<String> = emptyList(),
    val rack: Rack? = null,
)

/**
 * A non-sensitive acknowledgement returned by the paired Windows receiver.
 * Access tokens and pairing material are intentionally excluded from history.
 */
data class DesktopDeliveryReceipt(
    val receiverId: String,
    val receiverName: String,
    val uploadId: String,
    val fileName: String,
    val savedPath: String,
    val sha256: String,
    val sizeBytes: Long,
    val receivedAtMillis: Long,
)

data class TaskHistoryItem(
    val taskId: String,
    val roomName: String,
    val createdAtMillis: Long,
    val cabinetIds: List<String>,
    val summary: ExportSummary,
    val resultName: String?,
    val resultUri: String?,
    val startedAtMillis: Long = createdAtMillis,
    val finishedAtMillis: Long = createdAtMillis,
    val reviewItems: List<TaskReviewSnapshot> = emptyList(),
    val desktopDelivery: DesktopDeliveryReceipt? = null,
    val efficiency: TaskEfficiencyMetrics = TaskEfficiencyMetrics.EMPTY,
    /** 未确认 / 识别后直接确认 / 现场复核后确认. */
    val confirmationMode: String = "未确认",
    val recognitionFinishedAtMillis: Long? = null,
    val reviewStartedAtMillis: Long? = null,
    val alarmRecords: List<AlarmRecord> = emptyList(),
) {
    val durationMillis: Long
        get() = (finishedAtMillis - startedAtMillis).coerceAtLeast(0L)
}

class TaskHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<TaskHistoryItem> = TaskHistoryJson
        .decode(preferences.getString(KEY_HISTORY, "[]"))
        .sortedByDescending(TaskHistoryItem::createdAtMillis)

    fun add(item: TaskHistoryItem) {
        val all = TaskHistoryPolicy.upsert(load(), item)
        preferences.edit().putString(KEY_HISTORY, TaskHistoryJson.encode(all)).apply()
    }

    fun remove(taskId: String) {
        val all = TaskHistoryPolicy.remove(load(), taskId)
        preferences.edit().putString(KEY_HISTORY, TaskHistoryJson.encode(all)).apply()
    }

    fun clear() = preferences.edit().remove(KEY_HISTORY).apply()

    private companion object {
        const val PREFERENCES = "yunshu_task_history"
        const val KEY_HISTORY = "history"
    }
}

/** Keeps completed tasks in chronological order without imposing a retention cap. */
object TaskHistoryPolicy {
    fun upsert(existing: List<TaskHistoryItem>, item: TaskHistoryItem): List<TaskHistoryItem> =
        (listOf(item) + existing)
            .distinctBy(TaskHistoryItem::taskId)
            .sortedByDescending(TaskHistoryItem::createdAtMillis)

    fun remove(existing: List<TaskHistoryItem>, taskId: String): List<TaskHistoryItem> =
        existing.filterNot { it.taskId == taskId }
}

/** Removes retry labels that older app versions persisted inside the reason text. */
object TaskHistoryText {
    private val retryPrefix = Regex("^第\\s*\\d+\\s*次\\s*[:：]\\s*")

    fun normalizeRepairMessage(value: String): String {
        var normalized = value.trim()
        repeat(8) {
            val stripped = normalized.replaceFirst(retryPrefix, "").trim()
            if (stripped == normalized) return normalized
            normalized = stripped
        }
        return normalized
    }
}

/** JSON codec is public to keep the history payload independently testable. */
object TaskHistoryJson {
    fun encode(items: List<TaskHistoryItem>): String = JSONArray().apply {
        items.forEach { item -> put(item.toJson()) }
    }.toString()

    fun decode(payload: String?): List<TaskHistoryItem> = runCatching {
        val values = JSONArray(payload ?: "[]")
        buildList {
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.toHistoryItem()?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun TaskHistoryItem.toJson(): JSONObject = JSONObject()
        .put("taskId", taskId)
        .put("roomName", roomName)
        .put("createdAtMillis", createdAtMillis)
        .put("startedAtMillis", startedAtMillis)
        .put("finishedAtMillis", finishedAtMillis)
        .put("cabinetIds", cabinetIds.toJsonArray())
        .put("imageCount", summary.imageCount)
        .put("successCount", summary.successCount)
        .put("failedCount", summary.failedCount)
        .put("pendingCount", summary.pendingCount)
        .put("resultName", resultName)
        .put("resultUri", resultUri)
        .put("efficiency", efficiency.toJson())
        .put("confirmationMode", confirmationMode)
        .put("recognitionFinishedAtMillis", recognitionFinishedAtMillis)
        .put("reviewStartedAtMillis", reviewStartedAtMillis)
        .put("alarmRecords", JSONArray().apply { alarmRecords.forEach { put(AlarmJsonCodec.toJsonObject(it)) } })
        .put("desktopDelivery", desktopDelivery?.toJson())
        .put("reviewItems", JSONArray().apply { reviewItems.forEach { put(it.toJson()) } })

    private fun JSONObject.toHistoryItem(): TaskHistoryItem? {
        val taskId = optString("taskId").trim()
        if (taskId.isBlank()) return null
        val createdAt = optLong("createdAtMillis", 0L)
        val startedAt = longOrDefault("startedAtMillis", createdAt)
        val finishedAt = longOrDefault("finishedAtMillis", createdAt)
        return TaskHistoryItem(
            taskId = taskId,
            roomName = optString("roomName"),
            createdAtMillis = createdAt,
            startedAtMillis = startedAt,
            finishedAtMillis = finishedAt.coerceAtLeast(startedAt),
            cabinetIds = optJSONArray("cabinetIds").strings(),
            summary = ExportSummary(
                imageCount = optInt("imageCount"),
                successCount = optInt("successCount"),
                failedCount = optInt("failedCount"),
                pendingCount = optInt("pendingCount"),
            ),
            resultName = optString("resultName").takeIf { it.isNotBlank() },
            resultUri = optString("resultUri").takeIf { it.isNotBlank() },
            efficiency = optJSONObject("efficiency").toEfficiencyMetrics(),
            confirmationMode = optString("confirmationMode").trim().ifBlank { "未确认" },
            recognitionFinishedAtMillis = optionalLong("recognitionFinishedAtMillis"),
            reviewStartedAtMillis = optionalLong("reviewStartedAtMillis"),
            alarmRecords = AlarmJsonCodec.decodeArray(optJSONArray("alarmRecords")),
            reviewItems = optJSONArray("reviewItems").objects().mapNotNull { it.toReviewSnapshot() },
            desktopDelivery = optJSONObject("desktopDelivery")?.toDesktopDeliveryReceipt(),
        )
    }

    private fun TaskEfficiencyMetrics.toJson(): JSONObject = JSONObject()
        .put("humanRecognitionMillis", humanRecognitionMillis)
        .put("humanExportMillis", humanExportMillis)
        .put("aiRecognitionMillis", aiRecognitionMillis)
        .put("manualReviewMillis", manualReviewMillis)
        .put("automatedExportMillis", automatedExportMillis)
        .put("recognitionSavedMillis", recognitionSavedMillis)
        .put("exportSavedMillis", exportSavedMillis)
        .put("netSavedMillis", netSavedMillis)

    private fun JSONObject?.toEfficiencyMetrics(): TaskEfficiencyMetrics = TaskEfficiencyMetrics(
        humanRecognitionMillis = this?.optLong("humanRecognitionMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        humanExportMillis = this?.optLong("humanExportMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        aiRecognitionMillis = this?.optLong("aiRecognitionMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        manualReviewMillis = this?.optLong("manualReviewMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        automatedExportMillis = this?.optLong("automatedExportMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        recognitionSavedMillis = this?.optLong("recognitionSavedMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        exportSavedMillis = this?.optLong("exportSavedMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
        netSavedMillis = this?.optLong("netSavedMillis", 0L)?.coerceAtLeast(0L) ?: 0L,
    )

    private fun DesktopDeliveryReceipt.toJson(): JSONObject = JSONObject()
        .put("receiverId", receiverId)
        .put("receiverName", receiverName)
        .put("uploadId", uploadId)
        .put("fileName", fileName)
        .put("savedPath", savedPath)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
        .put("receivedAtMillis", receivedAtMillis)

    private fun JSONObject.toDesktopDeliveryReceipt(): DesktopDeliveryReceipt? {
        val receiverId = optString("receiverId").trim()
        val receiverName = optString("receiverName").trim()
        val fileName = optString("fileName").trim()
        val savedPath = optString("savedPath").trim()
        if (receiverId.isBlank() || receiverName.isBlank() || fileName.isBlank() || savedPath.isBlank()) return null
        return DesktopDeliveryReceipt(
            receiverId = receiverId,
            receiverName = receiverName,
            uploadId = optString("uploadId"),
            fileName = fileName,
            savedPath = savedPath,
            sha256 = optString("sha256"),
            sizeBytes = optLong("sizeBytes", 0L).coerceAtLeast(0L),
            receivedAtMillis = optLong("receivedAtMillis", 0L).coerceAtLeast(0L),
        )
    }

    private fun TaskReviewSnapshot.toJson(): JSONObject = JSONObject()
        .put("imageId", imageId)
        .put("imageName", imageName)
        .put("cabinetId", cabinetId)
        .put("reviewImagePath", reviewImagePath)
        .put("state", state)
        .put("reviewConfirmed", reviewConfirmed)
        .put("message", message)
        .put("retryCount", retryCount)
        .put("repairEvents", repairEvents.toJsonArray())
        .put("uncertain", uncertain.toJsonArray())
        .put("riskTexts", riskTexts.toJsonArray())
        .put("rack", rack?.toJson())

    private fun JSONObject.toReviewSnapshot(): TaskReviewSnapshot? {
        val imageId = optString("imageId").trim()
        val imageName = optString("imageName").trim()
        if (imageId.isBlank() || imageName.isBlank()) return null
        val rack = optJSONObject("rack")?.toRack()
        return TaskReviewSnapshot(
            imageId = imageId,
            imageName = imageName,
            cabinetId = optString("cabinetId").trim().ifBlank { rack?.cabinetId },
            reviewImagePath = optString("reviewImagePath").trim().takeIf { it.isNotBlank() },
            state = optString("state").trim().ifBlank { "等待识别" },
            reviewConfirmed = optBoolean("reviewConfirmed", false),
            message = optString("message"),
            retryCount = optInt("retryCount").coerceAtLeast(0),
            repairEvents = optJSONArray("repairEvents").strings().map(TaskHistoryText::normalizeRepairMessage),
            uncertain = optJSONArray("uncertain").strings().ifEmpty { rack?.uncertain.orEmpty() },
            riskTexts = optJSONArray("riskTexts").strings().ifEmpty { rack?.riskCandidates.orEmpty().map(::riskText) },
            rack = rack,
        )
    }

    private fun Rack.toJson(): JSONObject = JSONObject()
        .put("cabinetId", cabinetId)
        .put("imageName", imageName)
        .put("devices", JSONArray().apply { devices.forEach { put(it.toJson()) } })
        .put("uncertain", uncertain.toJsonArray())
        .put("riskCandidates", JSONArray().apply { riskCandidates.forEach { put(it.toJson()) } })
        .put("actionHints", actionHints.toJson())
        .put("metadata", metadata.toJson())

    private fun JSONObject.toRack(): Rack? {
        val cabinetId = optString("cabinetId").trim()
        if (cabinetId.isBlank()) return null
        return Rack(
            cabinetId = cabinetId,
            imageName = optString("imageName"),
            devices = optJSONArray("devices").objects().mapNotNull { it.toDevice() },
            uncertain = optJSONArray("uncertain").strings(),
            riskCandidates = optJSONArray("riskCandidates").objects().mapNotNull { it.toRiskCandidate() },
            actionHints = optJSONObject("actionHints").toActionHints(),
            metadata = optJSONObject("metadata").toRackMetadata(),
        )
    }

    private fun Device.toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("bottomU", bottomU)
        .put("heightU", heightU)
        .put("confidence", confidence)
        .put("evidence", evidence)
        .put("displayName", displayName)
        .put("model", model)
        .put("manufacturer", manufacturer)
        .put("assetId", assetId)
        .put("serialNumber", serialNumber)
        .put("managementIp", managementIp)
        .put("businessSystem", businessSystem)
        .put("purpose", purpose)
        .put("powerW", powerW)
        .put("owner", owner)
        .put("phone", phone)
        .put("installDate", installDate)
        .put("status", status)
        .put("notes", notes)

    private fun JSONObject.toDevice(): Device? {
        val type = optString("type").trim()
        val bottomU = optInt("bottomU", 0)
        val heightU = optInt("heightU", 0)
        if (type.isBlank() || bottomU !in 1..47 || heightU !in 1..2) return null
        return Device(
            type = type,
            bottomU = bottomU,
            heightU = heightU,
            confidence = optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            evidence = optString("evidence"),
            displayName = optString("displayName"),
            model = optString("model"),
            manufacturer = optString("manufacturer"),
            assetId = optString("assetId"),
            serialNumber = optString("serialNumber"),
            managementIp = optString("managementIp"),
            businessSystem = optString("businessSystem"),
            purpose = optString("purpose"),
            powerW = optionalInt("powerW"),
            owner = optString("owner"),
            phone = optString("phone"),
            installDate = optString("installDate"),
            status = optString("status"),
            notes = optString("notes"),
        )
    }

    private fun ModelRiskCandidate.toJson(): JSONObject = JSONObject()
        .put("category", category)
        .put("description", description)
        .put("level", level.name)
        .put("recommendation", recommendation)
        .put("evidence", evidence)
        .put("confidence", confidence)
        .put("dataSource", dataSource)

    private fun JSONObject.toRiskCandidate(): ModelRiskCandidate? {
        val category = optString("category").trim()
        val description = optString("description").trim()
        if (category.isBlank() || description.isBlank()) return null
        return ModelRiskCandidate(
            category = category,
            description = description,
            level = riskLevel(optString("level")),
            recommendation = optString("recommendation"),
            evidence = optString("evidence"),
            confidence = optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            dataSource = optString("dataSource"),
        )
    }

    private fun ActionHints.toJson(): JSONObject = JSONObject()
        .put("short", short)
        .put("medium", medium)
        .put("long", long)

    private fun JSONObject?.toActionHints(): ActionHints = ActionHints(
        short = this?.optString("short").orEmpty(),
        medium = this?.optString("medium").orEmpty(),
        long = this?.optString("long").orEmpty(),
    )

    private fun RackMetadata.toJson(): JSONObject = JSONObject()
        .put("physicalLocation", physicalLocation)
        .put("cabinetPower", cabinetPower)
        .put("powerType", powerType)
        .put("roomPurpose", roomPurpose)
        .put("powerConfig", powerConfig)
        .put("ratedCurrent", ratedCurrent)
        .put("temperature", temperature)
        .put("humidity", humidity)
        .put("antiStatic", antiStatic)
        .put("fireSystem", fireSystem)
        .put("monitoring", monitoring)
        .put("accessControl", accessControl)

    private fun JSONObject?.toRackMetadata(): RackMetadata = RackMetadata(
        physicalLocation = this?.optString("physicalLocation").orEmpty(),
        cabinetPower = this?.optString("cabinetPower").orEmpty(),
        powerType = this?.optString("powerType").orEmpty(),
        roomPurpose = this?.optString("roomPurpose").orEmpty(),
        powerConfig = this?.optString("powerConfig").orEmpty(),
        ratedCurrent = this?.optString("ratedCurrent").orEmpty(),
        temperature = this?.optString("temperature").orEmpty(),
        humidity = this?.optString("humidity").orEmpty(),
        antiStatic = this?.optString("antiStatic").orEmpty(),
        fireSystem = this?.optString("fireSystem").orEmpty(),
        monitoring = this?.optString("monitoring").orEmpty(),
        accessControl = this?.optString("accessControl").orEmpty(),
    )

    private fun Collection<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { value -> array.put(value) }
    }

    private fun JSONArray?.strings(): List<String> = buildList {
        if (this@strings == null) return@buildList
        for (index in 0 until this@strings.length()) {
            this@strings.opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = buildList {
        if (this@objects == null) return@buildList
        for (index in 0 until this@objects.length()) {
            this@objects.optJSONObject(index)?.let(::add)
        }
    }

    private fun JSONObject.longOrDefault(key: String, fallback: Long): Long =
        if (has(key) && !isNull(key)) optLong(key, fallback) else fallback

    private fun JSONObject.optionalLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun JSONObject.optionalInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it >= 0 }

    private fun riskLevel(raw: String): RiskLevel = RiskLevel.entries.firstOrNull {
        it.name == raw || it.label == raw
    } ?: RiskLevel.PENDING

    private fun riskText(candidate: ModelRiskCandidate): String =
        "${candidate.category}：${candidate.description}"
}
