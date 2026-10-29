package com.personal.focusguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ficha de una app controlada (regla de tiempo + regla de aperturas). */
@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val usageEnabled: Boolean,
    val usageLimitMinutes: Int,
    val usagePeriodType: String,
    val usageEveryHours: Int,
    /** null = usar la hora de reinicio global. */
    val usageResetMinute: Int?,
    val openEnabled: Boolean,
    val openMax: Int,
    val openWindowType: String,
    val openWindowMinutes: Int,
    val openAnchorMinute: Int,
)

/** Intervalo en primer plano. Solo se guarda para apps con límite de tiempo activo. */
@Entity(tableName = "sessions", indices = [Index("packageName"), Index("endMs")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
)

/** Apertura contabilizada. Solo se guarda para apps con límite de aperturas activo. */
@Entity(tableName = "opens", indices = [Index("packageName"), Index("timestampMs")])
data class OpenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampMs: Long,
)
