package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_sessions")
data class TrackingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val trackerType: String, // "Sleep", "Study", "Productivity", "Workout", "Physical Activity", "Training", "Habit"
    val startTime: Long,     // Milliseconds timestamp
    val endTime: Long = 0L,  // Milliseconds timestamp
    val isCompleted: Boolean = false,
    val quality: String = "", // "GOOD", "NORMAL", "BAD"
    val notes: String = "",
    val dateString: String   // "yyyy-MM-dd" format for local date
)
