package com.ermofit.app.newplan.ui.workoutdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.relation.TrainingWithExercises
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutDetailsUiState(
    val training: TrainingWithExercises? = null,
    val isFavorite: Boolean = false
)

@HiltViewModel
class WorkoutDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewPlanRepository
) : ViewModel() {

    private val trainingId: String = checkNotNull(savedStateHandle["trainingId"])

    val uiState: StateFlow<WorkoutDetailsUiState> = combine(
        repository.observeTrainingWithExercises(trainingId),
        repository.observeIsFavorite(trainingId, NewPlanRepository.TYPE_TRAINING)
    ) { training, isFavorite ->
        WorkoutDetailsUiState(training = training, isFavorite = isFavorite)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutDetailsUiState()
    )

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(trainingId, NewPlanRepository.TYPE_TRAINING)
        }
    }
}
