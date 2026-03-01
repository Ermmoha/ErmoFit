package com.ermofit.app.newplan.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.RestrictionTags
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels
import com.ermofit.app.newplan.ui.common.durationLabel
import com.ermofit.app.newplan.ui.common.equipmentLabel
import com.ermofit.app.newplan.ui.common.goalLabel
import com.ermofit.app.newplan.ui.common.levelLabel
import com.ermofit.app.newplan.ui.common.restrictionLabel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onGoalSelect: (String) -> Unit,
    onLevelSelect: (String) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onEquipmentToggle: (String) -> Unit,
    onRestrictionToggle: (String) -> Unit,
    onRestSelect: (Int) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onLanguageSelect: (AppLanguage) -> Unit,
    onSave: () -> Unit,
    onClearProgress: () -> Unit
) {
    val openDialog = remember { mutableStateOf(false) }
    val settings = uiState.settings

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { openDialog.value = false },
            title = { Text("Очистить прогресс?") },
            text = { Text("Будут удалены все выполненные тренировки и сгенерированные планы.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDialog.value = false
                        onClearProgress()
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Параметры тренировок", style = MaterialTheme.typography.titleLarge)

        Text("Язык контента", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.contentLanguage == AppLanguage.RU,
                onClick = { onLanguageSelect(AppLanguage.RU) },
                label = { Text("Русский") }
            )
            FilterChip(
                selected = uiState.contentLanguage == AppLanguage.EN,
                onClick = { onLanguageSelect(AppLanguage.EN) },
                label = { Text("English") }
            )
        }

        Text("Цель", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrainingGoals.all.forEach { goal ->
                FilterChip(
                    selected = settings.goal == goal,
                    onClick = { onGoalSelect(goal) },
                    label = { Text(goalLabel(goal)) }
                )
            }
        }

        Text("Уровень", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrainingLevels.all.forEach { level ->
                FilterChip(
                    selected = settings.level == level,
                    onClick = { onLevelSelect(level) },
                    label = { Text(levelLabel(level)) }
                )
            }
        }

        Text("Длительность", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 20, 30, 45, 60).forEach { minutes ->
                FilterChip(
                    selected = settings.durationMinutes == minutes,
                    onClick = { onDurationSelect(minutes) },
                    label = { Text(durationLabel(minutes)) }
                )
            }
        }

        Text("Оборудование", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EquipmentTags.all.forEach { tag ->
                FilterChip(
                    selected = tag in settings.equipmentOwned,
                    onClick = { onEquipmentToggle(tag) },
                    label = { Text(equipmentLabel(tag)) }
                )
            }
        }

        Text("Ограничения", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RestrictionTags.all.forEach { tag ->
                FilterChip(
                    selected = tag in settings.restrictions,
                    onClick = { onRestrictionToggle(tag) },
                    label = { Text(restrictionLabel(tag)) }
                )
            }
        }

        Text("Отдых", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 45, 60).forEach { sec ->
                FilterChip(
                    selected = settings.restSec == sec,
                    onClick = { onRestSelect(sec) },
                    label = { Text("$sec сек") }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Уведомления")
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotificationsToggle
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.saving
        ) {
            Text("Сохранить настройки")
        }

        Button(
            onClick = { openDialog.value = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Очистить прогресс")
        }

        uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}