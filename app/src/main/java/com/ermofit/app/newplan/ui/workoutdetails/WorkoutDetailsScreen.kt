package com.ermofit.app.newplan.ui.workoutdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ermofit.app.newplan.ui.common.durationLabel
import com.ermofit.app.newplan.ui.common.equipmentLabel
import com.ermofit.app.newplan.ui.common.goalLabel
import com.ermofit.app.newplan.ui.common.levelLabel

@Composable
fun WorkoutDetailsScreen(
    uiState: WorkoutDetailsUiState,
    onExerciseClick: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val data = uiState.training
    if (data == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Тренировка не найдена.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(data.training.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${goalLabel(data.training.goal)} · ${levelLabel(data.training.level)} · ${durationLabel(data.training.durationMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                data.training.equipmentRequired.joinToString { equipmentLabel(it) },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Button(onClick = { onStartWorkout(data.training.id) }) {
                    Text("Начать тренировку")
                }
                Button(onClick = onToggleFavorite) {
                    Text(if (uiState.isFavorite) "Убрать из избранного" else "В избранное")
                }
            }
        }

        item {
            Text("Упражнения", style = MaterialTheme.typography.titleLarge)
        }

        items(data.exercises, key = { "${it.trainingId}_${it.exerciseId}" }) { ex ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExerciseClick(ex.exerciseId) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${ex.orderIndex}. ${ex.name}", style = MaterialTheme.typography.titleMedium)
                    val typeText = if (ex.type == "time") {
                        val sec = ex.customDurationSec ?: ex.defaultDurationSec
                        "Время: $sec сек"
                    } else {
                        val reps = ex.customReps ?: ex.defaultReps
                        "Повторы: $reps"
                    }
                    Text(typeText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

