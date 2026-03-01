package com.ermofit.app.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.repository.FavoritesRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseDetailsUiState(
    val exercise: ExerciseEntity? = null,
    val resolvedText: ResolvedExerciseText? = null,
    val isFavorite: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localDataRepository: LocalDataRepository,
    private val favoritesRepository: FavoritesRepository,
    private val exerciseTextResolver: ExerciseTextResolver
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])
    private val exerciseFlow = localDataRepository.observeExerciseById(exerciseId)
    private val exerciseTextFlow = exerciseFlow.flatMapLatest { exercise ->
        if (exercise == null) {
            flowOf(null)
        } else {
            exerciseTextResolver.observeTextForExercise(exercise.id)
        }
    }

    val uiState: StateFlow<ExerciseDetailsUiState> = combine(
        exerciseFlow,
        exerciseTextFlow,
        favoritesRepository.observeFavoriteExerciseIds()
    ) { exercise, resolvedText, favoriteIds ->
        ExerciseDetailsUiState(
            exercise = exercise,
            resolvedText = resolvedText,
            isFavorite = favoriteIds.contains(exerciseId)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExerciseDetailsUiState()
    )

    fun toggleFavorite() {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.toggleFavoriteExercise(exerciseId)
            }
        }
    }
}
