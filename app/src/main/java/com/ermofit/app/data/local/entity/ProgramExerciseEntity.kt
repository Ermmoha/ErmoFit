package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "program_exercises",
    primaryKeys = ["programId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["programId"]),
        Index(value = ["exerciseId"])
    ]
)
data class ProgramExerciseEntity(
    val programId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val defaultDurationSec: Int,
    val defaultReps: Int
)
