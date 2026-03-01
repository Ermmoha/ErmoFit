package com.ermofit.app.data.local

import androidx.room.TypeConverter
import com.ermofit.app.data.local.model.MediaType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromTags(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toTags(value: String): List<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(value, type) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.fromRaw(value)
}
