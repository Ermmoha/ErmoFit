package com.ermofit.app.data.local.model

enum class MediaType {
    IMAGE,
    GIF,
    VIDEO;

    companion object {
        fun fromRaw(value: String): MediaType {
            return when (value.lowercase()) {
                "gif" -> GIF
                "video" -> VIDEO
                else -> IMAGE
            }
        }
    }
}
