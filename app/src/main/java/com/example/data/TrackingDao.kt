package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<TrackingSession>>

    @Query("SELECT * FROM tracking_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessionsFlow(): Flow<List<TrackingSession>>

    @Query("SELECT * FROM tracking_sessions WHERE isCompleted = 0")
    fun getActiveSessionsFlow(): Flow<List<TrackingSession>>

    @Query("SELECT * FROM tracking_sessions WHERE trackerType = :type AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveSessionByType(type: String): TrackingSession?

    @Query("SELECT * FROM tracking_sessions WHERE dateString = :dateString AND isCompleted = 1")
    fun getCompletedSessionsByDateFlow(dateString: String): Flow<List<TrackingSession>>

    @Query("SELECT * FROM tracking_sessions WHERE dateString = :dateString")
    suspend fun getSessionsByDate(dateString: String): List<TrackingSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrackingSession): Long

    @Update
    suspend fun updateSession(session: TrackingSession)

    @Delete
    suspend fun deleteSession(session: TrackingSession)

    @Query("DELETE FROM tracking_sessions")
    suspend fun clearAllSessions()
}
