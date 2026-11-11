package com.beemind.personal.data

import androidx.room.withTransaction
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.data.local.EditorDraftEntity
import com.beemind.personal.data.local.NoteEntity
import com.beemind.personal.domain.*
import java.time.Clock
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import com.beemind.personal.notifications.ReminderGate
import com.beemind.personal.data.local.ReminderEntity
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RoomNotesRepository(private val database: BeeMindDatabase, private val clock: Clock = Clock.systemUTC(),
    private val remindersChanged: suspend (ReminderEntity?) -> Unit = {}) : NotesRepository {
    private val dao = database.notesDao()
    private val refresh = MutableStateFlow(0L)
    override fun refreshTime() { refresh.value += 1 }
    // One timer per active data subscription, never per card. Lifecycle/time broadcasts
    // invalidate immediately; the bound also handles manual clock movement safely.
    private fun times() = combine(refresh, dao.boundaries().distinctUntilChanged()) { _, boundaries -> boundaries }.flatMapLatest { boundaries ->
        flow {
            var previous: Long? = null
            while (true) {
                val now = clock.millis()
                val before = previous
                if (before == null || boundaries.any { (before >= it) != (now >= it) }) emit(now)
                previous = now
                val next = boundaries.asSequence().filter { it > now }.minOrNull()
                delay(next?.let { (it - clock.millis()).coerceIn(1, 30_000) } ?: 30_000)
            }
        }
    }
    override fun observeThreadNotes(id: String, linkedOnly: Boolean) = times()
        .flatMapLatest { if (linkedOnly) database.noteLinksDao().observeLinked(id, it) else database.noteLinksDao().observeCandidates(id, it) }.distinctUntilChanged()
    override suspend fun setLinked(a: String, b: String, linked: Boolean) = database.withTransaction {
        require(a != b) { "A note cannot link to itself" }
        if (!dao.exists(a) || !dao.exists(b)) throw NoteMissingException()
        if (linked) database.noteLinksDao().connect(a, b) else database.noteLinksDao().disconnect(a, b)
    }
    override fun observeNotes() = times().flatMapLatest { dao.observeNotes(it) }.distinctUntilChanged()
    override suspend fun scheduleResurface(id: String, dueAt: Long?) = database.withTransaction {
        if (dao.getHeader(id) == null) throw NoteMissingException()
        dueAt?.let { ReminderTimes.future(it, clock.instant()) }
        dao.reschedule(id, dueAt)
    }
    override suspend fun scheduleReminder(id: String, dueAt: Long?) = ReminderGate.mutex.withLock {
        val previous = database.reminderDao().get(id)
        database.withTransaction {
            if (dao.getHeader(id) == null) throw NoteMissingException()
            dueAt?.let { ReminderTimes.future(it, clock.instant()) }
            if (dueAt != null || previous != null) database.reminderDao().put(ReminderEntity(id,
                dueAt ?: previous!!.dueAt, if (dueAt == null) "CANCELLED" else "SCHEDULED",
                UUID.randomUUID().toString(), "PENDING", null, "PENDING"))
        }
        remindersChanged(previous)
    }
    override suspend fun opened(id: String) { dao.markOpened(id, clock.millis()) }

    override fun observeNote(id: String) = combine(dao.observeHeader(id), database.reminderDao().observe(id), times()) { header, _, _ -> if (header == null) null else getNote(id) }
    override fun observeLatestDraft() = dao.observeLatestDraft()
    override suspend fun getNote(id: String): Note? = database.withTransaction {
        val header = dao.getHeader(id) ?: return@withTransaction null
        val locked = TemporalRules.locked(header.unlockAt, clock.millis())
        if (locked) return@withTransaction Note(header.id, null, "", header.createdAt, header.updatedAt,
            header.expressiveTrait, database.reminderDao().get(id)?.toReminder(), header.resurfaceAt,
            header.resurfaceSeenAt, header.unlockAt, true)
        val title = header.titleBytes?.let { readText(it) { offset -> checkNotNull(dao.readTitleChunk(id, offset, TEXT_CHUNK)) } }
        val body = readText(header.bodyBytes) { offset -> checkNotNull(dao.readBodyChunk(id, offset, TEXT_CHUNK)) }
        Note(header.id, title, body, header.createdAt, header.updatedAt, header.expressiveTrait, database.reminderDao().get(id)?.toReminder(), header.resurfaceAt, header.resurfaceSeenAt, header.unlockAt)
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
        header.noteId?.let { id -> if (TemporalRules.locked(dao.getHeader(id)?.unlockAt, clock.millis())) throw CapsuleLockedException() }
        val title = readText(header.titleBytes) { offset -> checkNotNull(dao.readDraftTitleChunk(sessionId, offset, TEXT_CHUNK)) }
        val body = readText(header.bodyBytes) { offset -> checkNotNull(dao.readDraftBodyChunk(sessionId, offset, TEXT_CHUNK)) }
        EditorDraft(header.sessionId, header.noteId, title, body, header.editedAt, header.expressiveTrait, ReminderEdit(header.reminderEdited, header.reminderDueAt), TemporalEdit(header.resurfaceEdited, header.resurfaceAt, header.sealUntil))
    }
    override suspend fun latestDraftForNote(noteId: String) = dao.latestDraftForNote(noteId)
    override suspend fun putDraft(draft: EditorDraft) {
        database.withTransaction {
        draft.noteId?.let { if (TemporalRules.locked(dao.getHeader(it)?.unlockAt, clock.millis())) throw CapsuleLockedException() }
        dao.putDraft(EditorDraftEntity(draft.sessionId, draft.noteId, draft.title, draft.body, draft.editedAt, draft.expressiveTrait, draft.reminderEdit.edited, draft.reminderEdit.dueAt, draft.temporalEdit.resurfaceEdited, draft.temporalEdit.resurfaceAt, draft.temporalEdit.sealUntil))
        }
    }
    override suspend fun discardDraft(sessionId: String) = dao.discardDraft(sessionId)

    override suspend fun save(id: String, title: String, body: String, isNew: Boolean, sessionId: String, expressiveTrait: ExpressiveTrait, reminderEdit: ReminderEdit, temporalEdit: TemporalEdit): Note = ReminderGate.mutex.withLock {
        if (title.isBlank() && body.isBlank()) throw EmptyNoteException()
        val normalizedTitle = title.trim().ifEmpty { null }
        val previous = database.reminderDao().get(id)
        val result = database.withTransaction {
            if (reminderEdit.edited) reminderEdit.dueAt?.let { ReminderTimes.future(it, clock.instant()) }
            if (temporalEdit.resurfaceEdited) temporalEdit.resurfaceAt?.let { ReminderTimes.future(it, clock.instant()) }
            temporalEdit.sealUntil?.let { ReminderTimes.future(it, clock.instant()) }
            val old = getNote(id)
            if (old?.locked == true) throw CapsuleLockedException()
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
            if (temporalEdit.resurfaceEdited) {
                temporalEdit.resurfaceAt?.let { ReminderTimes.future(it, clock.instant()) }
                dao.reschedule(id, temporalEdit.resurfaceAt)
            }
            temporalEdit.sealUntil?.let { ReminderTimes.future(it, clock.instant()) }
            temporalEdit.sealUntil?.let { dao.seal(id, it); dao.discardNoteDrafts(id) }
            dao.discardDraft(sessionId)
            // Read the safe representation after all changes, within the same transaction.
            checkNotNull(getNote(saved.id))
        }
        // Room has committed. Android failures must never masquerade as a failed note save.
        // Text autosaves must not reconcile every alarm on every typing pause.
        if (reminderEdit.edited || temporalEdit.sealUntil != null)
            remindersChanged(if (reminderEdit.edited) previous else null)
        result
    }

    private companion object { const val TEXT_CHUNK = 65_536 }

    override suspend fun delete(id: String) = ReminderGate.mutex.withLock {
        val previous = database.reminderDao().get(id)
        database.withTransaction {
            dao.discardNoteDrafts(id)
            dao.delete(id)
            database.foundationDao().deleteValue(com.beemind.personal.notifications.ReminderDiagnostics.key(id))
        }
        remindersChanged(previous)
    }
}
