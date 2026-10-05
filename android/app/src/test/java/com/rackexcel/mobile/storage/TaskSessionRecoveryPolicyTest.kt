package com.rackexcel.mobile.storage

import com.rackexcel.mobile.excel.ExportSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskSessionRecoveryPolicyTest {
    @Test
    fun durableResultWinsWhenSessionWasCapturedDuringWorkbookDelivery() {
        val sessionItem = item(taskId = "TASK-1", resultName = null, resultUri = null)
        val durableItem = item(
            taskId = "TASK-1",
            resultName = "A机房_202608201600.xlsx",
            resultUri = "content://com.rackexcel.mobile.files/external_files/Documents/result.xlsx",
        )

        val restored = TaskSessionRecoveryPolicy.preferDurableDelivery(sessionItem, listOf(durableItem))

        assertEquals(durableItem.resultName, restored.resultName)
        assertEquals(durableItem.resultUri, restored.resultUri)
    }

    @Test
    fun incompleteHistoryDoesNotReplaceTheActiveSession() {
        val sessionItem = item(taskId = "TASK-1", resultName = null, resultUri = null)
        val incompleteStored = item(taskId = "TASK-1", resultName = "only-name.xlsx", resultUri = null)

        val restored = TaskSessionRecoveryPolicy.preferDurableDelivery(sessionItem, listOf(incompleteStored))

        assertNull(restored.resultName)
        assertNull(restored.resultUri)
    }

    private fun item(taskId: String, resultName: String?, resultUri: String?) = TaskHistoryItem(
        taskId = taskId,
        roomName = "测试机房",
        createdAtMillis = 1_000L,
        cabinetIds = listOf("K01"),
        summary = ExportSummary(1, 1, 0, 0),
        resultName = resultName,
        resultUri = resultUri,
    )
}
