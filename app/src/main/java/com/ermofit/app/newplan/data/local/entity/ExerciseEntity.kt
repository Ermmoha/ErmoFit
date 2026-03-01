package com.ermofit.app.newplan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "np_exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
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
