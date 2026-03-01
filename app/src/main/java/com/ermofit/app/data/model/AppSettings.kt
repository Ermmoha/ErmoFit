package com.ermofit.app.data.model

enum class AppThemeMode(val raw: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.raw == normalized } ?: SYSTEM
        }
    }
}

enum class AppLanguage(val raw: String) {
    SYSTEM("system"),
    RU("ru"),
    EN("en");

    companion object {
        fun fromRaw(raw: String?): AppLanguage {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.raw == normalized } ?: RU
        }
    }
}
