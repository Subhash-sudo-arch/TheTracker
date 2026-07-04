package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trackerType = intent.getStringExtra("tracker_type") ?: "Habit"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "thetracker_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to track your activities"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Track your $trackerType"
        val text = when (trackerType) {
            "Sleep" -> "Time to track your sleep! Sleep well and stay energized. 😴"
            "Study" -> "Ready to hit the books? Let's get some study done! 📖"
            "Workout" -> "Time to move! Get your daily workout in. 💪"
            else -> "Don't forget to track your daily habits and keep your streak! 🔥"
        }

        // Drawables could be anything, let's use the default application icon for safe fallback
        val iconRes = context.applicationInfo.icon

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = when (trackerType) {
            "Sleep" -> 101
            "Study" -> 102
            "Workout" -> 103
            else -> 104
        }

        notificationManager.notify(notificationId, notification)
    }
}
