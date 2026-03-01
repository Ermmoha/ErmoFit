package com.ermofit.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.data.model.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.observeThemeMode().collect { themeMode ->
                _uiState.update { it.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeLanguage().collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
    }
}
