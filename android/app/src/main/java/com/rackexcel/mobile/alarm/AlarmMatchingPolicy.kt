package com.rackexcel.mobile.alarm

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import java.util.Locale

/**
 * Matches on a configurable combination of device name, management IP and asset ID.
 * It deliberately never falls back to device type alone: "服务器" and "交换机" are
 * not unique keys and would make an alert look more precise than the evidence allows.
 */
object AlarmMatchPolicy {
    fun match(
        alarm: AlarmRecord,
        racks: List<Rack>,
        config: AlarmMatchConfig = AlarmMatchConfig(),
    ): AlarmMatchResult {
        val fields = config.fields.intersect(AlarmIdentityField.entries.toSet())
        if (fields.isEmpty()) return unmatched(
            alarm,
            "未匹配：未配置可用的设备关联主键。",
            "请在设置中选择设备名称、管理 IP 或资产编号作为关联主键。",
        )
        val alarmFields = fields.filter { field -> alarm.identity.hasValue(field) }.toSet()
        if (alarmFields.isEmpty()) return unmatched(
            alarm,
            "未匹配：告警未携带可用于关联的设备名称、管理 IP 或资产编号。",
            "建议补采设备资产标签，或从网管补充管理 IP、设备名称后重新关联。",
        )

        val candidates = buildList {
            racks.forEach { rack ->
                rack.devices.forEachIndexed { deviceIndex, device ->
                    val identity = AlarmDeviceIdentityResolver.forDevice(rack, device)
                    val matchedFields = alarmFields.filter { field ->
                        valuesEqual(field, alarm.identity.value(field), identity.value(field))
                    }.toSet()
                    val shouldMatch = when (config.mode) {
                        AlarmMatchMode.ANY -> matchedFields.isNotEmpty()
                        AlarmMatchMode.ALL -> matchedFields == alarmFields
                    }
                    if (shouldMatch) add(
                        Candidate(
                            coordinate = AlarmRackCoordinate(
                                cabinetId = rack.cabinetId,
                                deviceIndex = deviceIndex,
                                type = device.type,
                                bottomU = device.bottomU,
                                heightU = device.heightU,
                                displayName = identity.deviceName,
                                managementIp = device.managementIp,
                                assetId = device.assetId,
                            ),
                            matchedFields = matchedFields,
                            score = score(matchedFields),
                        ),
                    )
                }
            }
        }
        if (candidates.isEmpty()) return unmatched(
            alarm,
            "未匹配：该设备不在当前上架图中。",
            "建议补采对应机柜照片或补充设备资产标签、管理 IP 后重新关联。",
        )

        val bestScore = candidates.maxOf(Candidate::score)
        val best = candidates.filter { it.score == bestScore }
        if (best.size > 1) return unmatched(
            alarm,
            "未匹配：设备主键命中多个机柜设备，无法唯一定位。",
            "建议补充资产编号或管理 IP 等唯一主键后重新关联。",
        )
        return best.single().let { candidate ->
            AlarmMatchResult.Matched(
                alarm = alarm,
                coordinate = candidate.coordinate,
                matchedFields = candidate.matchedFields,
                score = candidate.score,
            )
        }
    }

    fun matchAll(
        alarms: Iterable<AlarmRecord>,
        racks: List<Rack>,
        config: AlarmMatchConfig = AlarmMatchConfig(),
    ): List<AlarmMatchResult> = alarms.map { alarm -> match(alarm, racks, config) }

    private fun unmatched(alarm: AlarmRecord, reason: String, suggestion: String): AlarmMatchResult.Unmatched =
        AlarmMatchResult.Unmatched(alarm, reason, suggestion)

    private fun valuesEqual(field: AlarmIdentityField, left: String, right: String): Boolean =
        left.isNotBlank() && right.isNotBlank() && normalize(field, left) == normalize(field, right)

    private fun normalize(field: AlarmIdentityField, value: String): String = when (field) {
        AlarmIdentityField.DEVICE_NAME -> value.lowercase(Locale.ROOT)
            .replace(Regex("[\\s_\\-—（）()【】\\[\\].]"), "")
        AlarmIdentityField.MANAGEMENT_IP -> value.trim().lowercase(Locale.ROOT).replace(" ", "")
        AlarmIdentityField.ASSET_ID -> value.uppercase(Locale.ROOT).replace(Regex("[\\s_\\-—]"), "")
    }

    private fun score(fields: Set<AlarmIdentityField>): Int = fields.fold(0) { total, field ->
        total + when (field) {
            AlarmIdentityField.ASSET_ID -> 10_000
            AlarmIdentityField.MANAGEMENT_IP -> 1_000
            AlarmIdentityField.DEVICE_NAME -> 100
        }
    }

    private data class Candidate(
        val coordinate: AlarmRackCoordinate,
        val matchedFields: Set<AlarmIdentityField>,
        val score: Int,
    )
}

/**
 * Produces the same identity view for matching and for mock-demo generation.
 * If image recognition did not reveal any primary key, the fallback is a
 * deterministic cabinet/U/type label rather than a guessed real-world asset ID.
 */
object AlarmDeviceIdentityResolver {
    fun forDevice(rack: Rack, device: Device): AlarmDeviceIdentity {
        val readableName = device.displayName.trim().ifBlank {
            "${rack.cabinetId.trim().ifBlank { "未命名机柜" }} U${device.bottomU} ${device.type.trim().ifBlank { "设备" }}"
        }
        return AlarmDeviceIdentity(
            deviceName = readableName,
            managementIp = device.managementIp.trim(),
            assetId = device.assetId.trim(),
        )
    }
}
