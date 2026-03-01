package com.ermofit.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.data.model.AppThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ermofit_prefs")

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val seedVersion = intPreferencesKey("seed_version")
        val externalImportVersion = intPreferencesKey("external_import_version")
        val searchHistory = stringPreferencesKey("search_history")
        val appThemeMode = stringPreferencesKey("app_theme_mode")
        val appLanguage = stringPreferencesKey("app_language")
        val showOnlyWithTranslation = booleanPreferencesKey("show_only_with_translation")
    }

    val seedVersionFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.seedVersion] ?: 0
    }

    val externalImportVersionFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.externalImportVersion] ?: 0
    }

    fun observeSearchHistory(): Flow<List<String>> = context.dataStore.data.map { prefs ->
        decodeHistory(prefs[Keys.searchHistory] ?: "[]")
    }

    fun observeThemeMode(): Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromRaw(prefs[Keys.appThemeMode])
    }

    fun observeLanguage(): Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromRaw(prefs[Keys.appLanguage])
    }

    fun observeShowOnlyWithTranslation(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.showOnlyWithTranslation] ?: false
    }

    suspend fun setSeedVersion(version: Int) {
        context.dataStore.edit { it[Keys.seedVersion] = version }
    }

    suspend fun setExternalImportVersion(version: Int) {
        context.dataStore.edit { it[Keys.externalImportVersion] = version }
    }

    suspend fun addSearchQuery(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = decodeHistory(prefs[Keys.searchHistory] ?: "[]").toMutableList()
            current.removeAll { it.equals(query, ignoreCase = true) }
            current.add(0, query)
            while (current.size > 5) {
                current.removeLast()
            }
            prefs[Keys.searchHistory] = gson.toJson(current)
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeHistory(prefs[Keys.searchHistory] ?: "[]")
                .filterNot { it.equals(query, ignoreCase = true) }
            prefs[Keys.searchHistory] = gson.toJson(updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs[Keys.searchHistory] = "[]"
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.appThemeMode] = mode.raw
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.appLanguage] = language.raw
        }
    }

    suspend fun setShowOnlyWithTranslation(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.showOnlyWithTranslation] = value
        }
    }

    private fun decodeHistory(raw: String): List<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
