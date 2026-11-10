package com.beemind.personal.domain

import java.time.*

/** One row per note; generation invalidates every previously issued Android intent. */
data class Reminder(val noteId: String, val dueAt: Long, val status: String,
    val generation: String, val delivery: String = "PENDING", val deliveredAt: Long? = null,
    val scheduling: String = "PENDING") {
    val active get() = status == "SCHEDULED" || status == "SNOOZED"
}

/** Edited=false preserves concurrent notification actions when only text is saved. */
data class ReminderEdit(val edited: Boolean = false, val dueAt: Long? = null)
class ReminderTimeException : IllegalArgumentException("Choose a future, unambiguous local time")
object ReminderTimes {
    fun relative(reference: Instant, minutes: Long): Long {
        if (minutes !in 1..525600) throw ReminderTimeException()
        return reference.plusSeconds(Math.multiplyExact(minutes, 60)).toEpochMilli()
    }
    fun local(value: LocalDateTime, zone: ZoneId, now: Instant): Long {
        val offsets = zone.rules.getValidOffsets(value)
        // Reject DST gaps and overlaps explicitly; never silently shift the chosen wall time.
        if (offsets.size != 1) throw ReminderTimeException()
        return future(value.toInstant(offsets.single()).toEpochMilli(), now)
    }
    fun future(value: Long, now: Instant): Long {
        if (value <= now.toEpochMilli()) throw ReminderTimeException()
        return value
    }
}
