package com.ermofit.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.model.ResolvedProgramText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val programs: List<ProgramEntity> = emptyList(),
    val programTexts: Map<String, ResolvedProgramText> = emptyMap(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap(),
    val recentQueries: List<String> = emptyList(),
    val error: String? = null
)

private data class SearchCoreState(
    val query: String,
    val programs: List<ProgramEntity>,
    val programTexts: Map<String, ResolvedProgramText>,
    val exercises: List<ExerciseEntity>
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val localDataRepository: LocalDataRepository,
    private val preferencesManager: UserPreferencesManager,
    private val exerciseTextResolver: ExerciseTextResolver,
    private val programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)

    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { preferredLang, onlyTranslated ->
        val fallbackLang = if (preferredLang == "ru") "en" else "ru"
        Triple(preferredLang, fallbackLang, onlyTranslated)
    }

    private val programsFlow = _query
        .debounce(350)
        .combine(settingsFlow) { query, settings ->
            query.trim() to settings
        }
        .flatMapLatest { (clean, settings) ->
            if (clean.isBlank()) {
                flowOf(emptyList())
            } else {
                localDataRepository.searchPrograms(
                    query = clean,
                    preferredLang = settings.first,
                    fallbackLang = settings.second,
                    onlyTranslated = settings.third
                )
            }
        }

    private val exercisesFlow = _query
        .debounce(350)
        .combine(settingsFlow) { query, settings ->
            query.trim() to settings
        }
        .flatMapLatest { (clean, settings) ->
            if (clean.isBlank()) {
                flowOf(emptyList())
            } else {
                localDataRepository.searchExercisesLocalized(
                    query = clean,
                    preferredLang = settings.first,
                    fallbackLang = settings.second,
                    onlyTranslated = settings.third
                )
            }
        }

    private val exerciseTextsFlow = exerciseTextResolver.observeTextsForExercises(exercisesFlow)
    private val programTextsFlow = programTextResolver.observeTextsForPrograms(programsFlow)

    private val coreBaseFlow = combine(
        _query,
        programsFlow,
        programTextsFlow,
        exercisesFlow
    ) { query, programs, programTexts, exercises ->
        SearchCoreState(
            query = query,
            programs = programs,
            programTexts = programTexts,
            exercises = exercises
        )
    }

    private val coreUiStateFlow = combine(
        coreBaseFlow,
        exerciseTextsFlow
    ) { core, exerciseTexts ->
        SearchUiState(
            query = core.query,
            programs = core.programs,
            programTexts = core.programTexts,
            exercises = core.exercises,
            exerciseTexts = exerciseTexts,
            recentQueries = emptyList()
        )
    }

    private val baseUiState = combine(
        coreUiStateFlow,
        preferencesManager.observeSearchHistory()
    ) { core, history ->
        core.copy(recentQueries = history)
    }

    val uiState: StateFlow<SearchUiState> = combine(baseUiState, _error) { base, error ->
        base.copy(error = error)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SearchUiState()
    )

    fun onQueryChanged(query: String) {
        _query.update { query }
    }

    fun submitQuery() {
        viewModelScope.launch {
            runCatching {
                preferencesManager.addSearchQuery(_query.value)
            }.onFailure { throwable ->
                _error.update { throwable.message.orEmpty() }
            }
        }
    }

    fun applyRecentQuery(query: String) {
        _query.update { query }
        submitQuery()
    }

    fun removeRecentQuery(query: String) {
        viewModelScope.launch {
            preferencesManager.removeSearchQuery(query)
        }
    }

    fun clearRecentQueries() {
        viewModelScope.launch {
            preferencesManager.clearSearchHistory()
        }
    }

    fun clearError() {
        _error.update { null }
    }
}
