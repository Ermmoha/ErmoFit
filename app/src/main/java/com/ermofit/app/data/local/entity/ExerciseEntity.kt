package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ermofit.app.data.local.model.MediaType

@Entity(
    tableName = "exercises",
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
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    val description: String,
    val muscleGroup: String,
    val equipment: String,
    val tags: List<String>,
    val mediaType: MediaType,
    val mediaUrl: String,
    val fallbackImageUrl: String? = null,
    val languageCode: String = "en",
    val updatedAt: Long = System.currentTimeMillis()
)
