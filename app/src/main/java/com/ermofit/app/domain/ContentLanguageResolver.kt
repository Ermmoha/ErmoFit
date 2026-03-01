package com.ermofit.app.domain

import android.content.Context
import com.ermofit.app.data.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentLanguageResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveToLangCode(language: AppLanguage): String {
        return when (language) {
            AppLanguage.RU -> "ru"
            AppLanguage.EN -> "en"
            AppLanguage.SYSTEM -> {
                val localeCode = context.resources.configuration.locales[0].language.lowercase()
                if (localeCode.startsWith("ru")) "ru" else "en"
            }
        }
    }

    fun fallbackFor(langCode: String): String {
        return if (langCode == "ru") "en" else "ru"
    }
}
