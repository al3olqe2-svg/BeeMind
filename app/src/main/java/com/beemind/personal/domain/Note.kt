package com.beemind.personal.domain

/** Identity and original creation time remain stable across temporal trait cycles. */
data class Note(val id: String, val title: String?, val body: String, val createdAt: Long, val updatedAt: Long, val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE, val reminder: Reminder? = null, val resurfaceAt: Long? = null, val resurfaceSeenAt: Long? = null, val unlockAt: Long? = null, val locked: Boolean = false)

data class NoteSummary(val id: String, val title: String?, val preview: String, val createdAt: Long, val updatedAt: Long, val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE, val reminderDueAt: Long? = null, val resurfaceAt: Long? = null, val resurfaceSeenAt: Long? = null, val unlockAt: Long? = null, val locked: Boolean = false, val resurfaced: Boolean = false)

data class EditorDraft(
    val sessionId: String,
    val noteId: String?,
    val title: String,
    val body: String,
    val editedAt: Long,
    val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
    val reminderEdit: ReminderEdit = ReminderEdit(),
    val temporalEdit: TemporalEdit = TemporalEdit(),
)

data class DraftSummary(val sessionId: String, val noteId: String?)

class EmptyNoteException : IllegalArgumentException()
class NoteMissingException : IllegalStateException()

/** Redacted, bounded projection; never full diary text. */
data class ThreadNote(@androidx.room.Embedded val note: NoteSummary, val linked: Boolean)
