package com.ermofit.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseCard
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.i18n.appStrings

@OptIn(ExperimentalLayoutApi::class)
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = strings.navSearchAction,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text(strings.searchPlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.submitQuery()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.deleteQuery
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.searchTarget == SearchTarget.PROGRAMS,
                        onClick = { viewModel.selectTarget(SearchTarget.PROGRAMS) },
                        label = { Text(strings.programsLabel) }
                    )
                    FilterChip(
                        selected = uiState.searchTarget == SearchTarget.EXERCISES,
                        onClick = { viewModel.selectTarget(SearchTarget.EXERCISES) },
                        label = { Text(strings.exercisesLabel) }
                    )
                }
            }

            if (uiState.recentQueries.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.recentQueries,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearRecentQueries) {
                            Text(strings.clearHistory)
                        }
                    }
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.recentQueries.forEach { query ->
                            Row(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = query,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { viewModel.applyRecentQuery(query) }
                                )
                                IconButton(
                                    onClick = { viewModel.removeRecentQuery(query) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.deleteQuery
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.query.isNotBlank()) {
                if (uiState.searchTarget == SearchTarget.PROGRAMS) {
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
                }
                if (uiState.searchTarget == SearchTarget.EXERCISES) {
                    item {
                        Text(
                            text = strings.exercisesLabel,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
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
}
