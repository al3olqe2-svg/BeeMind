package com.beemind.personal.domain

/** The stable note ID is also the future foreign-key target of independent trait records. */
data class Note(val id: String, val title: String?, val body: String, val createdAt: Long, val updatedAt: Long, val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE)

data class NoteSummary(val id: String, val title: String?, val preview: String, val createdAt: Long, val updatedAt: Long, val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE)

data class EditorDraft(
    val sessionId: String,
    val noteId: String?,
    val title: String,
    val body: String,
    val editedAt: Long,
    val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
)

data class DraftSummary(val sessionId: String, val noteId: String?)

class EmptyNoteException : IllegalArgumentException()
class NoteMissingException : IllegalStateException()
