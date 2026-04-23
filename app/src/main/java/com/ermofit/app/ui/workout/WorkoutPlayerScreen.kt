package com.ermofit.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseMedia
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings
import kotlin.math.roundToInt

@Composable
fun WorkoutPlayerScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    viewModel: WorkoutPlayerViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showTextMode by rememberSaveable { mutableStateOf(false) }

    if (uiState.isFinished) {
        WorkoutFinishedState(
            title = if (isRu) "Поздравляю, вы справились" else strings.workoutComplete,
            button = strings.finishWorkout,
            onFinish = onFinish
        )
        return
    }

    val totalExercises = uiState.exercises.size
    val isCompletionStep = totalExercises > 0 && uiState.currentIndex >= totalExercises
    if (isCompletionStep) {
        WorkoutCompletionStep(
            title = uiState.programTitle,
            message = if (isRu) "Поздравляю, вы справились" else "Congratulations, you did it",
            finishButton = strings.finishWorkout,
            returnButton = if (isRu) "Вернуться к тренировке" else "Return to workout",
            backContentDescription = strings.navBackAction,
            onBack = onBack,
            onPrevious = viewModel::previous,
            onFinish = {
                viewModel.finish()
                onFinish()
            }
        )
        return
    }

    val current = uiState.exercises.getOrNull(uiState.currentIndex)
    if (current == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(strings.noExercisesForProgram)
        }
        return
    }

    val safeTotalExercises = totalExercises.coerceAtLeast(1)
    val currentPosition = (uiState.currentIndex + 1).coerceAtMost(totalExercises)
    val resolvedText = uiState.exerciseTexts[current.exerciseId]
    val exerciseTitleText = resolvedText?.title?.ifBlank { current.title } ?: current.title
    val exerciseDescriptionText = resolvedText?.description?.ifBlank { current.description } ?: current.description
    val isCurrentFavorite = uiState.favoriteExerciseIds.contains(current.exerciseId)
    val emptyMark = if (isRu) "—" else "-"
    val modeText = when {
        current.defaultDurationSec > 0 -> if (isRu) "По времени" else "Timed"
        current.defaultReps > 0 -> if (isRu) "По повторениям" else "Reps"
        else -> if (isRu) "Не задано" else "Not set"
    }
    val loadText = when {
        current.defaultDurationSec > 0 -> "${current.defaultDurationSec} ${strings.unitSecondsShort}"
        current.defaultReps > 0 -> "${current.defaultReps} ${strings.unitRepsShort}"
        else -> emptyMark
    }
    val tagsText = if (current.tags.isNotEmpty()) current.tags.joinToString(", ") else emptyMark

    val isTransitionRest = uiState.transitionRestSecondsLeft > 0
    val topBarTitle = uiState.programTitle

    val timerTotalSeconds = when {
        isTransitionRest -> uiState.interExerciseRestSec
        current.defaultDurationSec > 0 -> current.defaultDurationSec
        current.defaultReps > 0 && uiState.repsRestSecondsLeft > 0 -> UI_REPS_REST_SEC
        else -> 0
    }.coerceAtLeast(0)
    val timerLeftSeconds = when {
        isTransitionRest -> uiState.transitionRestSecondsLeft
        current.defaultDurationSec > 0 -> uiState.mainTimerSecondsLeft
        current.defaultReps > 0 && uiState.repsRestSecondsLeft > 0 -> uiState.repsRestSecondsLeft
        else -> 0
    }.coerceAtLeast(0)
    val timerElapsedSeconds = (timerTotalSeconds - timerLeftSeconds).coerceAtLeast(0)
    val timerProgress = if (timerTotalSeconds > 0) {
        (timerElapsedSeconds.toFloat() / timerTotalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        (currentPosition.toFloat() / safeTotalExercises.toFloat()).coerceIn(0f, 1f)
    }
    val animatedTimerProgress by animateFloatAsState(
        targetValue = timerProgress,
        animationSpec = tween(durationMillis = 450),
        label = "workout_progress"
    )

    val unifiedColor = MaterialTheme.colorScheme.surface

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(unifiedColor)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val horizontalPadding = (screenWidth * 0.04f).coerceIn(10.dp, 20.dp)
        val verticalPadding = (screenHeight * 0.01f).coerceIn(6.dp, 12.dp)
        val sectionSpacing = (screenHeight * 0.01f).coerceIn(6.dp, 10.dp)
        val cardCorner = (screenWidth * 0.045f).coerceIn(14.dp, 22.dp)
        val cardInnerPadding = (screenWidth * 0.02f).coerceIn(6.dp, 10.dp)
        val mediaCorner = (screenWidth * 0.04f).coerceIn(12.dp, 18.dp)
        val mediaPanelHeight = (screenHeight * 0.4f).coerceIn(220.dp, 340.dp)
        val titleHorizontalPadding = (screenWidth * 0.05f).coerceIn(12.dp, 24.dp)
        val topIconSize = (screenWidth * 0.065f).coerceIn(22.dp, 28.dp)
        val transportIconSize = (screenWidth * 0.1f).coerceIn(30.dp, 42.dp)
        val actionButtonHeight = (screenHeight * 0.05f).coerceIn(34.dp, 44.dp)
        val actionIconSize = (actionButtonHeight * 0.5f).coerceIn(16.dp, 22.dp)
        val topTitleHeight = (screenHeight * 0.04f).coerceIn(26.dp, 34.dp)
        val isCompactHeight = screenHeight < 740.dp
        val controlsToBottomGap = if (isCompactHeight) {
            (screenHeight * 0.002f).coerceIn(0.dp, 2.dp)
        } else {
            (screenHeight * 0.004f).coerceIn(0.dp, 4.dp)
        }
        val contentModifier = if (isCompactHeight) {
            Modifier
                .fillMaxSize()
                .padding(cardInnerPadding)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
                .fillMaxSize()
                .padding(cardInnerPadding)
        }

        Surface(
            shape = RoundedCornerShape(cardCorner),
            color = unifiedColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlatIconAction(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.navBackAction,
                        tint = MaterialTheme.colorScheme.onSurface,
                        size = topIconSize
                    )

                    LeftToRightMarqueeTitle(
                        text = topBarTitle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = titleHorizontalPadding),
                        height = topTitleHeight
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy((screenWidth * 0.03f).coerceIn(12.dp, 28.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlatIconAction(
                            onClick = viewModel::toggleCurrentExerciseFavorite,
                            icon = Icons.Default.Favorite,
                            contentDescription = if (isRu) "Лайк" else "Like",
                            tint = if (isCurrentFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = topIconSize
                        )
                    }
                }

                Box(
                    modifier = if (isCompactHeight) {
                        Modifier
                            .fillMaxWidth()
                            .height(mediaPanelHeight)
                            .padding(vertical = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .height(mediaPanelHeight)
                            .padding(vertical = 4.dp)
                    }
                ) {
                    Surface(
                        shape = RoundedCornerShape(mediaCorner),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = "${current.exerciseId}:$showTextMode",
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(260)) + slideInHorizontally { it / 5 })
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { -it / 5 })
                            },
                            label = "exercise_media_mode"
                        ) { target ->
                            val textMode = target.endsWith(":true")
                            if (textMode) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding((screenWidth * 0.038f).coerceIn(12.dp, 16.dp)),
                                    verticalArrangement = Arrangement.spacedBy((screenHeight * 0.012f).coerceIn(8.dp, 12.dp))
                                ) {
                                    Text(
                                        text = exerciseDescriptionText.ifBlank {
                                            if (isRu) {
                                                "Описание пока отсутствует."
                                            } else {
                                                "Description is not available yet."
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    ExerciseMetaLine(
                                        label = if (isRu) "Режим" else "Mode",
                                        value = modeText
                                    )
                                    ExerciseMetaLine(
                                        label = if (isRu) "Нагрузка" else "Load",
                                        value = loadText
                                    )
                                    ExerciseMetaLine(
                                        label = strings.muscleGroupLabel,
                                        value = current.muscleGroup.ifBlank { emptyMark }
                                    )
                                    ExerciseMetaLine(
                                        label = strings.equipmentLabel,
                                        value = current.equipment.ifBlank { emptyMark }
                                    )
                                    ExerciseMetaLine(
                                        label = if (isRu) "Теги" else "Tags",
                                        value = tagsText
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(mediaCorner)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ExerciseMedia(
                                        mediaType = current.mediaType,
                                        mediaUrl = current.mediaUrl,
                                        fallbackImageUrl = current.fallbackImageUrl,
                                        stableKey = current.exerciseId,
                                        fillHeight = true,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = exerciseTitleText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(240)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(animationSpec = tween(160)) + slideOutVertically { -it / 3 })
                    },
                    label = "exercise_title"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = buildString {
                        append("${strings.exerciseProgress} $currentPosition/$totalExercises")
                        if (current.defaultReps > 0) {
                            append(" • ${current.defaultReps} ${strings.unitRepsShort}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    textAlign = TextAlign.Start
                )

                LinearProgressIndicator(
                    progress = { animatedTimerProgress },
                    color = Color.Black,
                    trackColor = Color.Black.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (
                            current.defaultReps > 0 &&
                            timerLeftSeconds <= 0 &&
                            uiState.repsRestSecondsLeft <= 0 &&
                            uiState.transitionRestSecondsLeft <= 0
                        ) {
                            if (isRu) "Отдых $UI_REPS_REST_SEC сек" else "Rest $UI_REPS_REST_SEC sec"
                        } else {
                            formatTime(timerElapsedSeconds)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (timerTotalSeconds > 0) formatTime(timerTotalSeconds) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportIcon(
                        onClick = viewModel::previous,
                        enabled = uiState.currentIndex > 0,
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = strings.previous,
                        size = transportIconSize
                    )
                    TransportIcon(
                        onClick = viewModel::startPause,
                        enabled = true,
                        icon = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isRunning) strings.pause else strings.start,
                        size = transportIconSize
                    )
                    TransportIcon(
                        onClick = viewModel::next,
                        enabled = uiState.currentIndex < uiState.exercises.size,
                        icon = Icons.Default.SkipNext,
                        contentDescription = strings.next,
                        size = transportIconSize
                    )
                }

                Spacer(modifier = Modifier.height(controlsToBottomGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillIconAction(
                        onClick = { showTextMode = !showTextMode },
                        icon = Icons.Default.Article,
                        contentDescription = if (showTextMode) {
                            if (isRu) "Скрыть описание" else "Hide description"
                        } else {
                            if (isRu) "Показать описание" else "Show description"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        height = actionButtonHeight,
                        iconSize = actionIconSize
                    )
                    PillIconAction(
                        onClick = viewModel::openIconsHelpDialog,
                        icon = Icons.Default.Info,
                        contentDescription = if (isRu) "Инфо" else "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        height = actionButtonHeight,
                        iconSize = actionIconSize
                    )
                }

                if (isTransitionRest) {
                    OutlinedButton(
                        onClick = viewModel::skipTransitionRest,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.skipRest)
                    }
                }

                if (uiState.showIconsHelpDialog) {
                    IconLegendDialog(
                        isRu = isRu,
                        onOk = viewModel::dismissIconsHelpDialog,
                        onNeverShowAgain = viewModel::disableIconsHelpDialog,
                        showNeverAgainButton = uiState.showNeverAgainInIconsHelp
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutFinishedState(
    title: String,
    button: String,
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(button)
                }
            }
        }
    }
}

@Composable
private fun WorkoutCompletionStep(
    title: String,
    message: String,
    finishButton: String,
    returnButton: String,
    backContentDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onFinish: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val horizontalPadding = (screenWidth * 0.04f).coerceIn(10.dp, 20.dp)
        val verticalPadding = (screenHeight * 0.01f).coerceIn(6.dp, 12.dp)
        val sectionSpacing = (screenHeight * 0.01f).coerceIn(6.dp, 10.dp)
        val cardCorner = (screenWidth * 0.045f).coerceIn(14.dp, 22.dp)
        val contentPadding = (screenWidth * 0.02f).coerceIn(6.dp, 10.dp)
        val panelCorner = (screenWidth * 0.04f).coerceIn(12.dp, 18.dp)
        val panelHeight = (screenHeight * 0.42f).coerceIn(240.dp, 360.dp)
        val titleHorizontalPadding = (screenWidth * 0.05f).coerceIn(12.dp, 24.dp)
        val topIconSize = (screenWidth * 0.065f).coerceIn(22.dp, 28.dp)
        val topTitleHeight = (screenHeight * 0.04f).coerceIn(26.dp, 34.dp)
        val buttonHeight = (screenHeight * 0.058f).coerceIn(44.dp, 52.dp)

        Surface(
            shape = RoundedCornerShape(cardCorner),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlatIconAction(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                        size = topIconSize
                    )

                    LeftToRightMarqueeTitle(
                        text = title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = titleHorizontalPadding),
                        height = topTitleHeight
                    )

                    Spacer(modifier = Modifier.size(topIconSize))
                }

                Surface(
                    shape = RoundedCornerShape(panelCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .height(panelHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onFinish,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(buttonHeight)
                        ) {
                            Text(finishButton)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onPrevious,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight)
                ) {
                    Text(returnButton)
                }
            }
        }
    }
}

@Composable
private fun FlatIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PillIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    iconSize: Dp = 18.dp
) {
    Surface(
        color = Color(0x33231218),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun IconLegendDialog(
    isRu: Boolean,
    onOk: () -> Unit,
    onNeverShowAgain: () -> Unit,
    showNeverAgainButton: Boolean
) {
    val title = if (isRu) "Как пользоваться тренировкой" else "How to use workout screen"
    val intro = if (isRu) {
        "Здесь управление как в плеере: верхние иконки работают с тренировкой, нижние - с текущим упражнением."
    } else {
        "This screen works like a player: top icons affect the workout, bottom icons affect current exercise."
    }
    val okText = if (isRu) "Понятно" else "Got it"
    val neverText = if (isRu) "Больше не показывать" else "Don't show again"
    val prevDesc = if (isRu) "Переход к предыдущему упражнению." else "Go to the previous exercise."
    val playDesc = if (isRu) "Запуск или пауза таймера текущего шага." else "Start or pause the current step timer."
    val nextDesc = if (isRu) {
        "Переход к следующему упражнению. После последнего упражнения открывает экран завершения."
    } else {
        "Go to the next exercise. After the last exercise, opens the completion screen."
    }
    val likeDesc = if (isRu) "Добавить упражнение в избранное или убрать из него." else "Add/remove the exercise to favorites."
    val textDesc = if (isRu) "Показать или скрыть описание и теги упражнения." else "Show or hide exercise description and tags."
    val infoDesc = if (isRu) "Открыть эту справку по иконкам." else "Open this icon guide."

    AlertDialog(
        onDismissRequest = onOk,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                IconLegendItem(icon = Icons.Default.SkipPrevious, text = prevDesc)
                IconLegendItem(icon = Icons.Default.PlayArrow, text = playDesc)
                IconLegendItem(icon = Icons.Default.SkipNext, text = nextDesc)
                IconLegendItem(icon = Icons.Default.Favorite, text = likeDesc)
                IconLegendItem(icon = Icons.Default.Article, text = textDesc)
                IconLegendItem(icon = Icons.Default.Info, text = infoDesc)
            }
        },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text(okText)
            }
        },
        dismissButton = if (showNeverAgainButton) {
            {
                TextButton(onClick = onNeverShowAgain) {
                    Text(neverText)
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun IconLegendItem(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.size(30.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ExerciseMetaLine(
    label: String,
    value: String
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LeftToRightMarqueeTitle(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val shouldScroll = boxWidth > 0 && textWidth > boxWidth
    val travel = (boxWidth + textWidth).coerceAtLeast(1)
    val durationMs = (travel * 10).coerceIn(7000, 22000)
    val transition = rememberInfiniteTransition(label = "workout_marquee")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing)
        ),
        label = "workout_marquee_progress"
    )
    val offsetX = if (shouldScroll) {
        (-textWidth.toFloat()) + (boxWidth + textWidth).toFloat() * progress
    } else {
        0f
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxWidth = it.width }
            .height(height)
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (!shouldScroll) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.onSizeChanged { textWidth = it.width }
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .onSizeChanged { textWidth = it.width }
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
            )
        }
    }
}

@Composable
private fun TransportIcon(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 32.dp
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

private fun formatTime(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val secs = clamped % 60
    return "%02d:%02d".format(minutes, secs)
}

private const val UI_REPS_REST_SEC = 30
