package com.ermofit.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthError {
    INVALID_CREDENTIALS,
    INVALID_EMAIL,
    WRONG_PASSWORD,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    USER_DISABLED,
    USER_COLLISION,
    NETWORK_ERROR,
    TOO_MANY_REQUESTS,
    CONFIGURATION_NOT_FOUND,
    UNKNOWN
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val currentUid: String? = null,
    val error: AuthError? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeUserId().collect { uid ->
                _uiState.update { it.copy(currentUid = uid) }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.login(email, password)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = mapAuthError(throwable)
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, error = null) }
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.register(name = name, email = email, password = password)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = mapAuthError(throwable)
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, error = null) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun mapAuthError(throwable: Throwable): AuthError {
        return when (throwable) {
            is FirebaseAuthInvalidCredentialsException -> {
                when (throwable.errorCode) {
                    "ERROR_INVALID_EMAIL" -> AuthError.INVALID_EMAIL
                    "ERROR_WRONG_PASSWORD" -> AuthError.WRONG_PASSWORD
                    "ERROR_WEAK_PASSWORD" -> AuthError.WEAK_PASSWORD
                    else -> AuthError.INVALID_CREDENTIALS
                }
            }

            is FirebaseAuthInvalidUserException -> {
                when (throwable.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> AuthError.USER_NOT_FOUND
                    "ERROR_USER_DISABLED" -> AuthError.USER_DISABLED
                    else -> AuthError.INVALID_CREDENTIALS
                }
            }

            is FirebaseAuthUserCollisionException -> AuthError.USER_COLLISION
            is FirebaseNetworkException -> AuthError.NETWORK_ERROR
            is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
            else -> {
                val message = throwable.message.orEmpty()
                if (message.contains("CONFIGURATION_NOT_FOUND")) {
                    AuthError.CONFIGURATION_NOT_FOUND
                } else {
                    AuthError.UNKNOWN
                }
            }
        }
    }
}
