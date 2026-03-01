package com.ermofit.app.domain.model

data class ResolvedProgramText(
    val title: String,
    val description: String,
    val langCode: String,
    val isFallback: Boolean
)
