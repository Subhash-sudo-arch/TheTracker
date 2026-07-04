package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.data.TrackingSession
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    suspend fun calculateDailyProgress(context: Context): Int {
        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.trackingDao()
            val todayDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val todaySessions = dao.getSessionsByDate(todayDateStr).filter { it.isCompleted }

            val settingsManager = SettingsManager(context)
            val goals = mapOf(
                "Sleep" to settingsManager.goalSleepFlow.first(),
                "Study" to settingsManager.goalStudyFlow.first(),
                "Productivity" to settingsManager.goalProductivityFlow.first(),
                "Workout" to settingsManager.goalWorkoutFlow.first(),
                "Physical Activity" to settingsManager.goalActivityFlow.first(),
                "Training" to settingsManager.goalTrainingFlow.first(),
                "Habit" to settingsManager.goalHabitFlow.first()
            )

            var goalsMet = 0
            for ((type, goal) in goals) {
                val durationMs = todaySessions.filter { it.trackerType == type }.sumOf { it.endTime - it.startTime }
                val hours = durationMs.toFloat() / (1000 * 60 * 60)
                if (hours >= goal) {
                    goalsMet++
                }
            }

            val progressPercent = if (goals.isNotEmpty()) {
                (goalsMet.toFloat() / goals.size * 100).toInt()
            } else {
                0
            }
            return progressPercent
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating daily progress", e)
            return 0
        }
    }

    suspend fun calculateCurrentStreak(context: Context): Int {
        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.trackingDao()
            // We need to fetch all completed sessions to calculate streak
            val allSessions = database.trackingDao().getSessionsByDate("") // Wait, getSessionsByDate expects dateString. Let's see if we can get them some other way
            // Wait, we can observe completed sessions or get them. Let's check TrackingDao queries.
            // In TrackingDao, we have getAllSessionsFlow(), but we don't have a suspend function for all completed sessions.
            // Let's check TrackingDao:
            // "SELECT * FROM tracking_sessions WHERE isCompleted = 1 ORDER BY startTime DESC"
            // Let's check if we can add a suspend function to TrackingDao to query all completed sessions.
            // Oh! Yes, let's look at TrackingDao. It has:
            // @Query("SELECT * FROM tracking_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
            // fun getCompletedSessionsFlow(): Flow<List<TrackingSession>>
            // Wait! Can we get the current first element of the Flow to retrieve the list?
            // Yes, flow.first() will suspend and return the current list of completed sessions!
            // That is incredibly easy and elegant!
            val completedSessions = dao.getCompletedSessionsFlow().first()
            val dates = completedSessions.map { it.dateString }.distinct().sorted()
            if (dates.isEmpty()) return 0

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val parsedDates = dates.map { LocalDate.parse(it, formatter) }.distinct().sorted()

            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            return when {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating streak", e)
            return 0
        }
    }

    fun updateAllWidgets(context: Context) {
        val widgetManager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            ComponentName(context, TheTrackerWidgetProvider::class.java),
            ComponentName(context, TheTrackerWidgetProviderMedium::class.java),
            ComponentName(context, TheTrackerWidgetProviderLarge::class.java)
        )
        for (provider in providers) {
            val ids = widgetManager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) {
                val updateIntent = Intent(context, provider.className::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }

    suspend fun triggerBackgroundUpdate(context: Context) {
        val progress = calculateDailyProgress(context)
        IconProgressUpdater.updateIcon(context, progress)
        updateAllWidgets(context)

        // Synchronize TrackingSyncService state with active trackers
        try {
            val db = AppDatabase.getDatabase(context)
            val activeList = db.trackingDao().getActiveSessionsFlow().first()
            if (activeList.isNotEmpty()) {
                TrackingSyncService.startService(context)
            } else {
                TrackingSyncService.stopService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to coordinate TrackingSyncService on widget refresh", e)
        }
    }
}
