package com.ermofit.app.newplan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.UserSettings
import com.ermofit.app.newplan.domain.usecase.GenerateDailyTrainingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val settings: UserSettings = UserSettings(),
    val saving: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: NewPlanRepository,
    private val generateDailyTrainingUseCase: GenerateDailyTrainingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.getCurrentSettings()
            _uiState.value = _uiState.value.copy(settings = settings)
        }
    }

    fun selectGoal(goal: String) = updateSettings { copy(goal = goal) }
    fun selectLevel(level: String) = updateSettings { copy(level = level) }
    fun selectDuration(minutes: Int) = updateSettings { copy(durationMinutes = minutes) }
    fun selectRest(rest: Int) = updateSettings { copy(restSec = rest) }
    fun setNotifications(enabled: Boolean) = updateSettings { copy(notificationsEnabled = enabled) }

    fun toggleEquipment(tag: String) {
        updateSettings {
            val next = equipmentOwned.toMutableSet()
            if (tag in next) {
                next.remove(tag)
            } else {
                next.add(tag)
            }
            if (next.isEmpty()) {
                next += EquipmentTags.NO_EQUIPMENT
            }
            copy(equipmentOwned = next.toList())
        }
    }

    fun toggleRestriction(tag: String) {
        updateSettings {
            val next = restrictions.toMutableSet()
            if (tag in next) next.remove(tag) else next.add(tag)
            copy(restrictions = next.toList())
        }
    }

    fun save() {
        val settings = _uiState.value.settings
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null)
            runCatching {
                repository.setSettings(settings)
                repository.setOnboardingDone(true)
                generateDailyTrainingUseCase(settings)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    completed = true
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = throwable.message ?: "Не удалось сохранить настройки."
                )
            }
        }
    }

    private fun updateSettings(block: UserSettings.() -> UserSettings) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.block()
        )
    }
}

