package com.ermofit.app.newplan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.relation.TrainingWithExercises
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import com.ermofit.app.newplan.domain.model.UserSettings
import com.ermofit.app.newplan.domain.usecase.GenerateDailyTrainingUseCase
import com.ermofit.app.newplan.ui.common.motivationPhrases
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val motivationPhrase: String = motivationPhrases.first(),
    val dailyTraining: TrainingWithExercises? = null,
    val recommended: List<TrainingEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NewPlanRepository,
    private val generateDailyTrainingUseCase: GenerateDailyTrainingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            motivationPhrase = motivationPhrases.random(Random(LocalDate.now().toEpochDay().toInt()))
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var contentJob: Job? = null

    init {
        observeData()
    }

    fun regenerate() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            runCatching {
                generateDailyTrainingUseCase(settings)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    error = throwable.message ?: "Не удалось перегенерировать тренировку."
                )
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    settings = settings,
                    error = null
                )
                observeForSettings(settings)
            }
        }
    }

    private fun observeForSettings(settings: UserSettings) {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            combine(
                repository.observeTrainingWithExercises(dailyTrainingId()),
                repository.getTrainingsByFilters(
                    goal = settings.goal,
                    level = settings.level,
                    durationMinutes = settings.durationMinutes,
                    equipmentTags = settings.equipmentOwned,
                    restrictions = settings.restrictions
                ),
                repository.observeAllTrainings()
            ) { daily, filtered, all ->
                val primary = filtered.filterNot { it.isGenerated }
                val fallback = all.filterNot { it.isGenerated || it.id in primary.map { item -> item.id }.toSet() }
                val targetMin = 3
                val result = if (primary.size >= targetMin) {
                    primary
                } else {
                    (primary + fallback).take(targetMin)
                }.take(6)
                daily to result
            }.collect { (daily, recommended) ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dailyTraining = daily,
                    recommended = recommended
                )
            }
        }
    }

    private fun dailyTrainingId(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return "daily_${LocalDate.now().format(formatter)}"
    }
}
