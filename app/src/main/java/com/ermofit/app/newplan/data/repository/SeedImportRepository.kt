package com.ermofit.app.newplan.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ermofit.app.data.local.AppDatabase
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.data.datastore.NewPlanPreferencesStore
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SeedImportRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: NewPlanDao,
    private val preferencesStore: NewPlanPreferencesStore,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend fun ensureSeedLoaded() {
        val languageCode = resolveContentLanguageCode()
        val hasExercises = dao.getExercisesCount() > 0
        val hasTrainings = dao.getTrainingsCount() > 0
        val hasLinks = dao.getTrainingExercisesCount() > 0
        val seedVersion = preferencesStore.getSeedVersion()
        val seededLanguage = preferencesStore.getSeededLanguageCode()

        val needImport = !hasExercises || !hasTrainings || !hasLinks ||
            seedVersion != SEED_VERSION || seededLanguage != languageCode
        if (!needImport) return

        val sourceExercises = readExercises(languageCode)
        require(sourceExercises.isNotEmpty()) { "Файл $languageCode не содержит упражнений." }

        val exercises = sourceExercises
            .mapNotNull { it.toEntityOrNull() }
            .distinctBy { it.id }
        require(exercises.isNotEmpty()) { "Не удалось подготовить упражнения для импорта." }

        val trainingSeed = generateTrainings(
            exercises = exercises,
            languageCode = languageCode
        )
        require(trainingSeed.links.isNotEmpty()) { "Не удалось собрать связи training-exercise." }

        database.withTransaction {
            dao.clearTrainingExercises()
            dao.clearTrainings()
            dao.clearExercises()

            dao.insertExercises(exercises)
            dao.insertTrainings(trainingSeed.trainings)
            dao.insertTrainingExercises(trainingSeed.links)
        }

        preferencesStore.setSeedVersion(SEED_VERSION)
        preferencesStore.setSeededLanguageCode(languageCode)
    }

    private suspend fun resolveContentLanguageCode(): String {
        return when (preferencesStore.getContentLanguage()) {
            AppLanguage.EN -> "en"
            AppLanguage.RU -> "ru"
            AppLanguage.SYSTEM -> "ru"
        }
    }

    private fun readExercises(languageCode: String): List<SourceExercise> {
        val assetName = if (languageCode == "ru") RU_ASSET else EN_ASSET
        val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<SourceExercise>>() {}.type
        return gson.fromJson<List<SourceExercise>>(json, type).orEmpty()
    }

    private fun generateTrainings(
        exercises: List<ExerciseEntity>,
        languageCode: String
    ): GeneratedTrainings {
        val goals = listOf(
            TrainingGoals.STRENGTH,
            TrainingGoals.FATBURN,
            TrainingGoals.ENDURANCE,
            TrainingGoals.MOBILITY
        )
        val levels = listOf(
            TrainingLevels.BEGINNER,
            TrainingLevels.INTERMEDIATE,
            TrainingLevels.ADVANCED
        )

        val trainings = mutableListOf<TrainingEntity>()
        val links = mutableListOf<TrainingExerciseEntity>()

        goals.forEach { goal ->
            levels.forEach { level ->
                val candidates = exercises.filter { exercise ->
                    levelRank(exercise.level) <= levelRank(level) && matchesGoal(exercise, goal)
                }

                val fallback = exercises.filter { exercise ->
                    levelRank(exercise.level) <= levelRank(level)
                }

                val pool = when {
                    candidates.size >= minExercisesForGoal(goal) -> candidates
                    fallback.size >= minExercisesForGoal(goal) -> fallback
                    else -> exercises
                }

                val count = targetExerciseCount(goal).coerceAtMost(pool.size)
                if (count <= 0) return@forEach

                val selected = selectDeterministic(
                    source = pool,
                    count = count,
                    salt = "$goal:$level"
                )

                val trainingId = "seed_${goal}_$level"
                val equipment = selected
                    .flatMap { it.equipmentTags }
                    .distinct()
                    .ifEmpty { listOf(EquipmentTags.NO_EQUIPMENT) }

                trainings += TrainingEntity(
                    id = trainingId,
                    title = trainingTitle(goal, level, languageCode),
                    description = trainingDescription(goal, level, languageCode),
                    goal = goal,
                    level = level,
                    durationMinutes = durationForGoal(goal),
                    equipmentRequired = equipment,
                    isGenerated = false
                )

                selected.forEachIndexed { index, exercise ->
                    val timing = exerciseTiming(goal, exercise)
                    links += TrainingExerciseEntity(
                        trainingId = trainingId,
                        exerciseId = exercise.id,
                        orderIndex = index + 1,
                        customDurationSec = timing.first,
                        customReps = timing.second
                    )
                }
            }
        }

        return GeneratedTrainings(
            trainings = trainings,
            links = links
        )
    }

    private fun selectDeterministic(
        source: List<ExerciseEntity>,
        count: Int,
        salt: String
    ): List<ExerciseEntity> {
        if (source.isEmpty() || count <= 0) return emptyList()
        val sorted = source.sortedBy { it.id }
        val start = abs(salt.hashCode()) % sorted.size
        val rotated = sorted.drop(start) + sorted.take(start)
        return rotated.take(count)
    }

    private fun targetExerciseCount(goal: String): Int {
        return when (goal) {
            TrainingGoals.STRENGTH -> 8
            TrainingGoals.FATBURN -> 9
            TrainingGoals.ENDURANCE -> 9
            TrainingGoals.MOBILITY -> 7
            else -> 8
        }
    }

    private fun minExercisesForGoal(goal: String): Int {
        return when (goal) {
            TrainingGoals.STRENGTH -> 6
            TrainingGoals.FATBURN -> 7
            TrainingGoals.ENDURANCE -> 7
            TrainingGoals.MOBILITY -> 6
            else -> 6
        }
    }

    private fun durationForGoal(goal: String): Int {
        return when (goal) {
            TrainingGoals.STRENGTH -> 35
            TrainingGoals.FATBURN -> 30
            TrainingGoals.ENDURANCE -> 40
            TrainingGoals.MOBILITY -> 25
            else -> 30
        }
    }

    private fun exerciseTiming(goal: String, exercise: ExerciseEntity): Pair<Int?, Int?> {
        if (exercise.type == "time") {
            val seconds = when (goal) {
                TrainingGoals.STRENGTH -> 30
                TrainingGoals.FATBURN -> 40
                TrainingGoals.ENDURANCE -> 50
                TrainingGoals.MOBILITY -> 45
                else -> 35
            }
            return seconds to null
        }

        val reps = when (goal) {
            TrainingGoals.STRENGTH -> 10
            TrainingGoals.FATBURN -> 14
            TrainingGoals.ENDURANCE -> 12
            TrainingGoals.MOBILITY -> 8
            else -> exercise.defaultReps.coerceAtLeast(8)
        }
        return null to reps
    }

    private fun trainingTitle(goal: String, level: String, languageCode: String): String {
        return if (languageCode == "ru") {
            val goalText = when (goal) {
                TrainingGoals.STRENGTH -> "Сила"
                TrainingGoals.FATBURN -> "Похудение"
                TrainingGoals.ENDURANCE -> "Выносливость"
                TrainingGoals.MOBILITY -> "Подвижность"
                else -> "Тренировка"
            }
            val levelText = when (level) {
                TrainingLevels.BEGINNER -> "Новичок"
                TrainingLevels.INTERMEDIATE -> "Средний"
                TrainingLevels.ADVANCED -> "Продвинутый"
                else -> level
            }
            "$goalText · $levelText"
        } else {
            val goalText = when (goal) {
                TrainingGoals.STRENGTH -> "Strength"
                TrainingGoals.FATBURN -> "Fat Burn"
                TrainingGoals.ENDURANCE -> "Endurance"
                TrainingGoals.MOBILITY -> "Mobility"
                else -> "Workout"
            }
            val levelText = when (level) {
                TrainingLevels.BEGINNER -> "Beginner"
                TrainingLevels.INTERMEDIATE -> "Intermediate"
                TrainingLevels.ADVANCED -> "Advanced"
                else -> level
            }
            "$goalText · $levelText"
        }
    }

    private fun trainingDescription(goal: String, level: String, languageCode: String): String {
        return if (languageCode == "ru") {
            val levelText = when (level) {
                TrainingLevels.BEGINNER -> "новичок"
                TrainingLevels.INTERMEDIATE -> "средний"
                TrainingLevels.ADVANCED -> "продвинутый"
                else -> level
            }
            when (goal) {
                TrainingGoals.STRENGTH -> "Базовый силовой комплекс, уровень: $levelText."
                TrainingGoals.FATBURN -> "Интервальный комплекс для расхода калорий, уровень: $levelText."
                TrainingGoals.ENDURANCE -> "Продолжительная тренировка на выносливость, уровень: $levelText."
                TrainingGoals.MOBILITY -> "Комплекс на мобильность и контроль движений, уровень: $levelText."
                else -> "Готовая тренировка, уровень: $levelText."
            }
        } else {
            when (goal) {
                TrainingGoals.STRENGTH -> "Baseline strength routine for $level level."
                TrainingGoals.FATBURN -> "Interval routine for calorie burn at $level level."
                TrainingGoals.ENDURANCE -> "Longer endurance routine for $level level."
                TrainingGoals.MOBILITY -> "Mobility and movement-control routine for $level level."
                else -> "Ready workout for $level level."
            }
        }
    }

    private fun matchesGoal(exercise: ExerciseEntity, goal: String): Boolean {
        return when (goal) {
            TrainingGoals.STRENGTH -> exercise.type == "reps"
            TrainingGoals.FATBURN -> exercise.type == "time" || exercise.musclePrimary == "cardio"
            TrainingGoals.ENDURANCE -> exercise.type == "time" || exercise.musclePrimary in setOf("legs", "core")
            TrainingGoals.MOBILITY -> exercise.musclePrimary in setOf("mobility", "stretch", "core")
            else -> true
        }
    }

    private fun levelRank(level: String): Int {
        return when (level.lowercase()) {
            TrainingLevels.BEGINNER -> 0
            TrainingLevels.INTERMEDIATE -> 1
            TrainingLevels.ADVANCED -> 2
            else -> 0
        }
    }

    private fun SourceExercise.toEntityOrNull(): ExerciseEntity? {
        val safeId = id.trim()
        if (safeId.isBlank()) return null

        val safeName = name.trim().ifBlank { safeId }
        val normalizedLevel = normalizeLevel(level)
        val normalizedEquipment = normalizeEquipment(equipment)
        val normalizedCategory = category.lowercase().trim()
        val primary = normalizePrimaryMuscle(primaryMuscles, normalizedCategory)
        val secondary = normalizeSecondaryMuscle(secondaryMuscles, primary)
        val type = normalizeType(normalizedCategory, force)
        val contraindications = normalizeContraindications(normalizedCategory)

        val defaultDurationSec = if (type == "time") {
            when (normalizedCategory) {
                "cardio", "кардио", "plyometrics", "плиометрика" -> 45
                "stretching", "растяжка" -> 40
                else -> 35
            }
        } else {
            0
        }

        val defaultReps = if (type == "reps") {
            when (normalizedLevel) {
                TrainingLevels.BEGINNER -> 10
                TrainingLevels.INTERMEDIATE -> 12
                TrainingLevels.ADVANCED -> 15
                else -> 10
            }
        } else {
            0
        }

        val safeDescription = instructions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { descriptionFromCategory(normalizedCategory) }

        return ExerciseEntity(
            id = safeId,
            name = safeName,
            description = safeDescription,
            type = type,
            defaultDurationSec = defaultDurationSec,
            defaultReps = defaultReps,
            musclePrimary = primary,
            muscleSecondary = secondary,
            equipmentTags = normalizedEquipment,
            contraindications = contraindications,
            level = normalizedLevel,
            mediaResName = null
        )
    }

    private fun normalizeLevel(raw: String): String {
        return when (raw.lowercase().trim()) {
            "beginner", "новичок" -> TrainingLevels.BEGINNER
            "intermediate", "средний", "промежуточный" -> TrainingLevels.INTERMEDIATE
            "expert", "advanced", "эксперт", "продвинутый" -> TrainingLevels.ADVANCED
            else -> TrainingLevels.BEGINNER
        }
    }

    private fun normalizeEquipment(raw: String?): List<String> {
        val tag = when (raw.orEmpty().lowercase().trim()) {
            "body only", "собственный вес", "только тело" -> EquipmentTags.NO_EQUIPMENT
            "dumbbell", "гантель", "гантели" -> EquipmentTags.DUMBBELLS
            "kettlebells", "kettlebell", "гири", "гиря" -> EquipmentTags.KETTLEBELL
            "barbell", "штанга" -> EquipmentTags.BARBELL
            "cable", "кабель", "кабельный тренажер" -> EquipmentTags.CABLE
            "machine", "тренажер", "машина" -> EquipmentTags.MACHINE
            "bands", "эспандерные ленты", "резинки", "полосы" -> EquipmentTags.BANDS
            "medicine ball", "медбол", "медицинский мяч" -> EquipmentTags.MEDICINE_BALL
            "exercise ball", "фитбол", "мяч для упражнений" -> EquipmentTags.EXERCISE_BALL
            "foam roll", "роллер", "роллер (foam roller)", "пенопластовый рулон" -> EquipmentTags.FOAM_ROLLER
            "e-z curl bar", "ez-гриф", "стержень для завивки e-z" -> EquipmentTags.EZ_BAR
            else -> EquipmentTags.OTHER
        }
        return listOf(tag)
    }

    private fun normalizePrimaryMuscle(raw: List<String>, category: String): String {
        val first = raw.firstOrNull()?.lowercase()?.trim().orEmpty()

        if (category in setOf("stretching", "растяжка")) return "stretch"
        if (category in setOf("cardio", "кардио", "plyometrics", "плиометрика")) return "cardio"

        return when (first) {
            "abdominals", "пресс", "брюшной пресс", "core" -> "core"
            "quadriceps", "квадрицепсы",
            "hamstrings", "бицепс бедра", "подколенные сухожилия",
            "calves", "икроножные мышцы",
            "glutes", "ягодицы",
            "adductors", "приводящие мышцы бедра",
            "abductors", "отводящие мышцы бедра" -> "legs"
            "chest", "грудные мышцы",
            "shoulders", "плечи",
            "triceps", "трицепсы",
            "biceps", "бицепсы",
            "lats", "широчайшие мышцы спины",
            "middle back", "средняя часть спины",
            "lower back", "поясница",
            "forearms", "предплечья",
            "traps", "трапеции",
            "neck", "шея" -> "upper"
            else -> if (category in setOf("strength", "сила")) "upper" else "core"
        }
    }

    private fun normalizeSecondaryMuscle(raw: List<String>, primary: String): String? {
        val first = raw.firstOrNull()?.lowercase()?.trim().orEmpty()
        val mapped = when (first) {
            "abdominals", "пресс", "брюшной пресс" -> "core"
            "quadriceps", "квадрицепсы",
            "hamstrings", "бицепс бедра", "подколенные сухожилия",
            "calves", "икроножные мышцы",
            "glutes", "ягодицы",
            "adductors", "приводящие мышцы бедра",
            "abductors", "отводящие мышцы бедра" -> "legs"
            "chest", "грудные мышцы",
            "shoulders", "плечи",
            "triceps", "трицепсы",
            "biceps", "бицепсы",
            "lats", "широчайшие мышцы спины",
            "middle back", "средняя часть спины",
            "lower back", "поясница",
            "forearms", "предплечья",
            "traps", "трапеции",
            "neck", "шея" -> "upper"
            else -> null
        }
        return if (mapped == primary) null else mapped
    }

    private fun normalizeType(category: String, force: String?): String {
        val normalizedForce = force.orEmpty().lowercase().trim()
        return when {
            category in setOf("stretching", "растяжка", "cardio", "кардио", "plyometrics", "плиометрика") -> "time"
            normalizedForce in setOf("static", "изометрия") -> "time"
            else -> "reps"
        }
    }

    private fun normalizeContraindications(category: String): List<String> {
        return when (category) {
            "plyometrics", "плиометрика" -> listOf("no_jumps", "knees", "quiet")
            "cardio", "кардио" -> listOf("quiet")
            else -> emptyList()
        }
    }

    private fun descriptionFromCategory(category: String): String {
        return when (category) {
            "stretching", "растяжка" -> "Упражнение на мобильность и контроль амплитуды движения."
            "cardio", "кардио" -> "Упражнение на пульс и функциональную выносливость."
            "plyometrics", "плиометрика" -> "Взрывное упражнение с контролем приземления."
            else -> "Базовое силовое упражнение с контролем техники."
        }
    }

    private data class SourceExercise(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("force") val force: String? = null,
        @SerializedName("level") val level: String,
        @SerializedName("mechanic") val mechanic: String? = null,
        @SerializedName("equipment") val equipment: String? = null,
        @SerializedName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
        @SerializedName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
        @SerializedName("instructions") val instructions: List<String> = emptyList(),
        @SerializedName("category") val category: String
    )

    private data class GeneratedTrainings(
        val trainings: List<TrainingEntity>,
        val links: List<TrainingExerciseEntity>
    )

    private companion object {
        const val RU_ASSET = "bd_ru.json"
        const val EN_ASSET = "bd_en.json"
        const val SEED_VERSION = 3
    }
}
