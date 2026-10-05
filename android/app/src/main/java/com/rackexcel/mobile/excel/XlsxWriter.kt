package com.rackexcel.mobile.excel

import com.rackexcel.mobile.model.Device
import com.rackexcel.mobile.model.Rack
import com.rackexcel.mobile.model.RackOrdering
import com.rackexcel.mobile.model.RiskItem
import com.rackexcel.mobile.model.RiskLevel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports the three worksheets in the customer workbook.
 *
 * The supplied workbook stays the base asset so its styles, print setup,
 * column widths and embedded visual assets survive mobile export. Rows and
 * rack-diagram blocks extend when a task exceeds the template's sample size.
 */
object XlsxWriter {
    private const val TEMPLATE_RESOURCE = "机柜信息登记图模板.xlsx"
    private const val RACKS_PER_BLOCK = 6
    private const val MIN_DETAIL_ROWS = 87
    private const val MIN_RISK_ROWS = 7
    private const val MIN_RACK_ANALYSIS_ROWS = 6
    private const val MIN_TYPE_ROWS = 13
    private const val MIN_MANUFACTURER_ROWS = 5
    private const val MIN_SYSTEM_ROWS = 8

    fun bytes(task: ExportTask, templateBytes: ByteArray? = loadClasspathTemplate()): ByteArray =
        ByteArrayOutputStream().use { output ->
            write(task, output, templateBytes)
            output.toByteArray()
        }

    fun write(
        task: ExportTask,
        output: OutputStream,
        templateBytes: ByteArray? = loadClasspathTemplate(),
    ) {
        val ordered = task.copy(racks = RackOrdering.sort(task.racks).racks)
        val template = requireNotNull(templateBytes) { "未找到机柜信息登记图模板.xlsx，无法生成交付文件。" }
        val entries = patchTemplate(template, ordered)
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                if (name.endsWith('/')) return@forEach
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun write(racks: List<Rack>, output: OutputStream) {
        val ordered = RackOrdering.sort(racks).racks
        write(
            ExportTask(
                taskId = "TASK-${System.currentTimeMillis()}",
                roomName = "",
                createdAtMillis = System.currentTimeMillis(),
                modelName = "",
                promptName = "通信机房上架图通用版",
                promptVersion = "v2.5",
                promptContent = "",
                requestedConcurrency = 5,
                effectiveConcurrency = ordered.size.coerceAtMost(5),
                outcomes = ordered.map { rack -> ImageOutcome(rack.imageName, rack, ImageProcessingState.COMPLETED) },
                racks = ordered,
                analysis = com.rackexcel.mobile.model.RiskAnalysisEngine.analyze(ordered, emptyList()),
            ),
            output,
        )
    }

    private fun patchTemplate(templateBytes: ByteArray, task: ExportTask): LinkedHashMap<String, ByteArray> {
        val entries = unzip(templateBytes)
        val sheet1Layout = Sheet1Layout.create(task.racks, task.analysis.risks.size)
        val sheet2Layout = Sheet2Layout.create(task.racks.sumOf { it.devices.size })
        val sheet3Layout = sheet3Layout(task)

        entries["xl/worksheets/sheet1.xml"] = replaceWorksheet(
            xml = entries.getValue("xl/worksheets/sheet1.xml").decodeToString(),
            data = buildSheet1Data(task, sheet1Layout),
            merges = sheet1Merges(sheet1Layout),
            dimension = "A1:O${sheet1Layout.riskEndRow}",
        ).toByteArray(StandardCharsets.UTF_8)
        entries["xl/worksheets/sheet2.xml"] = replaceWorksheet(
            xml = entries.getValue("xl/worksheets/sheet2.xml").decodeToString(),
            data = buildSheet2Data(task, sheet2Layout),
            merges = sheet2Merges(sheet2Layout),
            dimension = "A1:R${sheet2Layout.totalRow}",
        ).toByteArray(StandardCharsets.UTF_8)
        entries["xl/worksheets/sheet3.xml"] = replaceWorksheet(
            xml = entries.getValue("xl/worksheets/sheet3.xml").decodeToString(),
            data = buildSheet3Data(task, sheet3Layout),
            merges = sheet3Merges(sheet3Layout),
            dimension = "A1:L${sheet3Layout.planEndRow}",
        ).toByteArray(StandardCharsets.UTF_8)
        removeTemplateWatermarkArtwork(entries)
        removeUnusedSharedStrings(entries)
        return entries
    }

    private fun replaceWorksheet(xml: String, data: String, merges: List<String>, dimension: String): String =
        replaceDimension(replaceMergeCells(replaceSheetData(xml, data), merges), dimension)

    private fun replaceSheetData(xml: String, data: String): String {
        val start = xml.indexOf("<sheetData>")
        val end = xml.indexOf("</sheetData>", start)
        require(start >= 0 && end > start) { "模板工作表缺少 sheetData" }
        return xml.substring(0, start + "<sheetData>".length) + data + xml.substring(end)
    }

    private fun replaceMergeCells(xml: String, ranges: List<String>): String {
        val normalized = ranges.distinct()
        val block = "<mergeCells count=\"${normalized.size}\">" +
            normalized.joinToString("") { "<mergeCell ref=\"${escape(it)}\"/>" } +
            "</mergeCells>"
        val start = xml.indexOf("<mergeCells")
        if (start >= 0) {
            val end = xml.indexOf("</mergeCells>", start)
            require(end > start) { "模板工作表的 mergeCells 无效" }
            return xml.substring(0, start) + block + xml.substring(end + "</mergeCells>".length)
        }
        val insertion = xml.indexOf("</sheetData>") + "</sheetData>".length
        return xml.substring(0, insertion) + block + xml.substring(insertion)
    }

    private fun replaceDimension(xml: String, ref: String): String =
        xml.replace(Regex("""<dimension\s+ref="[^"]+"\s*/>"""), "<dimension ref=\"$ref\"/>")

    private fun removeUnusedSharedStrings(entries: LinkedHashMap<String, ByteArray>) {
        entries.remove("xl/sharedStrings.xml")
        entries["xl/_rels/workbook.xml.rels"]?.let { bytes ->
            val cleaned = bytes.decodeToString().replace(
                Regex("""<Relationship\b[^>]*Type="[^"]*/sharedStrings"[^>]*/>"""),
                "",
            )
            entries["xl/_rels/workbook.xml.rels"] = cleaned.toByteArray(StandardCharsets.UTF_8)
        }
        entries["[Content_Types].xml"]?.let { bytes ->
            val cleaned = bytes.decodeToString().replace(
                Regex("""<Override\b[^>]*PartName="/xl/sharedStrings\.xml"[^>]*/>"""),
                "",
            )
            entries["[Content_Types].xml"] = cleaned.toByteArray(StandardCharsets.UTF_8)
        }
    }

    /**
     * The source workbook carries a visible AI-generation watermark as a
     * drawing on the detail and analysis sheets. It is presentation metadata,
     * not asset data, so remove only those worksheet drawing relationships and
     * their now-unreferenced image parts while leaving sheet styles untouched.
     */
    private fun removeTemplateWatermarkArtwork(entries: LinkedHashMap<String, ByteArray>) {
        val worksheetRelationships = listOf(
            "xl/worksheets/sheet2.xml" to "xl/worksheets/_rels/sheet2.xml.rels",
            "xl/worksheets/sheet3.xml" to "xl/worksheets/_rels/sheet3.xml.rels",
        )
        val removedDrawings = linkedSetOf<String>()
        val removedMedia = linkedSetOf<String>()

        worksheetRelationships.forEach { (worksheetPart, relationshipsPart) ->
            entries[worksheetPart]?.let { bytes ->
                val cleaned = removeDrawingTags(bytes.decodeToString())
                entries[worksheetPart] = cleaned.toByteArray(StandardCharsets.UTF_8)
            }

            val relationships = entries[relationshipsPart]?.decodeToString() ?: return@forEach
            val drawingTargets = relationshipTargets(relationships, "drawing")
            drawingTargets.forEach { target ->
                val drawingPart = resolveRelationshipTarget(worksheetPart, target)
                removedDrawings += drawingPart
                val drawingRelationshipsPart = relationshipPart(drawingPart)
                entries[drawingRelationshipsPart]?.let { drawingRelationships ->
                    relationshipTargets(drawingRelationships.decodeToString(), "image").forEach { imageTarget ->
                        removedMedia += resolveRelationshipTarget(drawingPart, imageTarget)
                    }
                    entries.remove(drawingRelationshipsPart)
                }
            }

            val cleanedRelationships = removeRelationships(relationships, "drawing")
            if (relationshipTargets(cleanedRelationships, null).isEmpty()) {
                entries.remove(relationshipsPart)
            } else {
                entries[relationshipsPart] = cleanedRelationships.toByteArray(StandardCharsets.UTF_8)
            }
        }

        removedDrawings.forEach { drawingPart ->
            entries.remove(drawingPart)
            entries.remove(relationshipPart(drawingPart))
        }

        // An image can be shared by more than one drawing. Only remove it when
        // no remaining relationship in the package points at that part.
        val stillReferencedMedia = entries
            .filterKeys { it.endsWith(".rels") }
            .flatMap { (relationshipsPart, bytes) ->
                val sourcePart = relationshipSourcePart(relationshipsPart)
                relationshipTargets(bytes.decodeToString(), "image").map { target ->
                    resolveRelationshipTarget(sourcePart, target)
                }
            }
            .toSet()
        removedMedia.filter { it !in stillReferencedMedia }.forEach { entries.remove(it) }

        entries["[Content_Types].xml"]?.let { bytes ->
            var contentTypes = bytes.decodeToString()
            removedDrawings.forEach { drawingPart ->
                contentTypes = contentTypes.replace(
                    Regex("""<Override\b[^>]*PartName="/${Regex.escape(drawingPart)}"[^>]*/>"""),
                    "",
                )
            }
            if (entries.keys.none { it.startsWith("xl/media/") }) {
                contentTypes = contentTypes.replace(
                    Regex("""<Default\b[^>]*Extension="(?:png|jpg|jpeg|gif)"[^>]*/>"""),
                    "",
                )
            }
            entries["[Content_Types].xml"] = contentTypes.toByteArray(StandardCharsets.UTF_8)
        }
    }

    private fun removeDrawingTags(xml: String): String {
        val selfClosing = Regex("""<drawing\b[^>]*/>""").replace(xml, "")
        return Regex("""<drawing\b[^>]*>.*?</drawing>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .replace(selfClosing, "")
    }

    private fun removeRelationships(xml: String, relationKind: String): String {
        val pattern = if (relationKind == "drawing") {
            Regex("""<Relationship\b(?=[^>]*\bType="[^"]*/drawing")[^>]*/>""")
        } else {
            Regex("""<Relationship\b(?=[^>]*\bType="[^"]*/${Regex.escape(relationKind)}")[^>]*/>""")
        }
        return pattern.replace(xml, "")
    }

    private fun relationshipTargets(xml: String, relationKind: String?): List<String> {
        val relationshipPattern = relationKind?.let {
            Regex("""<Relationship\b(?=[^>]*\bType="[^"]*/${Regex.escape(it)}")[^>]*/>""")
        } ?: Regex("""<Relationship\b[^>]*/>""")
        val targetPattern = Regex("""\bTarget="([^"]+)"""")
        return relationshipPattern.findAll(xml).mapNotNull { relationship ->
            targetPattern.find(relationship.value)?.groupValues?.get(1)
        }.toList()
    }

    private fun relationshipPart(sourcePart: String): String {
        val slash = sourcePart.lastIndexOf('/')
        val directory = sourcePart.substring(0, slash)
        val file = sourcePart.substring(slash + 1)
        return "$directory/_rels/$file.rels"
    }

    private fun relationshipSourcePart(relationshipsPart: String): String {
        val marker = "/_rels/"
        val markerIndex = relationshipsPart.indexOf(marker)
        if (markerIndex < 0) return relationshipsPart.removeSuffix(".rels")
        val directory = relationshipsPart.substring(0, markerIndex)
        val file = relationshipsPart.substring(markerIndex + marker.length).removeSuffix(".rels")
        return "$directory/$file"
    }

    private fun resolveRelationshipTarget(sourcePart: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val base = sourcePart.substringBeforeLast('/')
        val segments = ("$base/$target").split('/')
        val normalized = ArrayDeque<String>()
        segments.forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeLast()
                else -> normalized.addLast(segment)
            }
        }
        return normalized.joinToString("/")
    }

    private fun buildSheet1Data(task: ExportTask, layout: Sheet1Layout): String = buildString {
        val common = CommonFields.from(task.racks)
        val physicalLocation = common.physicalLocation.ifBlank { task.roomName.trim() }
        row(1, 32, cell("B1", 33, "机房机柜上架图"))
        // Sheet 1 is intentionally kept to the four fields used by the field
        // handover template.  Values not visible in the source photo remain
        // empty rather than being inferred from common cabinet defaults.
        metadataRow(3, "物理位置", physicalLocation, "机柜功率", common.cabinetPower, "机柜规格", "47U 标准机柜", "供电类型", common.powerType)

        layout.blocks.forEach { block ->
            block.titleRow?.let { titleRow ->
                row(titleRow, 21, cell("A$titleRow", 3, "机柜上架图（续） · 第 ${block.index + 1} 组"))
            }
            row(block.cabinetHeaderRow, 25, block.racks.indices.map { slot ->
                cell("${rackNameColumn(slot)}${block.cabinetHeaderRow}", 34, "${block.racks[slot].cabinetId}柜")
            })
            row(block.columnHeaderRow, 19, buildList {
                for (slot in block.racks.indices) {
                    add(cell("${rackNameColumn(slot)}${block.columnHeaderRow}", 4, "设备名称"))
                    add(cell("${rackUColumn(slot)}${block.columnHeaderRow}", 4, "U位"))
                }
            })
            for (u in 47 downTo 1) {
                val rowNumber = block.rowForU(u)
                row(rowNumber, 14, buildList {
                    for (slot in block.racks.indices) {
                        val device = block.racks.getOrNull(slot)?.devices?.firstOrNull { it.bottomU + it.heightU - 1 == u }
                        add(cell("${rackNameColumn(slot)}$rowNumber", deviceStyle(device), device?.let(::displayName).orEmpty()))
                        add(cell("${rackUColumn(slot)}$rowNumber", 36, u))
                    }
                })
            }
        }

        row(layout.legendStartRow, 21, cell("A${layout.legendStartRow}", 3, "设备类型图例"))
        row(layout.legendStartRow + 1, 17, legendCells(layout.legendStartRow + 1, listOf("核心交换机", "汇聚交换机", "接入交换机", "路由器", "防火墙")))
        row(layout.legendStartRow + 2, 17, legendCells(layout.legendStartRow + 2, listOf("安全设备", "负载均衡", "数据库服务器", "应用服务器", "缓存服务器")))
        row(layout.legendStartRow + 3, 17, legendCells(layout.legendStartRow + 3, listOf("存储阵列", "备份设备", "KVM", "PDU", "理线架/空白")))

        row(layout.overviewTitleRow, 21, cell("A${layout.overviewTitleRow}", 3, "机柜概览统计"))
        row(layout.overviewHeaderRow, 21, summaryHeaderCells(layout.overviewHeaderRow))
        for (index in 0 until layout.overviewRows) {
            val rack = task.racks.getOrNull(index)
            val values = rack?.let {
                val used = usedU(it)
                listOf(
                    "${it.cabinetId}柜", "${used}U", percent(used, 47), it.devices.size.toString(),
                    knownPower(it.devices)?.toString().orEmpty(), "", typeSummary(it.devices),
                )
            }.orEmpty()
            row(layout.overviewFirstRow + index, 19, summaryRowCells(layout.overviewFirstRow + index, values))
        }

        row(layout.riskTitleRow, 21, cell("A${layout.riskTitleRow}", 3, "风险分析与优化建议"))
        row(layout.riskHeaderRow, 19, riskHeaderCells(layout.riskHeaderRow))
        for (index in 0 until layout.riskRows) {
            row(layout.riskFirstRow + index, 25, riskRowCells(layout.riskFirstRow + index, index + 1, task.analysis.risks.getOrNull(index)))
        }
    }

    private fun sheet1Merges(layout: Sheet1Layout): List<String> = buildList {
        addAll(listOf("B1:M1", "C3:D3", "F3:G3", "I3:J3", "L3:M3"))
        layout.blocks.forEach { block ->
            block.titleRow?.let { add("A$it:N$it") }
            for (slot in block.racks.indices) {
                add("${rackNameColumn(slot)}${block.cabinetHeaderRow}:${rackUColumn(slot)}${block.cabinetHeaderRow}")
            }
            block.racks.forEachIndexed { slot, rack ->
                val nameColumn = rackNameColumn(slot)
                rack.devices.filter { it.heightU > 1 }.forEach { device ->
                    add("$nameColumn${block.rowForU(device.bottomU + device.heightU - 1)}:$nameColumn${block.rowForU(device.bottomU)}")
                }
            }
        }
        add("A${layout.legendStartRow}:N${layout.legendStartRow}")
        add("A${layout.overviewTitleRow}:N${layout.overviewTitleRow}")
        val summaryColumns = listOf("B" to "C", "D" to "E", "F" to "G", "H" to "I", "J" to "K", "L" to "M", "N" to "O")
        (listOf(layout.overviewHeaderRow) + (0 until layout.overviewRows).map { layout.overviewFirstRow + it }).forEach { rowNumber ->
            summaryColumns.forEach { (start, end) -> add("$start$rowNumber:$end$rowNumber") }
        }
        add("A${layout.riskTitleRow}:N${layout.riskTitleRow}")
        val riskColumns = listOf("B" to "C", "D" to "E", "F" to "G", "H" to "I", "J" to "K")
        (listOf(layout.riskHeaderRow) + (0 until layout.riskRows).map { layout.riskFirstRow + it }).forEach { rowNumber ->
            riskColumns.forEach { (start, end) -> add("$start$rowNumber:$end$rowNumber") }
        }
    }

    private fun StringBuilder.metadataRow(
        rowNumber: Int,
        label1: String, value1: String,
        label2: String, value2: String,
        label3: String, value3: String,
        label4: String, value4: String,
    ) = row(rowNumber, 19, listOf(
        cell("B$rowNumber", 4, label1), cell("C$rowNumber", 5, value1),
        cell("E$rowNumber", 4, label2), cell("F$rowNumber", 5, value2),
        cell("H$rowNumber", 4, label3), cell("I$rowNumber", 5, value3),
        cell("K$rowNumber", 4, label4), cell("L$rowNumber", 5, value4),
    ))

    private fun legendCells(rowNumber: Int, values: List<String>): List<String> {
        val columns = listOf("C", "E", "G", "J", "L")
        return values.mapIndexed { index, value -> cell("${columns[index]}$rowNumber", 52, value) }
    }

    private fun summaryHeaderCells(rowNumber: Int): List<String> = listOf(
        cell("B$rowNumber", 6, "机柜编号"), cell("D$rowNumber", 6, "已用U位"), cell("F$rowNumber", 6, "使用率"),
        cell("H$rowNumber", 6, "设备数量"), cell("J$rowNumber", 6, "预估功率(W)"), cell("L$rowNumber", 6, "预估电流(A)"), cell("N$rowNumber", 6, "主要设备类型"),
    )

    private fun summaryRowCells(rowNumber: Int, values: List<String>): List<String> {
        val columns = listOf("B", "D", "F", "H", "J", "L", "N")
        return columns.indices.map { valueIndex ->
            val value = values.getOrNull(valueIndex).orEmpty()
            val style = if (valueIndex == 2) percentStyle(value.removeSuffix("%").toDoubleOrNull() ?: 0.0, 100.0) else 7
            cell("${columns[valueIndex]}$rowNumber", style, value)
        }
    }

    private fun riskHeaderCells(rowNumber: Int): List<String> = listOf(
        cell("B$rowNumber", 6, "序号"), cell("D$rowNumber", 6, "风险类别"), cell("F$rowNumber", 6, "风险描述"),
        cell("H$rowNumber", 6, "风险等级"), cell("J$rowNumber", 6, "处置建议"),
    )

    private fun riskRowCells(rowNumber: Int, index: Int, risk: RiskItem?): List<String> = listOf(
        cell("B$rowNumber", 7, index),
        cell("D$rowNumber", 5, risk?.category.orEmpty()),
        cell("F$rowNumber", 5, risk?.description.orEmpty()),
        cell("H$rowNumber", riskStyle(risk?.level), risk?.level?.label.orEmpty()),
        // Recommendations are the analytical/reverse-side output of the
        // workbook. Prefix them so the operator can distinguish them from
        // the front-side evidence (cabinet/U position and device type).
        cell("J$rowNumber", 7, reverseAdvice(risk?.recommendation.orEmpty())),
    )

    private fun buildSheet2Data(task: ExportTask, layout: Sheet2Layout): String = buildString {
        val devices = task.racks.flatMap { rack ->
            rack.devices.sortedWith(compareByDescending<Device> { it.bottomU }.thenBy { it.type }).map { rack to it }
        }
        row(1, 28, cell("A1", 1, "设备明细登记表"))
        row(2, 19, listOf(
            cell("A2", 23, "机房：${task.roomName.trim()}"),
            cell("E2", 23, "登记日期：${formatDate(task.createdAtMillis)}"),
            cell("I2", 23, "登记人："),
            cell("M2", 23, "审核人："),
        ))
        row(3, 26, detailHeaderCells())
        for (index in 0 until layout.detailRows) row(4 + index, 17, detailRowCells(4 + index, index, devices.getOrNull(index)))
        row(layout.totalRow, 21, listOf(
            cell("A${layout.totalRow}", 4, "合计"),
            cell("L${layout.totalRow}", 4, "设备总数：${devices.size}台"),
            cell("O${layout.totalRow}", 4, "总功率：${totalKnownPower(devices)?.let { "${it}W" }.orEmpty()}"),
        ))
    }

    private fun sheet2Merges(layout: Sheet2Layout): List<String> = listOf(
        "A1:R1", "A2:D2", "E2:H2", "I2:L2", "M2:R2",
        "A${layout.totalRow}:K${layout.totalRow}", "L${layout.totalRow}:N${layout.totalRow}", "O${layout.totalRow}:R${layout.totalRow}",
    )

    private fun detailHeaderCells(): List<String> = listOf(
        "序号", "机柜编号", "U位", "设备类型", "设备名称", "设备型号", "厂商", "资产编号",
        "序列号", "管理IP", "业务系统", "设备用途", "功率(W)", "责任人", "联系电话", "上架日期", "运维状态", "备注",
    ).mapIndexed { index, value -> cell("${column(index)}3", 6, value) }

    private fun detailRowCells(rowNumber: Int, index: Int, pair: Pair<Rack, Device>?): List<String> {
        val base = if (index % 2 == 0) 24 else 26
        val wrap = if (index % 2 == 0) 25 else 27
        val rack = pair?.first
        val device = pair?.second
        val values: List<Any?> = listOf(
            pair?.let { index + 1 }, rack?.let { "${it.cabinetId}柜" }, device?.let { uRange(it.bottomU, it.heightU) },
            device?.type, device?.let(::displayName), device?.model, device?.manufacturer, device?.assetId,
            device?.serialNumber, device?.managementIp, device?.businessSystem, device?.purpose, device?.powerW,
            device?.owner, device?.phone, device?.installDate, device?.status, device?.let(::detailNotes),
        )
        return values.mapIndexed { valueIndex, value ->
            val style = if (valueIndex in setOf(0, 1, 2, 3, 6, 12, 15, 16)) base else wrap
            cell("${column(valueIndex)}$rowNumber", style, value)
        }
    }

    private fun buildSheet3Data(task: ExportTask, layout: Sheet3Layout): String = buildString {
        val racks = task.racks
        val devices = racks.flatMap(Rack::devices)
        val totalUsed = devices.sumOf(Device::heightU)
        val totalPower = knownPower(devices)
        val room = task.roomName.trim().ifBlank { "待人工补充" }
        val typeGroups = devices.groupingBy { it.type }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val manufacturers = devices.filter { it.manufacturer.isNotBlank() }.groupBy { it.manufacturer }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Device>>> { it.value.size }.thenBy { it.key })
        val systems = devices.filter { it.businessSystem.isNotBlank() }.groupBy { it.businessSystem }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Device>>> { it.value.size }.thenBy { it.key })

        row(1, 32, cell("A1", 1, "机房资产统计分析报告"))
        row(2, 19, cell("A2", 2, "统计周期：截至${formatChineseDate(task.createdAtMillis)} | 统计范围：$room · ${racks.size}个机柜"))
        row(4, 23, cell("A4", 3, "一、总体概况"))
        row(5, 21, listOf(
            cell("A5", 4, "机柜总数"), cell("C5", 5, "${racks.size}个（47U标准机柜）"),
            cell("E5", 4, "设备总数"), cell("G5", 5, "${devices.size}台"),
            cell("I5", 4, "总U位数"), cell("K5", 5, "${racks.size * 47}U（${racks.size}柜×47U）"),
        ))
        row(6, 21, listOf(
            cell("A6", 4, "已用U位"), cell("C6", 5, "${totalUsed}U（整体使用率${percent(totalUsed, racks.size * 47)}）"),
            cell("E6", 4, "总预估功率"), cell("G6", 5, totalPower?.let { "${it}W" }.orEmpty()),
            cell("I6", 4, "总预估电流"), cell("K6", 5, ""),
        ))

        row(layout.rackTitleRow, 23, cell("A${layout.rackTitleRow}", 3, "二、机柜U位使用率分析"))
        row(layout.rackHeaderRow, 21, listOf("机柜编号", "总U位", "已用U位", "空闲U位", "使用率", "设备数", "功率(W)", "负载率").mapIndexed { index, value -> cell("${column(index)}${layout.rackHeaderRow}", 6, value) })
        for (index in 0 until layout.rackRows) {
            val rack = racks.getOrNull(index)
            val rowNumber = layout.rackFirstRow + index
            val used = rack?.let(::usedU) ?: 0
            val power = rack?.let { knownPower(it.devices) }
            row(rowNumber, 19, listOf(
                cell("A$rowNumber", 7, rack?.let { "${it.cabinetId}柜" }.orEmpty()),
                cell("B$rowNumber", 7, rack?.let { 47 }), cell("C$rowNumber", 7, rack?.let { used }),
                cell("D$rowNumber", 7, rack?.let { 47 - used }), cell("E$rowNumber", percentStyle(used.toDouble(), 47.0), rack?.let { percent(used, 47) }.orEmpty()),
                cell("F$rowNumber", 7, rack?.devices?.size), cell("G$rowNumber", 7, power), cell("H$rowNumber", 8, ""),
            ))
        }

        row(layout.typeTitleRow, 23, cell("A${layout.typeTitleRow}", 3, "三、设备类型分布"))
        row(layout.typeHeaderRow, 21, listOf("设备类型", "数量", "占比", "总功率(W)", "功率占比").mapIndexed { index, value -> cell("${column(index)}${layout.typeHeaderRow}", 6, value) })
        for (index in 0 until layout.typeRows) {
            val rowNumber = layout.typeFirstRow + index
            val entry = typeGroups.getOrNull(index)
            val groupDevices = entry?.let { devices.filter { device -> device.type == it.key } }.orEmpty()
            val groupPower = knownPower(groupDevices)
            row(rowNumber, 19, listOf(
                cell("A$rowNumber", 10 + index.coerceAtMost(12), entry?.key.orEmpty()),
                cell("B$rowNumber", 7, entry?.value), cell("C$rowNumber", 7, entry?.let { percent(it.value, devices.size) }.orEmpty()),
                cell("D$rowNumber", 7, groupPower), cell("E$rowNumber", 7, if (groupPower != null && totalPower != null) percent(groupPower, totalPower) else ""),
            ))
        }

        row(layout.manufacturerTitleRow, 23, cell("A${layout.manufacturerTitleRow}", 3, "四、设备厂商分布"))
        row(layout.manufacturerHeaderRow, 21, listOf("厂商", "设备数量", "占比", "主要设备类型").mapIndexed { index, value -> cell("${column(index)}${layout.manufacturerHeaderRow}", 6, value) })
        for (index in 0 until layout.manufacturerRows) {
            val rowNumber = layout.manufacturerFirstRow + index
            val entry = manufacturers.getOrNull(index)
            val types = entry?.value?.groupingBy { it.type }?.eachCount()?.entries?.sortedByDescending { it.value }?.joinToString("、") { it.key }.orEmpty()
            row(rowNumber, 19, listOf(
                cell("A$rowNumber", 7, entry?.key.orEmpty()), cell("B$rowNumber", 7, entry?.value?.size),
                cell("C$rowNumber", 7, entry?.let { percent(it.value.size, devices.size) }.orEmpty()), cell("D$rowNumber", 5, types),
            ))
        }

        row(layout.systemTitleRow, 23, cell("A${layout.systemTitleRow}", 3, "五、业务系统分布"))
        row(layout.systemHeaderRow, 21, listOf("业务系统", "设备数量", "占比", "涉及机柜").mapIndexed { index, value -> cell("${column(index)}${layout.systemHeaderRow}", 6, value) })
        for (index in 0 until layout.systemRows) {
            val rowNumber = layout.systemFirstRow + index
            val entry = systems.getOrNull(index)
            val cabinets = entry?.value?.mapNotNull { device -> racks.firstOrNull { it.devices.contains(device) }?.cabinetId }
                ?.distinct()?.sortedWith(compareBy<String> { RackOrdering.normalize(it)?.drop(1)?.toIntOrNull() ?: Int.MAX_VALUE })
                ?.joinToString("、") { "${it}柜" }.orEmpty()
            row(rowNumber, 19, listOf(
                cell("A$rowNumber", 7, entry?.key.orEmpty()), cell("B$rowNumber", 7, entry?.value?.size),
                cell("C$rowNumber", 7, entry?.let { percent(it.value.size, devices.size) }.orEmpty()), cell("D$rowNumber", 5, cabinets),
            ))
        }

        row(layout.planTitleRow, 23, cell("A${layout.planTitleRow}", 3, "六、优化建议与行动计划"))
        val periods = listOf("短期(1个月内)", "中期(3个月内)", "长期(6个月内)")
        for (index in periods.indices) {
            val rowNumber = layout.planFirstRow + index
            row(rowNumber, 35, listOf(
                cell("A$rowNumber", 4, periods[index]),
                cell("C$rowNumber", 5, reverseAdvice(task.analysis.plans.getOrNull(index)?.text.orEmpty())),
            ))
        }
    }

    /**
     * Marks analytical recommendations as the reverse-side content of the
     * delivery workbook. Empty cells remain empty and an already-prefixed
     * value is kept idempotent for re-exported/reviewed tasks.
     */
    private fun reverseAdvice(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.startsWith("反面：")) return trimmed
        return "反面：$trimmed"
    }

    private fun sheet3Merges(layout: Sheet3Layout): List<String> = buildList {
        addAll(listOf(
            "A1:L1", "A2:L2", "A4:L4", "A5:B5", "C5:D5", "E5:F5", "G5:H5", "I5:J5", "K5:L5",
            "A6:B6", "C6:D6", "E6:F6", "G6:H6", "I6:J6", "K6:L6",
            "A${layout.rackTitleRow}:L${layout.rackTitleRow}", "A${layout.typeTitleRow}:L${layout.typeTitleRow}",
            "A${layout.manufacturerTitleRow}:L${layout.manufacturerTitleRow}", "A${layout.systemTitleRow}:L${layout.systemTitleRow}",
            "A${layout.planTitleRow}:L${layout.planTitleRow}",
        ))
        for (index in 0..2) {
            val rowNumber = layout.planFirstRow + index
            add("A$rowNumber:B$rowNumber")
            add("C$rowNumber:L$rowNumber")
        }
    }

    private fun sheet3Layout(task: ExportTask): Sheet3Layout {
        val devices = task.racks.flatMap(Rack::devices)
        val typeCount = devices.groupingBy { it.type }.eachCount().size
        val manufacturerCount = devices.map { it.manufacturer }.filter { it.isNotBlank() }.distinct().size
        val systemCount = devices.map { it.businessSystem }.filter { it.isNotBlank() }.distinct().size
        return Sheet3Layout.create(task.racks.size, typeCount, manufacturerCount, systemCount)
    }

    private fun displayName(device: Device): String = device.displayName.ifBlank { device.type }
    private fun detailNotes(device: Device): String = listOf(device.notes.trim(), device.evidence.trim())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("；")
    private fun usedU(rack: Rack): Int = rack.devices.sumOf(Device::heightU)
    private fun knownPower(devices: List<Device>): Int? = devices.takeIf { it.isNotEmpty() && it.all { device -> device.powerW != null } }?.sumOf { it.powerW ?: 0 }
    private fun totalKnownPower(devices: List<Pair<Rack, Device>>): Int? = knownPower(devices.map { it.second })
    private fun typeSummary(devices: List<Device>): String = devices.groupingBy { it.type }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString("、") { "${it.key}×${it.value}" }
    private fun uRange(bottom: Int, height: Int): String = (bottom + height - 1).let { top -> if (top == bottom) "$bottom" else "$top-$bottom" }
    private fun percent(part: Int, total: Int): String = if (total <= 0) "0.0%" else "%.1f%%".format(Locale.ROOT, part.toDouble() * 100 / total)
    private fun percentStyle(part: Double, total: Double): Int = if (total > 0 && part / total > 0.5) 9 else 8
    private fun riskStyle(level: RiskLevel?): Int = when (level) { RiskLevel.HIGH -> 14; RiskLevel.MEDIUM -> 9; RiskLevel.LOW -> 8; else -> 10 }
    private fun deviceStyle(device: Device?): Int = when (device?.type) {
        "交换机" -> 37
        "服务器" -> 42
        "传输设备" -> 40
        "路由器" -> 41
        "自定义设备" -> 35
        else -> 35
    }
    private fun rackNameColumn(slot: Int): String = column(slot * 2 + 1)
    private fun rackUColumn(slot: Int): String = column(slot * 2 + 2)
    private fun column(index: Int): String {
        var number = index + 1
        val result = StringBuilder()
        while (number > 0) {
            val remainder = (number - 1) % 26
            result.append(('A'.code + remainder).toChar())
            number = (number - 1) / 26
        }
        return result.reverse().toString()
    }
    private fun formatDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(value))
    private fun formatChineseDate(value: Long): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(value))
    private fun formatNumber(value: Number): String = when (value) {
        is Double -> "%.6f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
        is Float -> "%.6f".format(Locale.ROOT, value.toDouble()).trimEnd('0').trimEnd('.')
        else -> value.toString()
    }
    private fun escape(value: String): String = value.filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\n", "&#10;")

    private fun StringBuilder.row(number: Int, height: Int? = null, vararg cells: String) {
        append("<row r=\"$number\"")
        height?.let { append(" ht=\"$it\" customHeight=\"1\"") }
        append(">")
        append(cells.joinToString(""))
        append("</row>")
    }

    private fun StringBuilder.row(number: Int, height: Int? = null, cells: List<String>) = row(number, height, *cells.toTypedArray())

    private fun cell(ref: String, style: Int, value: Any?): String {
        if (value == null || value.toString().isBlank()) return "<c r=\"$ref\" s=\"$style\"/>"
        return when (value) {
            is Number -> "<c r=\"$ref\" s=\"$style\"><v>${formatNumber(value)}</v></c>"
            else -> "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value.toString())}</t></is></c>"
        }
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun loadClasspathTemplate(): ByteArray? = runCatching {
        XlsxWriter::class.java.classLoader?.getResourceAsStream(TEMPLATE_RESOURCE)?.use(InputStream::readBytes)
    }.getOrNull()

    private data class RackBlock(
        val index: Int,
        val racks: List<Rack>,
        val titleRow: Int?,
        val cabinetHeaderRow: Int,
        val columnHeaderRow: Int,
        val uStartRow: Int,
    ) {
        val uEndRow: Int get() = uStartRow + 46
        fun rowForU(u: Int): Int = uStartRow + (47 - u)
    }

    private data class Sheet1Layout(
        val blocks: List<RackBlock>,
        val legendStartRow: Int,
        val overviewTitleRow: Int,
        val overviewHeaderRow: Int,
        val overviewFirstRow: Int,
        val overviewRows: Int,
        val riskTitleRow: Int,
        val riskHeaderRow: Int,
        val riskFirstRow: Int,
        val riskRows: Int,
    ) {
        val riskEndRow: Int get() = riskFirstRow + riskRows - 1

        companion object {
            fun create(racks: List<Rack>, riskCount: Int): Sheet1Layout {
                val chunks: List<List<Rack>> = racks.chunked(RACKS_PER_BLOCK).ifEmpty { listOf(emptyList()) }
                var previousEnd = 0
                val blocks = chunks.mapIndexed { index, group ->
                    val titleRow = if (index == 0) null else previousEnd + 3
                    val cabinetHeaderRow = if (index == 0) 7 else titleRow!! + 1
                    val block = RackBlock(index, group, titleRow, cabinetHeaderRow, cabinetHeaderRow + 1, cabinetHeaderRow + 2)
                    previousEnd = block.uEndRow
                    block
                }
                val legendStart = previousEnd + 2
                val overviewTitle = legendStart + 6
                val overviewRows = maxOf(MIN_RACK_ANALYSIS_ROWS, racks.size)
                val overviewFirst = overviewTitle + 2
                val overviewEnd = overviewFirst + overviewRows - 1
                val riskTitle = overviewEnd + 2
                return Sheet1Layout(
                    blocks = blocks,
                    legendStartRow = legendStart,
                    overviewTitleRow = overviewTitle,
                    overviewHeaderRow = overviewTitle + 1,
                    overviewFirstRow = overviewFirst,
                    overviewRows = overviewRows,
                    riskTitleRow = riskTitle,
                    riskHeaderRow = riskTitle + 1,
                    riskFirstRow = riskTitle + 2,
                    riskRows = maxOf(MIN_RISK_ROWS, riskCount),
                )
            }
        }
    }

    private data class Sheet2Layout(val detailRows: Int, val totalRow: Int) {
        companion object {
            fun create(deviceCount: Int): Sheet2Layout {
                val detailRows = maxOf(MIN_DETAIL_ROWS, deviceCount)
                return Sheet2Layout(detailRows, 4 + detailRows + 1)
            }
        }
    }

    private data class Sheet3Layout(
        val rackRows: Int,
        val typeRows: Int,
        val manufacturerRows: Int,
        val systemRows: Int,
        val rackTitleRow: Int,
        val rackHeaderRow: Int,
        val rackFirstRow: Int,
        val typeTitleRow: Int,
        val typeHeaderRow: Int,
        val typeFirstRow: Int,
        val manufacturerTitleRow: Int,
        val manufacturerHeaderRow: Int,
        val manufacturerFirstRow: Int,
        val systemTitleRow: Int,
        val systemHeaderRow: Int,
        val systemFirstRow: Int,
        val planTitleRow: Int,
        val planFirstRow: Int,
    ) {
        val planEndRow: Int get() = planFirstRow + 2

        companion object {
            fun create(rackCount: Int, typeCount: Int, manufacturerCount: Int, systemCount: Int): Sheet3Layout {
                val rackRows = maxOf(MIN_RACK_ANALYSIS_ROWS, rackCount)
                val rackTitle = 8
                val rackHeader = rackTitle + 1
                val rackFirst = rackHeader + 1
                val typeTitle = rackFirst + rackRows + 1
                val typeHeader = typeTitle + 1
                val typeFirst = typeHeader + 1
                val typeRows = maxOf(MIN_TYPE_ROWS, typeCount)
                val manufacturerTitle = typeFirst + typeRows + 1
                val manufacturerHeader = manufacturerTitle + 1
                val manufacturerFirst = manufacturerHeader + 1
                val manufacturerRows = maxOf(MIN_MANUFACTURER_ROWS, manufacturerCount)
                val systemTitle = manufacturerFirst + manufacturerRows + 1
                val systemHeader = systemTitle + 1
                val systemFirst = systemHeader + 1
                val systemRows = maxOf(MIN_SYSTEM_ROWS, systemCount)
                val planTitle = systemFirst + systemRows + 1
                return Sheet3Layout(
                    rackRows = rackRows,
                    typeRows = typeRows,
                    manufacturerRows = manufacturerRows,
                    systemRows = systemRows,
                    rackTitleRow = rackTitle,
                    rackHeaderRow = rackHeader,
                    rackFirstRow = rackFirst,
                    typeTitleRow = typeTitle,
                    typeHeaderRow = typeHeader,
                    typeFirstRow = typeFirst,
                    manufacturerTitleRow = manufacturerTitle,
                    manufacturerHeaderRow = manufacturerHeader,
                    manufacturerFirstRow = manufacturerFirst,
                    systemTitleRow = systemTitle,
                    systemHeaderRow = systemHeader,
                    systemFirstRow = systemFirst,
                    planTitleRow = planTitle,
                    planFirstRow = planTitle + 1,
                )
            }
        }
    }

    private data class CommonFields(
        val physicalLocation: String,
        val cabinetPower: String,
        val powerType: String,
    ) {
        companion object {
            fun from(racks: List<Rack>): CommonFields = CommonFields(
                physicalLocation = common(racks.map { it.metadata.physicalLocation }),
                cabinetPower = common(racks.map { it.metadata.cabinetPower }),
                powerType = common(racks.map { it.metadata.powerType }),
            )

            private fun common(values: List<String>): String = values.map(String::trim).filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty()
        }
    }
}
