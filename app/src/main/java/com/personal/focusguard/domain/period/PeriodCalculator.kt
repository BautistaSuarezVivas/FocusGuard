package com.personal.focusguard.domain.period

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Intervalo semiabierto [start, end) en epoch ms. */
data class TimeRange(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t >= start && t < end
}

sealed interface PeriodSpec {
    /**
     * Bloques fijos de [lengthMinutes] que empiezan cada día a [anchorMinuteOfDay] (hora local).
     * Un período diario es `FixedBlocks(1440, anchor)`.
     * Si la duración no divide exactamente el día, el último bloque del ciclo se recorta en el
     * siguiente ancla, de modo que todos los días empiezan en el mismo horario.
     */
    data class FixedBlocks(val lengthMinutes: Int, val anchorMinuteOfDay: Int) : PeriodSpec

    /** Semana que comienza el día [startDay] a [anchorMinuteOfDay]. */
    data class Weekly(val anchorMinuteOfDay: Int, val startDay: DayOfWeek = DayOfWeek.MONDAY) : PeriodSpec

    /** Ventana móvil: los últimos [lengthMinutes] minutos hasta ahora. */
    data class Rolling(val lengthMinutes: Int) : PeriodSpec
}

/**
 * Cálculo de períodos 100 % derivado de "ahora" y la zona horaria: no depende de alarmas ni de
 * estado guardado, por lo que se corrige solo tras reinicios, cambios de hora o de zona.
 * Los anclajes se calculan con [java.time.ZonedDateTime], que resuelve horarios de verano
 * (horas inexistentes se desplazan hacia adelante, horas repetidas toman el primer instante).
 */
object PeriodCalculator {

    private const val MINUTE_MS = 60_000L

    fun currentRange(spec: PeriodSpec, now: Long, zone: ZoneId): TimeRange = when (spec) {
        is PeriodSpec.FixedBlocks -> fixedBlock(spec, now, zone)
        is PeriodSpec.Weekly -> weekly(spec, now, zone)
        is PeriodSpec.Rolling -> TimeRange(now - spec.lengthMinutes * MINUTE_MS, now + 1)
    }

    /** Próximo reinicio del contador, o null para ventanas móviles (se liberan de forma gradual). */
    fun nextReset(spec: PeriodSpec, now: Long, zone: ZoneId): Long? = when (spec) {
        is PeriodSpec.Rolling -> null
        else -> currentRange(spec, now, zone).end
    }

    private fun anchorInstant(date: LocalDate, anchorMinute: Int, zone: ZoneId): Long {
        val m = anchorMinute.coerceIn(0, 1439)
        return date.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    private fun fixedBlock(spec: PeriodSpec.FixedBlocks, now: Long, zone: ZoneId): TimeRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var cycleDate = today
        var cycleStart = anchorInstant(cycleDate, spec.anchorMinuteOfDay, zone)
        if (cycleStart > now) {
            cycleDate = today.minusDays(1)
            cycleStart = anchorInstant(cycleDate, spec.anchorMinuteOfDay, zone)
        }
        val cycleEnd = anchorInstant(cycleDate.plusDays(1), spec.anchorMinuteOfDay, zone)
        val lengthMs = spec.lengthMinutes.coerceAtLeast(1) * MINUTE_MS
        val index = (now - cycleStart) / lengthMs
        val blockStart = cycleStart + index * lengthMs
        val blockEnd = minOf(blockStart + lengthMs, cycleEnd)
        return TimeRange(blockStart, blockEnd)
    }

    private fun weekly(spec: PeriodSpec.Weekly, now: Long, zone: ZoneId): TimeRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var weekDate = today.with(TemporalAdjusters.previousOrSame(spec.startDay))
        var start = anchorInstant(weekDate, spec.anchorMinuteOfDay, zone)
        if (start > now) {
            weekDate = weekDate.minusWeeks(1)
            start = anchorInstant(weekDate, spec.anchorMinuteOfDay, zone)
        }
        val end = anchorInstant(weekDate.plusWeeks(1), spec.anchorMinuteOfDay, zone)
        return TimeRange(start, end)
    }
}
