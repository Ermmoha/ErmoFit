package com.ermofit.app.newplan.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ermofit.app.newplan.data.local.entity.WorkoutSessionEntity
import com.ermofit.app.newplan.data.repository.NewPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsDay(
    val date: LocalDate,
    val sessions: Int,
    val minutes: Int
)

data class StatsUiState(
    val workoutsWeek: Int = 0,
    val workoutsMonth: Int = 0,
    val totalMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val days: List<StatsDay> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: NewPlanRepository
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()

    val uiState: StateFlow<StatsUiState> = repository.observeAllSessions()
        .map { sessions -> buildStats(sessions) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )

    private fun buildStats(sessions: List<WorkoutSessionEntity>): StatsUiState {
        if (sessions.isEmpty()) return StatsUiState()
        val today = LocalDate.now(zoneId)
        val weekStart = today.minusDays(6)
        val monthStart = today.minusDays(29)

        val withDate = sessions.map { session ->
            Instant.ofEpochMilli(session.finishedAt).atZone(zoneId).toLocalDate() to session
        }

        val workoutsWeek = withDate.count { (date, _) -> !date.isBefore(weekStart) }
        val workoutsMonth = withDate.count { (date, _) -> !date.isBefore(monthStart) }
        val totalMinutes = sessions.sumOf { it.totalSeconds } / 60

        val byDate = withDate.groupBy({ it.first }, { it.second })
        val days = (0L..6L).map { shift ->
            val day = today.minusDays(6 - shift)
            val daySessions = byDate[day].orEmpty()
            StatsDay(
                date = day,
                sessions = daySessions.size,
                minutes = daySessions.sumOf { it.totalSeconds } / 60
            )
        }

        return StatsUiState(
            workoutsWeek = workoutsWeek,
            workoutsMonth = workoutsMonth,
            totalMinutes = totalMinutes,
            currentStreakDays = calculateStreak(byDate.keys),
            days = days
        )
    }

    private fun calculateStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        var streak = 0
        var current = LocalDate.now(zoneId)
        while (current in dates) {
            streak += 1
            current = current.minusDays(1)
        }
        return streak
    }
}

