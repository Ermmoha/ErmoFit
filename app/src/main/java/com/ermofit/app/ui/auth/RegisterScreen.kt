package com.ermofit.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.i18n.appStrings
import com.ermofit.app.ui.i18n.authErrorMessage
import com.ermofit.app.ui.i18n.validationMessage

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showRepeatPassword by remember { mutableStateOf(false) }

    val nameError = AuthValidation.nameError(name)
    val emailError = AuthValidation.emailError(email)
    val passwordError = AuthValidation.registerPasswordError(password)
    val repeatPasswordError = AuthValidation.repeatPasswordError(password, repeatPassword)

    LaunchedEffect(uiState.currentUid) {
        if (uiState.currentUid != null) onRegistered()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(strings.authErrorMessage(it))
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AuthBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 42.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = strings.registerHeader,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.registerSubtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AuthCard(
                        title = strings.registerCardTitle,
                        subtitle = strings.registerCardSubtitle,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strings.nameLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            isError = attemptedSubmit && nameError != null,
                            supportingText = {
                                if (attemptedSubmit && nameError != null) {
                                    Text(strings.validationMessage(nameError))
                                }
                            }
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(strings.emailLabel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            isError = attemptedSubmit && emailError != null,
                            supportingText = {
                                if (attemptedSubmit && emailError != null) {
                                    Text(strings.validationMessage(emailError))
                                }
                            }
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(strings.passwordLabel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (showPassword) {
                                            strings.authHidePassword
                                        } else {
                                            strings.authShowPassword
                                        }
                                    )
                                }
                            },
                            isError = attemptedSubmit && passwordError != null,
                            supportingText = {
                                if (attemptedSubmit && passwordError != null) {
                                    Text(strings.validationMessage(passwordError))
                                } else {
                                    Text(strings.passwordRulesHint)
                                }
                            }
                        )

                        OutlinedTextField(
                            value = repeatPassword,
                            onValueChange = { repeatPassword = it },
                            label = { Text(strings.repeatPasswordLabel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            visualTransformation = if (showRepeatPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showRepeatPassword = !showRepeatPassword }) {
                                    Icon(
                                        imageVector = if (showRepeatPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (showRepeatPassword) {
                                            strings.authHidePassword
                                        } else {
                                            strings.authShowPassword
                                        }
                                    )
                                }
                            },
                            isError = attemptedSubmit && repeatPasswordError != null,
                            supportingText = {
                                if (attemptedSubmit && repeatPasswordError != null) {
                                    Text(strings.validationMessage(repeatPasswordError))
                                }
                            }
                        )

                        Button(
                            onClick = {
                                attemptedSubmit = true
                                if (nameError == null &&
                                    emailError == null &&
                                    passwordError == null &&
                                    repeatPasswordError == null
                                ) {
                                    viewModel.register(
                                        name = name.trim(),
                                        email = email.trim(),
                                        password = password
                                    )
                                }
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(top = 14.dp)
                        ) {
                            Text(if (uiState.isLoading) strings.creatingAccount else strings.registerAction)
                        }
                    }
                }
            }
        }
    }
}
