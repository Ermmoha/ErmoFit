package com.ermofit.app.newplan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "np_workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val trainingId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val totalSeconds: Int,
    val completedExercises: Int
)
