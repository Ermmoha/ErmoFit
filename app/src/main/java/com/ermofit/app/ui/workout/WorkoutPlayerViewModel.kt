package com.ermofit.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.model.LastWorkoutShortcut
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import com.ermofit.app.data.repository.CustomProgramsRepository
import com.ermofit.app.data.repository.FavoritesRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.data.repository.WorkoutProgressRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import com.ermofit.app.navigation.MainRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_WORKOUT_INTER_EXERCISE_REST_SEC = 15

data class WorkoutPlayerUiState(
    val programId: String = "",
    val programTitle: String = "",
    val exercises: List<ProgramExerciseWithDetails> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap(),
    val favoriteExerciseIds: Set<String> = emptySet(),
    val interExerciseRestSec: Int = DEFAULT_WORKOUT_INTER_EXERCISE_REST_SEC,
    val plannedDurationMinutes: Int = 0,
    val currentIndex: Int = 0,
    val isRunning: Boolean = false,
    val mainTimerSecondsLeft: Int = 0,
    val repsRestSecondsLeft: Int = 0,
    val transitionRestSecondsLeft: Int = 0,
    val startedAtMillis: Long = 0L,
    val showIconsHelpDialog: Boolean = false,
    val showNeverAgainInIconsHelp: Boolean = true,
    val isFinishing: Boolean = false,
    val isFinished: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localDataRepository: LocalDataRepository,
    private val customProgramsRepository: CustomProgramsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val workoutProgressRepository: WorkoutProgressRepository,
    private val preferencesManager: UserPreferencesManager,
    exerciseTextResolver: ExerciseTextResolver,
    programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val programId: String = checkNotNull(savedStateHandle["programId"])
    private val workoutSource: String = savedStateHandle["source"] ?: MainRoutes.WorkoutSourceStock
    private val isCustomWorkout = workoutSource == MainRoutes.WorkoutSourceCustom
    private val stateHandle = savedStateHandle
    private val customProgramFlow = if (isCustomWorkout) {
        customProgramsRepository.observeProgram(programId)
    } else {
        flowOf(null)
    }
    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { preferredLang, onlyTranslated ->
        WorkoutSearchSettings(
            preferredLang = preferredLang,
            onlyTranslated = onlyTranslated
        )
    }
    private val programTitleFlow = if (isCustomWorkout) {
        customProgramFlow.map { it?.title.orEmpty() }
    } else {
        combine(
            localDataRepository.observeProgramById(programId),
            programTextResolver.observeTextForProgram(programId)
        ) { program, text ->
            text.title.ifBlank { program?.title.orEmpty() }
        }
    }
    private val programExercisesFlow = if (isCustomWorkout) {
        combine(customProgramFlow, settingsFlow) { program, settings ->
            program to settings
        }.flatMapLatest { (program, settings) ->
            val exerciseIds = program?.exercises.orEmpty()
                .map { it.exerciseId }
                .distinct()
            if (program == null || exerciseIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                localDataRepository.observeExercisesByIds(
                    ids = exerciseIds,
                    langCode = settings.preferredLang,
                    onlyTranslated = settings.onlyTranslated
                ).map { exercises ->
                    val exerciseById = exercises.associateBy { it.id }
                    program.exercises
                        .sortedBy { it.orderIndex }
                        .mapNotNull { item ->
                            val exercise = exerciseById[item.exerciseId] ?: return@mapNotNull null
                            ProgramExerciseWithDetails(
                                programId = program.id,
                                exerciseId = item.exerciseId,
                                orderIndex = item.orderIndex,
                                defaultDurationSec = item.durationSec,
                                defaultReps = item.reps,
                                title = exercise.title,
                                description = exercise.description,
                                muscleGroup = exercise.muscleGroup,
                                equipment = exercise.equipment,
                                tags = exercise.tags,
                                mediaType = exercise.mediaType,
                                mediaUrl = exercise.mediaUrl,
                                fallbackImageUrl = exercise.fallbackImageUrl
                            )
                        }
                }
            }
        }
    } else {
        localDataRepository.observeExercisesForProgram(programId)
    }
    private val interExerciseRestFlow = if (isCustomWorkout) {
        customProgramFlow.map { it?.interExerciseRestSec ?: DEFAULT_WORKOUT_INTER_EXERCISE_REST_SEC }
    } else {
        flowOf(DEFAULT_WORKOUT_INTER_EXERCISE_REST_SEC)
    }
    private val programDurationMinutesFlow = if (isCustomWorkout) {
        customProgramFlow.map { it?.estimatedDurationMinutes ?: 0 }
    } else {
        localDataRepository.observeProgramById(programId)
            .map { it?.durationMinutes ?: 0 }
    }

    private val _uiState = MutableStateFlow(
        WorkoutPlayerUiState(
            programId = programId,
            interExerciseRestSec = stateHandle[KEY_INTER_EXERCISE_REST] ?: DEFAULT_WORKOUT_INTER_EXERCISE_REST_SEC,
            currentIndex = stateHandle[KEY_INDEX] ?: 0,
            isRunning = stateHandle[KEY_RUNNING] ?: false,
            mainTimerSecondsLeft = stateHandle[KEY_MAIN_TIMER] ?: 0,
            repsRestSecondsLeft = stateHandle[KEY_REPS_REST] ?: 0,
            transitionRestSecondsLeft = stateHandle[KEY_TRANSITION_REST] ?: 0,
            startedAtMillis = stateHandle[KEY_STARTED_AT] ?: 0L,
            isFinished = stateHandle[KEY_FINISHED] ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            programTitleFlow.collect { title ->
                _uiState.updateAndPersist { it.copy(programTitle = title) }
                if (title.isNotBlank()) {
                    preferencesManager.setLastWorkoutShortcut(
                        LastWorkoutShortcut(
                            programId = programId,
                            programTitle = title,
                            source = workoutSource,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            interExerciseRestFlow.collect { restSec ->
                _uiState.updateAndPersist { it.copy(interExerciseRestSec = restSec.coerceAtLeast(0)) }
            }
        }
        viewModelScope.launch {
            programDurationMinutesFlow.collect { durationMinutes ->
                _uiState.updateAndPersist {
                    it.copy(plannedDurationMinutes = durationMinutes.coerceAtLeast(0))
                }
            }
        }
        viewModelScope.launch {
            programExercisesFlow.collect { exercises ->
                _uiState.updateAndPersist { state ->
                    val safeIndex = if (exercises.isEmpty()) {
                        0
                    } else {
                        state.currentIndex.coerceIn(0, exercises.size)
                    }
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
        if (state.isFinished || state.isFinishing) return

        if (state.isRunning) {
            pauseTimer()
            return
        }

        if (state.startedAtMillis == 0L) {
            _uiState.updateAndPersist { it.copy(startedAtMillis = System.currentTimeMillis()) }
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
        if (_uiState.value.isFinishing) return
        pauseTimer()
        val state = _uiState.value
        if (state.currentIndex >= state.exercises.lastIndex) {
            moveToCompletionStep()
        } else {
            moveToIndex(state.currentIndex + 1)
        }
    }

    fun previous() {
        if (_uiState.value.isFinishing) return
        pauseTimer()
        moveToIndex(_uiState.value.currentIndex - 1)
    }

    fun finish() {
        val state = _uiState.value
        if (state.isFinished || state.isFinishing) return
        pauseTimer()
        _uiState.update { current ->
            current.copy(
                isRunning = false,
                transitionRestSecondsLeft = 0,
                repsRestSecondsLeft = 0,
                isFinishing = true
            )
        }
        viewModelScope.launch {
            val finishedAt = System.currentTimeMillis()
            val totalSeconds = resolveCompletedWorkoutTotalSeconds(state, finishedAt)
            runCatching {
                workoutProgressRepository.saveCompletedWorkout(
                    programId = state.programId,
                    programTitle = state.programTitle,
                    source = workoutSource,
                    completedExercises = state.exercises.size,
                    totalSeconds = totalSeconds,
                    finishedAt = finishedAt
                )
            }
            _uiState.updateAndPersist {
                it.copy(
                    isFinished = true,
                    isRunning = false,
                    transitionRestSecondsLeft = 0,
                    repsRestSecondsLeft = 0,
                    isFinishing = false
                )
            }
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

    private fun moveToCompletionStep() {
        val state = _uiState.value
        if (state.exercises.isEmpty()) return
        timerJob?.cancel()
        timerJob = null
        _uiState.updateAndPersist {
            it.copy(
                currentIndex = state.exercises.size,
                isRunning = false,
                mainTimerSecondsLeft = 0,
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
                    val interExerciseRestSec = state.interExerciseRestSec.coerceAtLeast(0)
                    if (interExerciseRestSec > 0) {
                        _uiState.updateAndPersist {
                            it.copy(
                                mainTimerSecondsLeft = 0,
                                transitionRestSecondsLeft = interExerciseRestSec,
                                isRunning = true
                            )
                        }
                    } else {
                        moveToIndex(state.currentIndex + 1)
                    }
                } else {
                    moveToCompletionStep()
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
        stateHandle[KEY_INTER_EXERCISE_REST] = state.interExerciseRestSec
        stateHandle[KEY_STARTED_AT] = state.startedAtMillis
        stateHandle[KEY_FINISHED] = state.isFinished
    }

    private fun resolveCompletedWorkoutTotalSeconds(
        state: WorkoutPlayerUiState,
        finishedAt: Long
    ): Int {
        val actualSeconds = if (state.startedAtMillis > 0L) {
            ((finishedAt - state.startedAtMillis) / 1_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
        if (actualSeconds > 0) return actualSeconds

        val plannedSeconds = state.plannedDurationMinutes
            .coerceAtLeast(0)
            .times(60)
        if (plannedSeconds > 0) return plannedSeconds

        val exerciseSeconds = state.exercises.sumOf { exercise ->
            when {
                exercise.defaultDurationSec > 0 -> exercise.defaultDurationSec
                exercise.defaultReps > 0 -> DEFAULT_REPS_REST_SEC
                else -> 0
            }
        }
        val transitionSeconds = state.interExerciseRestSec
            .coerceAtLeast(0) * (state.exercises.size - 1).coerceAtLeast(0)
        return (exerciseSeconds + transitionSeconds).coerceAtLeast(60)
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private data class WorkoutSearchSettings(
        val preferredLang: String,
        val onlyTranslated: Boolean
    )

    private companion object {
        const val DEFAULT_REPS_REST_SEC = 30
        const val KEY_INDEX = "workout_index"
        const val KEY_RUNNING = "workout_running"
        const val KEY_MAIN_TIMER = "workout_main_timer"
        const val KEY_REPS_REST = "workout_reps_rest"
        const val KEY_TRANSITION_REST = "workout_transition_rest"
        const val KEY_INTER_EXERCISE_REST = "workout_inter_exercise_rest"
        const val KEY_STARTED_AT = "workout_started_at"
        const val KEY_FINISHED = "workout_finished"
    }
}
