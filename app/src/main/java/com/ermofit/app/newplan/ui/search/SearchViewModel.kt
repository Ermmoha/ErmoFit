package com.ermofit.app.newplan.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val history: List<String> = emptyList(),
    val trainings: List<TrainingEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NewPlanRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    private val trainingsFlow = queryFlow.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else repository.searchTrainings(query)
    }

    private val exercisesFlow = queryFlow.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else repository.searchExercises(query)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        queryFlow,
        repository.observeSearchHistory(),
        trainingsFlow,
        exercisesFlow
    ) { query, history, trainings, exercises ->
        SearchUiState(
            query = query,
            history = history,
            trainings = trainings.filterNot { it.isGenerated },
            exercises = exercises
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun setQuery(value: String) {
        queryFlow.value = value
    }

    fun submitQuery(value: String) {
        val query = value.trim()
        if (query.isBlank()) return
        queryFlow.value = query
        viewModelScope.launch {
            repository.addSearchQuery(query)
        }
    }

    fun useHistoryQuery(query: String) {
        submitQuery(query)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }
}

