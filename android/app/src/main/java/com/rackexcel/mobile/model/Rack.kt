package com.rackexcel.mobile.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Device(
    val type: String,
    val bottomU: Int,
    val heightU: Int,
    val confidence: Double = 0.0,
    val evidence: String = "",
    /** Optional fields are populated only when the image makes them legible. */
    val displayName: String = "",
    val model: String = "",
    val manufacturer: String = "",
    val assetId: String = "",
    val serialNumber: String = "",
    val managementIp: String = "",
    val businessSystem: String = "",
    val purpose: String = "",
    val powerW: Int? = null,
    val owner: String = "",
    val phone: String = "",
    val installDate: String = "",
    val status: String = "",
    val notes: String = "",
)

data class RackMetadata(
    val physicalLocation: String = "",
    /** Cabinet-level rated/observed power, only when visible in the source image. */
    val cabinetPower: String = "",
    /** Cabinet supply type, only when visible in the source image. */
    val powerType: String = "",
    val roomPurpose: String = "",
    val powerConfig: String = "",
    val ratedCurrent: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val antiStatic: String = "",
    val fireSystem: String = "",
    val monitoring: String = "",
    val accessControl: String = "",
)

data class ModelRiskCandidate(
    val category: String,
    val description: String,
    val level: RiskLevel,
    val recommendation: String,
    val evidence: String,
    val confidence: Double,
    val dataSource: String,
)

data class ActionHints(
    val short: String = "",
    val medium: String = "",
    val long: String = "",
)

data class Rack(
    val cabinetId: String,
    val imageName: String,
    val devices: List<Device>,
    val uncertain: List<String>,
    val riskCandidates: List<ModelRiskCandidate> = emptyList(),
    val actionHints: ActionHints = ActionHints(),
    val metadata: RackMetadata = RackMetadata(),
)

class RackValidationException(message: String) : IllegalArgumentException(message)

object RackJsonParser {
    private val allowedTypes = DeviceTypePolicy.allowedTypes

    fun parse(raw: String, imageName: String): Rack {
        val jsonText = raw.trim().let { text ->
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) text.substring(start, end + 1) else text
        }
        val root = try {
            JSONObject(jsonText)
        } catch (error: Exception) {
            throw RackValidationException("模型返回不是有效 JSON: ${error.message ?: "解析失败"}")
        }

        val cabinetId = root.optString("cabinet_id").trim().uppercase(Locale.ROOT)
        if (!Regex("K\\d+").matches(cabinetId)) {
            throw RackValidationException("柜号格式无效: $cabinetId")
        }

        if (!root.has("devices") || root.opt("devices") !is JSONArray) {
            throw RackValidationException("devices 必须是数组")
        }
        val devices = mutableListOf<Device>()
        val occupied = mutableMapOf<Int, Device>()
        val array = root.optJSONArray("devices") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw RackValidationException("devices[$index] 不是对象")
            val type = item.optString("type").trim()
            if (type !in allowedTypes) {
                throw RackValidationException("devices[$index] 类型不在登记口径内: $type，可选：${DeviceTypePolicy.allowedTypes.joinToString("、")}")
            }
            val bottom = item.optInt("bottom_u", Int.MIN_VALUE)
            val height = item.optInt("height_u", Int.MIN_VALUE)
            if (bottom !in 1..47) {
                throw RackValidationException("devices[$index] bottom_u 必须在 1-47: $bottom")
            }
            if (height !in 1..2) {
                throw RackValidationException("devices[$index] height_u 只允许 1 或 2: $height")
            }
            val top = bottom + height - 1
            if (top > 47) {
                throw RackValidationException("devices[$index] 超出 47U 范围")
            }
            val confidence = item.optDouble("confidence", 0.0)
            if (confidence !in 0.0..1.0) {
                throw RackValidationException("devices[$index] confidence 必须在 0-1")
            }
            val device = Device(
                type = type,
                bottomU = bottom,
                heightU = height,
                confidence = confidence,
                evidence = item.optString("evidence", ""),
                displayName = item.firstText("display_name", "name", "设备名称"),
                model = item.firstText("model", "型号", "device_model"),
                manufacturer = item.firstText("manufacturer", "vendor", "厂商"),
                assetId = item.firstText("asset_id", "asset_no", "资产编号"),
                serialNumber = item.firstText("serial_number", "serial", "序列号"),
                managementIp = item.firstText("management_ip", "management_ip_address", "管理IP"),
                businessSystem = item.firstText("business_system", "业务系统"),
                purpose = item.firstText("purpose", "device_purpose", "设备用途"),
                powerW = item.optionalInt("power_w", "power", "功率_w"),
                owner = item.firstText("owner", "responsible_person", "责任人"),
                phone = item.firstText("phone", "contact_phone", "联系电话"),
                installDate = item.firstText("install_date", "上架日期"),
                status = item.firstText("status", "运行状态", "运维状态"),
                notes = item.firstText("notes", "remark", "备注"),
            )
            for (u in bottom..top) {
                val previous = occupied.put(u, device)
                if (previous != null) {
                    throw RackValidationException("设备 U 位重叠: U$u")
                }
            }
            devices += device
        }

        val uncertain = mutableListOf<String>()
        root.optJSONArray("uncertain")?.let { values ->
            for (index in 0 until values.length()) {
                values.optString(index).trim().takeIf { it.isNotEmpty() }?.let(uncertain::add)
            }
        }
        val riskCandidates = mutableListOf<ModelRiskCandidate>()
        root.optJSONArray("risk_candidates")?.let { values ->
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val category = item.optString("category").trim()
                val description = item.optString("description").trim()
                val evidence = item.optString("evidence").trim()
                if (category.isBlank() || description.isBlank() || evidence.isBlank()) continue
                val rawConfidence = item.optDouble("confidence", 0.0)
                val confidence = rawConfidence.takeIf { it in 0.0..1.0 } ?: 0.0
                riskCandidates += ModelRiskCandidate(
                    category = category,
                    description = description,
                    level = RiskLevel.entries.firstOrNull { it.label == item.optString("level").trim() } ?: RiskLevel.PENDING,
                    recommendation = item.optString("recommendation").trim(),
                    evidence = evidence,
                    confidence = confidence,
                    dataSource = item.optString("data_source").trim().ifBlank { "照片识别" },
                )
            }
        }
        val hints = root.optJSONObject("action_hints")?.let { value ->
            ActionHints(
                short = value.optString("short").trim(),
                medium = value.optString("medium").trim(),
                long = value.optString("long").trim(),
            )
        } ?: ActionHints()
        val metadataObject = root.optJSONObject("rack_fields")
            ?: root.optJSONObject("metadata")
            ?: JSONObject()
        val metadata = RackMetadata(
            physicalLocation = metadataObject.firstText("physical_location", "location", "物理位置"),
            cabinetPower = metadataObject.firstText("cabinet_power", "cabinet_power_w", "机柜功率"),
            powerType = metadataObject.firstText("power_type", "supply_type", "供电类型"),
            roomPurpose = metadataObject.firstText("room_purpose", "purpose", "机房用途"),
            powerConfig = metadataObject.firstText("power_config", "动力配置"),
            ratedCurrent = metadataObject.firstText("rated_current", "额定电流"),
            temperature = metadataObject.firstText("temperature", "environment_temperature", "环境温度"),
            humidity = metadataObject.firstText("humidity", "relative_humidity", "相对湿度"),
            antiStatic = metadataObject.firstText("anti_static", "防静电"),
            fireSystem = metadataObject.firstText("fire_system", "消防系统"),
            monitoring = metadataObject.firstText("monitoring", "monitoring_system", "监控系统"),
            accessControl = metadataObject.firstText("access_control", "access_management", "出入管理"),
        )
        return Rack(
            cabinetId = cabinetId,
            imageName = imageName,
            devices = devices.sortedWith(compareByDescending<Device> { it.bottomU }.thenBy { it.type }),
            uncertain = uncertain,
            riskCandidates = riskCandidates,
            actionHints = hints,
            metadata = metadata,
        )
    }

    private fun JSONObject.firstText(vararg keys: String): String = keys
        .asSequence()
        .map { key -> optString(key, "").trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    private fun JSONObject.optionalInt(vararg keys: String): Int? = keys
        .asSequence()
        .mapNotNull { key ->
            if (!has(key) || isNull(key)) return@mapNotNull null
            when (val value = opt(key)) {
                is Number -> value.toInt()
                is String -> value.trim().removeSuffix("W").toIntOrNull()
                else -> null
            }
        }
        .firstOrNull { it >= 0 }
}
