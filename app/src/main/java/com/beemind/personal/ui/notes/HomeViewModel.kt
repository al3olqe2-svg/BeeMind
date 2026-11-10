package com.beemind.personal.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beemind.personal.domain.DraftSummary
import com.beemind.personal.domain.NoteSummary
import com.beemind.personal.domain.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val notes: List<NoteSummary>, val draft: DraftSummary?) : HomeState
    data object Error : HomeState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repository: NotesRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)
    val state: StateFlow<HomeState> = reload.flatMapLatest {
        combine(repository.observeNotes(), repository.observeLatestDraft()) { notes, draft ->
            HomeState.Ready(notes, draft) as HomeState
        }.onStart { emit(HomeState.Loading) }.catch { emit(HomeState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HomeState.Loading)
    fun retry() { reload.value += 1 }
}
