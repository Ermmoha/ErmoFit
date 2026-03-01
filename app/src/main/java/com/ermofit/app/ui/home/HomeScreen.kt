package com.ermofit.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.components.ShimmerPlaceholder
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    sortDialogSignal: Int = 0,
    onProgramClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val programs by viewModel.recommendedPrograms.collectAsStateWithLifecycle(initialValue = emptyList())
    val programTexts by viewModel.programTexts.collectAsStateWithLifecycle(initialValue = emptyMap())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedLevel by viewModel.selectedLevel.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedMaxDuration by viewModel.selectedMaxDuration.collectAsStateWithLifecycle()
    val slogan by viewModel.slogan.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var handledSortDialogSignal by rememberSaveable { mutableStateOf(sortDialogSignal) }

    LaunchedEffect(error) {
        error?.let { message ->
            snackbarHostState.showSnackbar(message.ifBlank { strings.unknownStartupError })
            viewModel.clearError()
        }
    }

    LaunchedEffect(sortDialogSignal) {
        if (sortDialogSignal > handledSortDialogSignal) {
            showSortDialog = true
            handledSortDialogSignal = sortDialogSignal
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.motivationTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = slogan,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                isLoading -> {
                    items(4) {
                        HomeProgramCardShimmer()
                    }
                }

                programs.isEmpty() -> {
                    item {
                        Text(
                            text = if (isRu) {
                                "\u041f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0435 \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u044b \u043f\u043e\u043a\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b."
                            } else {
                                "No matching programs found."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    items(programs, key = ProgramEntity::id) { program ->
                        ProgramCard(
                            program = program,
                            titleOverride = programTexts[program.id]?.title,
                            onClick = { onProgramClick(program.id) }
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            isRu = isRu,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            selectedLevel = selectedLevel,
            selectedSort = selectedSort,
            selectedMaxDuration = selectedMaxDuration,
            onSelectCategory = viewModel::selectCategory,
            onSelectLevel = viewModel::selectLevel,
            onSelectSort = viewModel::selectSort,
            onSelectMaxDuration = viewModel::selectMaxDuration,
            onReset = {
                viewModel.selectCategory(null)
                viewModel.selectLevel(HomeViewModel.LevelFilter.ALL)
                viewModel.selectSort(HomeViewModel.SortOption.DEFAULT)
                viewModel.selectMaxDuration(90)
            },
            onDismiss = { showSortDialog = false }
        )
    }
}

@Composable
private fun HomeProgramCardShimmer() {
    ShimmerPlaceholder(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .clip(RoundedCornerShape(18.dp))
    )
}

private enum class SortSection {
    CATEGORY,
    LEVEL,
    SORT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortDialog(
    isRu: Boolean,
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    selectedLevel: HomeViewModel.LevelFilter,
    selectedSort: HomeViewModel.SortOption,
    selectedMaxDuration: Int,
    onSelectCategory: (String?) -> Unit,
    onSelectLevel: (HomeViewModel.LevelFilter) -> Unit,
    onSelectSort: (HomeViewModel.SortOption) -> Unit,
    onSelectMaxDuration: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedSection by rememberSaveable { mutableStateOf<SortSection?>(null) }
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.9f).dp
    val dialogHorizontalPadding = if (configuration.screenWidthDp < 380) 8.dp else 12.dp
    val scrollState = rememberScrollState()

    val categoryOptions = remember(categories, isRu) {
        buildList {
            add(
                null to if (isRu) {
                    "\u0412\u0441\u0435 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438"
                } else {
                    "All categories"
                }
            )
            categories.forEach { category ->
                add(category.id to cleanCategoryTitle(category.title))
            }
        }
    }
    val levelOptions = if (isRu) {
        listOf(
            HomeViewModel.LevelFilter.ALL to "\u041b\u044e\u0431\u043e\u0439 \u0443\u0440\u043e\u0432\u0435\u043d\u044c",
            HomeViewModel.LevelFilter.BEGINNER to "\u041d\u043e\u0432\u0438\u0447\u043e\u043a",
            HomeViewModel.LevelFilter.INTERMEDIATE to "\u0421\u0440\u0435\u0434\u043d\u0438\u0439",
            HomeViewModel.LevelFilter.ADVANCED to "\u041f\u0440\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044b\u0439"
        )
    } else {
        listOf(
            HomeViewModel.LevelFilter.ALL to "Any level",
            HomeViewModel.LevelFilter.BEGINNER to "Beginner",
            HomeViewModel.LevelFilter.INTERMEDIATE to "Intermediate",
            HomeViewModel.LevelFilter.ADVANCED to "Advanced"
        )
    }
    val sortOptions = if (isRu) {
        listOf(
            HomeViewModel.SortOption.DEFAULT to "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e",
            HomeViewModel.SortOption.TITLE_ASC to "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 (\u0410-\u042f)",
            HomeViewModel.SortOption.DURATION_ASC to "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043a\u043e\u0440\u043e\u0442\u043a\u0438\u0435",
            HomeViewModel.SortOption.DURATION_DESC to "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0434\u043b\u0438\u043d\u043d\u044b\u0435",
            HomeViewModel.SortOption.LEVEL_ASC to "\u0423\u0440\u043e\u0432\u0435\u043d\u044c \u0441\u043b\u043e\u0436\u043d\u043e\u0441\u0442\u0438"
        )
    } else {
        listOf(
            HomeViewModel.SortOption.DEFAULT to "Default",
            HomeViewModel.SortOption.TITLE_ASC to "Title (A-Z)",
            HomeViewModel.SortOption.DURATION_ASC to "Duration: short first",
            HomeViewModel.SortOption.DURATION_DESC to "Duration: long first",
            HomeViewModel.SortOption.LEVEL_ASC to "Level"
        )
    }

    val selectedCategoryLabel = categoryOptions.firstOrNull { it.first == selectedCategoryId }?.second
        ?: categoryOptions.first().second
    val selectedLevelLabel = levelOptions.firstOrNull { it.first == selectedLevel }?.second
        ?: levelOptions.first().second
    val selectedSortLabel = sortOptions.firstOrNull { it.first == selectedSort }?.second
        ?: sortOptions.first().second
    val durationSummary = if (isRu) {
        "\u0414\u043e $selectedMaxDuration \u043c\u0438\u043d"
    } else {
        "Up to $selectedMaxDuration min"
    }

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

                ExpandableSortBlock(
                    title = if (isRu) "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438" else "Categories",
                    summary = selectedCategoryLabel,
                    expanded = expandedSection == SortSection.CATEGORY,
                    onToggle = {
                        expandedSection = if (expandedSection == SortSection.CATEGORY) {
                            null
                        } else {
                            SortSection.CATEGORY
                        }
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryOptions.forEach { (id, label) ->
                            FilterChip(
                                selected = selectedCategoryId == id,
                                onClick = { onSelectCategory(id) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                ExpandableSortBlock(
                    title = if (isRu) "\u0423\u0440\u043e\u0432\u0435\u043d\u044c" else "Level",
                    summary = selectedLevelLabel,
                    expanded = expandedSection == SortSection.LEVEL,
                    onToggle = {
                        expandedSection = if (expandedSection == SortSection.LEVEL) {
                            null
                        } else {
                            SortSection.LEVEL
                        }
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        levelOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedLevel == value,
                                onClick = { onSelectLevel(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                ExpandableSortBlock(
                    title = if (isRu) "\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430" else "Sorting",
                    summary = selectedSortLabel,
                    expanded = expandedSection == SortSection.SORT,
                    onToggle = {
                        expandedSection = if (expandedSection == SortSection.SORT) {
                            null
                        } else {
                            SortSection.SORT
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isRu) "\u0412\u0440\u0435\u043c\u044f" else "Duration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val sliderMin = 30f
                        val sliderMax = 90f
                        Slider(
                            value = selectedMaxDuration.toFloat(),
                            onValueChange = { raw ->
                                val snapped = (((raw - sliderMin) / 5f).roundToInt() * 5 + 30)
                                    .coerceIn(30, 90)
                                onSelectMaxDuration(snapped)
                            },
                            valueRange = sliderMin..sliderMax,
                            steps = 11
                        )
                        Text(
                            text = durationSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
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
private fun ExpandableSortBlock(
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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

private fun cleanCategoryTitle(raw: String): String {
    return raw
        .replace("[", "")
        .replace("]", "")
        .trim()
}
