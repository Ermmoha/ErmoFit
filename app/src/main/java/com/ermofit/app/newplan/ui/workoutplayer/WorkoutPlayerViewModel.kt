package com.ermofit.app.newplan.ui.workoutplayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.WorkoutSessionEntity
import com.ermofit.app.newplan.data.local.relation.TrainingExerciseJoined
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlayerExerciseItem(
    val id: String,
    val orderIndex: Int,
    val name: String,
    val description: String,
    val type: String,
    val durationSec: Int,
    val reps: Int,
    val mediaResName: String?
)

data class WorkoutResultSummary(
    val totalSeconds: Int,
    val completedExercises: Int,
    val streakDays: Int
)

data class WorkoutPlayerUiState(
    val loading: Boolean = true,
    val trainingId: String = "",
    val trainingTitle: String = "",
    val exercises: List<PlayerExerciseItem> = emptyList(),
    val currentIndex: Int = 0,
    val completedExercises: Int = 0,
    val isRunning: Boolean = false,
    val isResting: Boolean = false,
    val remainingSec: Int = 0,
    val restRemainingSec: Int = 0,
    val restSec: Int = 30,
    val startedAt: Long = 0L,
    val finished: Boolean = false,
    val result: WorkoutResultSummary? = null,
    val resultSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NewPlanRepository
) : ViewModel() {

    private val trainingId: String = checkNotNull(savedStateHandle["trainingId"])
    private val zoneId = ZoneId.systemDefault()
    private var tickerJob: Job? = null
    private var pendingSession: WorkoutSessionEntity? = null

    private val _uiState = MutableStateFlow(WorkoutPlayerUiState(trainingId = trainingId))
    val uiState: StateFlow<WorkoutPlayerUiState> = _uiState.asStateFlow()

    init {
        observeTraining()
    }

    fun toggleStartPause() {
        val current = _uiState.value
        if (current.finished || current.exercises.isEmpty()) return

        if (current.startedAt == 0L) {
            _uiState.value = current.copy(startedAt = System.currentTimeMillis(), isRunning = true)
            startTicker()
            return
        }

        val nextRunning = !current.isRunning
        _uiState.value = current.copy(isRunning = nextRunning)
        if (nextRunning) startTicker() else stopTicker()
    }

    fun completeRepsSet() {
        val state = _uiState.value
        if (state.loading || state.finished || state.exercises.isEmpty()) return
        val currentExercise = state.exercises.getOrNull(state.currentIndex) ?: return
        if (currentExercise.type == "reps") {
            completeCurrentExercise()
        }
    }

    fun next() {
        val state = _uiState.value
        if (state.finished) return
        val nextIndex = (state.currentIndex + 1).coerceAtMost(state.exercises.lastIndex)
        moveToExercise(nextIndex)
    }

    fun prev() {
        val state = _uiState.value
        if (state.finished) return
        val prevIndex = (state.currentIndex - 1).coerceAtLeast(0)
        moveToExercise(prevIndex)
    }

    fun finish() {
        if (_uiState.value.finished) return
        stopTicker()
        viewModelScope.launch {
            val state = _uiState.value
            val finishedAt = System.currentTimeMillis()
            val totalSeconds = if (state.startedAt > 0L) {
                ((finishedAt - state.startedAt) / 1000L).toInt().coerceAtLeast(1)
            } else {
                0
            }
            val session = WorkoutSessionEntity(
                id = UUID.randomUUID().toString(),
                trainingId = trainingId,
                startedAt = state.startedAt.takeIf { it > 0 } ?: finishedAt,
                finishedAt = finishedAt,
                totalSeconds = totalSeconds,
                completedExercises = state.completedExercises
            )
            val streakDays = runCatching {
                val all = repository.observeAllSessions().first()
                val dates = all.map { toLocalDate(it.finishedAt) }.toMutableSet()
                dates += toLocalDate(finishedAt)
                calculateStreak(dates)
            }.getOrDefault(0)
            pendingSession = session
            _uiState.value = state.copy(
                finished = true,
                isRunning = false,
                isResting = false,
                result = WorkoutResultSummary(
                    totalSeconds = totalSeconds,
                    completedExercises = state.completedExercises,
                    streakDays = streakDays
                ),
                resultSaved = false,
                error = null
            )
        }
    }

    fun saveResult() {
        if (_uiState.value.resultSaved) return
        val session = pendingSession ?: return
        viewModelScope.launch {
            runCatching {
                repository.saveWorkoutSession(session)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    resultSaved = true,
                    error = null
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    error = throwable.message ?: "Не удалось сохранить результат."
                )
            }
        }
    }

    private fun observeTraining() {
        viewModelScope.launch {
            val settings = repository.getCurrentSettings()
            repository.observeTrainingWithExercises(trainingId).collect { training ->
                if (training == null) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "Тренировка не найдена."
                    )
                    return@collect
                }
                val items = training.exercises.map { it.toPlayerItem() }
                val first = items.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    trainingTitle = training.training.title,
                    exercises = items,
                    currentIndex = 0,
                    restSec = settings.restSec,
                    remainingSec = if (first?.type == "time") first.durationSec else 0,
                    restRemainingSec = settings.restSec
                )
            }
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val state = _uiState.value
                if (!state.isRunning || state.finished) continue

                if (state.isResting) {
                    if (state.restRemainingSec > 1) {
                        _uiState.value = state.copy(restRemainingSec = state.restRemainingSec - 1)
                    } else {
                        val nextIndex = state.currentIndex + 1
                        if (nextIndex > state.exercises.lastIndex) {
                            finish()
                        } else {
                            moveToExercise(nextIndex, fromRest = true)
                        }
                    }
                    continue
                }

                val currentExercise = state.exercises.getOrNull(state.currentIndex) ?: continue
                if (currentExercise.type == "time") {
                    if (state.remainingSec > 1) {
                        _uiState.value = state.copy(remainingSec = state.remainingSec - 1)
                    } else {
                        completeCurrentExercise()
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun completeCurrentExercise() {
        val state = _uiState.value
        if (state.finished || state.exercises.isEmpty()) return
        val updatedCompleted = (state.completedExercises + 1).coerceAtMost(state.exercises.size)

        if (state.currentIndex >= state.exercises.lastIndex) {
            _uiState.value = state.copy(completedExercises = updatedCompleted)
            finish()
            return
        }

        if (state.restSec <= 0) {
            _uiState.value = state.copy(completedExercises = updatedCompleted)
            moveToExercise(state.currentIndex + 1)
        } else {
            _uiState.value = state.copy(
                completedExercises = updatedCompleted,
                isResting = true,
                restRemainingSec = state.restSec
            )
        }
    }

    private fun moveToExercise(index: Int, fromRest: Boolean = false) {
        val state = _uiState.value
        val target = state.exercises.getOrNull(index) ?: return
        _uiState.value = state.copy(
            currentIndex = index,
            isResting = false,
            restRemainingSec = state.restSec,
            remainingSec = if (target.type == "time") target.durationSec else 0,
            isRunning = state.isRunning || fromRest
        )
    }

    private fun TrainingExerciseJoined.toPlayerItem(): PlayerExerciseItem {
        return PlayerExerciseItem(
            id = exerciseId,
            orderIndex = orderIndex,
            name = name,
            description = description,
            type = type,
            durationSec = (customDurationSec ?: defaultDurationSec).coerceAtLeast(0),
            reps = (customReps ?: defaultReps).coerceAtLeast(0),
            mediaResName = mediaResName
        )
    }

    private fun toLocalDate(epochMillis: Long): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    private fun calculateStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        var streak = 0
        var day = LocalDate.now(zoneId)
        while (day in dates) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }
}
