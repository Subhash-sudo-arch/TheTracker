package com.example.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TrackingSyncService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var isUpdatingLoopStarted = false

    companion object {
        private const val TAG = "TrackingSyncService"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "tracking_sync_channel"

        fun startService(context: Context) {
            val intent = Intent(context, TrackingSyncService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "startService helper called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TrackingSyncService::class.java)
            try {
                context.stopService(intent)
                Log.d(TAG, "stopService helper called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        // Start foreground immediately to comply with Android foreground service requirements
        val initialNotification = createNotification("Initializing tracker...", "")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received")
        if (intent?.action == "ACTION_STOP_ALL_TRACKERS") {
            Log.d(TAG, "Action Stop All Trackers received from notification")
            scope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val dao = db.trackingDao()
                    val active = dao.getActiveSessionsFlow().first()
                    val now = System.currentTimeMillis()
                    val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    for (session in active) {
                        val updated = session.copy(
                            endTime = now,
                            isCompleted = true,
                            dateString = todayStr
                        )
                        dao.updateSession(updated)
                    }
                    WidgetUpdater.triggerBackgroundUpdate(applicationContext)
                    stopSelf()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop all trackers from service action", e)
                }
            }
            return START_NOT_STICKY
        }

        if (!isUpdatingLoopStarted) {
            isUpdatingLoopStarted = true
            startObservingAndSyncing()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObservingAndSyncing() {
        // Observe active session list from database in main scope
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.trackingDao()
                dao.getActiveSessionsFlow()
                    .distinctUntilChanged()
                    .collect { activeList ->
                        if (activeList.isEmpty()) {
                            Log.d(TAG, "No active sessions found via Flow. Stopping service.")
                            stopSelf()
                        } else {
                            updateNotificationAndWidgets(activeList)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting active sessions flow", e)
            }
        }

        // Launch a background coroutine to run a periodic refresh loop (every 15 seconds)
        scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    kotlinx.coroutines.delay(15000)
                    val db = AppDatabase.getDatabase(applicationContext)
                    val activeList = db.trackingDao().getActiveSessionsFlow().first()
                    if (activeList.isNotEmpty()) {
                        Log.d(TAG, "Periodic 15s updates executing")
                        // Trigger general background update (computes goals progress & syncs widgets)
                        WidgetUpdater.triggerBackgroundUpdate(applicationContext)
                        
                        // Push an updated notification with real-time minutes/hours elapsed
                        scope.launch {
                            updateNotificationAndWidgets(activeList)
                        }
                    } else {
                        Log.d(TAG, "Periodic loop detected no active trackers. Self-stopping.")
                        stopSelf()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in background periodic sync loop", e)
            }
        }
    }

    private suspend fun updateNotificationAndWidgets(activeSessions: List<TrackingSession>) {
        if (activeSessions.isEmpty()) {
            stopSelf()
            return
        }

        // Calculate and format content
        val title = if (activeSessions.size == 1) {
            val type = activeSessions[0].trackerType
            "Active Tracker: $type"
        } else {
            "${activeSessions.size} Active Trackers Running"
        }

        val contentBuilder = StringBuilder()
        val now = System.currentTimeMillis()
        for (session in activeSessions) {
            val elapsedMs = now - session.startTime
            val elapsedMinutes = elapsedMs / 60000
            val elapsedHours = elapsedMinutes / 60
            val minsRemaining = elapsedMinutes % 60
            
            val timeText = if (elapsedHours > 0) {
                "${elapsedHours}h ${minsRemaining}m"
            } else {
                "${elapsedMinutes}m"
            }
            
            val emoji = getEmojiForType(session.trackerType)
            contentBuilder.append("$emoji ${session.trackerType} ($timeText elapsed)\n")
        }
        val contentText = contentBuilder.toString().trim()

        val notification = createNotification(title, contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
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

    private fun createNotification(title: String, contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Service Action button inside notification for easy control
        val stopIntent = Intent(this, TrackingSyncService::class.java).apply {
            action = "ACTION_STOP_ALL_TRACKERS"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play) // Standard play media symbol
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop All",
                stopPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracker Synchronization",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps tracker widgets and data synchronized in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        job.cancel()
    }
}
