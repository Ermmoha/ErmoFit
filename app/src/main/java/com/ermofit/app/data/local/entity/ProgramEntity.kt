package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class ProgramEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val level: String,
    val durationMinutes: Int,
    val categoryId: String,
    val backgroundImageUrl: String
)
