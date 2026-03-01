package com.ermofit.app.data.local.relation

import com.ermofit.app.data.local.model.MediaType

data class ProgramExerciseWithDetails(
    val programId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val defaultDurationSec: Int,
    val defaultReps: Int,
    val title: String,
    val description: String,
    val muscleGroup: String,
    val equipment: String,
    val tags: List<String>,
    val mediaType: MediaType,
    val mediaUrl: String,
    val fallbackImageUrl: String?
)
