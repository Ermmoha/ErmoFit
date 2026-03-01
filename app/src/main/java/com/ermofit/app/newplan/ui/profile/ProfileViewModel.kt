package com.ermofit.app.newplan.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.model.AppLanguage
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

data class ProfileUiState(
    val settings: UserSettings = UserSettings(),
    val contentLanguage: AppLanguage = AppLanguage.RU,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: NewPlanRepository,
    private val generateDailyTrainingUseCase: GenerateDailyTrainingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            repository.observeContentLanguage().collect { language ->
                _uiState.value = _uiState.value.copy(contentLanguage = language)
            }
        }
    }

    fun selectGoal(goal: String) = updateSettings { copy(goal = goal) }
    fun selectLevel(level: String) = updateSettings { copy(level = level) }
    fun selectDuration(minutes: Int) = updateSettings { copy(durationMinutes = minutes) }
    fun selectRest(sec: Int) = updateSettings { copy(restSec = sec) }
    fun setNotifications(enabled: Boolean) = updateSettings { copy(notificationsEnabled = enabled) }

    fun setContentLanguage(language: AppLanguage) {
        if (language == AppLanguage.SYSTEM) return
        if (language == _uiState.value.contentLanguage) return

        val settings = _uiState.value.settings
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, message = null, error = null)
            runCatching {
                repository.setContentLanguage(language)
                repository.ensureSeedLoaded()
                generateDailyTrainingUseCase(settings)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    contentLanguage = language,
                    message = if (language == AppLanguage.RU) {
                        "Язык контента переключен на русский."
                    } else {
                        "Content language switched to English."
                    }
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = throwable.message ?: "Не удалось переключить язык контента."
                )
            }
        }
    }

    fun toggleEquipment(tag: String) {
        updateSettings {
            val next = equipmentOwned.toMutableSet()
            if (tag in next) next.remove(tag) else next.add(tag)
            if (next.isEmpty()) next += EquipmentTags.NO_EQUIPMENT
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

    fun saveSettings() {
        val settings = _uiState.value.settings
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, message = null, error = null)
            runCatching {
                repository.setSettings(settings)
                generateDailyTrainingUseCase(settings)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    message = "Настройки сохранены."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = throwable.message ?: "Не удалось сохранить настройки."
                )
            }
        }
    }

    fun clearProgress() {
        viewModelScope.launch {
            runCatching {
                repository.clearProgress()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "Прогресс очищен.")
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    error = throwable.message ?: "Не удалось очистить прогресс."
                )
            }
        }
    }

    private fun updateSettings(block: UserSettings.() -> UserSettings) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.block(),
            message = null,
            error = null
        )
    }
}