package com.beemind.personal.data.local

import com.beemind.personal.domain.ExpressiveTrait
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.beemind.personal.domain.DraftSummary
import com.beemind.personal.domain.NoteSummary
import kotlinx.coroutines.flow.Flow

internal const val NOTE_SUMMARY_COLUMNS = "n.id, CASE WHEN n.unlockAt > :now THEN NULL ELSE substr(n.title, 1, 160) END AS title, CASE WHEN n.unlockAt > :now THEN '' ELSE substr(n.body, 1, 420) END AS preview, n.createdAt, n.updatedAt, n.expressiveTrait, n.resurfaceAt, n.resurfaceSeenAt, n.unlockAt, CASE WHEN n.unlockAt > :now THEN 1 ELSE 0 END AS locked, CASE WHEN n.resurfaceAt <= :now AND n.resurfaceSeenAt IS NULL THEN 1 ELSE 0 END AS resurfaced, CASE WHEN r.status IN ('SCHEDULED','SNOOZED') THEN r.dueAt ELSE NULL END AS reminderDueAt"

@Dao
interface NotesDao {
    // Bounded projection keeps full diaries out of the home screen's memory/layout work.
    @Query("SELECT " + NOTE_SUMMARY_COLUMNS + " FROM notes n LEFT JOIN reminders r ON r.noteId = n.id ORDER BY resurfaced DESC, CASE WHEN n.resurfaceAt <= :now AND n.resurfaceSeenAt IS NULL THEN n.resurfaceAt ELSE NULL END DESC, n.createdAt DESC, n.id ASC")
    fun observeNotes(now: Long): Flow<List<NoteSummary>>

    @Query("SELECT id, createdAt, updatedAt, expressiveTrait, resurfaceAt, resurfaceSeenAt, unlockAt, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM notes WHERE id = :id")
    fun observeHeader(id: String): Flow<NoteHeader?>

    @Query("SELECT id, createdAt, updatedAt, expressiveTrait, resurfaceAt, resurfaceSeenAt, unlockAt, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM notes WHERE id = :id")
    suspend fun getHeader(id: String): NoteHeader?

    @Query("SELECT substr(CAST(body AS BLOB), :offset, :count) FROM notes WHERE id = :id")
    suspend fun readBodyChunk(id: String, offset: Int, count: Int): ByteArray?

    @Query("SELECT substr(CAST(title AS BLOB), :offset, :count) FROM notes WHERE id = :id")
    suspend fun readTitleChunk(id: String, offset: Int, count: Int): ByteArray?

    @Query("SELECT EXISTS(SELECT 1 FROM notes WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert
    suspend fun insert(note: NoteEntity)

    // No update statement ever writes createdAt or replaces the note row.
    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt, expressiveTrait = :expressiveTrait WHERE id = :id")
    suspend fun updateContent(id: String, title: String?, body: String, updatedAt: Long, expressiveTrait: ExpressiveTrait)

    @Query("UPDATE notes SET resurfaceAt = :due, resurfaceSeenAt = NULL WHERE id = :id")
    suspend fun reschedule(id: String, due: Long?)

    @Query("UPDATE notes SET unlockAt = :until WHERE id = :id")
    suspend fun seal(id: String, until: Long)

    @Query("UPDATE notes SET resurfaceSeenAt = :now WHERE id = :id AND resurfaceAt <= :now AND resurfaceSeenAt IS NULL AND (unlockAt IS NULL OR unlockAt <= :now)")
    suspend fun markOpened(id: String, now: Long)

    @Query("SELECT resurfaceAt AS at FROM notes WHERE resurfaceSeenAt IS NULL AND resurfaceAt IS NOT NULL UNION ALL SELECT unlockAt AS at FROM notes WHERE unlockAt IS NOT NULL")
    fun boundaries(): Flow<List<Long>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT sessionId, noteId, editedAt, expressiveTrait, reminderEdited, reminderDueAt, resurfaceEdited, resurfaceAt, sealUntil, length(CAST(title AS BLOB)) AS titleBytes, length(CAST(body AS BLOB)) AS bodyBytes FROM editor_drafts WHERE sessionId = :sessionId")
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
