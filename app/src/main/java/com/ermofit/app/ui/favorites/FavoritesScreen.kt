package com.ermofit.app.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var customProgramsExpanded by rememberSaveable { mutableStateOf(false) }
    var favoriteProgramsExpanded by rememberSaveable { mutableStateOf(false) }
    var favoriteExercisesExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FavoritesExpandableSectionHeader(
                title = if (isRu) "Мои программы" else "My programs",
                count = uiState.customPrograms.size,
                expanded = customProgramsExpanded,
                onToggle = { customProgramsExpanded = !customProgramsExpanded }
            )
        }
        if (customProgramsExpanded) {
            if (uiState.customPrograms.isEmpty()) {
                item {
                    FavoritesEmptyState(
                        text = if (isRu) {
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
        }

        item {
            FavoritesExpandableSectionHeader(
                title = strings.favoriteProgramsTitle,
                count = uiState.programs.size,
                expanded = favoriteProgramsExpanded,
                onToggle = { favoriteProgramsExpanded = !favoriteProgramsExpanded }
            )
        }
        if (favoriteProgramsExpanded) {
            if (uiState.programs.isEmpty()) {
                item {
                    FavoritesEmptyState(text = strings.noFavoritePrograms)
                }
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
        }

        item {
            FavoritesExpandableSectionHeader(
                title = strings.favoriteExercisesTitle,
                count = uiState.exercises.size,
                expanded = favoriteExercisesExpanded,
                onToggle = { favoriteExercisesExpanded = !favoriteExercisesExpanded }
            )
        }
        if (favoriteExercisesExpanded) {
            if (uiState.exercises.isEmpty()) {
                item {
                    FavoritesEmptyState(text = strings.noFavoriteExercises)
                }
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
}

@Composable
private fun FavoritesExpandableSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoritesEmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
