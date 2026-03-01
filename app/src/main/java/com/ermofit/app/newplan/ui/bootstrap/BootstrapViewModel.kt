package com.ermofit.app.newplan.ui.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import com.ermofit.app.newplan.domain.usecase.GenerateDailyTrainingUseCase
import com.ermofit.app.newplan.navigation.NewPlanRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BootstrapUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val targetRoute: String? = null
)

@HiltViewModel
class BootstrapViewModel @Inject constructor(
    private val repository: NewPlanRepository,
    private val generateDailyTrainingUseCase: GenerateDailyTrainingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootstrapUiState())
    val uiState: StateFlow<BootstrapUiState> = _uiState.asStateFlow()

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.value = BootstrapUiState(isLoading = true)
            runCatching {
                repository.ensureSeedLoaded()
                val onboardingDone = repository.observeOnboardingDone().first()
                if (onboardingDone) {
                    val settings = repository.getCurrentSettings()
                    // Pre-generate a daily workout so Home can open it immediately.
                    generateDailyTrainingUseCase(settings)
                    NewPlanRoutes.Home
                } else {
                    NewPlanRoutes.Onboarding
                }
            }.onSuccess { route ->
                _uiState.value = BootstrapUiState(
                    isLoading = false,
                    targetRoute = route
                )
            }.onFailure { throwable ->
                _uiState.value = BootstrapUiState(
                    isLoading = false,
                    error = throwable.message ?: "Ошибка загрузки данных."
                )
            }
        }
    }
}

