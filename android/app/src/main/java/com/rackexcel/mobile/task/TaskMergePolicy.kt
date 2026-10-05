package com.rackexcel.mobile.task

/**
 * Replaces retried records in their original position so a partial retry never
 * discards the already confirmed part of a field task.
 */
object TaskMergePolicy {
    fun <T> replaceByKey(
        current: List<T>,
        replacements: List<T>,
        key: (T) -> String,
    ): List<T> {
        if (replacements.isEmpty()) return current
        val replacementByKey = replacements.associateBy(key)
        val merged = current.map { item -> replacementByKey[key(item)] ?: item }.toMutableList()
        val currentKeys = current.mapTo(mutableSetOf(), key)
        replacements.filter { key(it) !in currentKeys }.forEach(merged::add)
        return merged
    }
}
