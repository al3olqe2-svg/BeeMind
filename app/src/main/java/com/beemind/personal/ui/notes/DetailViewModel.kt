package com.beemind.personal.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beemind.personal.domain.Note
import com.beemind.personal.domain.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DetailContent {
    data object Loading : DetailContent
    data class Ready(val note: Note?) : DetailContent
    data object Error : DetailContent
}

data class DeleteState(val confirming: Boolean = false, val busy: Boolean = false, val deleted: Boolean = false, val error: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(private val repository: NotesRepository, private val id: String) : ViewModel() {
    private val threadReload = MutableStateFlow(0)
    fun retryThreads() { threadReload.value += 1 }
    private val pickerOpen = MutableStateFlow(false)
    private fun threadFlow(linkedOnly: Boolean): Flow<ThreadContent> = threadReload.flatMapLatest {
        repository.observeThreadNotes(id, linkedOnly).map { ThreadContent.Ready(it) as ThreadContent }
            .onStart { emit(ThreadContent.Loading) }.catch { emit(ThreadContent.Error) }
    }
    val linkedNotes = threadFlow(true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ThreadContent.Loading)
    val candidates = pickerOpen.flatMapLatest { if (it) threadFlow(false) else flowOf(ThreadContent.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ThreadContent.Loading)
    fun showThreadPicker(open: Boolean) { pickerOpen.value = open }
    private val mutableLinkBusy = MutableStateFlow<Set<String>>(emptySet())
    val linkBusy = mutableLinkBusy.asStateFlow()
    private val mutableLinkError = MutableStateFlow(false)
    val linkError = mutableLinkError.asStateFlow()
    fun setLinked(other: String, linked: Boolean) {
        if (other in mutableLinkBusy.value) return
        mutableLinkBusy.update { it + other }
        viewModelScope.launch {
            try { repository.setLinked(id, other, linked); mutableLinkError.value = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableLinkError.value = true }
            finally { mutableLinkBusy.update { it - other } }
        }
    }
    private val reload = MutableStateFlow(0)
    val content = reload.flatMapLatest {
        repository.observeNote(id).map { DetailContent.Ready(it) as DetailContent }
            .onStart { emit(DetailContent.Loading) }.catch { emit(DetailContent.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), DetailContent.Loading)
    private val mutableDelete = MutableStateFlow(DeleteState())
    val deletion = mutableDelete.asStateFlow()
    private val mutableTemporalError = MutableStateFlow(false)
    val temporalError = mutableTemporalError.asStateFlow()
    fun scheduleResurface(dueAt: Long?) { viewModelScope.launch {
        try { repository.scheduleResurface(id, dueAt); mutableTemporalError.value = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableTemporalError.value = true }
    } }
    fun readableOpened() { viewModelScope.launch {
        try { repository.opened(id); mutableTemporalError.value = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableTemporalError.value = true }
    } }
    fun retry() { reload.value += 1 }
    fun confirmDelete(show: Boolean) {
        if (!mutableDelete.value.busy) mutableDelete.update { it.copy(confirming = show, error = false) }
    }
    fun delete() {
        if (mutableDelete.value.busy) return
        mutableDelete.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            try {
                repository.delete(id)
                mutableDelete.value = DeleteState(deleted = true)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableDelete.value = DeleteState(confirming = true, error = true)
            }
        }
    }
}

sealed interface ThreadContent {
    data object Loading : ThreadContent
    data object Error : ThreadContent
    data class Ready(val notes: List<com.beemind.personal.domain.ThreadNote>) : ThreadContent
}
