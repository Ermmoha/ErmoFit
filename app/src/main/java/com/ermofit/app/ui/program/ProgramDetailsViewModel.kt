package com.ermofit.app.ui.program

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import com.ermofit.app.data.repository.FavoritesRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.model.ResolvedProgramText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramDetailsUiState(
    val program: ProgramEntity? = null,
    val resolvedProgramText: ResolvedProgramText? = null,
    val exercises: List<ProgramExerciseWithDetails> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap(),
    val isFavorite: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ProgramDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localDataRepository: LocalDataRepository,
    private val favoritesRepository: FavoritesRepository,
    exerciseTextResolver: ExerciseTextResolver,
    programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val programId: String = checkNotNull(savedStateHandle["programId"])
    private val programFlow = localDataRepository.observeProgramById(programId)
    private val programExercisesFlow = localDataRepository.observeExercisesForProgram(programId)
    private val programTextFlow = programTextResolver.observeTextForProgram(programId)
    private val exerciseTextMapFlow = exerciseTextResolver.observeTextsForProgramExercises(programExercisesFlow)
    private val _error = MutableStateFlow<String?>(null)
    private val favoriteStateFlow = favoritesRepository.observeFavoriteProgramIds()

    private val baseUiState = combine(
        programFlow,
        programTextFlow,
        programExercisesFlow,
        exerciseTextMapFlow,
        favoriteStateFlow
    ) { program, resolvedProgramText, exercises, exerciseTexts, favoriteIds ->
        ProgramDetailsUiState(
            program = program,
            resolvedProgramText = resolvedProgramText,
            exercises = exercises,
            exerciseTexts = exerciseTexts,
            isFavorite = favoriteIds.contains(programId),
            error = null,
            isLoading = false
        )
    }

    val uiState: StateFlow<ProgramDetailsUiState> = combine(baseUiState, _error) { state, error ->
        state.copy(error = error)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProgramDetailsUiState(isLoading = true)
    )

    fun toggleFavorite() {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.toggleFavoriteProgram(programId)
            }.onFailure { throwable ->
                _error.update { throwable.message.orEmpty() }
            }
        }
    }

    fun clearError() {
        _error.update { null }
    }
}
