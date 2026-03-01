package com.ermofit.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseProgramCard
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.components.ShimmerPlaceholder
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun ProgramDetailsScreen(
    onExerciseClick: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    viewModel: ProgramDetailsViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it.ifBlank { strings.favoritesUpdateFailed })
            viewModel.clearError()
        }
    }

    if (uiState.isLoading) {
        ProgramDetailsShimmer()
        return
    }

    val program = uiState.program
    if (program == null) {
        Text(text = strings.programNotFound, modifier = Modifier.padding(16.dp))
        return
    }
    val resolvedProgramText = uiState.resolvedProgramText
    val title = resolvedProgramText?.title?.ifBlank { program.title } ?: program.title
    val description = resolvedProgramText?.description?.ifBlank { program.description } ?: program.description

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProgramCard(
                    program = program,
                    titleOverride = title,
                    onClick = {}
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = title, fontWeight = FontWeight.Bold)
                    Text(text = description)
                    Text(text = "${strings.levelLabel}: ${localizedLevel(program.level, isRu)}")
                    Text(text = "${strings.durationLabel}: ${program.durationMinutes} ${strings.unitMinutesShort}")
                    OutlinedButton(onClick = viewModel::toggleFavorite) {
                        Text(
                            text = if (uiState.isFavorite) {
                                strings.removeFromFavorites
                            } else {
                                strings.addToFavorites
                            }
                        )
                    }
                    Button(onClick = { onStartWorkout(program.id) }) {
                        Text(strings.startWorkout)
                    }
                    Text(
                        text = strings.exercisesTitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            items(uiState.exercises, key = { it.exerciseId }) { item ->
                val resolvedText = uiState.exerciseTexts[item.exerciseId]
                ExerciseProgramCard(
                    item = item,
                    titleOverride = resolvedText?.title,
                    descriptionOverride = resolvedText?.description,
                    onClick = { onExerciseClick(item.exerciseId) }
                )
            }
        }
    }
}

@Composable
private fun ProgramDetailsShimmer() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }
        items(3) {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun ShimmerBlock(modifier: Modifier) {
    ShimmerPlaceholder(modifier = modifier)
}

private fun localizedLevel(rawLevel: String, isRu: Boolean): String {
    if (!isRu) return rawLevel
    return when {
        rawLevel.equals("beginner", ignoreCase = true) ->
            "\u041d\u043e\u0432\u0438\u0447\u043e\u043a"
        rawLevel.equals("intermediate", ignoreCase = true) ->
            "\u0421\u0440\u0435\u0434\u043d\u0438\u0439"
        rawLevel.equals("advanced", ignoreCase = true) ->
            "\u041f\u0440\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044b\u0439"
        else -> rawLevel
    }
}
