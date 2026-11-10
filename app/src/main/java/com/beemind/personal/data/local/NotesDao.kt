package com.beemind.personal.data.local

import com.beemind.personal.domain.ExpressiveTrait
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.beemind.personal.domain.DraftSummary
import com.beemind.personal.domain.NoteSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    // Bounded projection keeps full diaries out of the home screen's memory/layout work.
    @Query("SELECT n.id, substr(n.title, 1, 160) AS title, substr(n.body, 1, 420) AS preview, n.createdAt, n.updatedAt, n.expressiveTrait, CASE WHEN r.status IN ('SCHEDULED','SNOOZED') THEN r.dueAt ELSE NULL END AS reminderDueAt FROM notes n LEFT JOIN reminders r ON r.noteId = n.id ORDER BY n.createdAt DESC, n.id ASC")
    fun observeNotes(): Flow<List<NoteSummary>>

    @Query("SELECT id, createdAt, updatedAt, expressiveTrait, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM notes WHERE id = :id")
    fun observeHeader(id: String): Flow<NoteHeader?>

    @Query("SELECT id, createdAt, updatedAt, expressiveTrait, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM notes WHERE id = :id")
    suspend fun getHeader(id: String): NoteHeader?

    @Query("SELECT substr(CAST(body AS BLOB), :offset, :count) FROM notes WHERE id = :id")
    suspend fun readBodyChunk(id: String, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(title AS BLOB), :offset, :count) FROM notes WHERE id = :id")
    suspend fun readTitleChunk(id: String, offset: Int, count: Int): ByteArray?

    @Insert
    suspend fun insert(note: NoteEntity)

    // No update statement ever writes createdAt or replaces the note row.
    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt, expressiveTrait = :expressiveTrait WHERE id = :id")
    suspend fun updateContent(id: String, title: String?, body: String, updatedAt: Long, expressiveTrait: ExpressiveTrait)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT sessionId, noteId, editedAt, expressiveTrait, reminderEdited, reminderDueAt, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM editor_drafts WHERE sessionId = :sessionId")
    suspend fun getDraftHeader(sessionId: String): DraftHeader?

    @Query("SELECT substr(CAST(body AS BLOB), :offset, :count) FROM editor_drafts WHERE sessionId = :sessionId")
    suspend fun readDraftBodyChunk(sessionId: String, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(title AS BLOB), :offset, :count) FROM editor_drafts WHERE sessionId = :sessionId")
    suspend fun readDraftTitleChunk(sessionId: String, offset: Int, count: Int): ByteArray?

    @Query("SELECT sessionId, noteId FROM editor_drafts ORDER BY editedAt DESC, sessionId ASC LIMIT 1")
    fun observeLatestDraft(): Flow<DraftSummary?>

    @Query("SELECT sessionId, noteId FROM editor_drafts WHERE noteId = :noteId ORDER BY editedAt DESC, sessionId ASC LIMIT 1")
    suspend fun latestDraftForNote(noteId: String): DraftSummary?

    @Upsert
    suspend fun putDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE sessionId = :sessionId")
    suspend fun discardDraft(sessionId: String)

    @Query("DELETE FROM editor_drafts WHERE noteId = :noteId")
    suspend fun discardNoteDrafts(noteId: String)
}
