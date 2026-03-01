package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val level: String,
    val durationMinutes: Int,
    val categoryId: String,
    val backgroundImageUrl: String
)
