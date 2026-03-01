package com.ermofit.app.newplan.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "np_favorites",
    primaryKeys = ["id", "type"]
)
data class FavoriteEntity(
    val id: String,
    val type: String
)
