package com.ermofit.app.newplan.ui.exercisedetails

import androidx.compose.foundation.Image
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ermofit.app.newplan.ui.common.equipmentLabel
import com.ermofit.app.newplan.ui.common.exerciseTypeLabel
import com.ermofit.app.newplan.ui.common.levelLabel
import com.ermofit.app.newplan.ui.common.muscleLabel
import com.ermofit.app.newplan.ui.common.restrictionLabel
import com.ermofit.app.newplan.ui.common.resolveExerciseMedia

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailsScreen(
    uiState: ExerciseDetailsUiState,
    onToggleFavorite: () -> Unit
) {
    val exercise = uiState.exercise
    if (exercise == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Упражнение не найдено.")
        }
        return
    }

    val context = LocalContext.current
    val mediaId = resolveExerciseMedia(context, exercise.mediaResName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        Image(
            painter = painterResource(mediaId),
            contentDescription = exercise.name,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop
        )

        Text("Техника", style = MaterialTheme.typography.titleMedium)
        Text(exercise.description, style = MaterialTheme.typography.bodyMedium)

        Text("Уровень: ${levelLabel(exercise.level)}")
        Text(
            "${exerciseTypeLabel(exercise.type)}: " + if (exercise.type == "time") {
                "${exercise.defaultDurationSec} сек"
            } else {
                "${exercise.defaultReps} повторов"
            }
        )
        Text("Основная группа: ${muscleLabel(exercise.musclePrimary)}")
        exercise.muscleSecondary?.let { Text("Дополнительная группа: ${muscleLabel(it)}") }

        Text("Оборудование", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.equipmentTags.forEach { tag ->
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(equipmentLabel(tag)) },
                    enabled = false
                )
            }
        }

        if (exercise.contraindications.isNotEmpty()) {
            Text("Ограничения", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exercise.contraindications.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(restrictionLabel(tag)) },
                        enabled = false
                    )
                }
            }
        }

        Button(
            onClick = onToggleFavorite,
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isFavorite) "Убрать из избранного" else "В избранное")
        }
    }
}
