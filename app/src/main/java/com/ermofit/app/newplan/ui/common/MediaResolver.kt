package com.ermofit.app.newplan.ui.common

import android.content.Context
import com.ermofit.app.R

fun resolveExerciseMedia(context: Context, mediaResName: String?): Int {
    val rawName = mediaResName?.trim().orEmpty()
    if (rawName.isBlank()) return R.drawable.ex_media_01
    val id = context.resources.getIdentifier(rawName, "drawable", context.packageName)
    return if (id != 0) id else R.drawable.ex_media_01
}

