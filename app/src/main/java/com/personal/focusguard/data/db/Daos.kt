package com.personal.focusguard.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppRuleEntity?

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM app_rules")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("INSERT INTO opens (packageName, timestampMs) VALUES (:pkg, :ts)")
    suspend fun insertOpen(pkg: String, ts: Long)

    @Query("SELECT * FROM sessions WHERE endMs >= :since")
    suspend fun sessionsSince(since: Long): List<SessionEntity>

    @Query("SELECT * FROM opens WHERE timestampMs >= :since ORDER BY timestampMs")
    suspend fun opensSince(since: Long): List<OpenEventEntity>

    @Query("DELETE FROM sessions WHERE endMs < :before")
    suspend fun pruneSessions(before: Long)

    @Query("DELETE FROM opens WHERE timestampMs < :before")
    suspend fun pruneOpens(before: Long)

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM opens")
    suspend fun clearOpens()
}
