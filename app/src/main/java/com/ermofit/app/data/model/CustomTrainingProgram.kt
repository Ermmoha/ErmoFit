package com.ermofit.app.data.model

import com.ermofit.app.data.local.entity.ProgramEntity
import kotlin.math.ceil

data class CustomTrainingProgram(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val level: String = DEFAULT_LEVEL,
    val interExerciseRestSec: Int = DEFAULT_INTER_EXERCISE_REST_SEC,
    val coverImageUrl: String = "",
    val estimatedDurationMinutes: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val exercises: List<CustomTrainingExercise> = emptyList()
) {
    fun toPreviewProgram(): ProgramEntity {
        return ProgramEntity(
            id = id,
            title = title,
            description = description,
            level = level,
            durationMinutes = estimatedDurationMinutes,
            categoryId = CUSTOM_PROGRAM_CATEGORY_ID,
            backgroundImageUrl = coverImageUrl
        )
    }
}

data class CustomTrainingExercise(
    val exerciseId: String = "",
    val orderIndex: Int = 0,
    val sets: Int = DEFAULT_SETS,
    val reps: Int = DEFAULT_REPS,
    val durationSec: Int = 0,
    val restSec: Int = DEFAULT_REST_SEC,
    val notes: String = ""
)

data class CustomTrainingProgramDraft(
    val title: String,
    val description: String,
    val level: String,
    val interExerciseRestSec: Int,
    val coverImageUrl: String,
    val exercises: List<CustomTrainingExercise>
)

fun estimateCustomTrainingDurationMinutes(
    exercises: List<CustomTrainingExercise>,
    interExerciseRestSec: Int
): Int {
    if (exercises.isEmpty()) return 0
    val orderedExercises = exercises.sortedBy(CustomTrainingExercise::orderIndex)
    val totalSeconds = orderedExercises.sumOf { exercise ->
        val safeSets = exercise.sets.coerceAtLeast(1)
        val activeSeconds = when {
            exercise.durationSec > 0 -> safeSets * exercise.durationSec
            exercise.reps > 0 -> safeSets * exercise.reps * ESTIMATED_SECONDS_PER_REP
            else -> 0
        }
        val intraExerciseRest = exercise.restSec.coerceAtLeast(0) * (safeSets - 1)
        activeSeconds + intraExerciseRest
    } + (orderedExercises.size - 1).coerceAtLeast(0) * interExerciseRestSec.coerceAtLeast(0)

    return ceil(totalSeconds / 60f).toInt().coerceAtLeast(1)
}

const val CUSTOM_PROGRAM_CATEGORY_ID = "custom_program"
const val DEFAULT_LEVEL = "beginner"
const val DEFAULT_SETS = 3
const val DEFAULT_REPS = 10
const val DEFAULT_REST_SEC = 30
const val DEFAULT_INTER_EXERCISE_REST_SEC = 15

private const val ESTIMATED_SECONDS_PER_REP = 4
