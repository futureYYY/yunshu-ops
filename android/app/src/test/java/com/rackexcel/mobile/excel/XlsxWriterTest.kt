package com.rackexcel.mobile.excel

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.AnalysisResult
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RiskAnalysisEngine
import com.rackexcel.mobile.model.RiskItem
import com.rackexcel.mobile.model.RiskLevel
import com.rackexcel.mobile.model.RackMetadata
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XlsxWriterTest {
    @Test
    fun writes_three_target_sheets_with_sorted_cabinets_and_numeric_u_cells() {
        val entries = unzip(XlsxWriter.bytes(exportTask()))
        val workbook = entries.getValue("xl/workbook.xml")
        val rackSheet = entries.getValue("xl/worksheets/sheet1.xml")

        assertTrue(workbook.contains("机房上架图"))
        assertTrue(workbook.contains("设备明细登记表"))
        assertTrue(workbook.contains("统计分析"))
        assertFalse(workbook.contains("识别说明"))
        assertTrue(rackSheet.indexOf("K03柜") < rackSheet.indexOf("K04柜"))
        assertTrue(rackSheet.contains("<c r=\"C9\""))
        assertTrue(rackSheet.contains("<v>47</v>"))
        assertFalse(rackSheet.contains("r=\"C9\" s=\"2\" t=\"inlineStr\""))
        assertTrue(rackSheet.contains("物理位置"))
        assertTrue(rackSheet.contains("设备类型图例"))
        assertTrue(rackSheet.contains("showGridLines=\"0\"") || rackSheet.contains("sheetViews"))
    }

    @Test
    fun aligns_template_fields_without_fabricating_unavailable_values() {
        val entries = unzip(XlsxWriter.bytes(exportTask()))
        val rackSheet = entries.getValue("xl/worksheets/sheet1.xml")
        val assetSheet = entries.getValue("xl/worksheets/sheet2.xml")
        val analysisSheet = entries.getValue("xl/worksheets/sheet3.xml")

        listOf("物理位置", "机柜功率", "机柜规格", "供电类型", "核心交换机", "PDU", "理线架/空白")
            .forEach { assertTrue(rackSheet.contains(it), "Sheet1 缺少字段：$it") }
        listOf("机房用途", "动力配置", "机柜总数", "额定电流", "环境温度", "相对湿度", "防静电", "消防系统", "监控系统", "出入管理")
            .forEach { assertFalse(rackSheet.contains(it), "Sheet1 不应继续展示旧字段：$it") }
        assertTrue(rackSheet.contains("47U 标准机柜"))
        assertTrue(rackSheet.contains("6kW"))
        assertTrue(rackSheet.contains("双路市电"))
        assertTrue(rackSheet.contains("机柜概览统计"))
        assertTrue(rackSheet.contains("风险分析与优化建议"))
        assertTrue(rackSheet.contains("已用U位"))
        assertTrue(rackSheet.contains("测试机房"), "用户填写的机房名称应回填到表1可确认的物理位置字段")
        assertFalse(rackSheet.contains("核心交换机 A型"), "模板中的演示型号不能带入结果")

        listOf("机房", "登记日期", "登记人", "审核人", "设备型号", "管理IP", "功率(W)").forEach {
            assertTrue(assetSheet.contains(it), "Sheet2 缺少字段：$it")
        }
        assertTrue(assetSheet.contains("右侧轨 U43"), "设备明细备注应保留识别依据")
        assertTrue(assetSheet.contains("右侧轨 U21-U22"), "多U设备的识别依据应保留")

        listOf(
            "一、总体概况", "二、机柜U位使用率分析", "三、设备类型分布", "四、设备厂商分布",
            "五、业务系统分布", "六、优化建议与行动计划", "机柜编号", "空闲U位", "功率占比",
        ).forEach { assertTrue(analysisSheet.contains(it), "Sheet3 缺少字段：$it") }
        assertTrue(rackSheet.contains("空间利用率"))
        assertTrue(Regex("<c r=\"G6\"[^>]*s=\"5\"/>").containsMatchIn(analysisSheet), "预估功率应保留人工填写空值")
        assertTrue(Regex("<c r=\"K6\"[^>]*s=\"5\"/>").containsMatchIn(analysisSheet), "预估电流应保留人工填写空值")
    }

    @Test
    fun writes_risk_action_repair_and_review_records_into_expected_sheets() {
        val entries = unzip(XlsxWriter.bytes(exportTask()))

        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("风险分析与优化建议"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("空间利用率"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("反面："), "建议类内容应标注为反面建议")
        assertTrue(entries.getValue("xl/worksheets/sheet3.xml").contains("反面："), "行动计划应标注为反面建议")
        assertTrue(entries.getValue("xl/worksheets/sheet3.xml").contains("短期(1个月内)"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("反面：结合业务增长计划统筹空闲 U 位，形成容量使用台账。"))
        assertTrue(entries.getValue("xl/worksheets/sheet3.xml").contains("反面：完成照片待确认项复核，补拍失败机柜图片"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("<mergeCell ref=\"B36:B37\"/>"), "2U 设备应合并连续 U 位单元格")
        assertFalse(entries.containsKey("xl/worksheets/sheet4.xml"))
    }

    @Test
    fun removes_template_ai_watermark_from_detail_and_analysis_sheets() {
        val entries = unzip(XlsxWriter.bytes(exportTask()))

        assertFalse(entries.getValue("xl/worksheets/sheet2.xml").contains("<drawing"))
        assertFalse(entries.getValue("xl/worksheets/sheet3.xml").contains("<drawing"))
        assertFalse(entries.containsKey("xl/worksheets/_rels/sheet2.xml.rels"))
        assertFalse(entries.containsKey("xl/worksheets/_rels/sheet3.xml.rels"))
        assertFalse(entries.containsKey("xl/drawings/drawing1.xml"))
        assertFalse(entries.containsKey("xl/drawings/drawing2.xml"))
        assertFalse(entries.containsKey("xl/drawings/_rels/drawing1.xml.rels"))
        assertFalse(entries.containsKey("xl/drawings/_rels/drawing2.xml.rels"))
        assertFalse(entries.containsKey("xl/media/image1.png"), "交付文件不应残留模板 AI 水印图片")
        assertFalse(entries.getValue("[Content_Types].xml").contains("/xl/drawings/drawing1.xml"))
        assertFalse(entries.getValue("[Content_Types].xml").contains("/xl/drawings/drawing2.xml"))
    }

    @Test
    fun expands_all_three_sheets_without_truncating_large_tasks() {
        val task = overflowTask()
        val bytes = XlsxWriter.bytes(task)
        val entries = unzip(bytes)
        val rackSheet = entries.getValue("xl/worksheets/sheet1.xml")
        val assetSheet = entries.getValue("xl/worksheets/sheet2.xml")
        val analysisSheet = entries.getValue("xl/worksheets/sheet3.xml")

        assertTrue(rackSheet.contains("K07柜"), "第 7 个机柜必须进入表1续图区块")
        assertTrue(rackSheet.contains("机柜上架图（续）"))
        assertTrue(rackSheet.contains("风险-9"), "超过样例行数的风险项必须保留")
        assertTrue(analysisSheet.contains("K07柜"), "第 7 个机柜必须进入表3统计区")
        assertTrue(assetSheet.contains("<c r=\"A101\" s=\"26\"><v>98</v></c>"), "第 98 台设备必须写入表2")
        assertTrue(assetSheet.contains("<c r=\"A103\" s=\"4\" t=\"inlineStr\"><is><t xml:space=\"preserve\">合计</t></is></c>"), "合计行应随明细行下移")
        assertTrue(assetSheet.contains("设备总数：98台"))
        assertFalse(entries.containsKey("xl/sharedStrings.xml"), "交付包不应携带模板演示共享字符串")
        assertFalse(entries.values.any { it.contains("核心交换机 A型") }, "交付包不应残留模板演示型号")
    }

    @Test
    fun generated_xml_is_valid_and_has_no_excel_error_tokens() {
        val entries = unzip(XlsxWriter.bytes(exportTask()))
        entries.filterKeys { it.endsWith(".xml") }.forEach { (_, xml) ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray()))
            Regex("""<c r="([^"]+)"""").findAll(xml).forEach { match ->
                assertTrue(Regex("^[A-Z]+[1-9][0-9]*$").matches(match.groupValues[1]), "无效单元格坐标：${match.groupValues[1]}")
            }
            assertFalse(xml.contains("#REF!"))
            assertFalse(xml.contains("#DIV/0!"))
            assertFalse(xml.contains("#VALUE!"))
            assertFalse(xml.contains("#N/A"))
            assertFalse(xml.contains("#NAME?"))
        }
        assertEquals(3, entries.keys.count { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") })
    }

    private fun exportTask(): ExportTask {
        val input = listOf(
            Rack("K04", "K04.jpg", listOf(Device("交换机", 43, 1, 0.97, "右侧轨 U43"), Device("服务器", 21, 2, 0.91, "右侧轨 U21-U22")), listOf("U21 边界存在反光"), metadata = RackMetadata(cabinetPower = "6kW", powerType = "双路市电")),
            Rack("K03", "K03.jpg", listOf(Device("交换机", 42, 1, 0.95, "右侧轨 U42"), Device("服务器", 19, 2, 0.94, "右侧轨 U19-U20")), emptyList(), metadata = RackMetadata(cabinetPower = "6kW", powerType = "双路市电")),
        )
        return ExportTask(
            taskId = "TASK-20260814-001",
            roomName = "测试机房",
            createdAtMillis = 1_786_718_400_000L,
            modelName = "example-model",
            promptName = "通信机房上架图通用版",
            promptVersion = "v1.1",
            promptContent = "PROMPT",
            requestedConcurrency = 5,
            effectiveConcurrency = 2,
            outcomes = listOf(
                ImageOutcome("K04.jpg", input[0], ImageProcessingState.COMPLETED, retryCount = 1, repairEvents = listOf(RepairEvent(1, "模型响应格式复核"))),
                ImageOutcome("K03.jpg", input[1], ImageProcessingState.COMPLETED),
                ImageOutcome("K10.jpg", null, ImageProcessingState.FAILED, retryCount = 3, message = "修复失败，建议补拍", repairEvents = listOf(RepairEvent(1, "网络超时"), RepairEvent(2, "网络超时"), RepairEvent(3, "模型响应为空"))),
            ),
            racks = input,
            analysis = RiskAnalysisEngine.analyze(input, listOf("K10.jpg：修复失败，建议补拍")),
        )
    }

    private fun overflowTask(): ExportTask {
        val racks = (1..7).map { rackNumber ->
            Rack(
                cabinetId = "K%02d".format(rackNumber),
                imageName = "K%02d.jpg".format(rackNumber),
                devices = (0 until 14).map { offset ->
                    Device("交换机", 47 - offset, 1, 0.99, "右侧轨 U${47 - offset}")
                },
                uncertain = emptyList(),
            )
        }
        val risks = (1..9).map { index ->
            RiskItem(
                category = "风险-$index",
                description = "可见证据-$index",
                level = RiskLevel.PENDING,
                recommendation = "处置建议-$index",
                evidence = "照片-$index",
                confidence = 0.9,
                dataSource = "照片识别",
            )
        }
        return ExportTask(
            taskId = "TASK-OVERFLOW",
            roomName = "扩展测试机房",
            createdAtMillis = 1_786_718_400_000L,
            modelName = "model",
            promptName = "prompt",
            promptVersion = "v2.3",
            promptContent = "PROMPT",
            requestedConcurrency = 5,
            effectiveConcurrency = 5,
            outcomes = racks.map { ImageOutcome(it.imageName, it, ImageProcessingState.COMPLETED) },
            racks = racks,
            analysis = AnalysisResult(risks, emptyList()),
        )
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        return result
    }
}
