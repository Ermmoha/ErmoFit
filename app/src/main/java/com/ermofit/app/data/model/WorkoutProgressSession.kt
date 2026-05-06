package com.ermofit.app.data.model

data class WorkoutProgressSession(
    val id: String,
    val programId: String,
    val programTitle: String,
    val source: String,
    val completedExercises: Int = 0,
    val totalSeconds: Int = 0,
    val finishedAt: Long = 0L
)
