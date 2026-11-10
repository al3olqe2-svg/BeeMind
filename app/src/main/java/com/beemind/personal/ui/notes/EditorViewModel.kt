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

/** TextFieldState is retained by this ViewModel, never by rememberSaveable/Bundle. */
data class EditorState(
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
enum class EditorError { EMPTY, SAVE, DRAFT, MISSING }

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
    private var draftJob: Job? = null
    private var inputJob: Job? = null
    private val draftMutex = Mutex()
    private var closed = false
    private var loadingInProgress = false

    init { load() }

    fun load() {
        if (inputJob != null || loadingInProgress) return
        loadingInProgress = true
        mutableState.value = EditorState()
        viewModelScope.launch {
            try {
                val note = noteId?.let { repository.getNote(it) ?: throw NoteMissingException() }
                val draft = repository.getDraft(sessionId)
                if (closed) return@launch
                originalTitle = note?.title.orEmpty()
                originalBody = note?.body.orEmpty()
                title.edit { replace(0, length, draft?.title ?: originalTitle) }
                body.edit { replace(0, length, draft?.body ?: originalBody) }
                mutableState.value = EditorState(loading = false, draftStored = draft != null)
                inputJob = viewModelScope.launch {
                    snapshotFlow { title.text.toString() to body.text.toString() }.collect { (t, b) ->
                        val dirty = t != originalTitle || b != originalBody
                        mutableState.update { it.copy(dirty = dirty, canSave = t.isNotBlank() || b.isNotBlank(), draftStored = false) }
                        draftJob?.cancel()
                        draftJob = launch {
                            delay(250)
                            storeDraft(t, b, dirty)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (closed) return@launch
                mutableState.value = EditorState(loading = false, loadFailed = true,
                    error = if (e is NoteMissingException) EditorError.MISSING else EditorError.SAVE)
            } finally { loadingInProgress = false }
        }
    }

    private suspend fun storeDraft(t: String, b: String, dirty: Boolean) {
        try {
            draftMutex.withLock {
                if (closed || mutableState.value.busy) return
                if (dirty) repository.putDraft(EditorDraft(sessionId, noteId, t, b, System.currentTimeMillis()))
                else repository.discardDraft(sessionId)
                if (t == title.text.toString() && b == body.text.toString()) {
                    mutableState.update { it.copy(draftStored = dirty) }
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutableState.update { it.copy(error = EditorError.DRAFT) } }
    }

    fun persistOnBackground() {
        if (mutableState.value.loading || mutableState.value.loadFailed || closed) return
        draftJob?.cancel()
        val t = title.text.toString()
        val b = body.text.toString()
        draftJob = viewModelScope.launch { storeDraft(t, b, t != originalTitle || b != originalBody) }
    }

    fun save() {
        val current = mutableState.value
        if (current.busy || current.loading || current.loadFailed || closed) return
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
                    repository.save(noteId ?: sessionId, t, b, isNew = noteId == null, sessionId = sessionId)
                }
                closed = true
                mutableState.update { it.copy(busy = false, dirty = false, savedId = saved.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(busy = false, error = if (e is NoteMissingException) EditorError.MISSING else EditorError.SAVE) }
                persistOnBackground()
            }
        }
    }

    fun requestExit() {
        if (mutableState.value.busy || closed) return
        if (mutableState.value.loading || mutableState.value.loadFailed) {
            // Unknown text must never be interpreted as an empty draft to discard.
            closed = true
            mutableState.update { it.copy(exited = true) }
            return
        }
        // Read the live fields so a back press in the same frame as an edit cannot lose text.
        if (title.text.toString() != originalTitle || body.text.toString() != originalBody) {
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
