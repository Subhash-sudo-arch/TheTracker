package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.data.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

open class TheTrackerWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.widget.ACTION_TOGGLE_TRACKER") {
            val type = intent.getStringExtra("extra_tracker_type") ?: return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.trackingDao()
                    val active = dao.getActiveSessionByType(type)
                    if (active != null) {
                        // Stop the active tracker
                        val now = System.currentTimeMillis()
                        val updated = active.copy(
                            endTime = now,
                            isCompleted = true,
                            dateString = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        )
                        dao.updateSession(updated)
                        Log.d("WidgetProvider", "Background: Stopped active session of type: $type")
                    } else {
                        // Start a new tracker session
                        val now = System.currentTimeMillis()
                        val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val session = TrackingSession(
                            trackerType = type,
                            startTime = now,
                            dateString = dateStr
                        )
                        dao.insertSession(session)
                        Log.d("WidgetProvider", "Background: Started active session of type: $type")
                    }
                    // Recalculate everything and update launcher icon and all home widgets
                    WidgetUpdater.triggerBackgroundUpdate(context)
                } catch (e: Exception) {
                    Log.e("WidgetProvider", "Background quick action toggle failure", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.trackingDao()
                val todayDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val todaySessions = dao.getSessionsByDate(todayDateStr).filter { it.isCompleted }
                val activeSessions = dao.getActiveSessionsFlow().first()

                val progress = WidgetUpdater.calculateDailyProgress(context)
                val streak = WidgetUpdater.calculateCurrentStreak(context)

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

                for (appWidgetId in appWidgetIds) {
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    val layoutId = info?.initialLayout ?: R.layout.widget_small
                    val views = RemoteViews(context.packageName, layoutId)

                    // Standard values: Progress bar and progress text
                    views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
                    views.setTextViewText(R.id.widget_progress_text, "$progress%")

                    if (layoutId == R.layout.widget_small) {
                        // Small widget has one smart action button
                        val activeType = activeSessions.firstOrNull()?.trackerType
                        val btnText = if (activeType != null) {
                            val emoji = getEmojiForType(activeType)
                            "Stop $emoji"
                        } else {
                            "📚 Start Study"
                        }
                        views.setTextViewText(R.id.btn_quick_action, btnText)
                        
                        val targetType = activeType ?: "Study"
                        views.setOnClickPendingIntent(
                            R.id.btn_quick_action,
                            getTogglePendingIntent(context, appWidgetId, targetType)
                        )

                    } else if (layoutId == R.layout.widget_medium) {
                        // Medium widget has streak and a 2x2 grid of actions
                        views.setTextViewText(R.id.widget_streak_text, "🔥 $streak DAYS")

                        val trackerTypes = listOf("Sleep", "Study", "Workout", "Productivity")
                        val btnIds = listOf(R.id.btn_sleep, R.id.btn_study, R.id.btn_workout, R.id.btn_productivity)

                        for (i in trackerTypes.indices) {
                            val type = trackerTypes[i]
                            val btnId = btnIds[i]
                            val isActive = activeSessions.any { it.trackerType == type }

                            val emoji = getEmojiForType(type)
                            val text = if (isActive) "Stop $emoji" else "Start $emoji"
                            views.setTextViewText(btnId, text)
                            
                            // Highlight background resource if active
                            views.setInt(
                                btnId,
                                "setBackgroundResource",
                                if (isActive) R.drawable.widget_btn_active else R.drawable.widget_btn_inactive
                            )

                            views.setOnClickPendingIntent(
                                btnId,
                                getTogglePendingIntent(context, appWidgetId + i * 100, type)
                            )
                        }

                    } else if (layoutId == R.layout.widget_large) {
                        // Large widget displays details, goals, achievement and 3 action buttons
                        views.setTextViewText(R.id.widget_streak, "🔥 $streak DAY STREAK")

                        updateGoalText(views, R.id.txt_goal_sleep, "Sleep", todaySessions, goals["Sleep"] ?: 8.0f)
                        updateGoalText(views, R.id.txt_goal_study, "Study", todaySessions, goals["Study"] ?: 4.0f)
                        updateGoalText(views, R.id.txt_goal_workout, "Workout", todaySessions, goals["Workout"] ?: 1.0f)
                        updateGoalText(views, R.id.txt_goal_productivity, "Productivity", todaySessions, goals["Productivity"] ?: 6.0f)
                        updateGoalText(views, R.id.txt_goal_habit, "Habit", todaySessions, goals["Habit"] ?: 1.0f)

                        val trackerTypes = listOf("Sleep", "Study", "Workout")
                        val btnIds = listOf(R.id.btn_large_sleep, R.id.btn_large_study, R.id.btn_large_workout)

                        for (i in trackerTypes.indices) {
                            val type = trackerTypes[i]
                            val btnId = btnIds[i]
                            val isActive = activeSessions.any { it.trackerType == type }

                            val emoji = getEmojiForType(type)
                            val text = if (isActive) "Stop $emoji" else "Start $emoji"
                            views.setTextViewText(btnId, text)

                            views.setInt(
                                btnId,
                                "setBackgroundResource",
                                if (isActive) R.drawable.widget_btn_active else R.drawable.widget_btn_inactive
                            )

                            views.setOnClickPendingIntent(
                                btnId,
                                getTogglePendingIntent(context, appWidgetId + i * 1000, type)
                            )
                        }

                        // Determine a dynamic achievement status text
                        val achievementTitle = when {
                            streak >= 5 -> "Unlocked: Unstoppable Streak! (5+ Days)"
                            streak >= 1 -> "Unlocked: First Step (Active Today)"
                            else -> "Keep tracking to unlock achievements!"
                        }
                        views.setTextViewText(R.id.txt_achievement_title, achievementTitle)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("WidgetProvider", "Failed to update widget RemoteViews asynchronously", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getEmojiForType(type: String): String {
        return when (type) {
            "Sleep" -> "🌙"
            "Study" -> "📚"
            "Workout" -> "🏋"
            "Productivity" -> "💼"
            "Physical Activity" -> "🏃"
            "Training" -> "🎯"
            "Habit" -> "✅"
            else -> "✓"
        }
    }

    private fun getTogglePendingIntent(context: Context, requestId: Int, type: String): PendingIntent {
        val intent = Intent(context, TheTrackerWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_TOGGLE_TRACKER"
            putExtra("extra_tracker_type", type)
        }
        return PendingIntent.getBroadcast(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateGoalText(
        views: RemoteViews,
        textViewId: Int,
        type: String,
        sessions: List<TrackingSession>,
        goal: Float
    ) {
        val durationMs = sessions.filter { it.trackerType == type }.sumOf { it.endTime - it.startTime }
        val hours = durationMs.toFloat() / (1000 * 60 * 60)
        val emoji = getEmojiForType(type)
        val formattedHours = String.format("%.1fh", hours)
        val statusText = if (hours >= goal) {
            "$emoji $type: $formattedHours / ${goal}h (DONE!)"
        } else {
            "$emoji $type: $formattedHours / ${goal}h"
        }
        views.setTextViewText(textViewId, statusText)
    }
}
