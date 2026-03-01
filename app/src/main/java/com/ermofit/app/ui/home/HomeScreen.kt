package com.ermofit.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun HomeScreen(
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showControlsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showControlsDialog) {
        HomeControlsDialog(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            selectedLevel = selectedLevel,
            selectedSort = selectedSort,
            onDismiss = { showControlsDialog = false },
            onCategorySelect = viewModel::selectCategory,
            onLevelSelect = viewModel::selectLevel,
            onSortSelect = viewModel::selectSort
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.recommendedProgramsTitle, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { showControlsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = if (isRu) {
                                "\u0424\u0438\u043b\u044c\u0442\u0440\u044b \u0438 \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430"
                            } else {
                                "Filters and sorting"
                            }
                        )
                    }
                }
            }
            if (programs.isEmpty()) {
                item {
                    Text(
                        text = strings.noProgramsFound,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(programs, key = { it.id }) { program ->
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

@Composable
private fun HomeControlsDialog(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    selectedLevel: HomeViewModel.LevelFilter,
    selectedSort: HomeViewModel.SortOption,
    onDismiss: () -> Unit,
    onCategorySelect: (String?) -> Unit,
    onLevelSelect: (HomeViewModel.LevelFilter) -> Unit,
    onSortSelect: (HomeViewModel.SortOption) -> Unit
) {
    val isRu = appLanguage().raw == "ru"
    val sortOptions = listOf(
        HomeViewModel.SortOption.DEFAULT,
        HomeViewModel.SortOption.TITLE_ASC,
        HomeViewModel.SortOption.DURATION_ASC,
        HomeViewModel.SortOption.DURATION_DESC,
        HomeViewModel.SortOption.LEVEL_ASC
    )

    val levelOptions = listOf(
        HomeViewModel.LevelFilter.ALL,
        HomeViewModel.LevelFilter.BEGINNER,
        HomeViewModel.LevelFilter.INTERMEDIATE,
        HomeViewModel.LevelFilter.ADVANCED
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isRu) {
                    "\u0424\u0438\u043b\u044c\u0442\u0440\u044b \u0438 \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430"
                } else {
                    "Filters and sorting"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (isRu) "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f" else "Category",
                    fontWeight = FontWeight.SemiBold
                )
                SelectRow(
                    selected = selectedCategoryId == null,
                    label = if (isRu) {
                        "\u0412\u0441\u0435 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438"
                    } else {
                        "All categories"
                    },
                    onClick = { onCategorySelect(null) }
                )
                categories.forEach { category ->
                    SelectRow(
                        selected = selectedCategoryId == category.id,
                        label = normalizeCategoryLabel(category.title),
                        onClick = { onCategorySelect(category.id) }
                    )
                }

                Text(
                    if (isRu) "\u0423\u0440\u043e\u0432\u0435\u043d\u044c" else "Level",
                    fontWeight = FontWeight.SemiBold
                )
                levelOptions.forEach { level ->
                    SelectRow(
                        selected = selectedLevel == level,
                        label = levelLabel(level, isRu),
                        onClick = { onLevelSelect(level) }
                    )
                }

                Text(
                    if (isRu) "\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430" else "Sorting",
                    fontWeight = FontWeight.SemiBold
                )
                sortOptions.forEach { option ->
                    SelectRow(
                        selected = selectedSort == option,
                        label = sortLabel(option, isRu),
                        onClick = { onSortSelect(option) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRu) "\u0413\u043e\u0442\u043e\u0432\u043e" else "Done")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCategorySelect(null)
                    onLevelSelect(HomeViewModel.LevelFilter.ALL)
                    onSortSelect(HomeViewModel.SortOption.DEFAULT)
                }
            ) {
                Text(if (isRu) "\u0421\u0431\u0440\u043e\u0441\u0438\u0442\u044c" else "Reset")
            }
        }
    )
}

@Composable
private fun SelectRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val prefix = if (selected) "* " else ""
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = prefix + label,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun sortLabel(option: HomeViewModel.SortOption, isRu: Boolean): String {
    return when (option) {
        HomeViewModel.SortOption.DEFAULT ->
            if (isRu) "\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e" else "Default"
        HomeViewModel.SortOption.TITLE_ASC ->
            if (isRu) "\u041f\u043e \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u044e (\u0410-\u042f)" else "Title (A-Z)"
        HomeViewModel.SortOption.DURATION_ASC ->
            if (isRu) "\u0414\u043b\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0441\u0442\u044c: \u043a\u043e\u0440\u043e\u0442\u043a\u0438\u0435" else "Duration: short first"
        HomeViewModel.SortOption.DURATION_DESC ->
            if (isRu) "\u0414\u043b\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0441\u0442\u044c: \u0434\u043b\u0438\u043d\u043d\u044b\u0435" else "Duration: long first"
        HomeViewModel.SortOption.LEVEL_ASC ->
            if (isRu) "\u041f\u043e \u0443\u0440\u043e\u0432\u043d\u044e" else "By level"
    }
}

private fun levelLabel(level: HomeViewModel.LevelFilter, isRu: Boolean): String {
    return when (level) {
        HomeViewModel.LevelFilter.ALL ->
            if (isRu) "\u041b\u044e\u0431\u043e\u0439" else "Any"
        HomeViewModel.LevelFilter.BEGINNER ->
            if (isRu) "\u041d\u043e\u0432\u0438\u0447\u043e\u043a" else "Beginner"
        HomeViewModel.LevelFilter.INTERMEDIATE ->
            if (isRu) "\u0421\u0440\u0435\u0434\u043d\u0438\u0439" else "Intermediate"
        HomeViewModel.LevelFilter.ADVANCED ->
            if (isRu) "\u041f\u0440\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044b\u0439" else "Advanced"
    }
}

private fun normalizeCategoryLabel(raw: String): String {
    return raw
        .replace("[", "")
        .replace("]", "")
        .trim()
}
