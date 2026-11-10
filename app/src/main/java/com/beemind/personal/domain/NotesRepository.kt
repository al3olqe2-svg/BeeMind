package com.beemind.personal.domain

import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeNotes(): Flow<List<NoteSummary>>
    fun observeNote(id: String): Flow<Note?>
    fun observeLatestDraft(): Flow<DraftSummary?>
    suspend fun getNote(id: String): Note?
    suspend fun getDraft(sessionId: String): EditorDraft?
    suspend fun latestDraftForNote(noteId: String): DraftSummary?
    suspend fun putDraft(draft: EditorDraft)
    suspend fun discardDraft(sessionId: String)
    suspend fun save(id: String, title: String, body: String, isNew: Boolean, sessionId: String, expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE): Note
    suspend fun delete(id: String)
}
