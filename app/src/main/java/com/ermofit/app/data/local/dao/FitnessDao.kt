package com.ermofit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramExerciseEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseTexts(items: List<ExerciseTextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExerciseText(item: ExerciseTextEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(items: List<ProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramTexts(items: List<ProgramTextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgramExercises(items: List<ProgramExerciseEntity>)

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun getProgramsCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM exercises WHERE id = :exerciseId)")
    suspend fun hasExercise(exerciseId: String): Boolean

    @Query("SELECT * FROM categories ORDER BY title ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.*
        FROM categories c
        WHERE EXISTS (
            SELECT 1
            FROM programs p
            LEFT JOIN program_texts tp
              ON tp.programId = p.id AND tp.langCode = :langCode
            WHERE p.categoryId = c.id
              AND EXISTS (
                SELECT 1
                FROM program_exercises pe
                WHERE pe.programId = p.id
              )
              AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        )
        ORDER BY c.title ASC
        """
    )
    fun observeProgramCategoriesLocalized(
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.*
        FROM categories c
        WHERE EXISTS (
            SELECT 1
            FROM exercises e
            LEFT JOIN exercise_texts te
              ON te.exerciseId = e.id AND te.langCode = :langCode
            WHERE e.categoryId = c.id
              AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        )
        ORDER BY c.title ASC
        """
    )
    fun observeExerciseCategoriesLocalized(
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT p.*
        FROM programs p
        LEFT JOIN program_texts tp
          ON tp.programId = p.id AND tp.langCode = :langCode
        WHERE EXISTS (
            SELECT 1
            FROM program_exercises pe
            WHERE pe.programId = p.id
        )
        AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.title, ''), p.title) ASC, p.id ASC
        LIMIT :limit
        """
    )
    fun getProgramsRecommendedLocalized(
        limit: Int,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT p.*
        FROM programs p
        LEFT JOIN program_texts tp
          ON tp.programId = p.id AND tp.langCode = :langCode
        WHERE EXISTS (
            SELECT 1 FROM program_exercises pe
            WHERE pe.programId = p.id
        )
        AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.title, ''), p.title) ASC, p.id ASC
        LIMIT :limit
        """
    )
    fun getProgramsRecommendedStrictLocalized(
        limit: Int,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE id = :programId LIMIT 1")
    fun getProgramById(programId: String): Flow<ProgramEntity?>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    fun getExerciseById(exerciseId: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseByIdOnce(exerciseId: String): ExerciseEntity?

    @Query(
        """
        SELECT * FROM exercise_texts
        WHERE exerciseId = :exerciseId
          AND langCode IN (:preferredLang, :fallbackLang)
        ORDER BY CASE
            WHEN langCode = :preferredLang THEN 0
            WHEN langCode = :fallbackLang THEN 1
            ELSE 2
        END
        LIMIT 1
        """
    )
    fun observeExerciseText(
        exerciseId: String,
        preferredLang: String,
        fallbackLang: String
    ): Flow<ExerciseTextEntity?>

    @Query(
        """
        SELECT * FROM program_texts
        WHERE programId = :programId
          AND langCode IN (:preferredLang, :fallbackLang)
        ORDER BY CASE
            WHEN langCode = :preferredLang THEN 0
            WHEN langCode = :fallbackLang THEN 1
            ELSE 2
        END
        LIMIT 1
        """
    )
    fun observeProgramText(
        programId: String,
        preferredLang: String,
        fallbackLang: String
    ): Flow<ProgramTextEntity?>

    @Query(
        """
        SELECT * FROM exercise_texts
        WHERE exerciseId = :exerciseId
          AND langCode = :langCode
        LIMIT 1
        """
    )
    suspend fun getExerciseTextByLang(
        exerciseId: String,
        langCode: String
    ): ExerciseTextEntity?

    @Query(
        """
        SELECT * FROM exercise_texts
        WHERE exerciseId IN (:exerciseIds)
          AND langCode IN (:preferredLang, :fallbackLang)
        ORDER BY exerciseId ASC, CASE
            WHEN langCode = :preferredLang THEN 0
            WHEN langCode = :fallbackLang THEN 1
            ELSE 2
        END
        """
    )
    fun observeExerciseTextsByIds(
        exerciseIds: List<String>,
        preferredLang: String,
        fallbackLang: String
    ): Flow<List<ExerciseTextEntity>>

    @Query(
        """
        SELECT * FROM program_texts
        WHERE programId IN (:programIds)
          AND langCode IN (:preferredLang, :fallbackLang)
        ORDER BY programId ASC, CASE
            WHEN langCode = :preferredLang THEN 0
            WHEN langCode = :fallbackLang THEN 1
            ELSE 2
        END
        """
    )
    fun observeProgramTextsByIds(
        programIds: List<String>,
        preferredLang: String,
        fallbackLang: String
    ): Flow<List<ProgramTextEntity>>

    @Query(
        """
        SELECT 
            pe.programId AS programId,
            pe.exerciseId AS exerciseId,
            pe.orderIndex AS orderIndex,
            pe.defaultDurationSec AS defaultDurationSec,
            pe.defaultReps AS defaultReps,
            e.title AS title,
            e.description AS description,
            e.muscleGroup AS muscleGroup,
            e.equipment AS equipment,
            e.tags AS tags,
            e.mediaType AS mediaType,
            e.mediaUrl AS mediaUrl,
            e.fallbackImageUrl AS fallbackImageUrl
        FROM program_exercises pe
        INNER JOIN exercises e ON pe.exerciseId = e.id
        WHERE pe.programId = :programId
        ORDER BY pe.orderIndex ASC
        """
    )
    fun getExercisesForProgram(programId: String): Flow<List<ProgramExerciseWithDetails>>

    @Query(
        """
        SELECT p.*
        FROM programs p
        LEFT JOIN program_texts tp
          ON tp.programId = p.id AND tp.langCode = :preferredLang
        LEFT JOIN program_texts tf
          ON tf.programId = p.id AND tf.langCode = :fallbackLang
        WHERE EXISTS (
            SELECT 1
            FROM program_exercises pe
            WHERE pe.programId = p.id
        )
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
          AND (
            COALESCE(NULLIF(tp.title, ''), NULLIF(tf.title, ''), p.title) LIKE '%' || :query || '%'
            OR EXISTS (
                SELECT 1
                FROM program_exercises pe2
                INNER JOIN exercises e2 ON e2.id = pe2.exerciseId
                WHERE pe2.programId = p.id
                  AND e2.tags LIKE '%' || :query || '%'
            )
          )
        ORDER BY COALESCE(NULLIF(tp.title, ''), NULLIF(tf.title, ''), p.title) ASC
        """
    )
    fun searchProgramsLocalized(
        query: String,
        preferredLang: String,
        fallbackLang: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT e.*
        FROM exercises e
        LEFT JOIN exercise_texts tp
          ON tp.exerciseId = e.id AND tp.langCode = :preferredLang
        LEFT JOIN exercise_texts tf
          ON tf.exerciseId = e.id AND tf.langCode = :fallbackLang
        WHERE (:onlyTranslated = 0 OR :onlyTranslated = 1)
          AND (
            e.tags LIKE '%' || :query || '%'
            OR COALESCE(NULLIF(tp.name, ''), NULLIF(tf.name, ''), e.title) LIKE '%' || :query || '%'
          )
        ORDER BY COALESCE(NULLIF(tp.name, ''), NULLIF(tf.name, ''), e.title) ASC
        """
    )
    fun searchExercisesLocalizedFiltered(
        query: String,
        preferredLang: String,
        fallbackLang: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT e.*
        FROM exercises e
        LEFT JOIN exercise_texts tp
          ON tp.exerciseId = e.id AND tp.langCode = :preferredLang
        LEFT JOIN exercise_texts tf
          ON tf.exerciseId = e.id AND tf.langCode = :fallbackLang
        WHERE e.mediaType = 'VIDEO'
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
          AND (
            e.tags LIKE '%' || :query || '%'
            OR COALESCE(NULLIF(tp.name, ''), NULLIF(tf.name, ''), e.title) LIKE '%' || :query || '%'
          )
        ORDER BY COALESCE(NULLIF(tp.name, ''), NULLIF(tf.name, ''), e.title) ASC
        """
    )
    fun searchExercisesWithVideoLocalizedFiltered(
        query: String,
        preferredLang: String,
        fallbackLang: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT p.*
        FROM programs p
        LEFT JOIN program_texts tp
          ON tp.programId = p.id AND tp.langCode = :langCode
        WHERE p.categoryId = :categoryId
          AND EXISTS (
            SELECT 1
            FROM program_exercises pe
            WHERE pe.programId = p.id
          )
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.title, ''), p.title) ASC
        """
    )
    fun getProgramsByCategoryLocalized(
        categoryId: String,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT e.*
        FROM exercises e
        LEFT JOIN exercise_texts tp
          ON tp.exerciseId = e.id AND tp.langCode = :langCode
        WHERE e.categoryId = :categoryId
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.name, ''), e.title) ASC
        """
    )
    fun getExercisesByCategoryLocalized(
        categoryId: String,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT p.*
        FROM programs p
        LEFT JOIN program_texts tp
          ON tp.programId = p.id AND tp.langCode = :langCode
        WHERE p.id IN (:ids)
          AND EXISTS (
            SELECT 1
            FROM program_exercises pe
            WHERE pe.programId = p.id
          )
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.title, ''), p.title) ASC
        """
    )
    fun getProgramsByIdsLocalized(
        ids: List<String>,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT e.*
        FROM exercises e
        LEFT JOIN exercise_texts tp
          ON tp.exerciseId = e.id AND tp.langCode = :langCode
        WHERE e.id IN (:ids)
          AND (:onlyTranslated = 0 OR :onlyTranslated = 1)
        ORDER BY COALESCE(NULLIF(tp.name, ''), e.title) ASC
        """
    )
    fun getExercisesByIdsLocalized(
        ids: List<String>,
        langCode: String,
        onlyTranslated: Boolean
    ): Flow<List<ExerciseEntity>>

    @Query("DELETE FROM program_exercises")
    suspend fun clearProgramExercises()

    @Query("DELETE FROM program_exercises WHERE programId IN (:programIds)")
    suspend fun clearProgramExercisesForPrograms(programIds: List<String>)

    @Query("DELETE FROM program_exercises WHERE programId LIKE 'web_%'")
    suspend fun clearExternalProgramExercises()

    @Query("DELETE FROM program_texts")
    suspend fun clearProgramTexts()

    @Query("DELETE FROM programs")
    suspend fun clearPrograms()

    @Query("DELETE FROM programs WHERE id LIKE 'web_%'")
    suspend fun clearExternalPrograms()

    @Query("DELETE FROM exercises")
    suspend fun clearExercises()

    @Query("DELETE FROM exercises WHERE id LIKE 'ext_%'")
    suspend fun clearExternalExercises()

    @Query("DELETE FROM exercise_texts")
    suspend fun clearExerciseTexts()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()
}
