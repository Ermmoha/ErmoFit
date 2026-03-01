package com.ermofit.app.newplan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "np_trainings")
data class TrainingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val goal: String,
    val level: String,
    val durationMinutes: Int,
    val equipmentRequired: List<String>,
    val isGenerated: Boolean
)
