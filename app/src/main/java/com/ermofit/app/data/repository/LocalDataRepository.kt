package com.ermofit.app.data.repository

import com.ermofit.app.data.local.dao.FitnessDao
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class LocalDataRepository @Inject constructor(
    private val dao: FitnessDao
) {
    fun observeRecommendedPrograms(
        limit: Int = 6,
        strictVideoMode: Boolean = true,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>> {
        return if (strictVideoMode) {
            dao.getProgramsRecommendedStrictLocalized(limit, langCode, onlyTranslated)
        } else {
            dao.getProgramsRecommendedLocalized(limit, langCode, onlyTranslated)
        }
    }

    fun observeProgramById(programId: String): Flow<ProgramEntity?> =
        dao.getProgramById(programId)

    fun observeExerciseById(exerciseId: String): Flow<ExerciseEntity?> =
        dao.getExerciseById(exerciseId)

    suspend fun getExerciseByIdOnce(exerciseId: String): ExerciseEntity? =
        dao.getExerciseByIdOnce(exerciseId)

    fun observeExerciseText(
        exerciseId: String,
        preferredLang: String,
        fallbackLang: String
    ): Flow<ExerciseTextEntity?> = dao.observeExerciseText(
        exerciseId = exerciseId,
        preferredLang = preferredLang,
        fallbackLang = fallbackLang
    )

    fun observeProgramText(
        programId: String,
        preferredLang: String,
        fallbackLang: String
    ): Flow<ProgramTextEntity?> = dao.observeProgramText(
        programId = programId,
        preferredLang = preferredLang,
        fallbackLang = fallbackLang
    )

    fun observeExerciseTextsByIds(
        ids: List<String>,
        preferredLang: String,
        fallbackLang: String
    ): Flow<List<ExerciseTextEntity>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return dao.observeExerciseTextsByIds(
            exerciseIds = ids,
            preferredLang = preferredLang,
            fallbackLang = fallbackLang
        )
    }

    fun observeProgramTextsByIds(
        ids: List<String>,
        preferredLang: String,
        fallbackLang: String
    ): Flow<List<ProgramTextEntity>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return dao.observeProgramTextsByIds(
            programIds = ids,
            preferredLang = preferredLang,
            fallbackLang = fallbackLang
        )
    }

    suspend fun getExerciseTextByLang(
        exerciseId: String,
        langCode: String
    ): ExerciseTextEntity? = dao.getExerciseTextByLang(
        exerciseId = exerciseId,
        langCode = langCode
    )

    suspend fun upsertExerciseText(item: ExerciseTextEntity) {
        dao.upsertExerciseText(item)
    }

    fun observeExercisesForProgram(programId: String): Flow<List<ProgramExerciseWithDetails>> =
        dao.getExercisesForProgram(programId)

    fun observeProgramsByCategory(
        categoryId: String,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>> = dao.getProgramsByCategoryLocalized(
        categoryId = categoryId,
        langCode = langCode,
        onlyTranslated = onlyTranslated
    )

    fun observeExercisesByCategory(
        categoryId: String,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>> = dao.getExercisesByCategoryLocalized(
        categoryId = categoryId,
        langCode = langCode,
        onlyTranslated = onlyTranslated
    )

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    fun observeProgramCategories(
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<CategoryEntity>> = dao.observeProgramCategoriesLocalized(
        langCode = langCode,
        onlyTranslated = onlyTranslated
    )

    fun observeExerciseCategories(
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<CategoryEntity>> = dao.observeExerciseCategoriesLocalized(
        langCode = langCode,
        onlyTranslated = onlyTranslated
    )

    fun searchPrograms(
        query: String,
        preferredLang: String,
        fallbackLang: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>> = dao.searchProgramsLocalized(
        query = query,
        preferredLang = preferredLang,
        fallbackLang = fallbackLang,
        onlyTranslated = onlyTranslated
    )

    fun searchExercisesLocalized(
        query: String,
        preferredLang: String,
        fallbackLang: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>> = dao.searchExercisesLocalizedFiltered(
        query = query,
        preferredLang = preferredLang,
        fallbackLang = fallbackLang,
        onlyTranslated = onlyTranslated
    )

    fun observeProgramsByIds(
        ids: List<String>,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return dao.getProgramsByIdsLocalized(
            ids = ids,
            langCode = langCode,
            onlyTranslated = onlyTranslated
        )
    }

    fun observeExercisesByIds(
        ids: List<String>,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return dao.getExercisesByIdsLocalized(
            ids = ids,
            langCode = langCode,
            onlyTranslated = onlyTranslated
        )
    }
}
