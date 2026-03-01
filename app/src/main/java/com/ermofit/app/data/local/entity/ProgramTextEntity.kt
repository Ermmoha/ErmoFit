package com.ermofit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "program_texts",
    primaryKeys = ["programId", "langCode"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["programId"]),
        Index(value = ["langCode"])
    ]
)
data class ProgramTextEntity(
    val programId: String,
    val langCode: String,
    val title: String,
    val description: String,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)
