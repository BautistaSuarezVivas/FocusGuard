package com.personal.focusguard.domain.limits

import com.personal.focusguard.domain.model.OpenRule
import com.personal.focusguard.domain.model.UsageRule
import com.personal.focusguard.domain.period.PeriodCalculator
import com.personal.focusguard.domain.period.PeriodSpec
import com.personal.focusguard.domain.period.TimeRange
import java.time.ZoneId

data class Interval(val start: Long, val end: Long)

object IntervalMath {
    /**
     * Duración total de la UNIÓN de [intervals] recortada a [clipStart, clipEnd).
     * Fusiona solapamientos, así que un intervalo registrado dos veces nunca se cuenta dos veces.
     */
    fun unionDuration(intervals: List<Interval>, clipStart: Long, clipEnd: Long): Long {
        if (clipEnd <= clipStart) return 0
        val clipped = intervals
            .map { Interval(maxOf(it.start, clipStart), minOf(it.end, clipEnd)) }
            .filter { it.end > it.start }
            .sortedBy { it.start }
        var total = 0L
        var curStart = Long.MIN_VALUE
        var curEnd = Long.MIN_VALUE
        for (iv in clipped) {
            if (iv.start > curEnd) {
                if (curEnd > curStart) total += curEnd - curStart
                curStart = iv.start
                curEnd = iv.end
            } else if (iv.end > curEnd) {
                curEnd = iv.end
            }
        }
        if (curEnd > curStart) total += curEnd - curStart
        return total
    }
}

data class UsageStatus(
    val usedMs: Long,
    val limitMs: Long,
    val period: TimeRange,
) {
    val remainingMs: Long get() = (limitMs - usedMs).coerceAtLeast(0)
    val exhausted: Boolean get() = usedMs >= limitMs
    /** Momento en el que se vuelve a tener tiempo disponible. */
    val resetsAt: Long get() = period.end
}

data class OpenStatus(
    val used: Int,
    val max: Int,
    val isRolling: Boolean,
    /** Fin del bloque fijo actual (null en ventanas móviles). */
    val blockEnd: Long?,
    /** Cuándo se habilita la próxima apertura si ya no quedan (null si quedan). */
    val nextAvailableAt: Long?,
) {
    val remaining: Int get() = (max - used).coerceAtLeast(0)
    val exhausted: Boolean get() = used >= max
}

object LimitEvaluator {

    fun usage(
        rule: UsageRule,
        defaultResetMinute: Int,
        sessions: List<Interval>,
        now: Long,
        zone: ZoneId,
    ): UsageStatus {
        val period = PeriodCalculator.currentRange(rule.periodSpec(defaultResetMinute), now, zone)
        // Solo cuenta tiempo ya transcurrido dentro del período actual.
        val used = IntervalMath.unionDuration(sessions, period.start, minOf(now, period.end))
        return UsageStatus(used, rule.limitMs, period)
    }

    fun opens(rule: OpenRule, opens: List<Long>, now: Long, zone: ZoneId): OpenStatus {
        val spec = rule.periodSpec()
        return when (spec) {
            is PeriodSpec.Rolling -> {
                val windowMs = spec.lengthMinutes * 60_000L
                val from = now - windowMs
                // Aperturas con marca posterior a "ahora" (p. ej. tras atrasar el reloj) se siguen
                // contando: así atrasar la hora no regala aperturas.
                val inWindow = opens.filter { it > from }.sorted()
                // Con N aperturas en la ventana y máximo M, la próxima se habilita cuando sale de la
                // ventana la apertura número (N - M + 1) más antigua.
                val next = if (rule.maxOpens >= 1 && inWindow.size >= rule.maxOpens) {
                    inWindow[inWindow.size - rule.maxOpens] + windowMs
                } else null
                OpenStatus(inWindow.size, rule.maxOpens, isRolling = true, blockEnd = null, nextAvailableAt = next)
            }
            else -> {
                val block = PeriodCalculator.currentRange(spec, now, zone)
                val count = opens.count { it in block }
                OpenStatus(
                    used = count,
                    max = rule.maxOpens,
                    isRolling = false,
                    blockEnd = block.end,
                    nextAvailableAt = if (count >= rule.maxOpens) block.end else null,
                )
            }
        }
    }
}
