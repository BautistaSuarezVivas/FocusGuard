package com.personal.focusguard.domain.morning

import com.personal.focusguard.domain.period.TimeRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class MorningStart {
    /** Empieza con el primer desbloqueo del día entre [MorningRule.earliestMinute] y [MorningRule.latestMinute]. */
    FIRST_UNLOCK,

    /** Empieza todos los días a [MorningRule.fixedMinute]. */
    FIXED_TIME,
}

/**
 * "Modo mañana": durante las primeras horas del día el teléfono queda limitado.
 *  - [restricted]: apps bloqueadas durante todo el período.
 *  - Límite de pantalla: tiempo máximo en apps que no son privilegiadas ni esenciales; al agotarse
 *    solo se pueden usar las privilegiadas.
 *  - [privileged]: siempre accesibles; su tiempo no cuenta para el límite.
 */
data class MorningRule(
    val enabled: Boolean = false,
    val start: MorningStart = MorningStart.FIRST_UNLOCK,
    val earliestMinute: Int = 5 * 60,
    val latestMinute: Int = 12 * 60,
    val fixedMinute: Int = 7 * 60,
    val durationMinutes: Int = 120,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val screenLimitEnabled: Boolean = true,
    val screenLimitMinutes: Int = 20,
    val restricted: Set<String> = emptySet(),
    val privileged: Set<String> = emptySet(),
) {
    val screenLimitMs: Long get() = screenLimitMinutes.coerceAtLeast(0) * 60_000L
}

/** Cálculo de la ventana del modo mañana, derivado de "ahora" y de los desbloqueos registrados. */
object MorningSchedule {

    private fun at(date: LocalDate, minute: Int, zone: ZoneId): Long {
        val m = minute.coerceIn(0, 1439)
        return date.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    /** Ventana que comienza el día [date], o null si ese día no hay modo mañana. */
    fun windowFor(rule: MorningRule, date: LocalDate, zone: ZoneId, unlocks: List<Long>): TimeRange? {
        if (!rule.enabled || date.dayOfWeek !in rule.days) return null
        val durationMs = rule.durationMinutes.coerceAtLeast(1) * 60_000L
        val start = when (rule.start) {
            MorningStart.FIXED_TIME -> at(date, rule.fixedMinute, zone)
            MorningStart.FIRST_UNLOCK -> {
                val from = at(date, rule.earliestMinute, zone)
                val to = at(date, rule.latestMinute, zone)
                unlocks.filter { it in from until to }.minOrNull() ?: return null
            }
        }
        return TimeRange(start, start + durationMs)
    }

    /** Ventana activa en [now] (se mira hoy y ayer por si cruza la medianoche), o null. */
    fun activeWindow(rule: MorningRule, now: Long, zone: ZoneId, unlocks: List<Long>): TimeRange? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return listOf(today, today.minusDays(1))
            .mapNotNull { windowFor(rule, it, zone, unlocks) }
            .firstOrNull { now in it }
    }
}
