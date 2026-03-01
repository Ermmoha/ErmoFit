package com.ermofit.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import com.ermofit.app.data.repository.FavoritesRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutPlayerUiState(
    val programId: String = "",
    val programTitle: String = "",
    val exercises: List<ProgramExerciseWithDetails> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap(),
    val favoriteExerciseIds: Set<String> = emptySet(),
    val currentIndex: Int = 0,
    val isRunning: Boolean = false,
    val mainTimerSecondsLeft: Int = 0,
    val repsRestSecondsLeft: Int = 0,
    val transitionRestSecondsLeft: Int = 0,
    val showIconsHelpDialog: Boolean = false,
    val showNeverAgainInIconsHelp: Boolean = true,
    val isFinished: Boolean = false
)

@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localDataRepository: LocalDataRepository,
    private val favoritesRepository: FavoritesRepository,
    private val preferencesManager: UserPreferencesManager,
    exerciseTextResolver: ExerciseTextResolver,
    programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val programId: String = checkNotNull(savedStateHandle["programId"])
    private val stateHandle = savedStateHandle
    private val programFlow = localDataRepository.observeProgramById(programId)
    private val programTextFlow = programTextResolver.observeTextForProgram(programId)
    private val programExercisesFlow = localDataRepository.observeExercisesForProgram(programId)

    private val _uiState = MutableStateFlow(
        WorkoutPlayerUiState(
            programId = programId,
            currentIndex = stateHandle[KEY_INDEX] ?: 0,
            isRunning = stateHandle[KEY_RUNNING] ?: false,
            mainTimerSecondsLeft = stateHandle[KEY_MAIN_TIMER] ?: 0,
            repsRestSecondsLeft = stateHandle[KEY_REPS_REST] ?: 0,
            transitionRestSecondsLeft = stateHandle[KEY_TRANSITION_REST] ?: 0,
            isFinished = stateHandle[KEY_FINISHED] ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            combine(programFlow, programTextFlow) { program, text ->
                text.title.ifBlank { program?.title.orEmpty() }
            }.collect { title ->
                _uiState.updateAndPersist { it.copy(programTitle = title) }
            }
        }
        viewModelScope.launch {
            programExercisesFlow.collect { exercises ->
                _uiState.updateAndPersist { state ->
                    val safeIndex = state.currentIndex.coerceIn(0, (exercises.size - 1).coerceAtLeast(0))
                    val current = exercises.getOrNull(safeIndex)
                    val mainTimer = when {
                        state.mainTimerSecondsLeft > 0 -> state.mainTimerSecondsLeft
                        current?.defaultDurationSec ?: 0 > 0 -> current?.defaultDurationSec ?: 0
                        else -> 0
                    }
                    state.copy(
                        exercises = exercises,
                        currentIndex = safeIndex,
                        mainTimerSecondsLeft = mainTimer
                    )
                }
                if (_uiState.value.isRunning && timerJob == null) startTicker()
            }
        }
        viewModelScope.launch {
            exerciseTextResolver
                .observeTextsForProgramExercises(programExercisesFlow)
                .collect { texts ->
                    _uiState.updateAndPersist { it.copy(exerciseTexts = texts) }
                }
        }
        viewModelScope.launch {
            favoritesRepository.observeFavoriteExerciseIds().collect { ids ->
                _uiState.update { it.copy(favoriteExerciseIds = ids.toSet()) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeShowWorkoutIconsHelp().collect { shouldShow ->
                _uiState.update { state ->
                    state.copy(
                        showIconsHelpDialog = shouldShow && !state.isFinished,
                        showNeverAgainInIconsHelp = shouldShow
                    )
                }
            }
        }
    }

    fun startPause() {
        val state = _uiState.value
        val current = currentExercise(state) ?: return
        if (state.isFinished) return

        if (state.isRunning) {
            pauseTimer()
            return
        }

        if (current.defaultDurationSec > 0) {
            if (state.mainTimerSecondsLeft <= 0) {
                _uiState.updateAndPersist { it.copy(mainTimerSecondsLeft = current.defaultDurationSec) }
            }
            _uiState.updateAndPersist { it.copy(isRunning = true) }
            startTicker()
            return
        }

        if (current.defaultReps > 0 && state.repsRestSecondsLeft <= 0) {
            _uiState.updateAndPersist { it.copy(repsRestSecondsLeft = DEFAULT_REPS_REST_SEC) }
        }
        _uiState.updateAndPersist { it.copy(isRunning = true) }
        startTicker()
    }

    fun next() {
        pauseTimer()
        moveToIndex(_uiState.value.currentIndex + 1)
    }

    fun previous() {
        pauseTimer()
        moveToIndex(_uiState.value.currentIndex - 1)
    }

    fun finish() {
        pauseTimer()
        _uiState.updateAndPersist {
            it.copy(
                isFinished = true,
                isRunning = false,
                transitionRestSecondsLeft = 0,
                repsRestSecondsLeft = 0
            )
        }
    }

    fun skipTransitionRest() {
        val nextIndex = _uiState.value.currentIndex + 1
        _uiState.updateAndPersist { it.copy(transitionRestSecondsLeft = 0) }
        moveToIndex(nextIndex)
    }

    fun toggleCurrentExerciseFavorite() {
        val exerciseId = currentExercise(_uiState.value)?.exerciseId ?: return
        viewModelScope.launch {
            runCatching {
                favoritesRepository.toggleFavoriteExercise(exerciseId)
            }
        }
    }

    fun openIconsHelpDialog() {
        _uiState.update {
            it.copy(
                showIconsHelpDialog = true,
                showNeverAgainInIconsHelp = false
            )
        }
    }

    fun dismissIconsHelpDialog() {
        _uiState.update { it.copy(showIconsHelpDialog = false) }
    }

    fun disableIconsHelpDialog() {
        viewModelScope.launch {
            preferencesManager.setShowWorkoutIconsHelp(false)
        }
        _uiState.update {
            it.copy(
                showIconsHelpDialog = false,
                showNeverAgainInIconsHelp = false
            )
        }
    }

    private fun moveToIndex(index: Int) {
        val state = _uiState.value
        if (state.exercises.isEmpty()) return
        if (index !in state.exercises.indices) return
        val nextExercise = state.exercises[index]
        _uiState.updateAndPersist {
            it.copy(
                currentIndex = index,
                isRunning = false,
                mainTimerSecondsLeft = if (nextExercise.defaultDurationSec > 0) {
                    nextExercise.defaultDurationSec
                } else {
                    0
                },
                repsRestSecondsLeft = 0,
                transitionRestSecondsLeft = 0
            )
        }
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    private fun tick() {
        val state = _uiState.value
        if (!state.isRunning) return

        if (state.transitionRestSecondsLeft > 0) {
            val next = state.transitionRestSecondsLeft - 1
            if (next <= 0) {
                _uiState.updateAndPersist { it.copy(transitionRestSecondsLeft = 0, isRunning = false) }
                moveToIndex(state.currentIndex + 1)
            } else {
                _uiState.updateAndPersist { it.copy(transitionRestSecondsLeft = next) }
            }
            return
        }

        if (state.repsRestSecondsLeft > 0) {
            val next = state.repsRestSecondsLeft - 1
            _uiState.updateAndPersist {
                it.copy(
                    repsRestSecondsLeft = next.coerceAtLeast(0),
                    isRunning = next > 0
                )
            }
            return
        }

        val current = currentExercise(state) ?: return
        if (current.defaultDurationSec > 0) {
            val next = state.mainTimerSecondsLeft - 1
            if (next <= 0) {
                val hasNext = state.currentIndex < state.exercises.lastIndex
                if (hasNext) {
                    _uiState.updateAndPersist {
                        it.copy(
                            mainTimerSecondsLeft = 0,
                            transitionRestSecondsLeft = INTER_EXERCISE_REST_SEC,
                            isRunning = true
                        )
                    }
                } else {
                    finish()
                }
            } else {
                _uiState.updateAndPersist { it.copy(mainTimerSecondsLeft = next) }
            }
            return
        }

        _uiState.updateAndPersist { it.copy(isRunning = false) }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.updateAndPersist { it.copy(isRunning = false) }
    }

    private fun currentExercise(state: WorkoutPlayerUiState): ProgramExerciseWithDetails? {
        return state.exercises.getOrNull(state.currentIndex)
    }

    private fun MutableStateFlow<WorkoutPlayerUiState>.updateAndPersist(
        transform: (WorkoutPlayerUiState) -> WorkoutPlayerUiState
    ) {
        update { current ->
            transform(current).also { next ->
                persist(next)
            }
        }
    }

    private fun persist(state: WorkoutPlayerUiState) {
        stateHandle[KEY_INDEX] = state.currentIndex
        stateHandle[KEY_RUNNING] = state.isRunning
        stateHandle[KEY_MAIN_TIMER] = state.mainTimerSecondsLeft
        stateHandle[KEY_REPS_REST] = state.repsRestSecondsLeft
        stateHandle[KEY_TRANSITION_REST] = state.transitionRestSecondsLeft
        stateHandle[KEY_FINISHED] = state.isFinished
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_REPS_REST_SEC = 30
        const val INTER_EXERCISE_REST_SEC = 15
        const val KEY_INDEX = "workout_index"
        const val KEY_RUNNING = "workout_running"
        const val KEY_MAIN_TIMER = "workout_main_timer"
        const val KEY_REPS_REST = "workout_reps_rest"
        const val KEY_TRANSITION_REST = "workout_transition_rest"
        const val KEY_FINISHED = "workout_finished"
    }
}
