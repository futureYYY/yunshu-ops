package com.rackexcel.mobile.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskAnalysisTest {
    @Test
    fun produces_space_and_asset_register_findings_from_verified_racks() {
        val result = RiskAnalysisEngine.analyze(
            racks = listOf(
                Rack(
                    cabinetId = "K03",
                    imageName = "K03.jpg",
                    devices = listOf(
                        Device("交换机", 43, 1, 0.97, "右侧轨 U43"),
                        Device("服务器", 21, 2, 0.95, "右侧轨 U21-U22"),
                    ),
                    uncertain = emptyList(),
                ),
            ),
            failures = emptyList(),
        )

        assertTrue(result.risks.any { it.category == "空间利用率" && it.dataSource == "照片识别" })
        assertTrue(result.risks.any { it.category == "资产台账" && it.level == RiskLevel.PENDING })
        assertTrue(result.plans.any { it.period == PlanPeriod.SHORT })
        assertTrue(result.plans.any { it.period == PlanPeriod.MEDIUM })
        assertTrue(result.plans.any { it.period == PlanPeriod.LONG })
    }

    @Test
    fun creates_capture_risk_only_when_a_real_image_failure_exists() {
        val result = RiskAnalysisEngine.analyze(
            racks = listOf(rack("K03")),
            failures = listOf("K04.jpg：修复失败，建议补拍"),
        )

        val capture = result.risks.single { it.category == "采集质量" }
        assertEquals(RiskLevel.PENDING, capture.level)
        assertTrue(capture.description.contains("K04.jpg"))
    }

    @Test
    fun identifies_uncertain_positions_as_review_items() {
        val result = RiskAnalysisEngine.analyze(
            racks = listOf(rack("K03", uncertain = listOf("U42 边界被线缆遮挡"))),
            failures = emptyList(),
        )

        assertTrue(result.risks.any { it.category == "识别复核" && it.evidence.contains("U42") })
    }

    private fun rack(id: String, uncertain: List<String> = emptyList()) = Rack(
        cabinetId = id,
        imageName = "$id.jpg",
        devices = listOf(Device("服务器", 21, 2, 0.95, "右侧轨 U21-U22")),
        uncertain = uncertain,
    )
}
