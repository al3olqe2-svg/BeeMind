package com.beemind.personal.ui.notes

import com.beemind.personal.domain.ReminderTimes
import java.time.*

/** Exactly one level is visible. Temporary dates never enter TemporalEdit before confirmation. */
enum class ToolExpansionState {
    Closed, Primary, Expressive, Reminder, Resurface, Capsule;
    val temporal get() = this in setOf(Reminder, Resurface, Capsule)
    fun back() = if (this == Primary) Closed else Primary
}

/** One selection session, anchored on its first +5 tap. No independent scheduling rules. */
data class AppointmentDraft(
    val initial: Long?, val due: Long? = initial, val reference: Instant? = null,
    val minutes: Int = 0, val error: String? = null, val localValue: LocalDateTime? = null,
) {
    fun plusFive(now: Instant): AppointmentDraft {
        val anchor = reference ?: now
        val count = (minutes + 5).coerceAtMost(525600)
        return copy(due = ReminderTimes.relative(anchor, count.toLong()), reference = anchor, minutes = count, error = null, localValue = null)
    }
    fun local(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): AppointmentDraft =
        try { copy(due = ReminderTimes.local(LocalDateTime.of(date, time), zone, now), reference = null, minutes = 0, error = null, localValue = LocalDateTime.of(date, time)) }
        catch (_: com.beemind.personal.domain.ReminderTimeException) {
            copy(localValue = LocalDateTime.of(date, time), reference = null, minutes = 0, error = "الوقت ماضٍ، أو غير موجود/ملتبس بسبب التوقيت الصيفي. اختر وقتًا آخر.")
        }
    fun valid(now: Instant) = error == null && due?.let { runCatching { ReminderTimes.future(it, now) }.isSuccess } == true
}
