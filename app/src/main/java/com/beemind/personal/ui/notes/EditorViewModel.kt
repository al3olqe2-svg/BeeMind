package com.beemind.personal.ui.notes

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beemind.personal.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class EditorInput(val title: String, val body: String, val trait: ExpressiveTrait,
    val reminder: ReminderEdit, val temporal: TemporalEdit)

data class EditorState(
    val temporalEdit: TemporalEdit = TemporalEdit(), val savedResurfaceAt: Long? = null,
    val lockedUntil: Long? = null, val reminderEdit: ReminderEdit = ReminderEdit(),
    val savedReminder: Reminder? = null, val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
    val loading: Boolean = true, val loadFailed: Boolean = false, val dirty: Boolean = false,
    val canSave: Boolean = false, val busy: Boolean = false, val draftStored: Boolean = false,
    val error: EditorError? = null, val savedId: String? = null, val exited: Boolean = false,
)
enum class EditorError { EMPTY, SAVE, DRAFT, MISSING, REMINDER_TIME }

/** A session UUID is also the new note's ID: retries and process recovery are upserts,
 * never additional notes. All draft/commit writes share one mutex. Text remains editable
 * during the debounced commit; only explicit departure freezes input until Room commits. */
class EditorViewModel(private val repository: NotesRepository, val sessionId: String,
    private val noteId: String?) : ViewModel() {
    // Legacy titles are retained internally, never requested, displayed or generated.
    val title = TextFieldState()
    val body = TextFieldState()
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    private val trait = MutableStateFlow(ExpressiveTrait.NONE)
    private val reminder = MutableStateFlow(ReminderEdit())
    private val temporal = MutableStateFlow(TemporalEdit())
    private val writes = Mutex()
    private var original = EditorInput("", "", ExpressiveTrait.NONE, ReminderEdit(), TemporalEdit())
    private var inputJob: Job? = null
    private var debounce: Job? = null
    private var committed = noteId != null
    private var closed = false
    private var loading = false
    private var lastAutoFailure: EditorInput? = null

    init {
        load()
        viewModelScope.launch {
            repository.observeNote(noteId ?: sessionId).collect { note ->
                if (note != null && !closed) {
                    mutableState.update { it.copy(savedReminder = note.reminder, savedResurfaceAt = note.resurfaceAt) }
                    if (!note.locked && state.value.lockedUntil != null) {
                        closed = true
                        mutableState.update { it.copy(savedId = note.id) }
                    } else if (note.locked && !state.value.busy) {
                        // External locking also wins over a retained editor and its semantics.
                        debounce?.cancel()
                        inputJob?.cancel()
                        writes.withLock {
                            title.edit { replace(0, length, "") }; body.edit { replace(0, length, "") }
                            original = EditorInput("", "", ExpressiveTrait.NONE, ReminderEdit(), TemporalEdit())
                            mutableState.update { it.copy(lockedUntil = note.unlockAt, canSave = false, dirty = false, draftStored = false) }
                        }
                    }
                }
            }
        }
    }
    private fun live() = EditorInput(title.text.toString(), body.text.toString(), trait.value, reminder.value, temporal.value)
    private fun dirty(input: EditorInput) = input != original
    private fun refresh() {
        val value = live()
        mutableState.update { it.copy(dirty = dirty(value), canSave = value.body.isNotBlank(),
            expressiveTrait = value.trait, reminderEdit = value.reminder, temporalEdit = value.temporal) }
    }
    fun load() {
        if (loading || inputJob != null) return
        loading = true
        viewModelScope.launch {
            try {
                val note = repository.getNote(noteId ?: sessionId)
                if (noteId != null && note == null) throw NoteMissingException()
                if (note?.locked == true) {
                    mutableState.value = EditorState(loading = false, lockedUntil = note.unlockAt)
                    return@launch
                }
                committed = note != null
                val draft = repository.getDraft(sessionId)
                original = EditorInput(note?.title.orEmpty(), note?.body.orEmpty(), note?.expressiveTrait ?: ExpressiveTrait.NONE, ReminderEdit(), TemporalEdit())
                title.edit { replace(0, length, draft?.title ?: original.title) }
                body.edit { replace(0, length, draft?.body ?: original.body) }
                trait.value = draft?.expressiveTrait ?: original.trait
                reminder.value = draft?.reminderEdit ?: ReminderEdit()
                temporal.value = draft?.temporalEdit ?: TemporalEdit()
                mutableState.value = EditorState(loading = false, savedReminder = note?.reminder, savedResurfaceAt = note?.resurfaceAt, draftStored = draft != null)
                refresh()
                inputJob = viewModelScope.launch {
                    combine(snapshotFlow { title.text.toString() to body.text.toString() }, trait, reminder, temporal) { text, t, r, p ->
                        EditorInput(text.first, text.second, t, r, p)
                    }.collectLatest { input ->
                        if (closed) return@collectLatest
                        refresh()
                        debounce?.cancel()
                        debounce = launch {
                            delay(200)
                            persistDraft()
                            delay(600)
                            // A capsule closes only on explicit Save/Back/background, after
                            // the date sheet's confirmation. Never hide text mid-sentence.
                            if (input.body.isNotBlank() && input.temporal.sealUntil == null && lastAutoFailure != input) commit(false)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = EditorState(loading = false, loadFailed = true, error = error(e))
            } finally { loading = false }
        }
    }
    private fun error(e: Exception) = when(e) {
        is ReminderTimeException -> EditorError.REMINDER_TIME
        is NoteMissingException -> EditorError.MISSING
        else -> EditorError.SAVE
    }
    private suspend fun draftLocked() {
        val input = live()
        if (dirty(input)) repository.putDraft(EditorDraft(sessionId, (noteId ?: sessionId).takeIf { committed },
            input.title, input.body, System.currentTimeMillis(), input.trait, input.reminder, input.temporal))
        else repository.discardDraft(sessionId)
        mutableState.update { it.copy(draftStored = dirty(input)) }
    }
    private suspend fun persistDraft() {
        try { writes.withLock { if (!closed && state.value.lockedUntil == null) draftLocked() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.update { it.copy(error = EditorError.DRAFT) } }
    }
    fun selectTrait(value: ExpressiveTrait) { if (editable()) { trait.value = value; refresh() } }
    fun selectReminder(dueAt: Long?) { if (editable()) { reminder.value = ReminderEdit(true, dueAt); refresh() } }
    fun selectResurface(dueAt: Long?) { if (editable()) { temporal.value = temporal.value.copy(resurfaceEdited = true, resurfaceAt = dueAt); refresh() } }
    fun selectCapsule(dueAt: Long?) { if (editable()) { temporal.value = temporal.value.copy(sealUntil = dueAt); refresh() } }
    private fun editable() = !closed && !state.value.loading && !state.value.loadFailed && !state.value.busy && state.value.lockedUntil == null

    fun persistOnBackground() {
        if (!editable()) return
        debounce?.cancel()
        // Activity recreation retains this VM; process recovery reads the durable Room draft.
        viewModelScope.launch { persistDraft(); commit(false, allowSeal = true) }
    }
    fun save() = depart(manual = true)
    fun requestExit() = depart(manual = false)
    private fun depart(manual: Boolean) {
        if (state.value.busy || closed) return
        if (state.value.loading || state.value.loadFailed || state.value.lockedUntil != null) {
            closed = true
            mutableState.update { it.copy(exited = true) }
            return
        }
        if (manual && body.text.isBlank()) {
            mutableState.update { it.copy(error = EditorError.EMPTY) }; return
        }
        mutableState.update { it.copy(busy = true) }
        debounce?.cancel()
        viewModelScope.launch { commit(true, allowSeal = true, returnHome = manual) }
    }
    private suspend fun commit(leave: Boolean, allowSeal: Boolean = false, returnHome: Boolean = false) {
        try {
            // NonCancellable protects an already-started Room commit from a newer keystroke
            // cancelling debounce. Serialization prevents a stale draft overwriting a save.
            withContext(NonCancellable) { writes.withLock {
                if (closed || state.value.lockedUntil != null) return@withLock
                val input = live()
                draftLocked() // Recovery is durable even when validation/commit fails.
                if (input.body.isBlank()) {
                    // An existing note temporarily cleared is retained; keep its empty draft.
                    if (leave) {
                        if (!committed) repository.discardDraft(sessionId)
                        closed = true; mutableState.update { it.copy(busy = false, exited = true) }
                    }
                    return@withLock
                }
                if (!dirty(input)) {
                    if (leave) { closed = true; mutableState.update { it.copy(busy = false, savedId = (noteId ?: sessionId).takeIf { returnHome }, exited = !returnHome) } }
                    return@withLock
                }
                if (input.temporal.sealUntil != null && !allowSeal) return@withLock
                val saved = repository.save(noteId ?: sessionId, input.title, input.body, !committed, sessionId,
                    input.trait, input.reminder, input.temporal)
                committed = true
                original = input.copy(reminder = ReminderEdit(), temporal = TemporalEdit())
                if (reminder.value == input.reminder) reminder.value = ReminderEdit()
                if (temporal.value == input.temporal) temporal.value = TemporalEdit()
                lastAutoFailure = null
                mutableState.update { it.copy(savedReminder = saved.reminder, savedResurfaceAt = saved.resurfaceAt, error = null) }
                if (saved.locked) {
                    closed = true
                    title.edit { replace(0, length, "") }; body.edit { replace(0, length, "") }
                    original = EditorInput("", "", ExpressiveTrait.NONE, ReminderEdit(), TemporalEdit())
                    mutableState.update { it.copy(busy = false, lockedUntil = saved.unlockAt, savedId = saved.id) }
                } else if (leave) {
                    closed = true
                    mutableState.update { it.copy(busy = false, dirty = false, savedId = saved.id.takeIf { returnHome }, exited = !returnHome) }
                } else { refresh(); draftLocked() }
            } }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            lastAutoFailure = live()
            mutableState.update { it.copy(busy = false, error = error(e)) }
            // Keep live input and the recovery draft. Never navigate on failure.
        }
    }
    fun clearError() { mutableState.update { it.copy(error = null) } }
}
