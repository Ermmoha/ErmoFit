package com.ermofit.app.newplan.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "np_training_exercises",
    primaryKeys = ["trainingId", "exerciseId"]
)
data class TrainingExerciseEntity(
    val trainingId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val customDurationSec: Int?,
    val customReps: Int?
)
