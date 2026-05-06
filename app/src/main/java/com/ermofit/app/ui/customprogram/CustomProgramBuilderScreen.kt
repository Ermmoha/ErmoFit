package com.ermofit.app.ui.customprogram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.model.MediaType
import com.ermofit.app.data.model.CustomTrainingExercise
import com.ermofit.app.data.model.CustomTrainingProgram
import com.ermofit.app.data.model.CustomTrainingProgramDraft
import com.ermofit.app.data.model.DEFAULT_INTER_EXERCISE_REST_SEC
import com.ermofit.app.data.model.DEFAULT_LEVEL
import com.ermofit.app.data.model.DEFAULT_REPS
import com.ermofit.app.data.model.DEFAULT_REST_SEC
import com.ermofit.app.data.model.DEFAULT_SETS
import com.ermofit.app.data.model.estimateCustomTrainingDurationMinutes
import com.ermofit.app.data.repository.CustomProgramsRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.ui.i18n.appLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomProgramBuilderUiState(
    val title: String = "",
    val description: String = "",
    val level: String = DEFAULT_LEVEL,
    val interExerciseRestSec: Int = DEFAULT_INTER_EXERCISE_REST_SEC,
    val query: String = "",
    val categoryOptions: List<BuilderCategoryOption> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCategoryLabel: String = "",
    val equipmentOptions: List<String> = emptyList(),
    val selectedEquipment: Set<String> = emptySet(),
    val muscleGroupOptions: List<String> = emptyList(),
    val selectedMuscleGroups: Set<String> = emptySet(),
    val availableExercises: List<BuilderAvailableExercise> = emptyList(),
    val selectedExercises: List<BuilderSelectedExercise> = emptyList(),
    val estimatedDurationMinutes: Int = 0,
    val isEditing: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedProgramId: String? = null
)

data class BuilderCategoryOption(
    val id: String?,
    val label: String
)

data class BuilderAvailableExercise(
    val id: String,
    val title: String,
    val description: String,
    val categoryLabel: String,
    val muscleGroup: String,
    val equipment: String,
    val isSelected: Boolean
)

data class BuilderSelectedExercise(
    val exerciseId: String,
    val title: String,
    val description: String,
    val muscleGroup: String,
    val equipment: String,
    val sets: Int,
    val reps: Int,
    val durationSec: Int,
    val restSec: Int,
    val notes: String,
    val coverImageUrl: String
)

private data class SelectedExerciseDraftState(
    val exerciseId: String,
    val sets: Int = DEFAULT_SETS,
    val reps: Int = DEFAULT_REPS,
    val durationSec: Int = 0,
    val restSec: Int = DEFAULT_REST_SEC,
    val notes: String = ""
)

private data class BuilderProgramFields(
    val title: String,
    val description: String,
    val level: String,
    val interExerciseRestSec: Int,
    val query: String,
    val selectedCategoryId: String?,
    val selectedEquipment: Set<String>,
    val selectedMuscleGroups: Set<String>
)

private data class BuilderFormState(
    val title: String,
    val description: String,
    val level: String,
    val interExerciseRestSec: Int,
    val query: String,
    val selectedCategoryId: String?,
    val selectedEquipment: Set<String>,
    val selectedMuscleGroups: Set<String>,
    val selectedDrafts: List<SelectedExerciseDraftState>
)

private data class BuilderResolvedState(
    val form: BuilderFormState,
    val categoryOptions: List<BuilderCategoryOption>,
    val selectedCategoryLabel: String,
    val equipmentOptions: List<String>,
    val muscleGroupOptions: List<String>,
    val availableExercises: List<BuilderAvailableExercise>,
    val selectedExercises: List<BuilderSelectedExercise>,
    val estimatedDurationMinutes: Int
)

private data class BuilderSearchSettings(
    val preferredLang: String,
    val fallbackLang: String,
    val onlyTranslated: Boolean
)

private data class BuilderAvailableData(
    val searchResults: List<ExerciseEntity>,
    val searchTexts: Map<String, ResolvedExerciseText>,
    val categories: List<CategoryEntity>,
    val settings: BuilderSearchSettings
)

private data class BuilderSelectedData(
    val selectedEntities: List<ExerciseEntity>,
    val selectedTexts: Map<String, ResolvedExerciseText>
)

private enum class BuilderFilterSection {
    CATEGORY,
    EQUIPMENT,
    MUSCLE_GROUP
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomProgramBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localDataRepository: LocalDataRepository,
    private val customProgramsRepository: CustomProgramsRepository,
    exerciseTextResolver: ExerciseTextResolver
) : ViewModel() {

    private val editingProgramId: String? = savedStateHandle["programId"]
    private val _title = MutableStateFlow("")
    private val _description = MutableStateFlow("")
    private val _level = MutableStateFlow(DEFAULT_LEVEL)
    private val _interExerciseRestSec = MutableStateFlow(DEFAULT_INTER_EXERCISE_REST_SEC)
    private val _query = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _selectedEquipment = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedMuscleGroups = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedExercises = MutableStateFlow<List<SelectedExerciseDraftState>>(emptyList())
    private val _isInitialLoading = MutableStateFlow(editingProgramId != null)
    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _savedProgramId = MutableStateFlow<String?>(null)
    private var editingProgramApplied = false
    private val editingProgramFlow: Flow<CustomTrainingProgram?> = if (editingProgramId == null) {
        flowOf(null)
    } else {
        customProgramsRepository.observeProgram(editingProgramId)
    }

    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { preferredLang, onlyTranslated ->
        BuilderSearchSettings(
            preferredLang = preferredLang,
            fallbackLang = if (preferredLang == "ru") "en" else "ru",
            onlyTranslated = onlyTranslated
        )
    }

    private val searchResultsFlow: Flow<List<ExerciseEntity>> = combine(
        _query,
        settingsFlow
    ) { query, settings ->
        query.trim() to settings
    }.flatMapLatest { (query, settings) ->
        localDataRepository.searchExercisesLocalized(
            query = query,
            preferredLang = settings.preferredLang,
            fallbackLang = settings.fallbackLang,
            onlyTranslated = settings.onlyTranslated
        )
    }

    private val searchTextMapFlow = exerciseTextResolver.observeTextsForExercises(searchResultsFlow)

    private val exerciseCategoriesFlow: Flow<List<CategoryEntity>> = settingsFlow.flatMapLatest { settings ->
        localDataRepository.observeExerciseCategories(
            langCode = settings.preferredLang,
            onlyTranslated = settings.onlyTranslated
        )
    }

    private val selectedExerciseEntitiesFlow: Flow<List<ExerciseEntity>> = combine(
        _selectedExercises,
        settingsFlow
    ) { drafts, settings ->
        drafts to settings
    }.flatMapLatest { (drafts, settings) ->
        val ids = drafts.map { it.exerciseId }.distinct()
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            localDataRepository.observeExercisesByIds(
                ids = ids,
                langCode = settings.preferredLang,
                onlyTranslated = settings.onlyTranslated
            )
        }
    }

    private val selectedTextMapFlow = exerciseTextResolver.observeTextsForExercises(selectedExerciseEntitiesFlow)

    private val baseProgramFieldsFlow: Flow<BuilderProgramFields> = combine(
        _title,
        _description,
        _level,
        _interExerciseRestSec,
        _query
    ) { title, description, level, interExerciseRestSec, query ->
        BuilderProgramFields(
            title = title,
            description = description,
            level = level,
            interExerciseRestSec = interExerciseRestSec,
            query = query,
            selectedCategoryId = null,
            selectedEquipment = emptySet(),
            selectedMuscleGroups = emptySet()
        )
    }

    private val programFieldsFlow: Flow<BuilderProgramFields> = combine(
        baseProgramFieldsFlow,
        _selectedCategoryId,
        _selectedEquipment,
        _selectedMuscleGroups
    ) { fields, selectedCategoryId, selectedEquipment, selectedMuscleGroups ->
        fields.copy(
            selectedCategoryId = selectedCategoryId,
            selectedEquipment = selectedEquipment,
            selectedMuscleGroups = selectedMuscleGroups
        )
    }

    private val formStateFlow: Flow<BuilderFormState> = combine(
        programFieldsFlow,
        _selectedExercises
    ) { fields, selectedDrafts ->
        BuilderFormState(
            title = fields.title,
            description = fields.description,
            level = fields.level,
            interExerciseRestSec = fields.interExerciseRestSec,
            query = fields.query,
            selectedCategoryId = fields.selectedCategoryId,
            selectedEquipment = fields.selectedEquipment,
            selectedMuscleGroups = fields.selectedMuscleGroups,
            selectedDrafts = selectedDrafts
        )
    }

    private val availableDataFlow: Flow<BuilderAvailableData> = combine(
        searchResultsFlow,
        searchTextMapFlow,
        exerciseCategoriesFlow,
        settingsFlow
    ) { searchResults, searchTexts, categories, settings ->
        BuilderAvailableData(
            searchResults = searchResults,
            searchTexts = searchTexts,
            categories = categories,
            settings = settings
        )
    }

    private val selectedDataFlow: Flow<BuilderSelectedData> = combine(
        selectedExerciseEntitiesFlow,
        selectedTextMapFlow
    ) { selectedEntities, selectedTexts ->
        BuilderSelectedData(
            selectedEntities = selectedEntities,
            selectedTexts = selectedTexts
        )
    }

    private val resolvedStateFlow: Flow<BuilderResolvedState> = combine(
        formStateFlow,
        availableDataFlow,
        selectedDataFlow
    ) { form, availableData, selectedData ->
        val categoryOptions = buildList {
            add(
                BuilderCategoryOption(
                    id = null,
                    label = if (availableData.settings.preferredLang == "ru") {
                        "Любая категория"
                    } else {
                        "Any category"
                    }
                )
            )
            availableData.categories.forEach { category ->
                add(
                    BuilderCategoryOption(
                        id = category.id,
                        label = cleanCategoryTitle(category.title)
                    )
                )
            }
        }
        val categoryLabelById = categoryOptions
            .mapNotNull { option -> option.id?.let { it to option.label } }
            .toMap()

        val selectedEntityById = selectedData.selectedEntities.associateBy { it.id }
        val selectedExercises = form.selectedDrafts.mapNotNull { draft ->
            val exercise = selectedEntityById[draft.exerciseId] ?: return@mapNotNull null
            BuilderSelectedExercise(
                exerciseId = draft.exerciseId,
                title = resolveExerciseTitle(exercise, selectedData.selectedTexts[draft.exerciseId]),
                description = resolveExerciseDescription(exercise, selectedData.selectedTexts[draft.exerciseId]),
                muscleGroup = normalizeMetaValue(exercise.muscleGroup),
                equipment = formatEquipmentValue(exercise.equipment),
                sets = draft.sets,
                reps = draft.reps,
                durationSec = draft.durationSec,
                restSec = draft.restSec,
                notes = draft.notes,
                coverImageUrl = coverImageUrlFor(exercise)
            )
        }

        val selectedIds = selectedExercises.map { it.exerciseId }.toSet()
        val exercisesByCategory = availableData.searchResults.filter { exercise ->
            form.selectedCategoryId == null || exercise.categoryId == form.selectedCategoryId
        }
        val equipmentOptions = exercisesByCategory
            .flatMap { parseEquipmentValues(it.equipment) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val exercisesByEquipment = exercisesByCategory.filter { exercise ->
            form.selectedEquipment.isEmpty() || parseEquipmentValues(exercise.equipment).any { equipment ->
                form.selectedEquipment.containsIgnoreCase(equipment)
            }
        }
        val muscleGroupOptions = exercisesByEquipment
            .map { normalizeMetaValue(it.muscleGroup) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val availableExercises = exercisesByEquipment
            .filter { exercise ->
                form.selectedMuscleGroups.isEmpty() || form.selectedMuscleGroups.any { selectedGroup ->
                    normalizeMetaValue(exercise.muscleGroup).equals(selectedGroup, ignoreCase = true)
                }
            }
            .map { exercise ->
                BuilderAvailableExercise(
                    id = exercise.id,
                    title = resolveExerciseTitle(exercise, availableData.searchTexts[exercise.id]),
                    description = resolveExerciseDescription(exercise, availableData.searchTexts[exercise.id]),
                    categoryLabel = categoryLabelById[exercise.categoryId].orEmpty(),
                    muscleGroup = normalizeMetaValue(exercise.muscleGroup),
                    equipment = formatEquipmentValue(exercise.equipment),
                    isSelected = exercise.id in selectedIds
                )
            }
            .sortedWith(compareBy<BuilderAvailableExercise> { it.isSelected }.thenBy { it.title.lowercase() })

        val estimatedDurationMinutes = estimateCustomTrainingDurationMinutes(
            exercises = selectedExercises.mapIndexed { index, exercise ->
                CustomTrainingExercise(
                    exerciseId = exercise.exerciseId,
                    orderIndex = index,
                    sets = exercise.sets,
                    reps = exercise.reps,
                    durationSec = exercise.durationSec,
                    restSec = exercise.restSec,
                    notes = exercise.notes
                )
            },
            interExerciseRestSec = form.interExerciseRestSec
        )

        BuilderResolvedState(
            form = form,
            categoryOptions = categoryOptions,
            selectedCategoryLabel = categoryOptions.firstOrNull { it.id == form.selectedCategoryId }?.label
                ?: categoryOptions.firstOrNull()?.label.orEmpty(),
            equipmentOptions = equipmentOptions,
            muscleGroupOptions = muscleGroupOptions,
            availableExercises = availableExercises,
            selectedExercises = selectedExercises,
            estimatedDurationMinutes = estimatedDurationMinutes
        )
    }

    val uiState: StateFlow<CustomProgramBuilderUiState> = combine(
        resolvedStateFlow,
        _isInitialLoading,
        _isSaving,
        _error,
        _savedProgramId
    ) { resolved, isInitialLoading, isSaving, error, savedProgramId ->
        CustomProgramBuilderUiState(
            title = resolved.form.title,
            description = resolved.form.description,
            level = resolved.form.level,
            interExerciseRestSec = resolved.form.interExerciseRestSec,
            query = resolved.form.query,
            categoryOptions = resolved.categoryOptions,
            selectedCategoryId = resolved.form.selectedCategoryId,
            selectedCategoryLabel = resolved.selectedCategoryLabel,
            equipmentOptions = resolved.equipmentOptions,
            selectedEquipment = resolved.form.selectedEquipment,
            muscleGroupOptions = resolved.muscleGroupOptions,
            selectedMuscleGroups = resolved.form.selectedMuscleGroups,
            availableExercises = resolved.availableExercises,
            selectedExercises = resolved.selectedExercises,
            estimatedDurationMinutes = resolved.estimatedDurationMinutes,
            isEditing = editingProgramId != null,
            isInitialLoading = isInitialLoading,
            isSaving = isSaving,
            error = error,
            savedProgramId = savedProgramId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CustomProgramBuilderUiState()
    )

    init {
        viewModelScope.launch {
            editingProgramFlow.collect { program ->
                if (!editingProgramApplied && program != null) {
                    applyEditingProgram(program)
                    editingProgramApplied = true
                }
                if (editingProgramId != null) {
                    _isInitialLoading.value = false
                }
            }
        }
        if (editingProgramId == null) {
            _isInitialLoading.value = false
        }
    }

    fun onTitleChanged(value: String) {
        _title.value = value.take(60)
    }

    fun onDescriptionChanged(value: String) {
        _description.value = value.take(240)
    }

    fun selectLevel(level: String) {
        _level.value = level
    }

    fun onInterExerciseRestChanged(value: String) {
        _interExerciseRestSec.value = value.toPositiveInt(maxValue = 300, defaultValue = 0)
    }

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
        _selectedEquipment.value = emptySet()
        _selectedMuscleGroups.value = emptySet()
    }

    fun toggleEquipment(equipment: String) {
        _selectedEquipment.update { current -> current.toggleSelection(equipment) }
    }

    fun toggleMuscleGroup(group: String) {
        _selectedMuscleGroups.update { current -> current.toggleSelection(group) }
    }

    fun resetFilters() {
        _selectedCategoryId.value = null
        _selectedEquipment.value = emptySet()
        _selectedMuscleGroups.value = emptySet()
    }

    fun addExercise(exerciseId: String) {
        _selectedExercises.update { current ->
            if (current.any { it.exerciseId == exerciseId }) {
                current
            } else {
                current + SelectedExerciseDraftState(exerciseId = exerciseId)
            }
        }
    }

    fun removeExercise(exerciseId: String) {
        _selectedExercises.update { current ->
            current.filterNot { it.exerciseId == exerciseId }
        }
    }

    fun moveExerciseUp(exerciseId: String) {
        _selectedExercises.update { current ->
            val index = current.indexOfFirst { it.exerciseId == exerciseId }
            if (index <= 0) return@update current
            current.toMutableList().apply {
                add(index - 1, removeAt(index))
            }
        }
    }

    fun moveExerciseDown(exerciseId: String) {
        _selectedExercises.update { current ->
            val index = current.indexOfFirst { it.exerciseId == exerciseId }
            if (index == -1 || index >= current.lastIndex) return@update current
            current.toMutableList().apply {
                add(index + 1, removeAt(index))
            }
        }
    }

    fun onSetsChanged(exerciseId: String, value: String) {
        updateExercise(exerciseId) { draft ->
            draft.copy(sets = value.toPositiveInt(maxValue = 20, defaultValue = 1).coerceAtLeast(1))
        }
    }

    fun onRepsChanged(exerciseId: String, value: String) {
        updateExercise(exerciseId) { draft ->
            draft.copy(reps = value.toPositiveInt(maxValue = 200, defaultValue = 0))
        }
    }

    fun onDurationChanged(exerciseId: String, value: String) {
        updateExercise(exerciseId) { draft ->
            draft.copy(durationSec = value.toPositiveInt(maxValue = 3600, defaultValue = 0))
        }
    }

    fun onRestChanged(exerciseId: String, value: String) {
        updateExercise(exerciseId) { draft ->
            draft.copy(restSec = value.toPositiveInt(maxValue = 600, defaultValue = 0))
        }
    }

    fun onNotesChanged(exerciseId: String, value: String) {
        updateExercise(exerciseId) { draft ->
            draft.copy(notes = value.take(140))
        }
    }

    fun saveProgram(
        titleRequiredMessage: String,
        exercisesRequiredMessage: String,
        loadRequiredMessage: String,
        fallbackErrorMessage: String
    ) {
        val state = uiState.value
        if (state.title.trim().isBlank()) {
            _error.value = titleRequiredMessage
            return
        }
        if (state.selectedExercises.isEmpty()) {
            _error.value = exercisesRequiredMessage
            return
        }
        if (state.selectedExercises.any { it.reps <= 0 && it.durationSec <= 0 }) {
            _error.value = loadRequiredMessage
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            runCatching {
                val draft = CustomTrainingProgramDraft(
                    title = state.title.trim(),
                    description = state.description.trim(),
                    level = state.level,
                    interExerciseRestSec = state.interExerciseRestSec,
                    coverImageUrl = state.selectedExercises.firstOrNull()?.coverImageUrl.orEmpty(),
                    exercises = state.selectedExercises.mapIndexed { index, exercise ->
                        CustomTrainingExercise(
                            exerciseId = exercise.exerciseId,
                            orderIndex = index,
                            sets = exercise.sets,
                            reps = exercise.reps,
                            durationSec = exercise.durationSec,
                            restSec = exercise.restSec,
                            notes = exercise.notes
                        )
                    }
                )
                if (editingProgramId == null) {
                    customProgramsRepository.createProgram(draft)
                } else {
                    customProgramsRepository.updateProgram(editingProgramId, draft)
                }
            }.onSuccess { programId ->
                _savedProgramId.value = programId
            }.onFailure { throwable ->
                _error.value = throwable.message ?: fallbackErrorMessage
            }
            _isSaving.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun consumeSavedProgram() {
        _savedProgramId.value = null
    }

    private fun updateExercise(
        exerciseId: String,
        transform: (SelectedExerciseDraftState) -> SelectedExerciseDraftState
    ) {
        _selectedExercises.update { current ->
            current.map { draft ->
                if (draft.exerciseId == exerciseId) transform(draft) else draft
            }
        }
    }

    private fun applyEditingProgram(program: CustomTrainingProgram) {
        _title.value = program.title
        _description.value = program.description
        _level.value = program.level.ifBlank { DEFAULT_LEVEL }
        _interExerciseRestSec.value = program.interExerciseRestSec
        _selectedExercises.value = program.exercises
            .sortedBy { it.orderIndex }
            .map { exercise ->
                SelectedExerciseDraftState(
                    exerciseId = exercise.exerciseId,
                    sets = exercise.sets,
                    reps = exercise.reps,
                    durationSec = exercise.durationSec,
                    restSec = exercise.restSec,
                    notes = exercise.notes
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomProgramBuilderScreen(
    onProgramSaved: (String) -> Unit,
    viewModel: CustomProgramBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRu = appLanguage().raw == "ru"
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedSection by rememberSaveable { mutableStateOf<BuilderFilterSection?>(BuilderFilterSection.CATEGORY) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.savedProgramId) {
        uiState.savedProgramId?.let { programId ->
            viewModel.consumeSavedProgram()
            onProgramSaved(programId)
        }
    }

    if (uiState.isInitialLoading) {
        BuilderLoadingScreen(isRu = isRu)
        return
    }

    val levels = if (isRu) {
        listOf(
            DEFAULT_LEVEL to "Новичок",
            "intermediate" to "Средний",
            "advanced" to "Продвинутый"
        )
    } else {
        listOf(
            DEFAULT_LEVEL to "Beginner",
            "intermediate" to "Intermediate",
            "advanced" to "Advanced"
        )
    }
    val equipmentSummary = selectedValuesSummary(
        selectedValues = uiState.selectedEquipment,
        emptyLabel = if (isRu) "Любой инвентарь" else "Any equipment",
        manyPrefix = if (isRu) "Выбрано" else "Selected"
    )
    val muscleSummary = selectedValuesSummary(
        selectedValues = uiState.selectedMuscleGroups,
        emptyLabel = if (isRu) "Все группы мышц" else "All muscle groups",
        manyPrefix = if (isRu) "Выбрано" else "Selected"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        viewModel.saveProgram(
                            titleRequiredMessage = if (isRu) {
                                "Введите название программы."
                            } else {
                                "Enter a program title."
                            },
                            exercisesRequiredMessage = if (isRu) {
                                "Добавьте хотя бы одно упражнение."
                            } else {
                                "Add at least one exercise."
                            },
                            loadRequiredMessage = if (isRu) {
                                "Для каждого упражнения укажите повторения или время."
                            } else {
                                "Set reps or duration for every exercise."
                            },
                            fallbackErrorMessage = if (isRu) {
                                if (uiState.isEditing) {
                                    "Не удалось обновить программу."
                                } else {
                                    "Не удалось создать программу."
                                }
                            } else {
                                if (uiState.isEditing) {
                                    "Failed to update the program."
                                } else {
                                    "Failed to create the program."
                                }
                            }
                        )
                    },
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = when {
                            uiState.isSaving && uiState.isEditing ->
                                if (isRu) "Сохраняем..." else "Saving..."
                            uiState.isSaving ->
                                if (isRu) "Создаём..." else "Creating..."
                            uiState.isEditing ->
                                if (isRu) "Сохранить изменения" else "Save changes"
                            else ->
                                if (isRu) "Создать" else "Create"
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                BuilderHeroCard(
                    isRu = isRu,
                    isEditing = uiState.isEditing,
                    selectedExercisesCount = uiState.selectedExercises.size,
                    estimatedDurationMinutes = uiState.estimatedDurationMinutes,
                    selectedCategoryLabel = uiState.selectedCategoryLabel
                )
            }

            item {
                BuilderSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle(
                            title = if (isRu) "Параметры программы" else "Program settings",
                            subtitle = if (isRu) {
                                "Задайте название, описание, уровень и отдых между упражнениями."
                            } else {
                                "Set the title, description, level, and rest between exercises."
                            }
                        )
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = viewModel::onTitleChanged,
                            label = {
                                Text(if (isRu) "Название программы" else "Program title")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChanged,
                            label = { Text(if (isRu) "Описание" else "Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isRu) "Уровень сложности" else "Difficulty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                levels.forEach { (value, label) ->
                                    FilterChip(
                                        selected = uiState.level == value,
                                        onClick = { viewModel.selectLevel(value) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                        NumericEditorField(
                            value = uiState.interExerciseRestSec.toString(),
                            onValueChange = viewModel::onInterExerciseRestChanged,
                            label = if (isRu) {
                                "Отдых между упражнениями, сек"
                            } else {
                                "Rest between exercises, sec"
                            }
                        )
                    }
                }
            }

            item {
                BuilderSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle(
                            title = if (isRu) "Фильтры упражнений" else "Exercise filters",
                            subtitle = if (isRu) {
                                "Категория задаёт направление, а инвентарь и группы мышц уточняют выбор."
                            } else {
                                "Category narrows the direction, then equipment and muscle groups refine it."
                            }
                        )
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChanged,
                            label = { Text(if (isRu) "Поиск упражнений" else "Search exercises") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        BuilderExpandableFilterBlock(
                            title = if (isRu) "Категория" else "Category",
                            summary = uiState.selectedCategoryLabel,
                            expanded = expandedSection == BuilderFilterSection.CATEGORY,
                            onToggle = {
                                expandedSection = if (expandedSection == BuilderFilterSection.CATEGORY) {
                                    null
                                } else {
                                    BuilderFilterSection.CATEGORY
                                }
                            }
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.categoryOptions.forEach { option ->
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == option.id,
                                        onClick = { viewModel.selectCategory(option.id) },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                        }
                        BuilderExpandableFilterBlock(
                            title = if (isRu) "Инвентарь" else "Equipment",
                            summary = equipmentSummary,
                            expanded = expandedSection == BuilderFilterSection.EQUIPMENT,
                            onToggle = {
                                expandedSection = if (expandedSection == BuilderFilterSection.EQUIPMENT) {
                                    null
                                } else {
                                    BuilderFilterSection.EQUIPMENT
                                }
                            }
                        ) {
                            if (uiState.equipmentOptions.isEmpty()) {
                                Text(
                                    text = if (isRu) {
                                        "Для текущей категории инвентарь пока не найден."
                                    } else {
                                        "No equipment options found for the current category."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.equipmentOptions.forEach { equipment ->
                                        FilterChip(
                                            selected = uiState.selectedEquipment.containsIgnoreCase(equipment),
                                            onClick = { viewModel.toggleEquipment(equipment) },
                                            label = { Text(equipment) }
                                        )
                                    }
                                }
                            }
                        }
                        BuilderExpandableFilterBlock(
                            title = if (isRu) "Группы мышц" else "Muscle groups",
                            summary = muscleSummary,
                            expanded = expandedSection == BuilderFilterSection.MUSCLE_GROUP,
                            onToggle = {
                                expandedSection = if (expandedSection == BuilderFilterSection.MUSCLE_GROUP) {
                                    null
                                } else {
                                    BuilderFilterSection.MUSCLE_GROUP
                                }
                            }
                        ) {
                            if (uiState.muscleGroupOptions.isEmpty()) {
                                Text(
                                    text = if (isRu) {
                                        "Для текущего набора фильтров группы мышц пока не найдены."
                                    } else {
                                        "No muscle groups found for the current filters."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.muscleGroupOptions.forEach { group ->
                                        FilterChip(
                                            selected = uiState.selectedMuscleGroups.containsIgnoreCase(group),
                                            onClick = { viewModel.toggleMuscleGroup(group) },
                                            label = { Text(group) }
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = viewModel::resetFilters) {
                                Text(if (isRu) "Сбросить фильтры" else "Reset filters")
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = if (isRu) "Выбранные упражнения" else "Selected exercises",
                    subtitle = if (uiState.selectedExercises.isEmpty()) {
                        if (isRu) {
                            "Пока пусто. Добавьте упражнения из списка ниже."
                        } else {
                            "Nothing selected yet. Add exercises from the list below."
                        }
                    } else {
                        if (isRu) {
                            "${uiState.selectedExercises.size} в программе"
                        } else {
                            "${uiState.selectedExercises.size} in program"
                        }
                    }
                )
            }

            if (uiState.selectedExercises.isEmpty()) {
                item {
                    EmptySelectionCard(
                        text = if (isRu) {
                            "У каждого упражнения можно настроить подходы, повторы, таймер, отдых и заметку."
                        } else {
                            "Each exercise can have its own sets, reps, timer, rest, and note."
                        }
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.selectedExercises,
                    key = { index, exercise -> "selected_${index}_${exercise.exerciseId}" }
                ) { _, exercise ->
                    SelectedExerciseEditorCard(
                        exercise = exercise,
                        isRu = isRu,
                        isFirst = uiState.selectedExercises.firstOrNull()?.exerciseId == exercise.exerciseId,
                        isLast = uiState.selectedExercises.lastOrNull()?.exerciseId == exercise.exerciseId,
                        onMoveUp = { viewModel.moveExerciseUp(exercise.exerciseId) },
                        onMoveDown = { viewModel.moveExerciseDown(exercise.exerciseId) },
                        onRemove = { viewModel.removeExercise(exercise.exerciseId) },
                        onSetsChange = { viewModel.onSetsChanged(exercise.exerciseId, it) },
                        onRepsChange = { viewModel.onRepsChanged(exercise.exerciseId, it) },
                        onDurationChange = { viewModel.onDurationChanged(exercise.exerciseId, it) },
                        onRestChange = { viewModel.onRestChanged(exercise.exerciseId, it) },
                        onNotesChange = { viewModel.onNotesChanged(exercise.exerciseId, it) }
                    )
                }
            }

            item {
                SectionTitle(
                    title = if (isRu) "Упражнения для добавления" else "Exercises to add",
                    subtitle = if (isRu) {
                        "Сначала выберите категорию, затем уточните инвентарь и мышцы для точного списка."
                    } else {
                        "Start with a category, then refine the list with equipment and muscle groups."
                    }
                )
            }

            if (uiState.availableExercises.isEmpty()) {
                item {
                    EmptySelectionCard(
                        text = if (isRu) {
                            "По текущим фильтрам упражнения не найдены. Попробуйте другую категорию, инвентарь, мышцы или запрос."
                        } else {
                            "No exercises match the current filters. Try another category, equipment, muscle group, or query."
                        }
                    )
                }
            } else {
                items(
                    items = uiState.availableExercises,
                    key = { exercise -> "available_${exercise.id}" }
                ) { exercise ->
                    AvailableExerciseCard(
                        exercise = exercise,
                        isRu = isRu,
                        onAdd = { viewModel.addExercise(exercise.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuilderLoadingScreen(isRu: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(if (isRu) "Загружаем программу..." else "Loading program...")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderHeroCard(
    isRu: Boolean,
    isEditing: Boolean,
    selectedExercisesCount: Int,
    estimatedDurationMinutes: Int,
    selectedCategoryLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEditing) {
                        if (isRu) "Доработайте программу под свой ритм" else "Refine your program around your rhythm"
                    } else {
                        if (isRu) "Соберите программу под свой ритм" else "Build a program around your rhythm"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isRu) {
                        "Категория помогает быстро сузить упражнения, а дальше вы уточняете подбор инвентарём и группами мышц."
                    } else {
                        "Category narrows the exercise pool first, then equipment and muscle groups sharpen the selection."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BuilderSummaryChip(
                        text = if (isRu) {
                            "$selectedExercisesCount упражнений"
                        } else {
                            "$selectedExercisesCount exercises"
                        }
                    )
                    BuilderSummaryChip(
                        text = if (isRu) {
                            "$estimatedDurationMinutes мин"
                        } else {
                            "$estimatedDurationMinutes min"
                        }
                    )
                    BuilderSummaryChip(text = selectedCategoryLabel)
                }
            }
        }
    }
}

@Composable
private fun BuilderSummaryChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun BuilderSectionCard(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptySelectionCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun BuilderExpandableFilterBlock(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableExerciseCard(
    exercise: BuilderAvailableExercise,
    isRu: Boolean,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = exercise.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (exercise.isSelected) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                            Text(if (isRu) "Добавлено" else "Added")
                        }
                    }
                } else {
                    OutlinedButton(onClick = onAdd) {
                        Text(if (isRu) "Добавить" else "Add")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (exercise.categoryLabel.isNotBlank()) {
                    MetaChip(
                        text = if (isRu) {
                            "Категория: ${exercise.categoryLabel}"
                        } else {
                            "Category: ${exercise.categoryLabel}"
                        }
                    )
                }
                MetaChip(
                    text = if (isRu) {
                        "Мышцы: ${exercise.muscleGroup.ifBlank { "не указано" }}"
                    } else {
                        "Muscles: ${exercise.muscleGroup.ifBlank { "not specified" }}"
                    }
                )
                MetaChip(
                    text = if (isRu) {
                        "Инвентарь: ${exercise.equipment.ifBlank { "не указан" }}"
                    } else {
                        "Equipment: ${exercise.equipment.ifBlank { "not specified" }}"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedExerciseEditorCard(
    exercise: BuilderSelectedExercise,
    isRu: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetsChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onRestChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = exercise.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaChip(
                    text = if (isRu) {
                        "Мышцы: ${exercise.muscleGroup.ifBlank { "не указано" }}"
                    } else {
                        "Muscles: ${exercise.muscleGroup.ifBlank { "not specified" }}"
                    }
                )
                MetaChip(
                    text = if (isRu) {
                        "Инвентарь: ${exercise.equipment.ifBlank { "не указан" }}"
                    } else {
                        "Equipment: ${exercise.equipment.ifBlank { "not specified" }}"
                    }
                )
            }

            Text(
                text = if (isRu) {
                    "Укажите повторения или время. Если заполнены оба поля, таймер будет приоритетным."
                } else {
                    "Set reps or duration. If both are filled, duration will be prioritized."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEditorField(
                    value = exercise.sets.toString(),
                    onValueChange = onSetsChange,
                    label = if (isRu) "Подходы" else "Sets",
                    modifier = Modifier.weight(1f)
                )
                NumericEditorField(
                    value = exercise.reps.toString(),
                    onValueChange = onRepsChange,
                    label = if (isRu) "Повторы" else "Reps",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEditorField(
                    value = exercise.durationSec.toString(),
                    onValueChange = onDurationChange,
                    label = if (isRu) "Время, сек" else "Time, sec",
                    modifier = Modifier.weight(1f)
                )
                NumericEditorField(
                    value = exercise.restSec.toString(),
                    onValueChange = onRestChange,
                    label = if (isRu) "Отдых, сек" else "Rest, sec",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = exercise.notes,
                onValueChange = onNotesChange,
                label = { Text(if (isRu) "Заметка" else "Note") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun NumericEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

private fun selectedValuesSummary(
    selectedValues: Set<String>,
    emptyLabel: String,
    manyPrefix: String
): String {
    return when (selectedValues.size) {
        0 -> emptyLabel
        1 -> selectedValues.first()
        2 -> selectedValues.joinToString(", ")
        else -> "$manyPrefix: ${selectedValues.size}"
    }
}

private fun String.toPositiveInt(
    maxValue: Int,
    defaultValue: Int
): Int {
    return filter(Char::isDigit)
        .toIntOrNull()
        ?.coerceIn(0, maxValue)
        ?: defaultValue
}

private fun resolveExerciseTitle(
    exercise: ExerciseEntity,
    text: ResolvedExerciseText?
): String {
    return text?.title
        ?.takeIf {
            it.isNotBlank() &&
                !it.equals("No translation", ignoreCase = true) &&
                !it.equals("Нет перевода", ignoreCase = true)
        }
        ?: exercise.title
}

private fun resolveExerciseDescription(
    exercise: ExerciseEntity,
    text: ResolvedExerciseText?
): String {
    return text?.description
        ?.takeIf {
            it.isNotBlank() &&
                !it.equals("No translation", ignoreCase = true) &&
                !it.equals("Нет перевода", ignoreCase = true)
        }
        ?: exercise.description
}

private fun coverImageUrlFor(exercise: ExerciseEntity): String {
    return exercise.fallbackImageUrl
        .orEmpty()
        .ifBlank {
            if (exercise.mediaType == MediaType.IMAGE || exercise.mediaType == MediaType.GIF) {
                exercise.mediaUrl
            } else {
                ""
            }
        }
}

private fun cleanCategoryTitle(raw: String): String {
    return raw
        .replace("[", "")
        .replace("]", "")
        .trim()
}

private fun normalizeMetaValue(raw: String): String {
    return raw
        .replace("[", "")
        .replace("]", "")
        .trim()
}

private fun formatEquipmentValue(raw: String): String {
    return parseEquipmentValues(raw).joinToString(", ")
}

private fun parseEquipmentValues(raw: String): List<String> {
    return normalizeMetaValue(raw)
        .split(',', ';', '/', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun Set<String>.toggleSelection(value: String): Set<String> {
    val existing = firstOrNull { it.equals(value, ignoreCase = true) }
    return if (existing == null) this + value else this - existing
}

private fun Set<String>.containsIgnoreCase(value: String): Boolean {
    return any { it.equals(value, ignoreCase = true) }
}
