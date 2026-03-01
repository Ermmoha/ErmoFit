package com.ermofit.app.newplan.domain.usecase

import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels
import com.ermofit.app.newplan.domain.model.UserSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first

@Singleton
class GenerateDailyTrainingUseCase @Inject constructor(
    private val repository: NewPlanRepository
) {

    suspend operator fun invoke(settings: UserSettings): GeneratedDailyTrainingResult {
        val all = repository.getAllExercisesOnce()
        require(all.isNotEmpty()) { "База упражнений пуста" }

        val filtered = all.filter { exercise ->
            matchesLevel(exercise.level, settings.level) &&
                matchesEquipment(exercise.equipmentTags, settings.equipmentOwned) &&
                matchesRestrictions(exercise.contraindications, settings.restrictions) &&
                matchesGoal(exercise, settings.goal)
        }
        val candidateBase = if (filtered.isNotEmpty()) filtered else all

        val recentIds = repository.observeLastGeneratedExerciseIds().first().toSet()
        val antiRepeatPool = candidateBase.filterNot { it.id in recentIds }
        val pool = if (antiRepeatPool.size >= 8) antiRepeatPool else candidateBase

        val daySeed = LocalDate.now().toEpochDay().toInt()
        val random = Random(daySeed)
        val targetCount = targetExerciseCount(settings.goal, settings.durationMinutes)
        val selected = mutableListOf<ExerciseEntity>()

        // Mandatory distribution: for non-mobility include core/legs/upper blocks first.
        if (settings.goal != TrainingGoals.MOBILITY) {
            pickByMuscle(pool, selected, random, "core", 1)
            pickByMuscle(pool, selected, random, "legs", 1)
            pickByMuscle(pool, selected, random, "upper", 1)
        } else {
            pickByMuscle(pool, selected, random, "mobility", 2)
        }

        var attempts = 0
        while (selected.size < targetCount && attempts < 800) {
            val candidate = pool[random.nextInt(pool.size)]
            attempts += 1
            if (candidate.id in selected.map { it.id }) continue
            if (violatesMuscleSequence(selected, candidate)) continue
            selected += candidate
        }

        // Fallback to ensure we always have a playable training.
        if (selected.size < minExercises(settings.goal)) {
            val fallback = candidateBase.shuffled(random)
            fallback.forEach { exercise ->
                if (selected.size >= minExercises(settings.goal)) return@forEach
                if (exercise.id in selected.map { it.id }) return@forEach
                if (violatesMuscleSequence(selected, exercise)) return@forEach
                selected += exercise
            }
        }

        val dailyId = dailyId()
        val restSec = resolveRest(settings)
        val links = selected.mapIndexed { index, exercise ->
            val values = resolveValuesForGoal(exercise, settings.goal)
            TrainingExerciseEntity(
                trainingId = dailyId,
                exerciseId = exercise.id,
                orderIndex = index + 1,
                customDurationSec = values.first,
                customReps = values.second
            )
        }.toMutableList()

        tuneForTargetDuration(
            settings = settings,
            selectedExercises = selected,
            links = links,
            restSec = restSec,
            pool = pool,
            random = random
        )

        val equipmentRequired = selected
            .flatMap { it.equipmentTags }
            .filterNot { it == "no_equipment" }
            .distinct()
            .ifEmpty { listOf("no_equipment") }

        val contentLanguage = repository.getContentLanguage()
        val training = TrainingEntity(
            id = dailyId,
            title = titleForGoal(settings.goal, contentLanguage),
            description = descriptionForGoal(
                goal = settings.goal,
                language = contentLanguage,
                exerciseCount = selected.size,
                restSec = restSec
            ),
            goal = settings.goal,
            level = settings.level,
            durationMinutes = settings.durationMinutes,
            equipmentRequired = equipmentRequired,
            isGenerated = true
        )

        repository.saveGeneratedTraining(training, links)
        val newRecent = (selected.map { it.id } + recentIds).distinct().take(20)
        repository.setLastGeneratedExerciseIds(newRecent)

        return GeneratedDailyTrainingResult(training = training, exercises = selected, links = links)
    }

    private fun tuneForTargetDuration(
        settings: UserSettings,
        selectedExercises: MutableList<ExerciseEntity>,
        links: MutableList<TrainingExerciseEntity>,
        restSec: Int,
        pool: List<ExerciseEntity>,
        random: Random
    ) {
        val targetSec = settings.durationMinutes * 60

        fun totalSeconds(): Int {
            val work = links.sumOf { link ->
                val exercise = selectedExercises.firstOrNull { it.id == link.exerciseId } ?: return@sumOf 0
                estimateExerciseSeconds(exercise, link)
            }
            val rest = (links.size - 1).coerceAtLeast(0) * restSec
            return work + rest
        }

        var current = totalSeconds()

        // If too short: add extra exercises until max target count is reached.
        var attempts = 0
        while (current < targetSec - 120 && links.size < maxExercises(settings.goal) && attempts < 100) {
            val extra = pool[random.nextInt(pool.size)]
            attempts += 1
            if (extra.id in selectedExercises.map { it.id }) continue
            if (violatesMuscleSequence(selectedExercises, extra)) continue

            selectedExercises += extra
            val values = resolveValuesForGoal(extra, settings.goal)
            links += TrainingExerciseEntity(
                trainingId = links.firstOrNull()?.trainingId ?: dailyId(),
                exerciseId = extra.id,
                orderIndex = links.size + 1,
                customDurationSec = values.first,
                customReps = values.second
            )
            current = totalSeconds()
        }

        // If too long: remove last items preserving minimum count.
        while (current > targetSec + 180 && links.size > minExercises(settings.goal)) {
            val removedLink = links.removeLast()
            selectedExercises.removeAll { it.id == removedLink.exerciseId }
            current = totalSeconds()
        }

        // Fine tune durations for timed exercises.
        if (current > 0 && links.isNotEmpty()) {
            val factor = (targetSec.toFloat() / current.toFloat()).coerceIn(0.85f, 1.15f)
            links.replaceAll { link ->
                val exercise = selectedExercises.firstOrNull { it.id == link.exerciseId }
                if (exercise?.type == "time") {
                    val base = (link.customDurationSec ?: exercise.defaultDurationSec).coerceAtLeast(20)
                    val adjusted = (base * factor).toInt().coerceIn(20, 90)
                    link.copy(customDurationSec = adjusted)
                } else {
                    link
                }
            }
        }
    }

    private fun resolveValuesForGoal(exercise: ExerciseEntity, goal: String): Pair<Int?, Int?> {
        if (exercise.type == "time") {
            val duration = when (goal) {
                TrainingGoals.STRENGTH -> 35
                TrainingGoals.FATBURN -> 35
                TrainingGoals.ENDURANCE -> 50
                TrainingGoals.MOBILITY -> 45
                else -> exercise.defaultDurationSec.coerceAtLeast(30)
            }
            return duration to null
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

    private fun estimateExerciseSeconds(
        exercise: ExerciseEntity,
        link: TrainingExerciseEntity
    ): Int {
        return if (exercise.type == "time") {
            (link.customDurationSec ?: exercise.defaultDurationSec).coerceAtLeast(20)
        } else {
            val reps = (link.customReps ?: exercise.defaultReps).coerceAtLeast(6)
            reps * 3
        }
    }

    private fun pickByMuscle(
        pool: List<ExerciseEntity>,
        selected: MutableList<ExerciseEntity>,
        random: Random,
        muscle: String,
        count: Int
    ) {
        val candidates = pool.filter { it.musclePrimary == muscle }.shuffled(random)
        var picked = selected.count { it.musclePrimary == muscle }
        candidates.forEach { exercise ->
            if (picked >= count) return@forEach
            if (exercise.id in selected.map { it.id }) return@forEach
            if (violatesMuscleSequence(selected, exercise)) return@forEach
            selected += exercise
            picked += 1
        }
    }

    private fun violatesMuscleSequence(
        selected: List<ExerciseEntity>,
        candidate: ExerciseEntity
    ): Boolean {
        if (selected.size < 2) return false
        val lastTwo = selected.takeLast(2)
        return lastTwo.all { it.musclePrimary == candidate.musclePrimary }
    }

    private fun matchesGoal(exercise: ExerciseEntity, goal: String): Boolean {
        return when (goal) {
            TrainingGoals.STRENGTH -> exercise.type == "reps" || exercise.musclePrimary == "core"
            TrainingGoals.FATBURN -> exercise.type == "time" && exercise.musclePrimary != "mobility"
            TrainingGoals.ENDURANCE -> exercise.type == "time" || exercise.musclePrimary == "cardio"
            TrainingGoals.MOBILITY -> exercise.musclePrimary in setOf("mobility", "core", "legs")
            else -> true
        }
    }

    private fun matchesEquipment(required: List<String>, owned: List<String>): Boolean {
        if (required.isEmpty()) return true
        val ownedSet = owned.toSet()
        return required.all { it in ownedSet || it == "no_equipment" }
    }

    private fun matchesRestrictions(
        contraindications: List<String>,
        restrictions: List<String>
    ): Boolean {
        if (restrictions.isEmpty()) return true
        return contraindications.none { it in restrictions }
    }

    private fun matchesLevel(exerciseLevel: String, selectedLevel: String): Boolean {
        return levelRank(exerciseLevel) <= levelRank(selectedLevel)
    }

    private fun levelRank(level: String): Int {
        return when (level) {
            TrainingLevels.BEGINNER -> 0
            TrainingLevels.INTERMEDIATE -> 1
            TrainingLevels.ADVANCED -> 2
            else -> 0
        }
    }

    private fun resolveRest(settings: UserSettings): Int {
        val range = when (settings.goal) {
            TrainingGoals.STRENGTH -> 60..90
            TrainingGoals.FATBURN -> 15..30
            TrainingGoals.ENDURANCE -> 15..30
            TrainingGoals.MOBILITY -> 10..20
            else -> 30..45
        }
        return settings.restSec.coerceIn(range)
    }

    private fun minExercises(goal: String): Int {
        return when (goal) {
            TrainingGoals.STRENGTH -> 6
            TrainingGoals.FATBURN -> 8
            TrainingGoals.ENDURANCE -> 7
            TrainingGoals.MOBILITY -> 6
            else -> 6
        }
    }

    private fun maxExercises(goal: String): Int {
        return when (goal) {
            TrainingGoals.STRENGTH -> 8
            TrainingGoals.FATBURN -> 10
            TrainingGoals.ENDURANCE -> 9
            TrainingGoals.MOBILITY -> 10
            else -> 10
        }
    }

    private fun targetExerciseCount(goal: String, durationMinutes: Int): Int {
        val base = when (goal) {
            TrainingGoals.STRENGTH -> 7
            TrainingGoals.FATBURN -> 9
            TrainingGoals.ENDURANCE -> 8
            TrainingGoals.MOBILITY -> 8
            else -> 7
        }
        val shift = when {
            durationMinutes <= 20 -> -1
            durationMinutes >= 60 -> 2
            durationMinutes >= 45 -> 1
            else -> 0
        }
        return (base + shift).coerceIn(minExercises(goal), maxExercises(goal))
    }

    private fun titleForGoal(goal: String, language: AppLanguage): String {
        return if (language == AppLanguage.EN) {
            when (goal) {
                TrainingGoals.STRENGTH -> "Today's workout: Strength"
                TrainingGoals.FATBURN -> "Today's workout: Fat Burn"
                TrainingGoals.ENDURANCE -> "Today's workout: Endurance"
                TrainingGoals.MOBILITY -> "Today's workout: Mobility"
                else -> "Today's workout"
            }
        } else {
            when (goal) {
                TrainingGoals.STRENGTH -> "Тренировка на сегодня: Сила"
                TrainingGoals.FATBURN -> "Тренировка на сегодня: Похудение"
                TrainingGoals.ENDURANCE -> "Тренировка на сегодня: Выносливость"
                TrainingGoals.MOBILITY -> "Тренировка на сегодня: Подвижность"
                else -> "Тренировка на сегодня"
            }
        }
    }

    private fun descriptionForGoal(
        goal: String,
        language: AppLanguage,
        exerciseCount: Int,
        restSec: Int
    ): String {
        return if (language == AppLanguage.EN) {
            when (goal) {
                TrainingGoals.STRENGTH -> "Auto-generated strength workout: $exerciseCount exercises, rest $restSec sec."
                TrainingGoals.FATBURN -> "Auto-generated fat burn workout: $exerciseCount exercises, rest $restSec sec."
                TrainingGoals.ENDURANCE -> "Auto-generated endurance workout: $exerciseCount exercises, rest $restSec sec."
                TrainingGoals.MOBILITY -> "Auto-generated mobility workout: $exerciseCount exercises, rest $restSec sec."
                else -> "Auto-generated workout: $exerciseCount exercises, rest $restSec sec."
            }
        } else {
            when (goal) {
                TrainingGoals.STRENGTH -> "Сгенерированная тренировка на силу: $exerciseCount упражнений, отдых $restSec сек."
                TrainingGoals.FATBURN -> "Сгенерированная тренировка на похудение: $exerciseCount упражнений, отдых $restSec сек."
                TrainingGoals.ENDURANCE -> "Сгенерированная тренировка на выносливость: $exerciseCount упражнений, отдых $restSec сек."
                TrainingGoals.MOBILITY -> "Сгенерированная тренировка на подвижность: $exerciseCount упражнений, отдых $restSec сек."
                else -> "Сгенерированная тренировка: $exerciseCount упражнений, отдых $restSec сек."
            }
        }
    }

    private fun dailyId(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return "daily_${LocalDate.now().format(formatter)}"
    }
}

data class GeneratedDailyTrainingResult(
    val training: TrainingEntity,
    val exercises: List<ExerciseEntity>,
    val links: List<TrainingExerciseEntity>
)
