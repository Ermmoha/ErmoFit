package com.ermofit.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.model.CustomTrainingProgram
import com.ermofit.app.data.repository.CustomProgramsRepository
import com.ermofit.app.data.repository.FavoritesRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.model.ResolvedProgramText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.domain.usecase.ProgramTextResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val customPrograms: List<CustomTrainingProgram> = emptyList(),
    val programs: List<ProgramEntity> = emptyList(),
    val programTexts: Map<String, ResolvedProgramText> = emptyMap(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val customProgramsRepository: CustomProgramsRepository,
    private val localDataRepository: LocalDataRepository,
    exerciseTextResolver: ExerciseTextResolver,
    programTextResolver: ProgramTextResolver
) : ViewModel() {

    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { langCode, onlyTranslated ->
        langCode to onlyTranslated
    }

    private val favoriteProgramsFlow = combine(
        favoritesRepository.observeFavoriteProgramIds(),
        settingsFlow
    ) { ids, settings ->
        ids to settings
    }.flatMapLatest { (ids, settings) ->
        localDataRepository.observeProgramsByIds(
            ids = ids,
            langCode = settings.first,
            onlyTranslated = settings.second
        ).map { list ->
            list.sortedBy { entity -> ids.indexOf(entity.id).coerceAtLeast(0) }
        }
    }

    private val favoriteExercisesFlow = combine(
        favoritesRepository.observeFavoriteExerciseIds(),
        settingsFlow
    ) { ids, settings ->
        ids to settings
    }.flatMapLatest { (ids, settings) ->
        localDataRepository.observeExercisesByIds(
            ids = ids,
            langCode = settings.first,
            onlyTranslated = settings.second
        ).map { list ->
            list.sortedBy { entity -> ids.indexOf(entity.id).coerceAtLeast(0) }
        }
    }

    private val favoriteProgramTextsFlow = programTextResolver.observeTextsForPrograms(favoriteProgramsFlow)
    private val favoriteExerciseTextsFlow = exerciseTextResolver.observeTextsForExercises(favoriteExercisesFlow)
    private val customProgramsFlow = customProgramsRepository.observePrograms()
        .catch { emit(emptyList()) }

    val uiState: StateFlow<FavoritesUiState> = combine(
        customProgramsFlow,
        favoriteProgramsFlow,
        favoriteProgramTextsFlow,
        favoriteExercisesFlow,
        favoriteExerciseTextsFlow
    ) { customPrograms, programs, programTexts, exercises, exerciseTexts ->
        FavoritesUiState(
            customPrograms = customPrograms,
            programs = programs,
            programTexts = programTexts,
            exercises = exercises,
            exerciseTexts = exerciseTexts
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FavoritesUiState()
    )

    fun toggleProgram(programId: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.toggleFavoriteProgram(programId)
            }
        }
    }

    fun toggleExercise(exerciseId: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.toggleFavoriteExercise(exerciseId)
            }
        }
    }
}
