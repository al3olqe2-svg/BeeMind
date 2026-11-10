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
    private val reload = MutableStateFlow(0)
    val content = reload.flatMapLatest {
        repository.observeNote(id).map { DetailContent.Ready(it) as DetailContent }
            .onStart { emit(DetailContent.Loading) }.catch { emit(DetailContent.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailContent.Loading)
    private val mutableDelete = MutableStateFlow(DeleteState())
    val deletion = mutableDelete.asStateFlow()
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
