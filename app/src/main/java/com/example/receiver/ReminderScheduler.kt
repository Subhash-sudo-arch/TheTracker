package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {

    fun scheduleReminder(context: Context, trackerType: String, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("tracker_type", trackerType)
        }

        val requestCode = when (trackerType) {
            "Sleep" -> 201
            "Study" -> 202
            "Workout" -> 203
            else -> 204
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (enabled) {
            val calendar = Calendar.getInstance().apply {
                val currentHour = get(Calendar.HOUR_OF_DAY)
                val targetHour = when (trackerType) {
                    "Sleep" -> 22 // 10 PM
                    "Study" -> 9  // 9 AM
                    "Workout" -> 17 // 5 PM
                    else -> 20 // 8 PM
                }
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)

                // If scheduled hour is in past, move to tomorrow
                if (get(Calendar.HOUR_OF_DAY) < currentHour || (get(Calendar.HOUR_OF_DAY) == currentHour && get(Calendar.MINUTE) <= Calendar.getInstance().get(Calendar.MINUTE))) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }
}
