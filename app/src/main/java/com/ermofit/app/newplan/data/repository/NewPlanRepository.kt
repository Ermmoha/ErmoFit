package com.ermofit.app.newplan.data.repository

import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.data.datastore.NewPlanPreferencesStore
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.FavoriteEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.data.local.entity.WorkoutSessionEntity
import com.ermofit.app.newplan.data.local.relation.TrainingWithExercises
import com.ermofit.app.newplan.domain.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class NewPlanRepository @Inject constructor(
    private val dao: NewPlanDao,
    private val seedImportRepository: SeedImportRepository,
    private val preferencesStore: NewPlanPreferencesStore
) {
    suspend fun ensureSeedLoaded() {
        seedImportRepository.ensureSeedLoaded()
    }

    suspend fun getSeedVersion(): Int = preferencesStore.getSeedVersion()

    suspend fun setSeedVersion(value: Int) {
        preferencesStore.setSeedVersion(value)
    }

    fun observeContentLanguage(): Flow<AppLanguage> = preferencesStore.observeContentLanguage()

    suspend fun getContentLanguage(): AppLanguage = preferencesStore.getContentLanguage()

    suspend fun setContentLanguage(language: AppLanguage) {
        preferencesStore.setContentLanguage(language)
    }

    fun observeOnboardingDone(): Flow<Boolean> = preferencesStore.observeOnboardingDone()

    suspend fun setOnboardingDone(value: Boolean) {
        preferencesStore.setOnboardingDone(value)
    }

    fun observeSettings(): Flow<UserSettings> = preferencesStore.observeSettings()

    suspend fun setSettings(settings: UserSettings) {
        preferencesStore.setSettings(settings)
    }

    suspend fun setRestSec(value: Int) {
        preferencesStore.setRestSec(value)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        preferencesStore.setNotificationsEnabled(enabled)
    }

    fun observeSearchHistory(): Flow<List<String>> = preferencesStore.observeSearchHistory()

    suspend fun addSearchQuery(query: String) {
        preferencesStore.addSearchQuery(query)
    }

    suspend fun clearSearchHistory() {
        preferencesStore.clearSearchHistory()
    }

    fun observeLastGeneratedExerciseIds(): Flow<List<String>> = preferencesStore.observeLastGeneratedExerciseIds()

    suspend fun setLastGeneratedExerciseIds(ids: List<String>) {
        preferencesStore.setLastGeneratedExerciseIds(ids)
    }

    fun observeAllExercises(): Flow<List<ExerciseEntity>> = dao.observeAllExercises()

    suspend fun getAllExercisesOnce(): List<ExerciseEntity> = dao.getAllExercisesOnce()

    fun observeExerciseById(exerciseId: String): Flow<ExerciseEntity?> = dao.observeExerciseById(exerciseId)

    fun observeAllTrainings(): Flow<List<TrainingEntity>> = dao.observeAllTrainings()

    fun getExercisesByFilters(
        goal: String?,
        level: String?,
        equipmentTags: List<String>,
        restrictions: List<String>
    ): Flow<List<ExerciseEntity>> = dao.getExercisesByFilters(
        goal = goal,
        level = level,
        equipmentTags = equipmentTags,
        restrictions = restrictions
    )

    fun getTrainingsByFilters(
        goal: String?,
        level: String?,
        durationMinutes: Int?,
        equipmentTags: List<String>,
        restrictions: List<String>
    ): Flow<List<TrainingEntity>> = dao.getTrainingsByFilters(
        goal = goal,
        level = level,
        durationMinutes = durationMinutes,
        equipmentTags = equipmentTags,
        restrictions = restrictions
    )

    fun searchExercises(query: String): Flow<List<ExerciseEntity>> = dao.searchExercises(query)

    fun searchTrainings(query: String): Flow<List<TrainingEntity>> = dao.searchTrainings(query)

    fun observeTrainingWithExercises(trainingId: String): Flow<TrainingWithExercises?> {
        return combine(
            dao.observeTrainingById(trainingId),
            dao.getTrainingWithExercises(trainingId)
        ) { training, exercises ->
            training?.let { TrainingWithExercises(training = it, exercises = exercises) }
        }
    }

    fun observeFavoriteTrainingIds(): Flow<List<String>> = dao.observeFavoritesByType(TYPE_TRAINING)
        .map { rows -> rows.map { it.id } }

    fun observeFavoriteExerciseIds(): Flow<List<String>> = dao.observeFavoritesByType(TYPE_EXERCISE)
        .map { rows -> rows.map { it.id } }

    fun observeFavoriteTrainings(): Flow<List<TrainingEntity>> {
        return combine(observeFavoriteTrainingIds(), dao.observeAllTrainings()) { ids, trainings ->
            val idSet = ids.toSet()
            trainings.filter { it.id in idSet }
        }
    }

    fun observeFavoriteExercises(): Flow<List<ExerciseEntity>> {
        return combine(observeFavoriteExerciseIds(), dao.observeAllExercises()) { ids, exercises ->
            val idSet = ids.toSet()
            exercises.filter { it.id in idSet }
        }
    }

    fun observeIsFavorite(id: String, type: String): Flow<Boolean> = dao.observeIsFavorite(id, type)

    suspend fun toggleFavorite(id: String, type: String) {
        val current = dao.isFavorite(id = id, type = type)
        if (current) {
            dao.removeFavorite(id = id, type = type)
        } else {
            dao.upsertFavorite(FavoriteEntity(id = id, type = type))
        }
    }

    suspend fun saveGeneratedTraining(
        training: TrainingEntity,
        links: List<TrainingExerciseEntity>
    ) {
        dao.clearGeneratedTrainingExercises()
        dao.clearGeneratedTrainings()
        dao.insertTrainings(listOf(training))
        dao.insertTrainingExercises(links)
    }

    suspend fun saveWorkoutSession(item: WorkoutSessionEntity) {
        dao.insertSession(item)
    }

    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>> = dao.observeAllSessions()

    fun querySessionsByRange(from: Long, to: Long): Flow<List<WorkoutSessionEntity>> = dao.querySessionsByRange(from, to)

    suspend fun clearProgress() {
        dao.clearSessions()
        dao.clearGeneratedTrainingExercises()
        dao.clearGeneratedTrainings()
        preferencesStore.setLastGeneratedExerciseIds(emptyList())
    }

    suspend fun getCurrentSettings(): UserSettings = observeSettings().first()

    companion object {
        const val TYPE_EXERCISE = "exercise"
        const val TYPE_TRAINING = "training"
    }
}
