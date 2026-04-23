package com.ermofit.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.data.model.AppThemeMode
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.uid) {
        if (uiState.uid == null) onLoggedOut()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val initials = uiState.displayName.trim().firstOrNull()?.uppercase() ?: "?"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = uiState.displayName.ifBlank { strings.profileNameLabel },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.email.ifBlank { "-" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.aboutMe.isNotBlank()) {
                                Text(
                                    text = uiState.aboutMe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                ProfileProgressSection(
                    uiState = uiState,
                    isRu = isRu
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(strings.profileAccountSection, style = MaterialTheme.typography.titleLarge)

                        if (uiState.isEditing) {
                            OutlinedTextField(
                                value = uiState.editDisplayName,
                                onValueChange = viewModel::onEditDisplayNameChanged,
                                label = { Text(strings.profileNameLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = uiState.editAboutMe,
                                onValueChange = viewModel::onEditAboutMeChanged,
                                label = { Text(strings.profileAboutLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 4
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::cancelEditing,
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isSaving
                                ) {
                                    Text(strings.profileCancel)
                                }
                                Button(
                                    onClick = {
                                        viewModel.saveProfile(
                                            validationMessage = strings.profileNameRequired,
                                            updatedMessage = strings.profileUpdated,
                                            failureMessage = strings.profileUpdateFailed
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isSaving
                                ) {
                                    Text(strings.profileSave)
                                }
                            }
                        } else {
                            Text("${strings.profileNameLabel}: ${uiState.displayName.ifBlank { "-" }}")
                            Text("${strings.profileEmailLabel}: ${uiState.email.ifBlank { "-" }}")
                            Text("${strings.profileAboutLabel}: ${uiState.aboutMe.ifBlank { "-" }}")
                            OutlinedButton(
                                onClick = viewModel::startEditing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.profileEdit)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.profileSettingsSection, style = MaterialTheme.typography.titleLarge)

                        Text(strings.profileLanguageLabel, style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = uiState.language == AppLanguage.SYSTEM,
                                onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                                label = { Text(strings.languageSystem) }
                            )
                            FilterChip(
                                selected = uiState.language == AppLanguage.RU,
                                onClick = { viewModel.setLanguage(AppLanguage.RU) },
                                label = { Text(strings.languageRussian) }
                            )
                            FilterChip(
                                selected = uiState.language == AppLanguage.EN,
                                onClick = { viewModel.setLanguage(AppLanguage.EN) },
                                label = { Text(strings.languageEnglish) }
                            )
                        }

                        Text(strings.profileThemeLabel, style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = uiState.themeMode == AppThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                                label = { Text(strings.themeSystem) }
                            )
                            FilterChip(
                                selected = uiState.themeMode == AppThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                                label = { Text(strings.themeLight) }
                            )
                            FilterChip(
                                selected = uiState.themeMode == AppThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                                label = { Text(strings.themeDark) }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.profileLogout)
                }
            }
        }
    }
}

@Composable
private fun ProfileProgressSection(
    uiState: ProfileUiState,
    isRu: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isRu) "Прогресс" else "Progress",
                style = MaterialTheme.typography.titleLarge
            )

            if (!uiState.hasProgress) {
                Text(
                    text = if (isRu) {
                        "Прогресс появится после первой завершённой тренировки."
                    } else {
                        "Progress will appear after your first completed workout."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatCard(
                        value = uiState.completedWorkoutsCount.toString(),
                        label = if (isRu) "Тренировок" else "Workouts",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        value = uiState.workoutsThisWeek.toString(),
                        label = if (isRu) "За 7 дней" else "Last 7 days",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatCard(
                        value = uiState.completedMinutes.toString(),
                        label = if (isRu) "Минут всего" else "Total minutes",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        value = if (isRu) {
                            "${uiState.currentStreakDays} дн"
                        } else {
                            "${uiState.currentStreakDays} d"
                        },
                        label = if (isRu) "Текущий стрик" else "Current streak",
                        modifier = Modifier.weight(1f)
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isRu) "Последняя тренировка" else "Last workout",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.lastWorkoutTitle.ifBlank { "-" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = uiState.lastWorkoutAtLabel.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ProfileProgressChart(
                    days = uiState.progressDays,
                    isRu = isRu
                )
            }
        }
    }
}

@Composable
private fun ProfileStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileProgressChart(
    days: List<ProfileProgressDayUi>,
    isRu: Boolean
) {
    val maxWorkouts = (days.maxOfOrNull { it.workouts } ?: 0).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isRu) "Активность за 7 дней" else "Activity for 7 days",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { day ->
                val heightFraction = if (day.workouts == 0) {
                    0.08f
                } else {
                    (day.workouts.toFloat() / maxWorkouts.toFloat()).coerceAtLeast(0.18f)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = day.workouts.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(72.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRu) "${day.minutes} мин" else "${day.minutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
