package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.receiver.ReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

data class StreakStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
)

data class TrackerStatus(
    val type: String,
    val isActive: Boolean,
    val startTime: Long = 0L,
    val activeSessionId: Int = 0
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean,
    val progress: Float, // 0f to 1f
    val progressText: String
)

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val trackingDao = AppDatabase.getDatabase(application).trackingDao()
    private val repository = TrackingRepository(trackingDao)
    private val settingsManager = SettingsManager(application)

    init {
        viewModelScope.launch {
            com.example.widget.WidgetUpdater.triggerBackgroundUpdate(application)
        }
    }

    // Exposed DB flows
    val allSessions: StateFlow<List<TrackingSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedSessions: StateFlow<List<TrackingSession>> = repository.completedSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessions: StateFlow<List<TrackingSession>> = repository.activeSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exposed Settings flows
    val themeMode: StateFlow<Int> = settingsManager.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dynamicColors: StateFlow<Boolean> = settingsManager.dynamicColorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Goals Map Flow
    val goals: StateFlow<Map<String, Float>> = combine(
        listOf(
            settingsManager.goalSleepFlow,
            settingsManager.goalStudyFlow,
            settingsManager.goalProductivityFlow,
            settingsManager.goalWorkoutFlow,
            settingsManager.goalActivityFlow,
            settingsManager.goalTrainingFlow,
            settingsManager.goalHabitFlow
        )
    ) { array ->
        mapOf(
            "Sleep" to array[0],
            "Study" to array[1],
            "Productivity" to array[2],
            "Workout" to array[3],
            "Physical Activity" to array[4],
            "Training" to array[5],
            "Habit" to array[6]
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Reminders Map Flow
    val reminders: StateFlow<Map<String, Boolean>> = combine(
        settingsManager.reminderSleepFlow,
        settingsManager.reminderStudyFlow,
        settingsManager.reminderWorkoutFlow,
        settingsManager.reminderHabitFlow
    ) { sleep, study, work, hab ->
        mapOf(
            "Sleep" to sleep,
            "Study" to study,
            "Workout" to work,
            "Habit" to hab
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Helper Flow to map active trackers
    val trackerStatuses: StateFlow<Map<String, TrackerStatus>> = activeSessions.map { actives ->
        val trackerTypes = listOf("Sleep", "Study", "Productivity", "Workout", "Physical Activity", "Training", "Habit")
        trackerTypes.associateWith { type ->
            val matching = actives.firstOrNull { it.trackerType == type }
            TrackerStatus(
                type = type,
                isActive = matching != null,
                startTime = matching?.startTime ?: 0L,
                activeSessionId = matching?.id ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Streak and History Stats
    val streakStats: StateFlow<StreakStats> = completedSessions.map { sessions ->
        val dates = sessions.map { it.dateString }.distinct().sorted()
        val (current, longest) = calculateStreak(dates)
        StreakStats(current, longest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakStats())

    // Achievements evaluation flow
    val achievements: StateFlow<List<Achievement>> = completedSessions.map { sessions ->
        evaluateAchievements(sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracker Workflows
    fun startTracker(type: String) {
        viewModelScope.launch {
            // Check if already active to prevent duplicates
            val active = repository.getActiveSessionByType(type)
            if (active == null) {
                val now = System.currentTimeMillis()
                val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val session = TrackingSession(
                    trackerType = type,
                    startTime = now,
                    dateString = dateStr
                )
                repository.insertSession(session)
                com.example.widget.WidgetUpdater.triggerBackgroundUpdate(getApplication())
            }
        }
    }

    fun finishTracker(id: Int, quality: String, notes: String) {
        viewModelScope.launch {
            val sessions = activeSessions.value
            val target = sessions.firstOrNull { it.id == id } ?: return@launch
            val now = System.currentTimeMillis()
            val updated = target.copy(
                endTime = now,
                isCompleted = true,
                quality = quality,
                notes = notes,
                dateString = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
            repository.updateSession(updated)
            com.example.widget.WidgetUpdater.triggerBackgroundUpdate(getApplication())
        }
    }

    fun cancelTracker(id: Int) {
        viewModelScope.launch {
            val sessions = activeSessions.value
            val target = sessions.firstOrNull { it.id == id } ?: return@launch
            repository.deleteSession(target)
            com.example.widget.WidgetUpdater.triggerBackgroundUpdate(getApplication())
        }
    }

    fun deleteSession(session: TrackingSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            com.example.widget.WidgetUpdater.triggerBackgroundUpdate(getApplication())
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllSessions()
            com.example.widget.WidgetUpdater.triggerBackgroundUpdate(getApplication())
        }
    }

    // Settings actions
    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDynamicColors(enabled)
        }
    }

    fun updateGoal(type: String, value: Float) {
        viewModelScope.launch {
            val key = when (type) {
                "Sleep" -> SettingsManager.GOAL_SLEEP
                "Study" -> SettingsManager.GOAL_STUDY
                "Productivity" -> SettingsManager.GOAL_PRODUCTIVITY
                "Workout" -> SettingsManager.GOAL_WORKOUT
                "Physical Activity" -> SettingsManager.GOAL_ACTIVITY
                "Training" -> SettingsManager.GOAL_TRAINING
                else -> SettingsManager.GOAL_HABIT
            }
            settingsManager.setGoal(key, value)
        }
    }

    fun updateReminder(type: String, enabled: Boolean) {
        viewModelScope.launch {
            val key = when (type) {
                "Sleep" -> SettingsManager.REMINDER_SLEEP
                "Study" -> SettingsManager.REMINDER_STUDY
                "Workout" -> SettingsManager.REMINDER_WORKOUT
                else -> SettingsManager.REMINDER_HABIT
            }
            settingsManager.setReminder(key, enabled)
            // Schedule using local alarm scheduler
            ReminderScheduler.scheduleReminder(getApplication(), type, enabled)
        }
    }

    // Export format strings
    fun exportToJson(): String {
        val list = completedSessions.value
        val jsonArray = JSONArray()
        for (session in list) {
            val obj = JSONObject().apply {
                put("id", session.id)
                put("trackerType", session.trackerType)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("quality", session.quality)
                put("notes", session.notes)
                put("dateString", session.dateString)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun exportToCsv(): String {
        val list = completedSessions.value
        val sb = java.lang.StringBuilder()
        sb.append("ID,Type,StartTime,EndTime,DurationMinutes,Quality,Notes,Date\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (s in list) {
            val durationMin = (s.endTime - s.startTime) / (1000 * 60)
            val startStr = sdf.format(Date(s.startTime))
            val endStr = sdf.format(Date(s.endTime))
            // escape notes
            val cleanNotes = s.notes.replace("\"", "\"\"")
            sb.append("${s.id},\"${s.trackerType}\",\"$startStr\",\"$endStr\",$durationMin,\"${s.quality}\",\"$cleanNotes\",\"${s.dateString}\"\n")
        }
        return sb.toString()
    }

    fun importFromJson(jsonStr: String): Boolean {
        return try {
            val array = JSONArray(jsonStr)
            viewModelScope.launch {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val session = TrackingSession(
                        trackerType = obj.getString("trackerType"),
                        startTime = obj.getLong("startTime"),
                        endTime = obj.getLong("endTime"),
                        isCompleted = true,
                        quality = obj.getString("quality"),
                        notes = obj.optString("notes", ""),
                        dateString = obj.getString("dateString")
                    )
                    repository.insertSession(session)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromCsv(csvStr: String): Boolean {
        return try {
            val lines = csvStr.lines()
            if (lines.size <= 1) return false
            viewModelScope.launch {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    // Extremely basic CSV parser splits by comma, respecting quotes
                    val tokens = parseCsvLine(line)
                    if (tokens.size < 7) continue
                    val type = tokens[1].replace("\"", "")
                    val startStr = tokens[2].replace("\"", "")
                    val endStr = tokens[3].replace("\"", "")
                    val quality = tokens[5].replace("\"", "")
                    val notes = tokens[6].replace("\"", "")
                    val dateStr = if (tokens.size > 7) tokens[7].replace("\"", "") else LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                    val startVal = try { sdf.parse(startStr)?.time ?: System.currentTimeMillis() } catch (ex: Exception) { System.currentTimeMillis() }
                    val endVal = try { sdf.parse(endStr)?.time ?: System.currentTimeMillis() } catch (ex: Exception) { System.currentTimeMillis() }

                    val session = TrackingSession(
                        trackerType = type,
                        startTime = startVal,
                        endTime = endVal,
                        isCompleted = true,
                        quality = quality,
                        notes = notes,
                        dateString = dateStr
                    )
                    repository.insertSession(session)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val curVal = StringBuilder()
        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(curVal.toString().trim())
                curVal.setLength(0)
            } else {
                curVal.append(ch)
            }
        }
        result.add(curVal.toString().trim())
        return result
    }

    // Helper algorithms
    private fun calculateStreak(dates: List<String>): Pair<Int, Int> {
        if (dates.isEmpty()) return Pair(0, 0)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val parsedDates = dates.map { LocalDate.parse(it, formatter) }.distinct().sorted()

        var longest = 0
        var tempStreak = 0
        var lastDate: LocalDate? = null

        for (date in parsedDates) {
            if (lastDate == null) {
                tempStreak = 1
            } else {
                val diff = ChronoUnit.DAYS.between(lastDate, date)
                if (diff == 1L) {
                    tempStreak++
                } else if (diff > 1L) {
                    if (tempStreak > longest) longest = tempStreak
                    tempStreak = 1
                }
            }
            lastDate = date
        }
        if (tempStreak > longest) longest = tempStreak

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val current = when {
            parsedDates.contains(today) -> {
                var c = 0
                var check = today
                while (parsedDates.contains(check)) {
                    c++
                    check = check.minusDays(1)
                }
                c
            }
            parsedDates.contains(yesterday) -> {
                var c = 0
                var check = yesterday
                while (parsedDates.contains(check)) {
                    c++
                    check = check.minusDays(1)
                }
                c
            }
            else -> 0
        }
        return Pair(current, longest)
    }

    private fun evaluateAchievements(sessions: List<TrackingSession>): List<Achievement> {
        val totalStudiedHours = sessions.filter { it.trackerType == "Study" }.sumOf { (it.endTime - it.startTime).toDouble() } / (1000.0 * 60.0 * 60.0)
        val totalWorkouts = sessions.count { it.trackerType == "Workout" }
        val hasSleep = sessions.any { it.trackerType == "Sleep" }
        val hasStudy = sessions.any { it.trackerType == "Study" }
        val hasWorkout = sessions.any { it.trackerType == "Workout" }

        val dates = sessions.map { it.dateString }.distinct().sorted()
        val (_, longestStreak) = calculateStreak(dates)

        val firstActiveDate = if (dates.isNotEmpty()) LocalDate.parse(dates.first(), DateTimeFormatter.ISO_LOCAL_DATE) else null
        val daysActive = if (firstActiveDate != null) ChronoUnit.DAYS.between(firstActiveDate, LocalDate.now()) + 1 else 0L

        return listOf(
            Achievement(
                id = "first_workout",
                title = "First Workout",
                description = "Complete your first workout session.",
                emoji = "💪",
                isUnlocked = hasWorkout,
                progress = if (hasWorkout) 1f else 0f,
                progressText = if (hasWorkout) "Completed" else "0/1"
            ),
            Achievement(
                id = "first_study",
                title = "First Study",
                description = "Complete your first study session.",
                emoji = "📖",
                isUnlocked = hasStudy,
                progress = if (hasStudy) 1f else 0f,
                progressText = if (hasStudy) "Completed" else "0/1"
            ),
            Achievement(
                id = "first_sleep",
                title = "First Sleep",
                description = "Complete your first sleep tracking entry.",
                emoji = "😴",
                isUnlocked = hasSleep,
                progress = if (hasSleep) 1f else 0f,
                progressText = if (hasSleep) "Completed" else "0/1"
            ),
            Achievement(
                id = "streak_7",
                title = "7-Day Streak",
                description = "Track consistently for 7 days in a row.",
                emoji = "🔥",
                isUnlocked = longestStreak >= 7,
                progress = (longestStreak.toFloat() / 7f).coerceAtMost(1f),
                progressText = "$longestStreak/7 days"
            ),
            Achievement(
                id = "streak_30",
                title = "30-Day Streak",
                description = "Track consistently for 30 days in a row.",
                emoji = "💥",
                isUnlocked = longestStreak >= 30,
                progress = (longestStreak.toFloat() / 30f).coerceAtMost(1f),
                progressText = "$longestStreak/30 days"
            ),
            Achievement(
                id = "study_100",
                title = "Academic Master",
                description = "Study for 100 hours or more.",
                emoji = "⭐",
                isUnlocked = totalStudiedHours >= 100,
                progress = (totalStudiedHours.toFloat() / 100f).coerceAtMost(1f),
                progressText = "${String.format(Locale.US, "%.1f", totalStudiedHours)}/100 hrs"
            ),
            Achievement(
                id = "workout_100",
                title = "Workout Warrior",
                description = "Complete 100 workout sessions.",
                emoji = "🏅",
                isUnlocked = totalWorkouts >= 100,
                progress = (totalWorkouts.toFloat() / 100f).coerceAtMost(1f),
                progressText = "$totalWorkouts/100 workouts"
            ),
            Achievement(
                id = "month_active",
                title = "One Month Active",
                description = "Stay registered and active for 30 days.",
                emoji = "🎉",
                isUnlocked = daysActive >= 30,
                progress = (daysActive.toFloat() / 30f).coerceAtMost(1f),
                progressText = "$daysActive/30 days"
            ),
            Achievement(
                id = "year_active",
                title = "Yearly Champion",
                description = "Stay registered and active for 365 days.",
                emoji = "👑",
                isUnlocked = daysActive >= 365,
                progress = (daysActive.toFloat() / 365f).coerceAtMost(1f),
                progressText = "$daysActive/365 days"
            )
        )
    }
}
