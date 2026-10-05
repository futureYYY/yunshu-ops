package com.rackexcel.mobile.model

enum class RiskLevel(val label: String) {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低"),
    PENDING("待核验"),
}

enum class PlanPeriod(val label: String) {
    SHORT("短期（1个月内）"),
    MEDIUM("中期（3个月内）"),
    LONG("长期（6个月内）"),
}

enum class ReviewStatus(val label: String) {
    PENDING("待复核"),
    ACCEPTED("已采纳"),
    IGNORED("已忽略"),
}

data class RiskItem(
    val category: String,
    val description: String,
    val level: RiskLevel,
    val recommendation: String,
    val evidence: String,
    val confidence: Double,
    val dataSource: String,
    val status: ReviewStatus = ReviewStatus.PENDING,
)

data class ActionPlan(
    val period: PlanPeriod,
    val text: String,
    val riskCategories: List<String>,
    val status: ReviewStatus = ReviewStatus.PENDING,
)

data class AnalysisResult(
    val risks: List<RiskItem>,
    val plans: List<ActionPlan>,
)

/**
 * Produces only evidence-backed statements from the current recognition task.
 * Electrical, thermal and topology conclusions deliberately stay pending until a data source exists.
 */
object RiskAnalysisEngine {
    fun analyze(racks: List<Rack>, failures: List<String>): AnalysisResult {
        if (racks.isEmpty()) return AnalysisResult(
            risks = failures.takeIf { it.isNotEmpty() }?.let(::captureRisks).orEmpty(),
            plans = plansFrom(failures.takeIf { it.isNotEmpty() }?.let(::captureRisks).orEmpty()),
        )

        val risks = buildList {
            add(spaceUtilizationRisk(racks))
            add(assetRegisterRisk(racks))
            uncertainRisk(racks)?.let(::add)
            if (failures.isNotEmpty()) addAll(captureRisks(failures))
            addAll(modelRisks(racks))
        }
        return AnalysisResult(risks = risks, plans = plansFrom(risks, modelHints(racks)))
    }

    private fun spaceUtilizationRisk(racks: List<Rack>): RiskItem {
        val totalU = racks.size * 47
        val usedU = racks.sumOf { rack -> rack.devices.sumOf(Device::heightU) }
        val ratio = if (totalU == 0) 0.0 else usedU.toDouble() / totalU
        val percent = "%.1f%%".format(java.util.Locale.ROOT, ratio * 100)
        val level = when {
            ratio < 0.40 -> RiskLevel.LOW
            ratio > 0.85 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val recommendation = when {
            ratio < 0.40 -> "结合业务增长计划统筹空闲 U 位，形成容量使用台账。"
            ratio > 0.85 -> "优先核验剩余 U 位与扩容需求，预留设备上架空间。"
            else -> "持续跟踪柜内 U 位使用率，按实际扩容计划安排上架。"
        }
        return RiskItem(
            category = "空间利用率",
            description = "已识别 ${racks.size} 个机柜，共占用 $usedU/$totalU U，当前使用率 $percent。",
            level = level,
            recommendation = recommendation,
            evidence = racks.joinToString("、") { rack -> "${rack.cabinetId} ${rack.devices.sumOf(Device::heightU)}U" },
            confidence = 0.98,
            dataSource = "照片识别",
        )
    }

    private fun assetRegisterRisk(racks: List<Rack>): RiskItem = RiskItem(
        category = "资产台账",
        description = "本次照片识别已确认机柜、U 位和设备类别；资产编号、序列号、管理 IP、功率等字段缺少可确认来源。",
        level = RiskLevel.PENDING,
        recommendation = "补充资产标签、序列号、管理 IP 和责任信息，并与网管或资产系统进行关联。",
        evidence = "${racks.size} 个机柜的照片识别结果",
        confidence = 0.92,
        dataSource = "照片识别",
    )

    private fun uncertainRisk(racks: List<Rack>): RiskItem? {
        val notes = racks.flatMap { rack -> rack.uncertain.map { note -> "${rack.cabinetId}：$note" } }
        if (notes.isEmpty()) return null
        return RiskItem(
            category = "识别复核",
            description = "存在 ${notes.size} 项需要人工确认的图像识别结果。",
            level = RiskLevel.PENDING,
            recommendation = "在机柜云图复核页核对标注位置；涉及遮挡或反光时补拍清晰正面图。",
            evidence = notes.joinToString("；"),
            confidence = 0.70,
            dataSource = "照片识别",
        )
    }

    private fun captureRisks(failures: List<String>): List<RiskItem> = listOf(
        RiskItem(
            category = "采集质量",
            description = "以下图片尚未形成可确认的识别结果：${failures.joinToString("；")}。",
            level = RiskLevel.PENDING,
            recommendation = "补拍完整、清晰、正面的机柜图片后，使用任务中心的单图重试功能继续处理。",
            evidence = failures.joinToString("；"),
            confidence = 1.0,
            dataSource = "任务处理记录",
        ),
    )

    private fun modelRisks(racks: List<Rack>): List<RiskItem> = racks
        .flatMap(Rack::riskCandidates)
        .filter { candidate -> candidate.evidence.isNotBlank() && candidate.description.isNotBlank() }
        .map { candidate ->
            RiskItem(
                category = candidate.category,
                description = candidate.description,
                level = candidate.level,
                recommendation = candidate.recommendation.ifBlank { "结合现场复核结果完善处置计划。" },
                evidence = candidate.evidence,
                confidence = candidate.confidence,
                dataSource = candidate.dataSource,
            )
        }

    private fun modelHints(racks: List<Rack>): Map<PlanPeriod, String> = buildMap {
        racks.map(Rack::actionHints).map(ActionHints::short).firstOrNull { it.isNotBlank() }?.let { put(PlanPeriod.SHORT, it) }
        racks.map(Rack::actionHints).map(ActionHints::medium).firstOrNull { it.isNotBlank() }?.let { put(PlanPeriod.MEDIUM, it) }
        racks.map(Rack::actionHints).map(ActionHints::long).firstOrNull { it.isNotBlank() }?.let { put(PlanPeriod.LONG, it) }
    }

    private fun plansFrom(risks: List<RiskItem>, hints: Map<PlanPeriod, String> = emptyMap()): List<ActionPlan> {
        val categories = risks.map(RiskItem::category)
        return listOf(
            ActionPlan(
                period = PlanPeriod.SHORT,
                text = hints[PlanPeriod.SHORT] ?: "完成照片待确认项复核，补拍失败机柜图片，并补充资产标签、序列号、管理 IP 等基础台账字段。",
                riskCategories = categories.filter { it in setOf("资产台账", "识别复核", "采集质量") },
            ),
            ActionPlan(
                period = PlanPeriod.MEDIUM,
                text = hints[PlanPeriod.MEDIUM] ?: "结合已识别的空闲 U 位完善容量规划，建立设备配置备份与网管关联核验流程。",
                riskCategories = categories.filter { it in setOf("空间利用率", "资产台账") },
            ),
            ActionPlan(
                period = PlanPeriod.LONG,
                text = hints[PlanPeriod.LONG] ?: "接入动环与网管数据，形成资产全生命周期、容量、电力和告警联动分析能力。",
                riskCategories = categories,
            ),
        )
    }
}
