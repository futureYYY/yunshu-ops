package com.rackexcel.mobile.model

import java.util.Locale

data class RackOrderResult(
    val racks: List<Rack>,
    val warnings: List<String>,
)

/** Keeps visual task ordering separate from deterministic Excel ordering. */
object RackOrdering {
    private val standardId = Regex("^K(\\d+)$", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String? {
        val digits = standardId.matchEntire(raw.trim())?.groupValues?.get(1) ?: return null
        val number = digits.toIntOrNull() ?: return null
        return String.format(Locale.ROOT, "K%02d", number)
    }

    fun sort(input: List<Rack>): RackOrderResult {
        val groups = input.groupBy { rack -> normalize(rack.cabinetId) ?: rack.cabinetId.trim().uppercase(Locale.ROOT) }
        val warnings = mutableListOf<String>()
        val selected = groups.map { (id, candidates) ->
            if (normalize(id) == null) warnings += "柜号待确认：$id"
            if (candidates.size > 1) warnings += "疑似重复柜号：$id"
            candidates.sortedWith(
                compareByDescending<Rack> { averageConfidence(it) }
                    .thenByDescending { it.devices.size }
                    .thenBy { it.imageName },
            ).first().copy(cabinetId = id)
        }
        return RackOrderResult(
            racks = selected.sortedWith(
                compareBy<Rack> { sortGroup(it.cabinetId) }
                    .thenBy { sortNumber(it.cabinetId) }
                    .thenBy { it.cabinetId },
            ),
            warnings = warnings,
        )
    }

    private fun averageConfidence(rack: Rack): Double =
        rack.devices.map(Device::confidence).average().takeUnless { it.isNaN() } ?: 0.0

    private fun sortGroup(id: String): Int = if (normalize(id) != null) 0 else 1

    private fun sortNumber(id: String): Int = standardId.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
}
