package com.rackexcel.mobile.alarm

/** The configured source is kept outside the UI so a real network-manager adapter can replace Mock later. */
enum class AlarmSourceMode(val label: String) {
    MOCK("模拟告警源"),
    NETWORK_MANAGER("网管接口"),
}

enum class AlarmSeverity(val label: String) {
    CRITICAL("严重"),
    WARNING("一般"),
    INFO("提示");

    companion object {
        fun from(raw: String?): AlarmSeverity = entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true) || it.label == raw?.trim()
        } ?: INFO
    }
}

enum class AlarmStatus(val label: String) {
    UNHANDLED("未处理"),
    HANDLED("已处理");

    companion object {
        fun from(raw: String?): AlarmStatus = entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true) || it.label == raw?.trim()
        } ?: UNHANDLED
    }
}

/** Fields that can act as the shared key between a network manager and a rack ledger. */
enum class AlarmIdentityField(val label: String) {
    DEVICE_NAME("设备名称"),
    MANAGEMENT_IP("管理 IP"),
    ASSET_ID("资产编号");
}

data class AlarmDeviceIdentity(
    val deviceName: String = "",
    val managementIp: String = "",
    val assetId: String = "",
) {
    fun value(field: AlarmIdentityField): String = when (field) {
        AlarmIdentityField.DEVICE_NAME -> deviceName
        AlarmIdentityField.MANAGEMENT_IP -> managementIp
        AlarmIdentityField.ASSET_ID -> assetId
    }

    fun hasValue(field: AlarmIdentityField): Boolean = value(field).trim().isNotEmpty()
    val hasAnyValue: Boolean get() = AlarmIdentityField.entries.any(::hasValue)
}

data class AlarmRecord(
    val alarmId: String,
    val identity: AlarmDeviceIdentity,
    val severity: AlarmSeverity,
    val occurredAtMillis: Long,
    val description: String,
    val status: AlarmStatus = AlarmStatus.UNHANDLED,
    val processing: List<AlarmProcessingRecord> = emptyList(),
    /** Identifies the recognition task that produced a dynamic Mock alarm. */
    val taskId: String? = null,
) {
    init {
        require(alarmId.isNotBlank()) { "告警 ID 不能为空" }
        require(occurredAtMillis >= 0L) { "告警发生时间无效" }
        require(description.isNotBlank()) { "告警描述不能为空" }
    }
}

/** A concrete device location returned after matching a network alarm to the rack drawing. */
data class AlarmRackCoordinate(
    val cabinetId: String,
    val deviceIndex: Int,
    val type: String,
    val bottomU: Int,
    val heightU: Int,
    val displayName: String = "",
    val managementIp: String = "",
    val assetId: String = "",
) {
    val upperU: Int get() = bottomU + heightU - 1
    val occupiedU: IntRange get() = bottomU..upperU
}

enum class AlarmMatchMode {
    /** Any one configured shared key can create a match. */
    ANY,
    /** Every populated configured shared key must agree. */
    ALL,
}

data class AlarmMatchConfig(
    val fields: Set<AlarmIdentityField> = AlarmIdentityField.entries.toSet(),
    val mode: AlarmMatchMode = AlarmMatchMode.ANY,
)

sealed class AlarmMatchResult(open val alarm: AlarmRecord) {
    data class Matched(
        override val alarm: AlarmRecord,
        val coordinate: AlarmRackCoordinate,
        val matchedFields: Set<AlarmIdentityField>,
        val score: Int,
    ) : AlarmMatchResult(alarm)

    /** An unmatched alarm is a visible ledger-gap signal, never an exception or a silent drop. */
    data class Unmatched(
        override val alarm: AlarmRecord,
        val reason: String,
        val suggestion: String,
    ) : AlarmMatchResult(alarm)
}

enum class AlarmReviewDecision(val label: String) {
    DEVICE_NOT_PRESENT("设备不存在"),
    DEVICE_FAULT("设备故障"),
    NEEDS_FURTHER_PROCESSING("需进一步处理"),
}

enum class AlarmFollowUpAction(val label: String) {
    NONE("无后续动作"),
    CLOSE_INVALID_ALARM("关闭无效告警"),
    CREATE_FAULT_DISPOSAL_RECORD("生成故障处置记录"),
    CREATE_FOLLOW_UP_TODO("生成待办"),
}

/** Immutable review input. Evidence paths refer only to app-private review images, never API credentials. */
data class AlarmReviewInput(
    val decision: AlarmReviewDecision,
    val reviewer: String = "现场人员",
    val reviewedAtMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val evidencePhotoPaths: List<String> = emptyList(),
)

data class AlarmProcessingRecord(
    val reviewer: String,
    val reviewedAtMillis: Long,
    val decision: AlarmReviewDecision,
    val note: String = "",
    val evidencePhotoPaths: List<String> = emptyList(),
    val followUpAction: AlarmFollowUpAction = AlarmFollowUpAction.NONE,
    val todoText: String? = null,
)

/** A write-back instruction is intentionally separate from the UI and the Excel writer. */
sealed interface AlarmAssetWriteback {
    data object NoChange : AlarmAssetWriteback

    data class RemoveDevice(
        val coordinate: AlarmRackCoordinate,
        val reason: String,
    ) : AlarmAssetWriteback

    data class MarkDeviceFault(
        val coordinate: AlarmRackCoordinate,
        val status: String,
        val note: String,
    ) : AlarmAssetWriteback
}

data class AlarmReviewTransition(
    val updatedAlarm: AlarmRecord,
    val followUpAction: AlarmFollowUpAction,
    val writeback: AlarmAssetWriteback,
)

data class AlarmWritebackResult(
    val racks: List<com.rackexcel.mobile.model.Rack>,
    val applied: Boolean,
    val message: String,
)

/**
 * Extension point for the existing ML Kit scanner: a scanned asset tag can be
 * resolved into the same [AlarmDeviceIdentity] used by the matching policy.
 */
fun interface AssetTagIdentityResolver {
    fun resolve(scannedValue: String): AlarmDeviceIdentity?
}

class AlarmSourceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
class AlarmDataFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
