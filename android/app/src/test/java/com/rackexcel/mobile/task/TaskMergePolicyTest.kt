package com.rackexcel.mobile.task

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskMergePolicyTest {
    @Test
    fun replacement_keeps_prior_successes_and_replaces_only_matching_image() {
        val merged = TaskMergePolicy.replaceByKey(
            current = listOf("image-a:K03", "image-b:FAILED", "image-c:K10"),
            replacements = listOf("image-b:K04"),
            key = { it.substringBefore(':') },
        )

        assertEquals(
            listOf("image-a:K03", "image-b:K04", "image-c:K10"),
            merged,
        )
    }

    @Test
    fun replacement_appends_a_new_image_when_the_original_task_did_not_contain_it() {
        val merged = TaskMergePolicy.replaceByKey(
            current = listOf("image-a:K03"),
            replacements = listOf("image-b:K04"),
            key = { it.substringBefore(':') },
        )

        assertEquals(listOf("image-a:K03", "image-b:K04"), merged)
    }
}
