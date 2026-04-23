package com.ermofit.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.data.repository.SeedRepository
import com.ermofit.app.domain.model.ResolvedProgramText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val localDataRepository: LocalDataRepository,
    private val seedRepository: SeedRepository,
    exerciseTextResolver: ExerciseTextResolver,
    programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _selectedLevel = MutableStateFlow(LevelFilter.ALL)
    val selectedLevel: StateFlow<LevelFilter> = _selectedLevel.asStateFlow()

    private val _selectedMuscleGroup = MutableStateFlow(MuscleGroupFilter.ALL)
    val selectedMuscleGroup: StateFlow<MuscleGroupFilter> = _selectedMuscleGroup.asStateFlow()

    private val _selectedSort = MutableStateFlow(SortOption.DEFAULT)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

    private val _selectedMaxDuration = MutableStateFlow(DEFAULT_MAX_DURATION_MINUTES)
    val selectedMaxDuration: StateFlow<Int> = _selectedMaxDuration.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private val baseProgramsFlow = settingsFlow.flatMapLatest { settings ->
        localDataRepository.searchPrograms(
            query = "",
            preferredLang = settings.preferredLang,
            fallbackLang = settings.fallbackLang,
            onlyTranslated = settings.onlyTranslated
        )
    }

    private val filtersFlow = combine(
        _selectedCategoryId,
        _selectedLevel,
        _selectedMuscleGroup,
        _selectedSort,
        _selectedMaxDuration
    ) { selectedCategoryId, selectedLevel, selectedMuscleGroup, selectedSort, selectedMaxDuration ->
        ProgramFilters(
            selectedCategoryId = selectedCategoryId,
            selectedLevel = selectedLevel,
            selectedMuscleGroup = selectedMuscleGroup,
            selectedSort = selectedSort,
            selectedMaxDuration = selectedMaxDuration
        )
    }

    private val filteredProgramsFlow = combine(
        baseProgramsFlow,
        filtersFlow
    ) { programs, filters ->
        val filtered = programs.filter { program ->
            val byCategory = filters.selectedCategoryId == null || program.categoryId == filters.selectedCategoryId
            val byLevel = filters.selectedLevel.matches(program.level)
            val byMuscleGroup = filters.selectedMuscleGroup.matches(program)
            val byDuration = program.durationMinutes <= filters.selectedMaxDuration
            byCategory && byLevel && byMuscleGroup && byDuration
        }
        filters.selectedSort.apply(filtered)
    }

    val recommendedPrograms: Flow<List<ProgramEntity>> = filteredProgramsFlow

    val programTexts: Flow<Map<String, ResolvedProgramText>> =
        programTextResolver.observeTextsForPrograms(recommendedPrograms)

    val categories: StateFlow<List<CategoryEntity>> = settingsFlow.flatMapLatest { settings ->
        localDataRepository.observeProgramCategories(
            langCode = settings.preferredLang,
            onlyTranslated = settings.onlyTranslated
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _slogan = MutableStateFlow("Тренируйся стабильно и фиксируй прогресс.")
    val slogan: StateFlow<String> = _slogan.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    seedRepository.ensureSeedLoaded()
                }
            }.onFailure { throwable ->
                _error.value = throwable.message.orEmpty()
            }
            _isLoading.value = false
        }
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _slogan.value = if (settings.preferredLang == "ru") {
                    ruSlogans.random()
                } else {
                    enSlogans.random()
                }
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun selectLevel(level: LevelFilter) {
        _selectedLevel.value = level
    }

    fun selectMuscleGroup(group: MuscleGroupFilter) {
        _selectedMuscleGroup.value = group
    }

    fun selectSort(sortOption: SortOption) {
        _selectedSort.value = sortOption
    }

    fun selectMaxDuration(minutes: Int) {
        _selectedMaxDuration.value = minutes.coerceIn(MIN_DURATION_MINUTES, DEFAULT_MAX_DURATION_MINUTES)
    }

    fun clearError() {
        _error.value = null
    }

    data class SearchSettings(
        val preferredLang: String,
        val fallbackLang: String,
        val onlyTranslated: Boolean
    )

    private data class ProgramFilters(
        val selectedCategoryId: String?,
        val selectedLevel: LevelFilter,
        val selectedMuscleGroup: MuscleGroupFilter,
        val selectedSort: SortOption,
        val selectedMaxDuration: Int
    )

    enum class LevelFilter {
        ALL,
        BEGINNER,
        INTERMEDIATE,
        ADVANCED;

        fun matches(rawLevel: String): Boolean {
            if (this == ALL) return true
            return when (this) {
                BEGINNER -> rawLevel.equals("beginner", ignoreCase = true)
                INTERMEDIATE -> rawLevel.equals("intermediate", ignoreCase = true)
                ADVANCED -> rawLevel.equals("advanced", ignoreCase = true)
                ALL -> true
            }
        }
    }

    enum class MuscleGroupFilter {
        ALL,
        FULL_BODY,
        ABS,
        LEGS_GLUTES,
        CHEST,
        BACK,
        SHOULDERS_ARMS,
        CARDIO,
        MOBILITY;

        fun matches(program: ProgramEntity): Boolean {
            if (this == ALL) return true
            val id = program.id.lowercase()
            val text = "${program.title} ${program.description}".lowercase()
            return when (this) {
                ALL -> true
                FULL_BODY -> id.contains("30_day") ||
                    id.contains("full_body") ||
                    id.contains("first_gym") ||
                    text.contains("все тело") ||
                    text.contains("full body")
                ABS -> id.contains("belly") ||
                    id.contains("abs") ||
                    id.contains("core") ||
                    text.contains("пресс") ||
                    text.contains("талия") ||
                    text.contains("core")
                LEGS_GLUTES -> id.contains("legs") ||
                    id.contains("glutes") ||
                    id.contains("lower") ||
                    text.contains("ног") ||
                    text.contains("ягод") ||
                    text.contains("legs") ||
                    text.contains("glutes")
                CHEST -> id.contains("chest") ||
                    text.contains("груд") ||
                    text.contains("chest")
                BACK -> id.contains("back") ||
                    id.contains("posture") ||
                    text.contains("спин") ||
                    text.contains("осанк") ||
                    text.contains("back") ||
                    text.contains("posture")
                SHOULDERS_ARMS -> id.contains("shoulders") ||
                    id.contains("arms") ||
                    text.contains("плеч") ||
                    text.contains("рук") ||
                    text.contains("shoulders") ||
                    text.contains("arms")
                CARDIO -> id.contains("cardio") ||
                    id.contains("weight_loss") ||
                    id.contains("interval") ||
                    id.contains("run") ||
                    id.contains("walk") ||
                    text.contains("кардио") ||
                    text.contains("похуд") ||
                    text.contains("интервал") ||
                    text.contains("cardio") ||
                    text.contains("weight loss") ||
                    text.contains("interval")
                MOBILITY -> id.contains("warm") ||
                    id.contains("recovery") ||
                    id.contains("mobility") ||
                    text.contains("размин") ||
                    text.contains("восстанов") ||
                    text.contains("мобил") ||
                    text.contains("warm") ||
                    text.contains("recovery") ||
                    text.contains("mobility")
            }
        }
    }

    enum class SortOption {
        DEFAULT,
        TITLE_ASC,
        DURATION_ASC,
        DURATION_DESC,
        LEVEL_ASC;

        fun apply(programs: List<ProgramEntity>): List<ProgramEntity> {
            return when (this) {
                DEFAULT -> programs
                TITLE_ASC -> programs.sortedBy { it.title.lowercase() }
                DURATION_ASC -> programs.sortedBy { it.durationMinutes }
                DURATION_DESC -> programs.sortedByDescending { it.durationMinutes }
                LEVEL_ASC -> programs.sortedBy { levelWeight(it.level) }
            }
        }

        private fun levelWeight(raw: String): Int {
            return when {
                raw.equals("beginner", ignoreCase = true) -> 0
                raw.equals("intermediate", ignoreCase = true) -> 1
                raw.equals("advanced", ignoreCase = true) -> 2
                else -> 3
            }
        }
    }

    private companion object {
        const val MIN_DURATION_MINUTES = 30
        const val DEFAULT_MAX_DURATION_MINUTES = 90
        val ruSlogans = listOf(
            "Тренируйся стабильно и фиксируй прогресс.",
            "Сильное тело строится повторяемыми привычками.",
            "Лучше короткая тренировка сегодня, чем идеальная завтра."
        )
        val enSlogans = listOf(
            "Train consistently and track your progress.",
            "Strong bodies are built with repeatable habits.",
            "A short workout today beats a perfect one tomorrow."
        )
    }
}

