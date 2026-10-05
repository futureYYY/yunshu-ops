package com.rackexcel.mobile.storage

/**
 * A workbook is durable as soon as its private file and matching history row
 * have both been written. A session snapshot taken during the delivery screen
 * can be older, so recovery must prefer that durable record.
 */
object TaskSessionRecoveryPolicy {
    fun preferDurableDelivery(
        sessionItem: TaskHistoryItem,
        persistedItems: List<TaskHistoryItem>,
    ): TaskHistoryItem {
        val durable = persistedItems.firstOrNull { item ->
            item.taskId == sessionItem.taskId &&
                !item.resultName.isNullOrBlank() &&
                !item.resultUri.isNullOrBlank()
        }
        return durable ?: sessionItem
    }
}
