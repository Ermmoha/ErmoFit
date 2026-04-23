package com.ermofit.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseCard
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun FavoritesScreen(
    onCustomProgramClick: (String) -> Unit,
    onProgramClick: (String) -> Unit,
    onExerciseClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = if (isRu) "Мои программы" else "My programs",
                fontWeight = FontWeight.Bold
            )
        }
        if (uiState.customPrograms.isEmpty()) {
            item {
                Text(
                    if (isRu) {
                        "Пока нет созданных программ."
                    } else {
                        "No created programs yet."
                    }
                )
            }
        } else {
            items(uiState.customPrograms, key = { it.id }) { program ->
                ProgramCard(
                    program = program.toPreviewProgram(),
                    titleOverride = program.title,
                    subtitleOverride = if (isRu) {
                        "${program.exercises.size} упражнений"
                    } else {
                        "${program.exercises.size} exercises"
                    },
                    onClick = { onCustomProgramClick(program.id) }
                )
            }
        }

        item {
            Text(
                text = strings.favoriteProgramsTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (uiState.programs.isEmpty()) {
            item { Text(strings.noFavoritePrograms) }
        } else {
            items(uiState.programs, key = { it.id }) { program ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgramCard(
                        program = program,
                        titleOverride = uiState.programTexts[program.id]?.title,
                        onClick = { onProgramClick(program.id) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { viewModel.toggleProgram(program.id) }) {
                            Text(strings.removeAction)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = strings.favoriteExercisesTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (uiState.exercises.isEmpty()) {
            item { Text(strings.noFavoriteExercises) }
        } else {
            items(uiState.exercises, key = { it.id }) { exercise ->
                val resolvedText = uiState.exerciseTexts[exercise.id]
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseCard(
                        exercise = exercise,
                        titleOverride = resolvedText?.title,
                        descriptionOverride = resolvedText?.description,
                        onClick = { onExerciseClick(exercise.id) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { viewModel.toggleExercise(exercise.id) }) {
                            Text(strings.removeAction)
                        }
                    }
                }
            }
        }
    }
}
