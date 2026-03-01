package com.ermofit.app.newplan.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(uiState: StatsUiState) {
    val maxSessions = (uiState.days.maxOfOrNull { it.sessions } ?: 0).coerceAtLeast(1)
    val formatter = DateTimeFormatter.ofPattern("dd.MM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatsBox(
                title = "7 дней",
                value = uiState.workoutsWeek.toString(),
                modifier = Modifier.weight(1f)
            )
            StatsBox(
                title = "30 дней",
                value = uiState.workoutsMonth.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatsBox(
                title = "Общее время",
                value = "${uiState.totalMinutes} мин",
                modifier = Modifier.weight(1f)
            )
            StatsBox(
                title = "Текущий стрик",
                value = "${uiState.currentStreakDays} дн.",
                modifier = Modifier.weight(1f)
            )
        }

        Text("Активность за последние 7 дней", style = MaterialTheme.typography.titleMedium)
        uiState.days.forEach { day ->
            val fraction = day.sessions.toFloat() / maxSessions.toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${day.date.format(formatter)} • ${day.sessions} трен., ${day.minutes} мин")
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .height(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun StatsBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
