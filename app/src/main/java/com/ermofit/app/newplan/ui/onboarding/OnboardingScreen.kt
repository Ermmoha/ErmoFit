package com.ermofit.app.newplan.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onGoalSelect: (String) -> Unit,
    onLevelSelect: (String) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onEquipmentToggle: (String) -> Unit,
    onRestrictionToggle: (String) -> Unit,
    onRestSelect: (Int) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    val settings = uiState.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Настройка профиля", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Выберите параметры, и приложение будет собирать тренировки под вас.",
            style = MaterialTheme.typography.bodyMedium
        )

        SettingsSectionTitle("Цель")
        MultiChoiceRow(
            values = TrainingGoals.all,
            selected = setOf(settings.goal),
            label = ::goalLabel,
            onSelect = onGoalSelect
        )

        SettingsSectionTitle("Уровень")
        MultiChoiceRow(
            values = TrainingLevels.all,
            selected = setOf(settings.level),
            label = ::levelLabel,
            onSelect = onLevelSelect
        )

        SettingsSectionTitle("Длительность")
        MultiChoiceRow(
            values = listOf(15, 20, 30, 45, 60),
            selected = setOf(settings.durationMinutes),
            label = ::durationLabel,
            onSelect = onDurationSelect
        )

        SettingsSectionTitle("Оборудование")
        MultiChoiceRow(
            values = EquipmentTags.all,
            selected = settings.equipmentOwned.toSet(),
            label = ::equipmentLabel,
            onSelect = onEquipmentToggle
        )

        SettingsSectionTitle("Ограничения")
        MultiChoiceRow(
            values = RestrictionTags.all,
            selected = settings.restrictions.toSet(),
            label = ::restrictionLabel,
            onSelect = onRestrictionToggle
        )

        SettingsSectionTitle("Отдых между упражнениями")
        MultiChoiceRow(
            values = listOf(15, 30, 45, 60),
            selected = setOf(settings.restSec),
            label = { "$it сек" },
            onSelect = onRestSelect
        )

        androidx.compose.foundation.layout.Row(
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

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            enabled = !uiState.saving
        ) {
            if (uiState.saving) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Готово")
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> MultiChoiceRow(
    values: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { item ->
            FilterChip(
                selected = item in selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) }
            )
        }
    }
}
