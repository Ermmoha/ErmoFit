package com.ermofit.app.newplan.ui.workoutplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ermofit.app.newplan.ui.common.resolveExerciseMedia

@Composable
fun WorkoutPlayerScreen(
    uiState: WorkoutPlayerUiState,
    onStartPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
    onCompleteReps: () -> Unit,
    onSaveResult: () -> Unit
) {
    if (uiState.loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Загрузка тренировки...")
        }
        return
    }

    uiState.result?.let { result ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Тренировка завершена", style = MaterialTheme.typography.headlineSmall)
            Text("Время: ${result.totalSeconds} сек")
            Text("Выполнено упражнений: ${result.completedExercises}")
            Text("Текущий стрик: ${result.streakDays} дней")
            Button(
                onClick = onSaveResult,
                enabled = !uiState.resultSaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.resultSaved) "Результат сохранен" else "Сохранить результат")
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        return
    }

    val current = uiState.exercises.getOrNull(uiState.currentIndex)
    if (current == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Нет упражнений для тренировки.")
        }
        return
    }

    val context = LocalContext.current
    val media = resolveExerciseMedia(context, current.mediaResName)
    val progress = if (uiState.exercises.isEmpty()) 0f else {
        (uiState.currentIndex + 1).toFloat() / uiState.exercises.size.toFloat()
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450),
        label = "newplan_workout_progress"
    )
    val isLastExercise = uiState.currentIndex == uiState.exercises.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(uiState.trainingTitle, style = MaterialTheme.typography.titleLarge)
        Text("Прогресс: ${uiState.currentIndex + 1}/${uiState.exercises.size}")
        LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.weight(0.3f))

        AnimatedContent(
            targetState = current.id,
            transitionSpec = {
                (fadeIn(animationSpec = tween(260)) + slideInHorizontally { it / 5 })
                    .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { -it / 5 })
            },
            label = "newplan_exercise_card"
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${current.orderIndex}. ${current.name}", style = MaterialTheme.typography.titleLarge)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(media),
                            contentDescription = current.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(current.description, style = MaterialTheme.typography.bodyMedium)
                    if (uiState.isResting) {
                        Text("Отдых: ${uiState.restRemainingSec} сек", style = MaterialTheme.typography.titleMedium)
                    } else if (current.type == "time") {
                        Text("Осталось: ${uiState.remainingSec} сек", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("Повторы: ${current.reps}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.7f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStartPause, modifier = Modifier.weight(1f)) {
                Text(if (uiState.isRunning) "Пауза" else "Старт")
            }
            Button(onClick = onPrev, modifier = Modifier.weight(1f)) {
                Text("Назад")
            }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text("Вперед")
            }
        }

        if (!uiState.isResting && current.type == "reps") {
            Button(onClick = onCompleteReps, modifier = Modifier.fillMaxWidth()) {
                Text("Сделал подход")
            }
        }

        if (isLastExercise) {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Завершить тренировку")
            }
        }
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
