package com.rackexcel.mobile.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AlarmReviewNavigationPolicyTest {
    @Test
    fun answer_does_not_auto_advance_and_next_requires_explicit_selection() {
        val current = AlarmRecord("A-1", AlarmDeviceIdentity(assetId = "asset-1"), AlarmSeverity.CRITICAL, 10L, "告警一")
        val next = AlarmRecord("A-2", AlarmDeviceIdentity(assetId = "asset-2"), AlarmSeverity.WARNING, 9L, "告警二")

        assertFalse(AlarmReviewNavigationPolicy.AUTO_ADVANCE_AFTER_DECISION)
        assertEquals("A-2", AlarmReviewNavigationPolicy.nextUnresolvedAlarmId(listOf(current, next), "A-1"))
    }

    @Test
    fun handled_alarm_is_not_selected_as_next_question() {
        val current = AlarmRecord("A-1", AlarmDeviceIdentity(assetId = "asset-1"), AlarmSeverity.CRITICAL, 10L, "告警一")
        val handled = AlarmRecord("A-2", AlarmDeviceIdentity(assetId = "asset-2"), AlarmSeverity.WARNING, 9L, "告警二", status = AlarmStatus.HANDLED)
        assertEquals(null, AlarmReviewNavigationPolicy.nextUnresolvedAlarmId(listOf(current, handled), "A-1"))
    }
}
