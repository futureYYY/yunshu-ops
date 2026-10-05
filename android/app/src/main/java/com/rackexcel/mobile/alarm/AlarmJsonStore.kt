package com.rackexcel.mobile.alarm

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/** JSON codec shared by the editable local mock file and future network-manager adapters. */
object AlarmJsonCodec {
    fun encode(alarms: List<AlarmRecord>): String = JSONObject()
        .put("version", 1)
        .put("alarms", JSONArray().apply { alarms.forEach { put(it.toJson()) } })
        .toString(2)

    fun decode(payload: String?): List<AlarmRecord> {
        val text = payload?.trim().orEmpty()
        if (text.isBlank()) return emptyList()
        return try {
            val values = if (text.startsWith("[")) JSONArray(text) else JSONObject(text).optJSONArray("alarms") ?: JSONArray()
            buildList {
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index)
                        ?: throw AlarmDataFormatException("模拟告警第 ${index + 1} 项不是对象")
                    add(item.toAlarm(index))
                }
            }
        } catch (error: AlarmDataFormatException) {
            throw error
        } catch (error: Exception) {
            throw AlarmDataFormatException("模拟告警 JSON 格式无效：${error.message ?: "解析失败"}", error)
        }
    }

    /** Public record-level helpers for task-history snapshots. */
    fun toJsonObject(alarm: AlarmRecord): JSONObject = alarm.toJson()

    fun decodeArray(values: JSONArray?): List<AlarmRecord> =
        runCatching { values?.let { decode(it.toString()) }.orEmpty() }.getOrDefault(emptyList())

    private fun AlarmRecord.toJson(): JSONObject = JSONObject()
        .put("alarmId", alarmId)
        .put("taskId", taskId)
        .put("device", JSONObject()
            .put("deviceName", identity.deviceName)
            .put("managementIp", identity.managementIp)
            .put("assetId", identity.assetId))
        .put("severity", severity.name)
        .put("occurredAtMillis", occurredAtMillis)
        .put("description", description)
        .put("status", status.name)
        .put("processing", JSONArray().apply { processing.forEach { put(it.toJson()) } })

    private fun AlarmProcessingRecord.toJson(): JSONObject = JSONObject()
        .put("reviewer", reviewer)
        .put("reviewedAtMillis", reviewedAtMillis)
        .put("decision", decision.name)
        .put("note", note)
        .put("evidencePhotoPaths", JSONArray().apply { evidencePhotoPaths.forEach(::put) })
        .put("followUpAction", followUpAction.name)
        .put("todoText", todoText)

    private fun JSONObject.toAlarm(index: Int): AlarmRecord {
        val alarmId = firstText("alarmId", "alarm_id", "id")
            .ifBlank { throw AlarmDataFormatException("模拟告警第 ${index + 1} 项缺少告警 ID") }
        val device = optJSONObject("device") ?: this
        val identity = AlarmDeviceIdentity(
            deviceName = device.firstText("deviceName", "device_name", "name", "设备名称"),
            managementIp = device.firstText("managementIp", "management_ip", "ip", "管理IP"),
            assetId = device.firstText("assetId", "asset_id", "assetNo", "资产编号"),
        )
        if (!identity.hasAnyValue) {
            throw AlarmDataFormatException("模拟告警 $alarmId 缺少设备名称、管理 IP 或资产编号")
        }
        val description = firstText("description", "message", "告警描述")
            .ifBlank { throw AlarmDataFormatException("模拟告警 $alarmId 缺少告警描述") }
        return AlarmRecord(
            alarmId = alarmId,
            identity = identity,
            severity = AlarmSeverity.from(firstText("severity", "level", "告警等级")),
            occurredAtMillis = occurredAtMillis("occurredAtMillis", "occurred_at_millis", "occurredAt", "occurred_at", "发生时间"),
            description = description,
            status = AlarmStatus.from(firstText("status", "状态")),
            processing = optJSONArray("processing")?.processingRecords().orEmpty(),
            taskId = optString("taskId").trim().takeIf { it.isNotBlank() },
        )
    }

    private fun JSONArray.processingRecords(): List<AlarmProcessingRecord> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val reviewer = item.firstText("reviewer", "operator", "处理人").ifBlank { "现场人员" }
            val decision = AlarmReviewDecision.entries.firstOrNull {
                it.name.equals(item.firstText("decision", "判定"), ignoreCase = true) || it.label == item.firstText("decision", "判定")
            } ?: AlarmReviewDecision.NEEDS_FURTHER_PROCESSING
            val action = AlarmFollowUpAction.entries.firstOrNull {
                it.name.equals(item.firstText("followUpAction", "follow_up_action"), ignoreCase = true)
            } ?: AlarmFollowUpAction.NONE
            add(
                AlarmProcessingRecord(
                    reviewer = reviewer,
                    reviewedAtMillis = item.occurredAtMillis("reviewedAtMillis", "reviewed_at_millis", "reviewedAt", "reviewed_at"),
                    decision = decision,
                    note = item.firstText("note", "remark", "说明"),
                    evidencePhotoPaths = item.optJSONArray("evidencePhotoPaths").strings(),
                    followUpAction = action,
                    todoText = item.firstText("todoText", "todo_text").ifBlank { null },
                ),
            )
        }
    }

    private fun JSONObject.occurredAtMillis(vararg keys: String): Long {
        keys.forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            val value = opt(key)
            when (value) {
                is Number -> return value.toLong().takeIf { it >= 0L }
                    ?: throw AlarmDataFormatException("告警时间无效")
                is String -> {
                    val parsed = value.trim().toLongOrNull()?.coerceAtLeast(0L)
                        ?: parseInstant(value.trim())
                    if (parsed != null) return parsed
                }
            }
        }
        throw AlarmDataFormatException("模拟告警缺少发生时间")
    }

    private fun parseInstant(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()

    private fun JSONObject.firstText(vararg keys: String): String = keys
        .asSequence()
        .map { key -> optString(key, "").trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    private fun JSONArray?.strings(): List<String> = buildList {
        if (this@strings == null) return@buildList
        for (index in 0 until this@strings.length()) {
            this@strings.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

/** Storage abstraction keeps mock persistence testable without Android runtime APIs. */
interface AlarmRecordStore {
    fun loadAlarms(): List<AlarmRecord>
    fun saveAlarms(alarms: List<AlarmRecord>)
}

class InMemoryAlarmRecordStore(initial: List<AlarmRecord> = emptyList()) : AlarmRecordStore {
    private var records: List<AlarmRecord> = initial.toList()
    var persistedPayload: String = AlarmJsonCodec.encode(records)
        private set

    override fun loadAlarms(): List<AlarmRecord> = records.toList()

    override fun saveAlarms(alarms: List<AlarmRecord>) {
        records = alarms.toList()
        persistedPayload = AlarmJsonCodec.encode(records)
    }
}

/**
 * Editable local JSON store. The seed is copied only on first use; afterwards
 * injections and demo generation update this file, so the active mock data is
 * not embedded in the UI and can be replaced without changing any screen code.
 */
class JsonFileAlarmRecordStore(
    val file: File,
    private val seedPayload: () -> String = { AlarmJsonCodec.encode(emptyList()) },
) : AlarmRecordStore {
    private val lock = Any()

    override fun loadAlarms(): List<AlarmRecord> = synchronized(lock) {
        ensureSeed()
        AlarmJsonCodec.decode(file.readText(StandardCharsets.UTF_8))
    }

    override fun saveAlarms(alarms: List<AlarmRecord>) = synchronized(lock) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeText(AlarmJsonCodec.encode(alarms), StandardCharsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun ensureSeed() {
        if (file.exists()) return
        file.parentFile?.mkdirs()
        file.writeText(seedPayload(), StandardCharsets.UTF_8)
    }
}

/** Android binding for the seeded asset plus the editable app-private active JSON file. */
class AlarmMockJsonStore(context: Context) : AlarmRecordStore {
    private val appContext = context.applicationContext
    val file = File(appContext.filesDir, "alarms/mock_alarms.json")
    private val delegate = JsonFileAlarmRecordStore(file) {
        appContext.assets.open(DEFAULT_ASSET_NAME).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    override fun loadAlarms(): List<AlarmRecord> = delegate.loadAlarms()
    override fun saveAlarms(alarms: List<AlarmRecord>) = delegate.saveAlarms(alarms)

    companion object {
        const val DEFAULT_ASSET_NAME = "mock_alarms.json"
    }
}
