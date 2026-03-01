package com.ermofit.app.newplan.ui.search

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTrainingClick: (String) -> Unit,
    onExerciseClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск тренировок и упражнений") }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(onClick = { onSubmitQuery(uiState.query) }) {
                    Text("Найти")
                }
                Button(onClick = onClearHistory) {
                    Text("Очистить историю")
                }
            }
        }

        if (uiState.query.isBlank()) {
            item {
                Text("Недавние запросы", style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.history.isEmpty()) {
                item { Text("История пуста.") }
            } else {
                items(uiState.history, key = { it }) { query ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHistoryClick(query) }
                    ) {
                        Text(
                            text = query,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        } else {
            item {
                Text("Тренировки", style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.trainings.isEmpty()) {
                item { Text("Нет результатов.") }
            } else {
                items(uiState.trainings, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrainingClick(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            item {
                Text("Упражнения", style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.exercises.isEmpty()) {
                item { Text("Нет результатов.") }
            } else {
                items(uiState.exercises, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseClick(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

