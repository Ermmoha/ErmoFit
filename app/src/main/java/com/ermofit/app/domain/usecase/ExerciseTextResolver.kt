package com.ermofit.app.domain.usecase

import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.ContentLanguageResolver
import com.ermofit.app.domain.model.ResolvedExerciseText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExerciseTextResolver @Inject constructor(
    private val localDataRepository: LocalDataRepository,
    private val preferencesManager: UserPreferencesManager,
    private val contentLanguageResolver: ContentLanguageResolver
) {
    private val preferredLangFlow: Flow<String> = preferencesManager.observeLanguage()
        .map(contentLanguageResolver::resolveToLangCode)
        .distinctUntilChanged()

    private val showOnlyTranslatedFlow: Flow<Boolean> = flowOf(false)

    fun observePreferredLangCode(): Flow<String> = preferredLangFlow

    fun observeOnlyWithTranslation(): Flow<Boolean> = showOnlyTranslatedFlow

    fun observeTextForExercise(exerciseId: String): Flow<ResolvedExerciseText> {
        return preferredLangFlow.flatMapLatest { preferredLang ->
            val fallbackLang = contentLanguageResolver.fallbackFor(preferredLang)
            localDataRepository.observeExerciseText(
                exerciseId = exerciseId,
                preferredLang = preferredLang,
                fallbackLang = fallbackLang
            ).map { text ->
                resolveText(
                    text = text,
                    preferredLang = preferredLang
                )
            }
        }
    }

    fun observeTextsForExercises(
        exercisesFlow: Flow<List<ExerciseEntity>>
    ): Flow<Map<String, ResolvedExerciseText>> {
        return combine(exercisesFlow, preferredLangFlow) { exercises, preferredLang ->
            exercises to preferredLang
        }.flatMapLatest { (exercises, preferredLang) ->
            val ids = exercises.map { it.id }
            if (ids.isEmpty()) {
                return@flatMapLatest flowOf(emptyMap<String, ResolvedExerciseText>())
            }
            val fallbackLang = contentLanguageResolver.fallbackFor(preferredLang)
            localDataRepository.observeExerciseTextsByIds(
                ids = ids,
                preferredLang = preferredLang,
                fallbackLang = fallbackLang
            ).map { rows ->
                val prioritized = linkedMapOf<String, ExerciseTextEntity>()
                rows.forEach { row ->
                    if (prioritized[row.exerciseId] == null) {
                        prioritized[row.exerciseId] = row
                    }
                }
                exercises.associate { exercise ->
                    val text = resolveText(
                        text = prioritized[exercise.id],
                        preferredLang = preferredLang
                    )
                    exercise.id to text
                }
            }
        }
    }

    fun observeTextsForProgramExercises(
        exercisesFlow: Flow<List<ProgramExerciseWithDetails>>
    ): Flow<Map<String, ResolvedExerciseText>> {
        return combine(exercisesFlow, preferredLangFlow) { exercises, preferredLang ->
            exercises to preferredLang
        }.flatMapLatest { (exercises, preferredLang) ->
            val ids = exercises.map { it.exerciseId }
            if (ids.isEmpty()) {
                return@flatMapLatest flowOf(emptyMap<String, ResolvedExerciseText>())
            }
            val fallbackLang = contentLanguageResolver.fallbackFor(preferredLang)
            localDataRepository.observeExerciseTextsByIds(
                ids = ids,
                preferredLang = preferredLang,
                fallbackLang = fallbackLang
            ).map { rows ->
                val prioritized = linkedMapOf<String, ExerciseTextEntity>()
                rows.forEach { row ->
                    if (prioritized[row.exerciseId] == null) {
                        prioritized[row.exerciseId] = row
                    }
                }
                exercises.associate { exercise ->
                    val text = resolveText(
                        text = prioritized[exercise.exerciseId],
                        preferredLang = preferredLang
                    )
                    exercise.exerciseId to text
                }
            }
        }
    }

    private fun resolveText(
        text: ExerciseTextEntity?,
        preferredLang: String
    ): ResolvedExerciseText {
        val noTranslation = if (preferredLang == "ru") "\u041d\u0435\u0442 \u043f\u0435\u0440\u0435\u0432\u043e\u0434\u0430" else "No translation"
        val resolvedTitle = text?.name?.takeIf { it.isNotBlank() } ?: noTranslation
        val resolvedDescription = text?.description?.takeIf { it.isNotBlank() } ?: noTranslation
        val resolvedLang = text?.langCode ?: preferredLang
        val hasPreferredText = text?.let { item ->
            item.langCode == preferredLang &&
                item.name.isNotBlank() &&
                item.description.isNotBlank()
        } == true
        return ResolvedExerciseText(
            title = resolvedTitle,
            description = resolvedDescription,
            langCode = resolvedLang,
            isFallback = !hasPreferredText
        )
    }
}

