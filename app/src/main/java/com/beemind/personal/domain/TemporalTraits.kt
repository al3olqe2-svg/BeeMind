package com.beemind.personal.domain

/** Each reschedule clears seenAt, including rescheduling to the same instant. */
data class TemporalEdit(val resurfaceEdited: Boolean = false, val resurfaceAt: Long? = null, val sealUntil: Long? = null)
class CapsuleLockedException : IllegalStateException()
object TemporalRules {
    fun locked(unlockAt: Long?, now: Long) = unlockAt != null && now < unlockAt
    fun due(resurfaceAt: Long?, seenAt: Long?, now: Long) = resurfaceAt != null && resurfaceAt <= now && seenAt == null
}
