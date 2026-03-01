package com.ermofit.app.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.ui.components.ExerciseCard
import com.ermofit.app.ui.i18n.appLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisesScreen(
    sortDialogSignal: Int = 0,
    onExerciseClick: (String) -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val isRu = appLanguage().raw == "ru"
    val exercises by viewModel.exercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val exerciseTexts by viewModel.exerciseTexts.collectAsStateWithLifecycle(initialValue = emptyMap())
    val muscleGroups by viewModel.muscleGroups.collectAsStateWithLifecycle()
    val equipmentOptions by viewModel.equipmentOptions.collectAsStateWithLifecycle()
    val selectedMuscleGroups by viewModel.selectedMuscleGroups.collectAsStateWithLifecycle()
    val selectedEquipment by viewModel.selectedEquipment.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var handledSortDialogSignal by rememberSaveable { mutableStateOf(sortDialogSignal) }

    LaunchedEffect(sortDialogSignal) {
        if (sortDialogSignal > handledSortDialogSignal) {
            showSortDialog = true
            handledSortDialogSignal = sortDialogSignal
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (exercises.isEmpty()) {
                item {
                    Text(
                        text = if (isRu) {
                            "\u0423\u043f\u0440\u0430\u0436\u043d\u0435\u043d\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b."
                        } else {
                            "No exercises found."
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(exercises, key = ExerciseEntity::id) { exercise ->
                    val resolved = exerciseTexts[exercise.id]
                    ExerciseCard(
                        exercise = exercise,
                        titleOverride = resolved?.title,
                        descriptionOverride = resolved?.description,
                        onClick = { onExerciseClick(exercise.id) }
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        ExercisesSortDialog(
            isRu = isRu,
            muscleGroups = muscleGroups,
            equipmentOptions = equipmentOptions,
            selectedMuscleGroups = selectedMuscleGroups,
            selectedEquipment = selectedEquipment,
            selectedSort = selectedSort,
            onToggleMuscleGroup = viewModel::toggleMuscleGroup,
            onToggleEquipment = viewModel::toggleEquipment,
            onSelectSort = viewModel::selectSort,
            onReset = viewModel::resetFilters,
            onDismiss = { showSortDialog = false }
        )
    }
}

private enum class ExercisesSortSection {
    MUSCLE,
    EQUIPMENT,
    SORT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisesSortDialog(
    isRu: Boolean,
    muscleGroups: List<String>,
    equipmentOptions: List<String>,
    selectedMuscleGroups: Set<String>,
    selectedEquipment: Set<String>,
    selectedSort: ExercisesViewModel.SortOption,
    onToggleMuscleGroup: (String) -> Unit,
    onToggleEquipment: (String) -> Unit,
    onSelectSort: (ExercisesViewModel.SortOption) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedSection by rememberSaveable { mutableStateOf<ExercisesSortSection?>(null) }
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.9f).dp
    val dialogHorizontalPadding = if (configuration.screenWidthDp < 380) 8.dp else 12.dp
    val scrollState = rememberScrollState()

    val sortOptions = if (isRu) {
        listOf(
            ExercisesViewModel.SortOption.DEFAULT to "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e",
            ExercisesViewModel.SortOption.TITLE_ASC to "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 (\u0410-\u042f)",
            ExercisesViewModel.SortOption.EQUIPMENT_ASC to "\u0418\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c (\u0410-\u042f)"
        )
    } else {
        listOf(
            ExercisesViewModel.SortOption.DEFAULT to "Default",
            ExercisesViewModel.SortOption.TITLE_ASC to "Title (A-Z)",
            ExercisesViewModel.SortOption.EQUIPMENT_ASC to "Equipment (A-Z)"
        )
    }

    val selectedMuscleLabel = selectedValuesSummary(
        selectedValues = selectedMuscleGroups,
        emptyLabel = if (isRu) "\u0412\u0441\u0435 \u0433\u0440\u0443\u043f\u043f\u044b \u043c\u044b\u0448\u0446" else "All muscle groups",
        manyPrefix = if (isRu) "\u0412\u044b\u0431\u0440\u0430\u043d\u043e" else "Selected"
    )
    val selectedEquipmentLabel = selectedValuesSummary(
        selectedValues = selectedEquipment,
        emptyLabel = if (isRu) "\u041b\u044e\u0431\u043e\u0439 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c" else "Any equipment",
        manyPrefix = if (isRu) "\u0412\u044b\u0431\u0440\u0430\u043d\u043e" else "Selected"
    )
    val selectedSortLabel = sortOptions.firstOrNull { it.first == selectedSort }?.second
        ?: sortOptions.first().second

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dialogHorizontalPadding, vertical = 8.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isRu) {
                        "\u0424\u0438\u043b\u044c\u0442\u0440\u044b \u0438 \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430"
                    } else {
                        "Filters and sorting"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                ExercisesExpandableSortBlock(
                    title = if (isRu) "\u0413\u0440\u0443\u043f\u043f\u0430 \u043c\u044b\u0448\u0446" else "Muscle group",
                    summary = selectedMuscleLabel,
                    expanded = expandedSection == ExercisesSortSection.MUSCLE,
                    onToggle = {
                        expandedSection = if (expandedSection == ExercisesSortSection.MUSCLE) {
                            null
                        } else {
                            ExercisesSortSection.MUSCLE
                        }
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        muscleGroups.forEach { group ->
                            FilterChip(
                                selected = selectedMuscleGroups.containsIgnoreCase(group),
                                onClick = { onToggleMuscleGroup(group) },
                                label = { Text(group) }
                            )
                        }
                    }
                }

                ExercisesExpandableSortBlock(
                    title = if (isRu) "\u0418\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c" else "Equipment",
                    summary = selectedEquipmentLabel,
                    expanded = expandedSection == ExercisesSortSection.EQUIPMENT,
                    onToggle = {
                        expandedSection = if (expandedSection == ExercisesSortSection.EQUIPMENT) {
                            null
                        } else {
                            ExercisesSortSection.EQUIPMENT
                        }
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        equipmentOptions.forEach { equipment ->
                            FilterChip(
                                selected = selectedEquipment.containsIgnoreCase(equipment),
                                onClick = { onToggleEquipment(equipment) },
                                label = { Text(equipment) }
                            )
                        }
                    }
                }

                ExercisesExpandableSortBlock(
                    title = if (isRu) "\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430" else "Sorting",
                    summary = selectedSortLabel,
                    expanded = expandedSection == ExercisesSortSection.SORT,
                    onToggle = {
                        expandedSection = if (expandedSection == ExercisesSortSection.SORT) {
                            null
                        } else {
                            ExercisesSortSection.SORT
                        }
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedSort == value,
                                onClick = { onSelectSort(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onReset) {
                        Text(if (isRu) "\u0421\u0431\u0440\u043e\u0441\u0438\u0442\u044c" else "Reset")
                    }
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(if (isRu) "\u0413\u043e\u0442\u043e\u0432\u043e" else "Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExercisesExpandableSortBlock(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary,
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

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

private fun selectedValuesSummary(
    selectedValues: Set<String>,
    emptyLabel: String,
    manyPrefix: String
): String {
    return when {
        selectedValues.isEmpty() -> emptyLabel
        selectedValues.size == 1 -> selectedValues.first()
        else -> "$manyPrefix: ${selectedValues.size}"
    }
}

private fun Set<String>.containsIgnoreCase(value: String): Boolean {
    return any { it.equals(value, ignoreCase = true) }
}
