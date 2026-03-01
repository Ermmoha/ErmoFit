package com.ermofit.app.newplan.data.local.relation

data class TrainingExerciseJoined(
    val trainingId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val customDurationSec: Int?,
    val customReps: Int?,
    val name: String,
    val description: String,
    val type: String,
    val defaultDurationSec: Int,
    val defaultReps: Int,
    val musclePrimary: String,
    val muscleSecondary: String?,
    val equipmentTags: List<String>,
    val contraindications: List<String>,
    val level: String,
    val mediaResName: String?
)
