package com.ermofit.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseMedia
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun WorkoutPlayerScreen(
    onFinish: () -> Unit,
    viewModel: WorkoutPlayerViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isFinished) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.workoutComplete,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onFinish,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.backToProgram)
                    }
                }
            }
        }
        return
    }

    val totalExercises = uiState.exercises.size.coerceAtLeast(1)
    val currentPosition = (uiState.currentIndex + 1).coerceAtMost(totalExercises)
    val progress = currentPosition.toFloat() / totalExercises.toFloat()
    val current = uiState.exercises.getOrNull(uiState.currentIndex)
    val programTitleText = uiState.programTitle

    if (current == null) {
        Text(strings.noExercisesForProgram, modifier = Modifier.padding(16.dp))
        return
    }
    val resolvedText = uiState.exerciseTexts[current.exerciseId]
    val exerciseTitleText = resolvedText?.title?.ifBlank { current.title } ?: current.title
    val exerciseDescriptionText = resolvedText?.description?.ifBlank { current.description } ?: current.description

    val isTransitionRest = uiState.transitionRestSecondsLeft > 0
    val statusTitle = when {
        isTransitionRest -> strings.restBeforeNextExercise
        current.defaultDurationSec > 0 -> strings.countdownLabel
        current.defaultReps > 0 && uiState.repsRestSecondsLeft > 0 -> strings.restTimerLabel
        current.defaultReps > 0 -> strings.targetLabel
        else -> strings.customExerciseMode
    }
    val statusValue = when {
        isTransitionRest -> "${uiState.transitionRestSecondsLeft} ${strings.unitSecondsShort}"
        current.defaultDurationSec > 0 -> formatTime(uiState.mainTimerSecondsLeft)
        current.defaultReps > 0 && uiState.repsRestSecondsLeft > 0 -> formatTime(uiState.repsRestSecondsLeft)
        current.defaultReps > 0 -> "${current.defaultReps} ${strings.unitRepsShort}"
        else -> strings.customShort
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = programTitleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CounterPill(
                        text = "${strings.exerciseProgress} $currentPosition / ${uiState.exercises.size}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    CounterPill(
                        text = "${(progress * 100f).toInt()}%",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExerciseMedia(
                    mediaType = current.mediaType,
                    mediaUrl = current.mediaUrl,
                    fallbackImageUrl = current.fallbackImageUrl,
                    stableKey = current.exerciseId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                )
                Text(
                    text = exerciseTitleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (exerciseDescriptionText.isNotBlank()) {
                    Text(
                        text = exerciseDescriptionText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isTransitionRest) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isTransitionRest) {
                    OutlinedButton(
                        onClick = viewModel::skipTransitionRest,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(strings.skipRest)
                    }
                } else if (current.defaultReps > 0 && uiState.repsRestSecondsLeft <= 0) {
                    Text(
                        text = strings.startRestHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = viewModel::previous,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                enabled = uiState.currentIndex > 0
            ) {
                Text(strings.previous)
            }
            Button(
                onClick = viewModel::startPause,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (uiState.isRunning) strings.pause else strings.start)
            }
            OutlinedButton(
                onClick = viewModel::next,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                enabled = uiState.currentIndex < uiState.exercises.lastIndex
            ) {
                Text(strings.next)
            }
        }
        OutlinedButton(
            onClick = viewModel::finish,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(strings.finishWorkout)
        }
    }
}

@Composable
private fun CounterPill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    val m = clamped / 60
    val s = clamped % 60
    return "%02d:%02d".format(m, s)
}
