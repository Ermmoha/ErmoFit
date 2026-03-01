package com.ermofit.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "program_exercises",
    primaryKeys = ["programId", "exerciseId"]
)
data class ProgramExerciseEntity(
    val programId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val defaultDurationSec: Int,
    val defaultReps: Int
)
