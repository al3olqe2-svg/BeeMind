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

private data class EditorInput(val title: String, val body: String, val trait: ExpressiveTrait, val reminder: ReminderEdit, val temporal: TemporalEdit)

/** TextFieldState is retained by this ViewModel, never by rememberSaveable/Bundle. */
data class EditorState(
    val temporalEdit: TemporalEdit = TemporalEdit(),
    val savedResurfaceAt: Long? = null,
    val lockedUntil: Long? = null,
    val reminderEdit: ReminderEdit = ReminderEdit(),
    val savedReminder: Reminder? = null,
    val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val dirty: Boolean = false,
    val canSave: Boolean = false,
    val busy: Boolean = false,
    val draftStored: Boolean = false,
    val confirmExit: Boolean = false,
    val error: EditorError? = null,
    val savedId: String? = null,
    val exited: Boolean = false,
)
enum class EditorError { EMPTY, SAVE, DRAFT, MISSING, REMINDER_TIME }

class EditorViewModel(
    private val repository: NotesRepository,
    val sessionId: String,
    private val noteId: String?,
) : ViewModel() {
    val title = TextFieldState()
    val body = TextFieldState()
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    private var originalTitle = ""
    private var originalBody = ""
    private var originalTrait = ExpressiveTrait.NONE
    private val reminderEdit = MutableStateFlow(ReminderEdit())
    private val temporalEdit = MutableStateFlow(TemporalEdit())
    private val trait = MutableStateFlow(ExpressiveTrait.NONE)
    private var draftJob: Job? = null
    private var inputJob: Job? = null
    private val draftMutex = Mutex()
    private var closed = false
    private var loadingInProgress = false

    init {
        load()
        noteId?.let { id -> viewModelScope.launch {
            repository.observeNote(id).collect { note ->
                if (note?.locked == true && !closed) {
                    draftJob?.cancelAndJoin(); inputJob?.cancelAndJoin()
                    draftMutex.withLock {
                        title.edit { replace(0, length, "") }; body.edit { replace(0, length, "") }
                        originalTitle = ""; originalBody = ""
                        mutableState.update { it.copy(loading = false, lockedUntil = note.unlockAt, dirty = false, canSave = false, draftStored = false) }
                    }
                } else if (note != null && state.value.lockedUntil != null && !closed) {
                    closed = true
                    mutableState.update { it.copy(savedId = id) }
                }
            }
        } }
    }

    fun load() {
        if (inputJob != null || loadingInProgress) return
        loadingInProgress = true
        mutableState.value = EditorState()
        viewModelScope.launch {
            try {
                val note = noteId?.let { repository.getNote(it) ?: throw NoteMissingException() }
                if (note?.locked == true) {
                    mutableState.value = EditorState(loading = false, lockedUntil = note.unlockAt)
                    return@launch
                }
                val draft = repository.getDraft(sessionId)
                if (closed) return@launch
                originalTitle = note?.title.orEmpty()
                originalBody = note?.body.orEmpty()
                originalTrait = note?.expressiveTrait ?: ExpressiveTrait.NONE
                trait.value = draft?.expressiveTrait ?: originalTrait
                reminderEdit.value = draft?.reminderEdit ?: ReminderEdit()
                temporalEdit.value = draft?.temporalEdit ?: TemporalEdit()
                title.edit { replace(0, length, draft?.title ?: originalTitle) }
                body.edit { replace(0, length, draft?.body ?: originalBody) }
                mutableState.value = EditorState(loading = false, draftStored = draft != null, expressiveTrait = trait.value, reminderEdit = reminderEdit.value, savedReminder = note?.reminder, temporalEdit = temporalEdit.value, savedResurfaceAt = note?.resurfaceAt)
                inputJob = viewModelScope.launch {
                    combine(snapshotFlow { title.text.toString() to body.text.toString() }, trait, reminderEdit, temporalEdit) { text, selected, reminder, temporal ->
                        EditorInput(text.first, text.second, selected, reminder, temporal)
                    }.collect { (t, b, selected, reminder, temporal) ->
                        val dirty = t != originalTitle || b != originalBody || selected != originalTrait || reminder.edited || temporal != TemporalEdit()
                        mutableState.update { it.copy(dirty = dirty, canSave = t.isNotBlank() || b.isNotBlank(), draftStored = false) }
                        draftJob?.cancel()
                        draftJob = launch {
                            delay(250)
                            storeDraft(t, b, selected, reminder, temporal, dirty)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (closed) return@launch
                mutableState.value = EditorState(loading = false, loadFailed = true,
                    error = if (e is ReminderTimeException) EditorError.REMINDER_TIME else if (e is NoteMissingException) EditorError.MISSING else EditorError.SAVE)
            } finally { loadingInProgress = false }
        }
    }

    private suspend fun storeDraft(t: String, b: String, selected: ExpressiveTrait, reminder: ReminderEdit, temporal: TemporalEdit, dirty: Boolean) {
        try {
            draftMutex.withLock {
                if (closed || mutableState.value.busy) return
                if (dirty) repository.putDraft(EditorDraft(sessionId, noteId, t, b, System.currentTimeMillis(), selected, reminder, temporal))
                else repository.discardDraft(sessionId)
                if (t == title.text.toString() && b == body.text.toString() && selected == trait.value && reminder == reminderEdit.value && temporal == temporalEdit.value) {
                    mutableState.update { it.copy(draftStored = dirty) }
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutableState.update { it.copy(error = EditorError.DRAFT) } }
    }

    fun persistOnBackground() {
        if (mutableState.value.loading || mutableState.value.loadFailed || mutableState.value.lockedUntil != null || closed) return
        draftJob?.cancel()
        val t = title.text.toString()
        val b = body.text.toString()
        draftJob = viewModelScope.launch { storeDraft(t, b, trait.value, reminderEdit.value, temporalEdit.value, t != originalTitle || b != originalBody || trait.value != originalTrait || reminderEdit.value.edited || temporalEdit.value != TemporalEdit()) }
    }

    fun selectResurface(dueAt: Long?) {
        if (closed || state.value.busy || state.value.lockedUntil != null) return
        temporalEdit.value = temporalEdit.value.copy(resurfaceEdited = true, resurfaceAt = dueAt)
        mutableState.update { it.copy(temporalEdit = temporalEdit.value) }
    }
    fun selectCapsule(dueAt: Long?) {
        if (closed || state.value.busy || state.value.lockedUntil != null) return
        temporalEdit.value = temporalEdit.value.copy(sealUntil = dueAt)
        mutableState.update { it.copy(temporalEdit = temporalEdit.value) }
    }

    fun selectReminder(dueAt: Long?) {
        if (closed || state.value.loading || state.value.loadFailed || state.value.lockedUntil != null || state.value.busy) return
        reminderEdit.value = ReminderEdit(true, dueAt)
        mutableState.update { it.copy(reminderEdit = reminderEdit.value, error = null) }
    }

    fun selectTrait(selected: ExpressiveTrait) {
        if (closed || state.value.loading || state.value.loadFailed || state.value.lockedUntil != null || state.value.busy) return
        trait.value = selected
        mutableState.update { it.copy(expressiveTrait = selected) }
    }

    fun save() {
        val current = mutableState.value
        if (current.busy || current.loading || current.loadFailed || current.lockedUntil != null || closed) return
        val t = title.text.toString()
        val b = body.text.toString()
        if (t.isBlank() && b.isBlank()) {
            mutableState.update { it.copy(error = EditorError.EMPTY) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null, confirmExit = false) }
        viewModelScope.launch {
            try {
                draftJob?.cancelAndJoin()
                val saved = draftMutex.withLock {
                    repository.save(noteId ?: sessionId, t, b, isNew = noteId == null, sessionId = sessionId, expressiveTrait = current.expressiveTrait, reminderEdit = current.reminderEdit, temporalEdit = current.temporalEdit)
                }
                closed = true
                inputJob?.cancel()
                if (saved.locked) {
                    title.edit { replace(0, length, "") }; body.edit { replace(0, length, "") }
                    originalTitle = ""; originalBody = ""
                }
                mutableState.update { it.copy(busy = false, dirty = false, lockedUntil = saved.unlockAt.takeIf { saved.locked }, savedId = saved.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(busy = false, error = if (e is ReminderTimeException) EditorError.REMINDER_TIME else if (e is NoteMissingException) EditorError.MISSING else EditorError.SAVE) }
                persistOnBackground()
            }
        }
    }

    fun requestExit() {
        if (mutableState.value.busy || closed) return
        if (mutableState.value.loading || mutableState.value.loadFailed || mutableState.value.lockedUntil != null) {
            // Unknown text must never be interpreted as an empty draft to discard.
            closed = true
            mutableState.update { it.copy(exited = true) }
            return
        }
        // Read the live fields so a back press in the same frame as an edit cannot lose text.
        if (title.text.toString() != originalTitle || body.text.toString() != originalBody || trait.value != originalTrait || reminderEdit.value.edited || temporalEdit.value != TemporalEdit()) {
            mutableState.update { it.copy(confirmExit = true) }
        } else discardAndExit()
    }
    fun stay() { mutableState.update { it.copy(confirmExit = false) } }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun discardAndExit() {
        if (mutableState.value.busy || closed) return
        mutableState.update { it.copy(busy = true, confirmExit = false) }
        viewModelScope.launch {
            try {
                draftJob?.cancelAndJoin()
                draftMutex.withLock { repository.discardDraft(sessionId); closed = true }
                mutableState.update { it.copy(busy = false, exited = true) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(busy = false, error = EditorError.DRAFT) } }
        }
    }
}
