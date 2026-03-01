package com.ermofit.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.components.ShimmerPlaceholder
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

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
    val slogan by viewModel.slogan.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let { message ->
            snackbarHostState.showSnackbar(message.ifBlank { strings.unknownStartupError })
            viewModel.clearError()
        }
    }

    LaunchedEffect(sortDialogSignal) {
        if (sortDialogSignal > 0) {
            showSortDialog = true
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
            onSelectCategory = viewModel::selectCategory,
            onSelectLevel = viewModel::selectLevel,
            onSelectSort = viewModel::selectSort,
            onReset = {
                viewModel.selectCategory(null)
                viewModel.selectLevel(HomeViewModel.LevelFilter.ALL)
                viewModel.selectSort(HomeViewModel.SortOption.DEFAULT)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortDialog(
    isRu: Boolean,
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    selectedLevel: HomeViewModel.LevelFilter,
    selectedSort: HomeViewModel.SortOption,
    onSelectCategory: (String?) -> Unit,
    onSelectLevel: (HomeViewModel.LevelFilter) -> Unit,
    onSelectSort: (HomeViewModel.SortOption) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isRu) {
                        "\u0424\u0438\u043b\u044c\u0442\u0440\u044b \u0438 \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430"
                    } else {
                        "Filters and sorting"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isRu) "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438" else "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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

                Text(
                    text = if (isRu) "\u0423\u0440\u043e\u0432\u0435\u043d\u044c" else "Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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

                Text(
                    text = if (isRu) "\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430" else "Sorting",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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

private fun cleanCategoryTitle(raw: String): String {
    return raw
        .replace("[", "")
        .replace("]", "")
        .trim()
}
