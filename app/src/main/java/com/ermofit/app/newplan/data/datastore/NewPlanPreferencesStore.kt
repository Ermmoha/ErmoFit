package com.ermofit.app.newplan.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.domain.model.UserSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.newPlanDataStore: DataStore<Preferences> by preferencesDataStore(name = "ermofit_newplan_prefs")

@Singleton
class NewPlanPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val seedVersion = intPreferencesKey("seed_version")
        val seededLanguage = stringPreferencesKey("seeded_language")
        val contentLanguage = stringPreferencesKey("content_language")
        val goal = stringPreferencesKey("goal")
        val level = stringPreferencesKey("level")
        val durationMinutes = intPreferencesKey("duration_minutes")
        val equipmentOwned = stringPreferencesKey("equipment_owned")
        val restrictions = stringPreferencesKey("restrictions")
        val restSec = intPreferencesKey("rest_sec")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val searchHistory = stringPreferencesKey("search_history")
        val lastGeneratedExerciseIds = stringPreferencesKey("last_generated_exercise_ids")
    }

    fun observeOnboardingDone(): Flow<Boolean> = context.newPlanDataStore.data.map { prefs ->
        prefs[Keys.onboardingDone] ?: false
    }

    fun observeSettings(): Flow<UserSettings> = context.newPlanDataStore.data.map { prefs ->
        UserSettings(
            goal = prefs[Keys.goal] ?: UserSettings().goal,
            level = prefs[Keys.level] ?: UserSettings().level,
            durationMinutes = prefs[Keys.durationMinutes] ?: UserSettings().durationMinutes,
            equipmentOwned = decodeStringList(prefs[Keys.equipmentOwned]).ifEmpty { UserSettings().equipmentOwned },
            restrictions = decodeStringList(prefs[Keys.restrictions]),
            restSec = prefs[Keys.restSec] ?: UserSettings().restSec,
            notificationsEnabled = prefs[Keys.notificationsEnabled] ?: UserSettings().notificationsEnabled
        )
    }

    fun observeContentLanguage(): Flow<AppLanguage> = context.newPlanDataStore.data.map { prefs ->
        val language = AppLanguage.fromRaw(prefs[Keys.contentLanguage])
        if (language == AppLanguage.SYSTEM) AppLanguage.RU else language
    }

    fun observeSearchHistory(): Flow<List<String>> = context.newPlanDataStore.data.map { prefs ->
        decodeStringList(prefs[Keys.searchHistory]).take(5)
    }

    fun observeLastGeneratedExerciseIds(): Flow<List<String>> = context.newPlanDataStore.data.map { prefs ->
        decodeStringList(prefs[Keys.lastGeneratedExerciseIds]).take(20)
    }

    suspend fun setOnboardingDone(value: Boolean) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.onboardingDone] = value
        }
    }

    suspend fun getSeedVersion(): Int {
        return context.newPlanDataStore.data.map { prefs ->
            prefs[Keys.seedVersion] ?: 0
        }.first()
    }

    suspend fun setSeedVersion(value: Int) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.seedVersion] = value
        }
    }

    suspend fun getSeededLanguageCode(): String {
        return context.newPlanDataStore.data.map { prefs ->
            prefs[Keys.seededLanguage].orEmpty()
        }.first()
    }

    suspend fun setSeededLanguageCode(value: String) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.seededLanguage] = value
        }
    }

    suspend fun getContentLanguage(): AppLanguage {
        return context.newPlanDataStore.data.map { prefs ->
            val language = AppLanguage.fromRaw(prefs[Keys.contentLanguage])
            if (language == AppLanguage.SYSTEM) AppLanguage.RU else language
        }.first()
    }

    suspend fun setContentLanguage(language: AppLanguage) {
        val normalized = if (language == AppLanguage.SYSTEM) AppLanguage.RU else language
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.contentLanguage] = normalized.raw
        }
    }

    suspend fun setSettings(settings: UserSettings) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.goal] = settings.goal
            prefs[Keys.level] = settings.level
            prefs[Keys.durationMinutes] = settings.durationMinutes
            prefs[Keys.equipmentOwned] = encodeStringList(settings.equipmentOwned.distinct())
            prefs[Keys.restrictions] = encodeStringList(settings.restrictions.distinct())
            prefs[Keys.restSec] = settings.restSec
            prefs[Keys.notificationsEnabled] = settings.notificationsEnabled
        }
    }

    suspend fun setRestSec(value: Int) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.restSec] = value
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.notificationsEnabled] = enabled
        }
    }

    suspend fun addSearchQuery(queryRaw: String) {
        val query = queryRaw.trim()
        if (query.isEmpty()) return
        context.newPlanDataStore.edit { prefs ->
            val list = decodeStringList(prefs[Keys.searchHistory]).toMutableList()
            list.removeAll { it.equals(query, ignoreCase = true) }
            list.add(0, query)
            while (list.size > 5) list.removeLast()
            prefs[Keys.searchHistory] = encodeStringList(list)
        }
    }

    suspend fun clearSearchHistory() {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.searchHistory] = "[]"
        }
    }

    suspend fun setLastGeneratedExerciseIds(ids: List<String>) {
        context.newPlanDataStore.edit { prefs ->
            prefs[Keys.lastGeneratedExerciseIds] = encodeStringList(ids.distinct().take(20))
        }
    }

    private fun encodeStringList(items: List<String>): String = gson.toJson(items)

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type).orEmpty()
        }.getOrDefault(emptyList())
    }
}
