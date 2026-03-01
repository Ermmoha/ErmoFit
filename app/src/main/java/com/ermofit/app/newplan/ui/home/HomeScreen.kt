package com.ermofit.app.newplan.ui.home

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
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.ui.common.durationLabel
import com.ermofit.app.newplan.ui.common.equipmentLabel
import com.ermofit.app.newplan.ui.common.goalLabel

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenTraining: (String) -> Unit,
    onStartTraining: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = uiState.motivationPhrase,
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Text(
                text = "Тренировка на сегодня",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            val daily = uiState.dailyTraining
            if (daily == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Тренировка пока не создана.")
                        Button(
                            onClick = onRegenerate,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Сгенерировать")
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(daily.training.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${durationLabel(daily.training.durationMinutes)} · ${goalLabel(daily.training.goal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            daily.training.equipmentRequired.joinToString { equipmentLabel(it) },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Button(onClick = { onStartTraining(daily.training.id) }) {
                                Text("Начать")
                            }
                            Button(onClick = onRegenerate) {
                                Text("Перегенерировать")
                            }
                            Button(onClick = { onOpenTraining(daily.training.id) }) {
                                Text("Детали")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Рекомендованные",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (uiState.recommended.isEmpty()) {
            item {
                Text("Нет подходящих тренировок под текущие фильтры.")
            }
        } else {
            items(uiState.recommended, key = { it.id }) { training ->
                TrainingPreviewCard(
                    training = training,
                    onClick = { onOpenTraining(training.id) }
                )
            }
        }

        uiState.error?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TrainingPreviewCard(
    training: TrainingEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(training.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${goalLabel(training.goal)} · ${durationLabel(training.durationMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                training.equipmentRequired.joinToString { equipmentLabel(it) },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

