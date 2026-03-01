package com.ermofit.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseCard
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun SearchScreen(
    onProgramClick: (String) -> Unit,
    onExerciseClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.ifBlank { strings.searchStoreFailed })
            viewModel.clearError()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text(strings.searchPlaceholder) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::submitQuery) {
                            Text(strings.saveQuery)
                        }
                        if (uiState.recentQueries.isNotEmpty()) {
                            OutlinedButton(onClick = viewModel::clearRecentQueries) {
                                Text(strings.clearHistory)
                            }
                        }
                    }
                }
            }

            if (uiState.recentQueries.isNotEmpty()) {
                item { Text(text = strings.recentQueries, fontWeight = FontWeight.Bold) }
                items(uiState.recentQueries, key = { it }) { query ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = query,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.applyRecentQuery(query) }
                        )
                        IconButton(onClick = { viewModel.removeRecentQuery(query) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = strings.deleteQuery)
                        }
                    }
                }
            }

            if (uiState.query.isNotBlank()) {
                item { Text(text = strings.programsLabel, fontWeight = FontWeight.Bold) }
                if (uiState.programs.isEmpty()) {
                    item { Text(strings.noProgramsFound) }
                } else {
                    items(uiState.programs, key = { it.id }) { program ->
                        ProgramCard(
                            program = program,
                            titleOverride = uiState.programTexts[program.id]?.title,
                            onClick = {
                                viewModel.submitQuery()
                                onProgramClick(program.id)
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = strings.exercisesLabel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (uiState.exercises.isEmpty()) {
                    item { Text(strings.noExercisesFound) }
                } else {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        val resolvedText = uiState.exerciseTexts[exercise.id]
                        ExerciseCard(
                            exercise = exercise,
                            titleOverride = resolvedText?.title,
                            descriptionOverride = resolvedText?.description,
                            onClick = {
                                viewModel.submitQuery()
                                onExerciseClick(exercise.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
