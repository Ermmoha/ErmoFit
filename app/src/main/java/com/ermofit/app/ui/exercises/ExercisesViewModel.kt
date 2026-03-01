package com.ermofit.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val localDataRepository: LocalDataRepository,
    exerciseTextResolver: ExerciseTextResolver
) : ViewModel() {

    private val _selectedMuscleGroups = MutableStateFlow<Set<String>>(emptySet())
    val selectedMuscleGroups: StateFlow<Set<String>> = _selectedMuscleGroups.asStateFlow()

    private val _selectedEquipment = MutableStateFlow<Set<String>>(emptySet())
    val selectedEquipment: StateFlow<Set<String>> = _selectedEquipment.asStateFlow()

    private val _selectedSort = MutableStateFlow(SortOption.DEFAULT)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { preferredLang, onlyTranslated ->
        val fallbackLang = if (preferredLang == "ru") "en" else "ru"
        SearchSettings(
            preferredLang = preferredLang,
            fallbackLang = fallbackLang,
            onlyTranslated = onlyTranslated
        )
    }

    private val baseExercisesFlow = settingsFlow.flatMapLatest { settings ->
        localDataRepository.searchExercisesLocalized(
            query = "",
            preferredLang = settings.preferredLang,
            fallbackLang = settings.fallbackLang,
            onlyTranslated = settings.onlyTranslated
        )
    }

    val muscleGroups: StateFlow<List<String>> = baseExercisesFlow
        .map { exercises ->
            exercises
                .map { it.muscleGroup.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val equipmentOptions: StateFlow<List<String>> = baseExercisesFlow
        .map { exercises ->
            exercises
                .flatMap { parseEquipmentValues(it.equipment) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val exercises: Flow<List<ExerciseEntity>> = combine(
        baseExercisesFlow,
        _selectedMuscleGroups,
        _selectedEquipment,
        _selectedSort
    ) { exercises, selectedMuscleGroups, selectedEquipment, selectedSort ->
        val filtered = exercises.filter { exercise ->
            val byMuscle = selectedMuscleGroups.isEmpty() ||
                selectedMuscleGroups.any { it.equals(exercise.muscleGroup, ignoreCase = true) }
            val byEquipment = selectedEquipment.isEmpty() ||
                parseEquipmentValues(exercise.equipment).any { exerciseEquipment ->
                    selectedEquipment.any { it.equals(exerciseEquipment, ignoreCase = true) }
                }
            byMuscle && byEquipment
        }
        selectedSort.apply(filtered)
    }

    val exerciseTexts: Flow<Map<String, ResolvedExerciseText>> =
        exerciseTextResolver.observeTextsForExercises(exercises)

    fun toggleMuscleGroup(muscleGroup: String) {
        _selectedMuscleGroups.value = _selectedMuscleGroups.value.toggleSelection(muscleGroup)
    }

    fun toggleEquipment(equipment: String) {
        _selectedEquipment.value = _selectedEquipment.value.toggleSelection(equipment)
    }

    fun selectSort(sortOption: SortOption) {
        _selectedSort.value = sortOption
    }

    fun resetFilters() {
        _selectedMuscleGroups.value = emptySet()
        _selectedEquipment.value = emptySet()
        _selectedSort.value = SortOption.DEFAULT
    }

    data class SearchSettings(
        val preferredLang: String,
        val fallbackLang: String,
        val onlyTranslated: Boolean
    )

    enum class SortOption {
        DEFAULT,
        TITLE_ASC,
        EQUIPMENT_ASC;

        fun apply(exercises: List<ExerciseEntity>): List<ExerciseEntity> {
            return when (this) {
                DEFAULT -> exercises
                TITLE_ASC -> exercises.sortedBy { it.title.lowercase() }
                EQUIPMENT_ASC -> exercises.sortedBy { it.equipment.lowercase() }
            }
        }
    }

    private fun parseEquipmentValues(raw: String): List<String> {
        return raw
            .split(',', ';', '/', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

private fun Set<String>.toggleSelection(value: String): Set<String> {
    val existing = firstOrNull { it.equals(value, ignoreCase = true) }
    return if (existing == null) this + value else this - existing
}
