package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exercise_texts",
    primaryKeys = ["exerciseId", "langCode"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exerciseId"]),
        Index(value = ["langCode"])
    ]
)
data class ExerciseTextEntity(
    val exerciseId: String,
    val langCode: String,
    val name: String,
    val description: String,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)
