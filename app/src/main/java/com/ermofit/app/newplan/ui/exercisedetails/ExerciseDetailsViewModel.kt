package com.ermofit.app.newplan.ui.exercisedetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseDetailsUiState(
    val exercise: ExerciseEntity? = null,
    val isFavorite: Boolean = false
)

@HiltViewModel
class ExerciseDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewPlanRepository
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    val uiState: StateFlow<ExerciseDetailsUiState> = combine(
        repository.observeExerciseById(exerciseId),
        repository.observeIsFavorite(exerciseId, NewPlanRepository.TYPE_EXERCISE)
    ) { exercise, isFavorite ->
        ExerciseDetailsUiState(
            exercise = exercise,
            isFavorite = isFavorite
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseDetailsUiState()
    )

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(exerciseId, NewPlanRepository.TYPE_EXERCISE)
        }
    }
}

