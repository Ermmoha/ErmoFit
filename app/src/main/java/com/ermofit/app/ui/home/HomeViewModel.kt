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

    private val _selectedSort = MutableStateFlow(SortOption.DEFAULT)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

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

    private val filteredProgramsFlow = combine(
        baseProgramsFlow,
        _selectedCategoryId,
        _selectedLevel,
        _selectedSort
    ) { programs, selectedCategoryId, selectedLevel, selectedSort ->
        val filtered = programs.filter { program ->
            val byCategory = selectedCategoryId == null || program.categoryId == selectedCategoryId
            val byLevel = selectedLevel.matches(program.level)
            byCategory && byLevel
        }
        selectedSort.apply(filtered)
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

    fun selectSort(sortOption: SortOption) {
        _selectedSort.value = sortOption
    }

    fun clearError() {
        _error.value = null
    }

    data class SearchSettings(
        val preferredLang: String,
        val fallbackLang: String,
        val onlyTranslated: Boolean
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

