package com.ermofit.app.newplan.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.RestrictionTags
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels
import com.ermofit.app.newplan.ui.common.durationLabel
import com.ermofit.app.newplan.ui.common.equipmentLabel
import com.ermofit.app.newplan.ui.common.exerciseTypeLabel
import com.ermofit.app.newplan.ui.common.goalLabel
import com.ermofit.app.newplan.ui.common.levelLabel
import com.ermofit.app.newplan.ui.common.restrictionLabel

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun CatalogScreen(
    uiState: CatalogUiState,
    onTabChange: (CatalogTab) -> Unit,
    onGoalChange: (String?) -> Unit,
    onLevelChange: (String?) -> Unit,
    onDurationChange: (Int?) -> Unit,
    onEquipmentToggle: (String) -> Unit,
    onRestrictionToggle: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onTrainingClick: (String) -> Unit,
    onExerciseClick: (String) -> Unit
) {
    val filters = uiState.filters

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.tab == CatalogTab.TRAININGS,
                    onClick = { onTabChange(CatalogTab.TRAININGS) },
                    label = { Text("Тренировки") }
                )
                FilterChip(
                    selected = filters.tab == CatalogTab.EXERCISES,
                    onClick = { onTabChange(CatalogTab.EXERCISES) },
                    label = { Text("Упражнения") }
                )
            }
        }

        item {
            OutlinedTextField(
                value = filters.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по названию") },
                singleLine = true
            )
        }

        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("Фильтры", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Text("Цель", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filters.goal == null,
                    onClick = { onGoalChange(null) },
                    label = { Text("Все") }
                )
                TrainingGoals.all.forEach { goal ->
                    FilterChip(
                        selected = filters.goal == goal,
                        onClick = { onGoalChange(goal) },
                        label = { Text(goalLabel(goal)) }
                    )
                }
            }
        }

        item {
            Text("Уровень", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filters.level == null,
                    onClick = { onLevelChange(null) },
                    label = { Text("Все") }
                )
                TrainingLevels.all.forEach { level ->
                    FilterChip(
                        selected = filters.level == level,
                        onClick = { onLevelChange(level) },
                        label = { Text(levelLabel(level)) }
                    )
                }
            }
        }

        item {
            Text("Длительность", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filters.durationMinutes == null,
                    onClick = { onDurationChange(null) },
                    label = { Text("Любая") }
                )
                listOf(15, 20, 30, 45, 60).forEach { duration ->
                    FilterChip(
                        selected = filters.durationMinutes == duration,
                        onClick = { onDurationChange(duration) },
                        label = { Text(durationLabel(duration)) }
                    )
                }
            }
        }

        item {
            Text("Оборудование", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EquipmentTags.all.forEach { tag ->
                    FilterChip(
                        selected = tag in filters.equipment,
                        onClick = { onEquipmentToggle(tag) },
                        label = { Text(equipmentLabel(tag)) }
                    )
                }
            }
        }

        item {
            Text("Ограничения", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RestrictionTags.all.forEach { tag ->
                    FilterChip(
                        selected = tag in filters.restrictions,
                        onClick = { onRestrictionToggle(tag) },
                        label = { Text(restrictionLabel(tag)) }
                    )
                }
            }
        }

        if (filters.tab == CatalogTab.TRAININGS) {
            if (uiState.trainings.isEmpty()) {
                item {
                    Text("Тренировки не найдены.")
                }
            } else {
                items(uiState.trainings, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrainingClick(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${goalLabel(item.goal)} · ${levelLabel(item.level)} · ${durationLabel(item.durationMinutes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            if (uiState.exercises.isEmpty()) {
                item {
                    Text("Упражнения не найдены.")
                }
            } else {
                items(uiState.exercises, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseClick(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${levelLabel(item.level)} · ${exerciseTypeLabel(item.type)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
