package com.beemind.personal.data

import androidx.room.withTransaction
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.data.local.EditorDraftEntity
import com.beemind.personal.data.local.NoteEntity
import com.beemind.personal.domain.*
import java.time.Clock
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import com.beemind.personal.notifications.ReminderGate
import com.beemind.personal.data.local.ReminderEntity
import java.util.UUID

class RoomNotesRepository(private val database: BeeMindDatabase, private val clock: Clock = Clock.systemUTC(),
    private val remindersChanged: suspend (ReminderEntity?) -> Unit = {}) : NotesRepository {
    private val dao = database.notesDao()
    override fun observeNotes() = dao.observeNotes()
    override fun observeNote(id: String) = combine(dao.observeHeader(id), database.reminderDao().observe(id)) { header, _ -> if (header == null) null else getNote(id) }
    override fun observeLatestDraft() = dao.observeLatestDraft()
    override suspend fun getNote(id: String): Note? = database.withTransaction {
        val header = dao.getHeader(id) ?: return@withTransaction null
        val title = header.titleBytes?.let { readText(it) { offset -> checkNotNull(dao.readTitleChunk(id, offset, TEXT_CHUNK)) } }
        val body = readText(header.bodyBytes) { offset -> checkNotNull(dao.readBodyChunk(id, offset, TEXT_CHUNK)) }
        Note(header.id, title, body, header.createdAt, header.updatedAt, header.expressiveTrait, database.reminderDao().get(id)?.toReminder())
    }

    private suspend fun readText(byteLength: Int, chunk: suspend (Int) -> ByteArray): String {
        val text = ByteArrayOutputStream()
        var offset = 1
        while (offset <= byteLength) {
            val bytes = chunk(offset)
            check(bytes.size == minOf(TEXT_CHUNK, byteLength - offset + 1)) { "Incomplete local text read" }
            text.write(bytes)
            offset += TEXT_CHUNK
        }
        return text.toString(StandardCharsets.UTF_8.name())
    }
    override suspend fun getDraft(sessionId: String): EditorDraft? = database.withTransaction {
        val header = dao.getDraftHeader(sessionId) ?: return@withTransaction null
        val title = readText(header.titleBytes) { offset -> checkNotNull(dao.readDraftTitleChunk(sessionId, offset, TEXT_CHUNK)) }
        val body = readText(header.bodyBytes) { offset -> checkNotNull(dao.readDraftBodyChunk(sessionId, offset, TEXT_CHUNK)) }
        EditorDraft(header.sessionId, header.noteId, title, body, header.editedAt, header.expressiveTrait, ReminderEdit(header.reminderEdited, header.reminderDueAt))
    }
    override suspend fun latestDraftForNote(noteId: String) = dao.latestDraftForNote(noteId)
    override suspend fun putDraft(draft: EditorDraft) {
        dao.putDraft(EditorDraftEntity(draft.sessionId, draft.noteId, draft.title, draft.body, draft.editedAt, draft.expressiveTrait, draft.reminderEdit.edited, draft.reminderEdit.dueAt))
    }
    override suspend fun discardDraft(sessionId: String) = dao.discardDraft(sessionId)

    override suspend fun save(id: String, title: String, body: String, isNew: Boolean, sessionId: String, expressiveTrait: ExpressiveTrait, reminderEdit: ReminderEdit): Note = ReminderGate.mutex.withLock {
        if (title.isBlank() && body.isBlank()) throw EmptyNoteException()
        val normalizedTitle = title.trim().ifEmpty { null }
        val previous = database.reminderDao().get(id)
        val result = database.withTransaction {
            if (reminderEdit.edited) reminderEdit.dueAt?.let { ReminderTimes.future(it, clock.instant()) }
            val old = getNote(id)
            val saved = if (old == null) {
                if (!isNew) throw NoteMissingException()
                val now = clock.millis()
                NoteEntity(id, normalizedTitle, body, now, now, expressiveTrait).also { dao.insert(it) }.toNote()
            } else if (old.title == normalizedTitle && old.body == body && old.expressiveTrait == expressiveTrait) {
                old
            } else {
                val edited = maxOf(clock.millis(), old.updatedAt + 1)
                dao.updateContent(id, normalizedTitle, body, edited, expressiveTrait)
                old.copy(title = normalizedTitle, body = body, updatedAt = edited, expressiveTrait = expressiveTrait)
            }
            if (reminderEdit.edited) {
                val due = reminderEdit.dueAt
                if (due != null || previous != null) {
                    database.reminderDao().put(ReminderEntity(id, due ?: previous!!.dueAt,
                        if (due == null) "CANCELLED" else "SCHEDULED", UUID.randomUUID().toString(), "PENDING", null, "PENDING"))
                }
            }
            dao.discardDraft(sessionId)
            saved.copy(reminder = database.reminderDao().get(id)?.toReminder())
        }
        // Room has committed. Android failures must never masquerade as a failed note save.
        remindersChanged(if (reminderEdit.edited) previous else null)
        result
    }

    private companion object { const val TEXT_CHUNK = 65_536 }

    override suspend fun delete(id: String) = ReminderGate.mutex.withLock {
        val previous = database.reminderDao().get(id)
        database.withTransaction {
            dao.discardNoteDrafts(id)
            dao.delete(id)
        }
        remindersChanged(previous)
    }
}
