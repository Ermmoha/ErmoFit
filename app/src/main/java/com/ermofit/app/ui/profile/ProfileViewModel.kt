package com.ermofit.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.data.model.AppThemeMode
import com.ermofit.app.data.repository.AuthRepository
import com.ermofit.app.data.repository.SeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val uid: String? = null,
    val displayName: String = "",
    val email: String = "",
    val aboutMe: String = "",
    val editDisplayName: String = "",
    val editAboutMe: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: UserPreferencesManager,
    private val seedRepository: SeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            uid = authRepository.currentUserId(),
            displayName = authRepository.currentUserDisplayName(),
            email = authRepository.currentUserEmail()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.uid != null) {
            loadProfile()
        }
        viewModelScope.launch {
            preferencesManager.observeThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeLanguage().collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            authRepository.observeUserId().collect { uid ->
                _uiState.update { it.copy(uid = uid) }
                if (uid != null) {
                    loadProfile()
                }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentUserProfile() ?: return@launch
            _uiState.update { state ->
                state.copy(
                    uid = profile.uid,
                    displayName = profile.displayName,
                    email = profile.email,
                    aboutMe = profile.aboutMe,
                    editDisplayName = if (state.isEditing) state.editDisplayName else profile.displayName,
                    editAboutMe = if (state.isEditing) state.editAboutMe else profile.aboutMe
                )
            }
        }
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editDisplayName = it.displayName,
                editAboutMe = it.aboutMe
            )
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editDisplayName = it.displayName,
                editAboutMe = it.aboutMe
            )
        }
    }

    fun onEditDisplayNameChanged(value: String) {
        _uiState.update { it.copy(editDisplayName = value) }
    }

    fun onEditAboutMeChanged(value: String) {
        _uiState.update { it.copy(editAboutMe = value) }
    }

    fun saveProfile(
        validationMessage: String,
        updatedMessage: String,
        failureMessage: String
    ) {
        val newName = _uiState.value.editDisplayName.trim()
        val newAbout = _uiState.value.editAboutMe.trim()
        if (newName.length < 2) {
            _uiState.update { it.copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                authRepository.updateCurrentUserProfile(
                    displayName = newName,
                    aboutMe = newAbout
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        displayName = newName,
                        aboutMe = newAbout,
                        editDisplayName = newName,
                        editAboutMe = newAbout,
                        isEditing = false,
                        isSaving = false,
                        message = updatedMessage
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        message = it.message ?: failureMessage
                    )
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
            runCatching {
                withContext(Dispatchers.IO) {
                    seedRepository.ensureSeedLoaded()
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun logout() {
        authRepository.logout()
    }
}
