package com.ermofit.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.data.model.AppThemeMode
import com.ermofit.app.data.model.WorkoutProgressSession
import com.ermofit.app.data.repository.AuthRepository
import com.ermofit.app.data.repository.SeedRepository
import com.ermofit.app.data.repository.WorkoutProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileProgressDayUi(
    val label: String,
    val workouts: Int,
    val minutes: Int
)

data class ProfileUiState(
    val uid: String? = null,
    val displayName: String = "",
    val email: String = "",
    val aboutMe: String = "",
    val editDisplayName: String = "",
    val editAboutMe: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU,
    val hasProgress: Boolean = false,
    val completedWorkoutsCount: Int = 0,
    val workoutsThisWeek: Int = 0,
    val completedMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val lastWorkoutTitle: String = "",
    val lastWorkoutAtLabel: String = "",
    val progressDays: List<ProfileProgressDayUi> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: UserPreferencesManager,
    private val workoutProgressRepository: WorkoutProgressRepository,
    private val seedRepository: SeedRepository
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()
    private val dayFormatter = DateTimeFormatter.ofPattern("dd.MM")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            uid = authRepository.currentUserId(),
            displayName = authRepository.currentUserDisplayName(),
            email = authRepository.currentUserEmail()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.uid != null) {
            loadProfile()
        }
        viewModelScope.launch {
            preferencesManager.observeThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeLanguage().collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            authRepository.observeUserId().collect { uid ->
                _uiState.update { it.copy(uid = uid) }
                if (uid != null) {
                    loadProfile()
                }
            }
        }
        viewModelScope.launch {
            workoutProgressRepository.observeSessions()
                .catch { emit(emptyList()) }
                .collect { sessions ->
                    val progress = buildProgressMetrics(sessions)
                    _uiState.update { state ->
                        state.copy(
                            hasProgress = progress.hasProgress,
                            completedWorkoutsCount = progress.completedWorkoutsCount,
                            workoutsThisWeek = progress.workoutsThisWeek,
                            completedMinutes = progress.completedMinutes,
                            currentStreakDays = progress.currentStreakDays,
                            lastWorkoutTitle = progress.lastWorkoutTitle,
                            lastWorkoutAtLabel = progress.lastWorkoutAtLabel,
                            progressDays = progress.progressDays
                        )
                    }
                }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentUserProfile() ?: return@launch
            _uiState.update { state ->
                state.copy(
                    uid = profile.uid,
                    displayName = profile.displayName,
                    email = profile.email,
                    aboutMe = profile.aboutMe,
                    editDisplayName = if (state.isEditing) state.editDisplayName else profile.displayName,
                    editAboutMe = if (state.isEditing) state.editAboutMe else profile.aboutMe
                )
            }
        }
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editDisplayName = it.displayName,
                editAboutMe = it.aboutMe
            )
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editDisplayName = it.displayName,
                editAboutMe = it.aboutMe
            )
        }
    }

    fun onEditDisplayNameChanged(value: String) {
        _uiState.update { it.copy(editDisplayName = value) }
    }

    fun onEditAboutMeChanged(value: String) {
        _uiState.update { it.copy(editAboutMe = value) }
    }

    fun saveProfile(
        validationMessage: String,
        updatedMessage: String,
        failureMessage: String
    ) {
        val newName = _uiState.value.editDisplayName.trim()
        val newAbout = _uiState.value.editAboutMe.trim()
        if (newName.length < 2) {
            _uiState.update { it.copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                authRepository.updateCurrentUserProfile(
                    displayName = newName,
                    aboutMe = newAbout
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        displayName = newName,
                        aboutMe = newAbout,
                        editDisplayName = newName,
                        editAboutMe = newAbout,
                        isEditing = false,
                        isSaving = false,
                        message = updatedMessage
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        message = it.message ?: failureMessage
                    )
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
            runCatching {
                withContext(Dispatchers.IO) {
                    seedRepository.ensureSeedLoaded()
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun logout() {
        authRepository.logout()
    }

    private fun buildProgressMetrics(
        sessions: List<WorkoutProgressSession>
    ): ProgressMetrics {
        if (sessions.isEmpty()) {
            return ProgressMetrics(progressDays = buildRecentDays(emptyMap()))
        }

        val today = LocalDate.now(zoneId)
        val weekStart = today.minusDays(6)
        val withDate = sessions.map { session ->
            Instant.ofEpochMilli(session.finishedAt).atZone(zoneId).toLocalDate() to session
        }
        val groupedByDay = withDate.groupBy({ it.first }, { it.second })
        val lastSession = sessions.maxByOrNull { it.finishedAt }
        val totalSeconds = sessions.sumOf { it.totalSeconds }

        return ProgressMetrics(
            hasProgress = true,
            completedWorkoutsCount = sessions.size,
            workoutsThisWeek = withDate.count { (date, _) -> !date.isBefore(weekStart) },
            completedMinutes = toRoundedMinutes(totalSeconds),
            currentStreakDays = calculateStreak(groupedByDay.keys),
            lastWorkoutTitle = lastSession?.programTitle.orEmpty(),
            lastWorkoutAtLabel = lastSession
                ?.let { session -> Instant.ofEpochMilli(session.finishedAt).atZone(zoneId).format(fullDateFormatter) }
                .orEmpty(),
            progressDays = buildRecentDays(groupedByDay)
        )
    }

    private fun buildRecentDays(
        groupedByDay: Map<LocalDate, List<WorkoutProgressSession>>
    ): List<ProfileProgressDayUi> {
        val today = LocalDate.now(zoneId)
        return (0L..6L).map { offset ->
            val day = today.minusDays(6 - offset)
            val daySessions = groupedByDay[day].orEmpty()
            ProfileProgressDayUi(
                label = day.format(dayFormatter),
                workouts = daySessions.size,
                minutes = toRoundedMinutes(daySessions.sumOf { it.totalSeconds })
            )
        }
    }

    private fun calculateStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        var streak = 0
        var day = LocalDate.now(zoneId)
        while (day in dates) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    private fun toRoundedMinutes(totalSeconds: Int): Int {
        if (totalSeconds <= 0) return 0
        return (totalSeconds + 59) / 60
    }

    private data class ProgressMetrics(
        val hasProgress: Boolean = false,
        val completedWorkoutsCount: Int = 0,
        val workoutsThisWeek: Int = 0,
        val completedMinutes: Int = 0,
        val currentStreakDays: Int = 0,
        val lastWorkoutTitle: String = "",
        val lastWorkoutAtLabel: String = "",
        val progressDays: List<ProfileProgressDayUi> = emptyList()
    )
}
