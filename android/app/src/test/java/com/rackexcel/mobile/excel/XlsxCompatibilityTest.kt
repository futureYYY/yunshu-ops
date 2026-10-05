package com.rackexcel.mobile.excel

import com.rackexcel.mobile.model.AnalysisResult
import com.rackexcel.mobile.model.Rack
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XlsxCompatibilityTest {
    @Test
    fun content_types_and_workbook_relationships_reference_the_three_target_worksheets() {
        val task = ExportTask(
            taskId = "TASK-1",
            roomName = "",
            createdAtMillis = 0,
            modelName = "model",
            promptName = "prompt",
            promptVersion = "v1",
            promptContent = "",
            requestedConcurrency = 5,
            effectiveConcurrency = 1,
            outcomes = emptyList(),
            racks = listOf(Rack("K01", "K01.jpg", emptyList(), emptyList())),
            analysis = AnalysisResult(emptyList(), emptyList()),
        )
        val entries = unzip(XlsxWriter.bytes(task))

        assertTrue(entries.getValue("[Content_Types].xml").contains("sheet3.xml"))
        assertTrue(entries.getValue("xl/_rels/workbook.xml.rels").contains("worksheets/sheet3.xml"))
        assertFalse(entries.getValue("[Content_Types].xml").contains("sheet4.xml"))
        assertTrue(entries.getValue("xl/worksheets/sheet3.xml").contains("六、优化建议与行动计划"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("orientation=\"landscape\""))
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
