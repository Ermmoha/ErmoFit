package com.ermofit.app.newplan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.FavoriteEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.data.local.entity.WorkoutSessionEntity
import com.ermofit.app.newplan.data.local.relation.TrainingExerciseJoined
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface NewPlanDao {

    @Query("SELECT COUNT(*) FROM np_exercises")
    suspend fun getExercisesCount(): Int

    @Query("SELECT COUNT(*) FROM np_trainings")
    suspend fun getTrainingsCount(): Int

    @Query("SELECT COUNT(*) FROM np_training_exercises")
    suspend fun getTrainingExercisesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainings(items: List<TrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingExercises(items: List<TrainingExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(item: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(items: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteEntity)

    @Query("DELETE FROM np_favorites WHERE id = :id AND type = :type")
    suspend fun removeFavorite(id: String, type: String)

    @Query("DELETE FROM np_training_exercises WHERE trainingId = :trainingId")
    suspend fun deleteTrainingExercisesByTrainingId(trainingId: String)

    @Query("DELETE FROM np_trainings WHERE id = :trainingId")
    suspend fun deleteTrainingById(trainingId: String)

    @Query("DELETE FROM np_favorites")
    suspend fun clearFavorites()

    @Query("DELETE FROM np_workout_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM np_training_exercises")
    suspend fun clearTrainingExercises()

    @Query("DELETE FROM np_trainings")
    suspend fun clearTrainings()

    @Query("DELETE FROM np_exercises")
    suspend fun clearExercises()

    @Query("DELETE FROM np_training_exercises WHERE trainingId LIKE 'daily_%'")
    suspend fun clearGeneratedTrainingExercises()

    @Query("DELETE FROM np_trainings WHERE isGenerated = 1")
    suspend fun clearGeneratedTrainings()

    @Query("SELECT * FROM np_exercises ORDER BY name ASC")
    fun observeAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM np_exercises ORDER BY name ASC")
    suspend fun getAllExercisesOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM np_exercises WHERE id = :exerciseId LIMIT 1")
    fun observeExerciseById(exerciseId: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM np_trainings ORDER BY title ASC")
    fun observeAllTrainings(): Flow<List<TrainingEntity>>

    @Query("SELECT * FROM np_trainings ORDER BY title ASC")
    suspend fun getAllTrainingsOnce(): List<TrainingEntity>

    @Query(
        """
        SELECT * FROM np_exercises
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
        """
    )
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM np_trainings
        WHERE title LIKE '%' || :query || '%'
        ORDER BY title ASC
        """
    )
    fun searchTrainings(query: String): Flow<List<TrainingEntity>>

    @Query("SELECT * FROM np_trainings WHERE id = :trainingId LIMIT 1")
    fun observeTrainingById(trainingId: String): Flow<TrainingEntity?>

    @Query("SELECT * FROM np_trainings WHERE id = :trainingId LIMIT 1")
    suspend fun getTrainingByIdOnce(trainingId: String): TrainingEntity?

    @Query(
        """
        SELECT
            te.trainingId AS trainingId,
            te.exerciseId AS exerciseId,
            te.orderIndex AS orderIndex,
            te.customDurationSec AS customDurationSec,
            te.customReps AS customReps,
            e.name AS name,
            e.description AS description,
            e.type AS type,
            e.defaultDurationSec AS defaultDurationSec,
            e.defaultReps AS defaultReps,
            e.musclePrimary AS musclePrimary,
            e.muscleSecondary AS muscleSecondary,
            e.equipmentTags AS equipmentTags,
            e.contraindications AS contraindications,
            e.level AS level,
            e.mediaResName AS mediaResName
        FROM np_training_exercises te
        INNER JOIN np_exercises e ON e.id = te.exerciseId
        WHERE te.trainingId = :trainingId
        ORDER BY te.orderIndex ASC
        """
    )
    fun getTrainingWithExercises(trainingId: String): Flow<List<TrainingExerciseJoined>>

    @Query(
        """
        SELECT * FROM np_workout_sessions
        WHERE finishedAt BETWEEN :from AND :to
        ORDER BY finishedAt DESC
        """
    )
    fun querySessionsByRange(from: Long, to: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM np_favorites WHERE type = :type ORDER BY id ASC")
    fun observeFavoritesByType(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM np_favorites WHERE id = :id AND type = :type)")
    fun observeIsFavorite(id: String, type: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM np_favorites WHERE id = :id AND type = :type)")
    suspend fun isFavorite(id: String, type: String): Boolean

    @Query("SELECT * FROM np_workout_sessions ORDER BY finishedAt DESC")
    fun observeAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM np_exercises WHERE id IN (:ids)")
    fun observeExercisesByIds(ids: List<String>): Flow<List<ExerciseEntity>>

    fun getExercisesByFilters(
        goal: String?,
        level: String?,
        equipmentTags: List<String>,
        restrictions: List<String>
    ): Flow<List<ExerciseEntity>> {
        return observeAllExercises().map { rows ->
            rows.filter { exercise ->
                matchesLevel(exercise.level, level) &&
                    matchesGoal(exercise, goal) &&
                    matchesEquipment(exercise.equipmentTags, equipmentTags) &&
                    matchesRestrictions(exercise.contraindications, restrictions)
            }
        }
    }

    fun getTrainingsByFilters(
        goal: String?,
        level: String?,
        durationMinutes: Int?,
        equipmentTags: List<String>,
        restrictions: List<String>
    ): Flow<List<TrainingEntity>> {
        return observeAllTrainings().map { rows ->
            rows.filter { training ->
                (goal.isNullOrBlank() || training.goal == goal) &&
                    matchesLevel(training.level, level) &&
                    matchesDuration(training.durationMinutes, durationMinutes) &&
                    matchesEquipment(training.equipmentRequired, equipmentTags) &&
                    matchesTrainingRestrictions(training, restrictions)
            }
        }
    }

    private fun matchesLevel(itemLevel: String, selectedLevel: String?): Boolean {
        if (selectedLevel.isNullOrBlank()) return true
        val rank = levelRank(itemLevel)
        val selectedRank = levelRank(selectedLevel)
        return rank <= selectedRank
    }

    private fun matchesDuration(itemDuration: Int, selectedDuration: Int?): Boolean {
        if (selectedDuration == null || selectedDuration <= 0) return true
        return kotlin.math.abs(itemDuration - selectedDuration) <= 15
    }

    private fun matchesGoal(exercise: ExerciseEntity, goal: String?): Boolean {
        if (goal.isNullOrBlank()) return true
        return when (goal) {
            "strength" -> exercise.type == "reps"
            "fatburn" -> exercise.type == "time" && exercise.musclePrimary !in setOf("mobility", "stretch")
            "endurance" -> exercise.type == "time"
            "mobility" -> exercise.musclePrimary in setOf("mobility", "stretch", "core")
            else -> true
        }
    }

    private fun matchesEquipment(required: List<String>, owned: List<String>): Boolean {
        if (required.isEmpty()) return true
        val normalizedOwned = owned.toSet()
        return required.all { it in normalizedOwned || it == "no_equipment" }
    }

    private fun matchesRestrictions(
        contraindications: List<String>,
        restrictions: List<String>
    ): Boolean {
        if (restrictions.isEmpty()) return true
        return contraindications.none { it in restrictions }
    }

    private fun matchesTrainingRestrictions(training: TrainingEntity, restrictions: List<String>): Boolean {
        if (restrictions.isEmpty()) return true
        // Training entity has no explicit contraindications; soft-match by title/description tags.
        val text = (training.title + " " + training.description).lowercase()
        return restrictions.none { restriction ->
            when (restriction) {
                "no_jumps", "quiet" -> {
                    text.contains("плиом") ||
                        text.contains("прыж") ||
                        text.contains("plyo") ||
                        text.contains("jump")
                }
                "knees" -> text.contains("колен") || text.contains("knee")
                "lower_back" -> text.contains("поясниц") || text.contains("lower back")
                else -> false
            }
        }
    }

    private fun levelRank(level: String): Int {
        return when (level.lowercase()) {
            "beginner" -> 0
            "intermediate" -> 1
            "advanced" -> 2
            else -> 0
        }
    }
}
