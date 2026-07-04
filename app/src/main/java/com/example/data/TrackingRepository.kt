package com.example.data

import kotlinx.coroutines.flow.Flow

class TrackingRepository(private val trackingDao: TrackingDao) {

    val allSessions: Flow<List<TrackingSession>> = trackingDao.getAllSessionsFlow()
    val completedSessions: Flow<List<TrackingSession>> = trackingDao.getCompletedSessionsFlow()
    val activeSessions: Flow<List<TrackingSession>> = trackingDao.getActiveSessionsFlow()

    fun getCompletedSessionsByDate(dateString: String): Flow<List<TrackingSession>> =
        trackingDao.getCompletedSessionsByDateFlow(dateString)

    suspend fun getSessionsByDate(dateString: String): List<TrackingSession> =
        trackingDao.getSessionsByDate(dateString)

    suspend fun getActiveSessionByType(type: String): TrackingSession? =
        trackingDao.getActiveSessionByType(type)

    suspend fun insertSession(session: TrackingSession): Long =
        trackingDao.insertSession(session)

    suspend fun updateSession(session: TrackingSession) =
        trackingDao.updateSession(session)

    suspend fun deleteSession(session: TrackingSession) =
        trackingDao.deleteSession(session)

    suspend fun clearAllSessions() =
        trackingDao.clearAllSessions()
}
