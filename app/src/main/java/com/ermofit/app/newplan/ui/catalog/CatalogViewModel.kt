package com.ermofit.app.newplan.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogTab {
    TRAININGS,
    EXERCISES
}

data class CatalogFilters(
    val tab: CatalogTab = CatalogTab.TRAININGS,
    val goal: String? = null,
    val level: String? = null,
    val durationMinutes: Int? = null,
    val equipment: Set<String> = emptySet(),
    val restrictions: Set<String> = emptySet(),
    val query: String = ""
)

data class CatalogUiState(
    val filters: CatalogFilters = CatalogFilters(),
    val trainings: List<TrainingEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList()
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: NewPlanRepository
) : ViewModel() {

    private val filters = MutableStateFlow(CatalogFilters())

    private val trainingsFlow = filters.flatMapLatest { f ->
        repository.getTrainingsByFilters(
            goal = f.goal,
            level = f.level,
            durationMinutes = f.durationMinutes,
            equipmentTags = f.equipment.toList(),
            restrictions = f.restrictions.toList()
        )
    }

    private val exercisesFlow = filters.flatMapLatest { f ->
        repository.getExercisesByFilters(
            goal = f.goal,
            level = f.level,
            equipmentTags = f.equipment.toList(),
            restrictions = f.restrictions.toList()
        )
    }

    val uiState: StateFlow<CatalogUiState> = combine(
        filters,
        trainingsFlow,
        exercisesFlow
    ) { f, trainings, exercises ->
        val query = f.query.trim().lowercase()
        val trainingsResult = trainings
            .filterNot { it.isGenerated }
            .filter { query.isBlank() || it.title.lowercase().contains(query) }
        val exercisesResult = exercises
            .filter { query.isBlank() || it.name.lowercase().contains(query) }
        CatalogUiState(
            filters = f,
            trainings = trainingsResult,
            exercises = exercisesResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogUiState()
    )

    init {
        viewModelScope.launch {
            val settings = repository.getCurrentSettings()
            filters.value = filters.value.copy(
                goal = settings.goal,
                level = settings.level,
                durationMinutes = settings.durationMinutes,
                equipment = settings.equipmentOwned.toSet(),
                restrictions = settings.restrictions.toSet()
            )
        }
    }

    fun setTab(tab: CatalogTab) = filters.update { it.copy(tab = tab) }
    fun setGoal(goal: String?) = filters.update { it.copy(goal = goal) }
    fun setLevel(level: String?) = filters.update { it.copy(level = level) }
    fun setDuration(minutes: Int?) = filters.update { it.copy(durationMinutes = minutes) }
    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun toggleEquipment(tag: String) = filters.update {
        val next = it.equipment.toMutableSet()
        if (tag in next) next.remove(tag) else next.add(tag)
        it.copy(equipment = next)
    }

    fun toggleRestriction(tag: String) = filters.update {
        val next = it.restrictions.toMutableSet()
        if (tag in next) next.remove(tag) else next.add(tag)
        it.copy(restrictions = next)
    }
}

