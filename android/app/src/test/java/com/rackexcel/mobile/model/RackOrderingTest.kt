package com.rackexcel.mobile.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RackOrderingTest {
    @Test
    fun orders_normalized_cabinets_by_numeric_suffix() {
        val result = RackOrdering.sort(
            listOf(
                rack("K17"),
                rack("K4"),
                rack("K03"),
                rack("K10"),
            ),
        )

        assertEquals(listOf("K03", "K04", "K10", "K17"), result.racks.map { it.cabinetId })
    }

    @Test
    fun retains_the_highest_confidence_duplicate_and_reports_it() {
        val result = RackOrdering.sort(
            listOf(
                rack("K03", 0.66),
                rack("K3", 0.94),
            ),
        )

        assertEquals(1, result.racks.size)
        assertEquals("K03", result.racks.single().cabinetId)
        assertTrue(result.warnings.single().contains("疑似重复柜号：K03"))
    }

    @Test
    fun duplicate_tie_prefers_the_more_complete_recognition() {
        val result = RackOrdering.sort(
            listOf(
                rack("K03", confidence = 0.88, deviceCount = 1),
                rack("K3", confidence = 0.88, deviceCount = 2),
            ),
        )

        assertEquals(2, result.racks.single().devices.size)
    }

    @Test
    fun puts_unrecognized_ids_after_standard_cabinets() {
        val result = RackOrdering.sort(listOf(rack("Z9"), rack("K2"), rack("K11")))

        assertEquals(listOf("K02", "K11", "Z9"), result.racks.map { it.cabinetId })
        assertTrue(result.warnings.any { it.contains("柜号待确认：Z9") })
    }

    private fun rack(id: String, confidence: Double = 0.8, deviceCount: Int = 1) = Rack(
        cabinetId = id,
        imageName = "$id.jpg",
        devices = (0 until deviceCount).map { index ->
            Device("交换机", 43 - index, 1, confidence, "右侧轨 U${43 - index}")
        },
        uncertain = emptyList(),
    )
}
