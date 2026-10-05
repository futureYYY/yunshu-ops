package com.rackexcel.mobile.alarm

/** One-shot UI notification emitted by the ViewModel when a new alarm arrives. */
data class AlarmUiEffect(
    val sequence: Long,
    val title: String,
    val detail: String,
    val playSound: Boolean,
    val vibrate: Boolean,
)

/**
 * A compact projection used by the Compose rack diagram.  The coordinate is
 * always recalculated from the current Rack list; it is never a stale device
 * index cached across review edits.
 */
data class RackAlarmHighlight(
    val alarmId: String,
    val severity: AlarmSeverity,
    val coordinate: AlarmRackCoordinate,
    val count: Int = 1,
)

fun AlarmMatchResult.matchedCoordinateOrNull(): AlarmRackCoordinate? =
    (this as? AlarmMatchResult.Matched)?.coordinate

