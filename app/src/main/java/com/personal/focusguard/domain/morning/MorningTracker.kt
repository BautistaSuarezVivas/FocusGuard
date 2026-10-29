package com.personal.focusguard.domain.morning

import com.personal.focusguard.domain.limits.Interval
import com.personal.focusguard.domain.limits.IntervalMath
import com.personal.focusguard.domain.period.TimeRange
import com.personal.focusguard.domain.time.Clock
import com.personal.focusguard.domain.tracking.BlockReason
import com.personal.focusguard.domain.tracking.SessionRecord
import com.personal.focusguard.domain.tracking.UsageLedger
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class MorningStatus(val window: TimeRange, val usedMs: Long, val limitMs: Long?) {
    val remainingMs: Long? get() = limitMs?.let { (it - usedMs).coerceAtLeast(0) }
    val exhausted: Boolean get() = limitMs != null && usedMs >= limitMs
}

sealed interface MorningDecision {
    /** El modo mañana no está activo ahora. */
    data object Inactive : MorningDecision

    /** Permitida. [remainingMs] es el tiempo de pantalla restante si esta app consume del límite. */
    data class Allowed(val remainingMs: Long?) : MorningDecision

    data class Blocked(val reason: BlockReason) : MorningDecision
}

/**
 * Aplica el modo mañana. Lleva la cuenta del tiempo con la pantalla encendida en apps que no son
 * privilegiadas ni esenciales, persistida en el historial (clave [SCREEN_KEY]) para sobrevivir a
 * reinicios, y registra los desbloqueos (clave [UNLOCK_KEY]) para saber cuándo empezó el día.
 * Las duraciones se miden con el reloj monotónico, igual que en [ForegroundTracker].
 */
class MorningTracker(
    private val ledger: UsageLedger,
    private val rule: () -> MorningRule,
    private val clock: Clock,
    /** Apps siempre permitidas y que no cuentan: launcher, teléfono, reloj, FocusGuard. */
    private val essentials: () -> Set<String>,
    private val labelOf: (String) -> String = { it },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Counting(val id: String, val startWall: Long, val startElapsed: Long)

    private var currentPkg: String? = null
    private var screenOn = true
    private var counting: Counting? = null

    @Synchronized
    fun onUnlock() {
        screenOn = true
        ledger.recordOpen(UNLOCK_KEY, clock.wallMillis())
    }

    @Synchronized
    fun onScreenOff() {
        stopCounting()
        screenOn = false
    }

    @Synchronized
    fun onForeground(pkg: String): MorningDecision {
        if (pkg != currentPkg) stopCounting()
        currentPkg = pkg
        screenOn = true
        return evaluate(pkg)
    }

    /** Latido: persiste el tiempo y reevalúa la app actual (p. ej. el límite se agotó adentro). */
    @Synchronized
    fun check(): MorningDecision? {
        val pkg = currentPkg ?: return null
        persist()
        return evaluate(pkg)
    }

    @Synchronized
    fun status(): MorningStatus? {
        persist()
        return MorningStatusCalculator.status(rule(), ledger, clock.wallMillis(), clock.zone())
    }

    private fun evaluate(pkg: String): MorningDecision {
        val r = rule()
        val now = clock.wallMillis()
        val window = MorningSchedule.activeWindow(r, now, clock.zone(), ledger.opensOf(UNLOCK_KEY))
        if (window == null) {
            stopCounting()
            return MorningDecision.Inactive
        }
        if (pkg in essentials() || pkg in r.privileged) {
            stopCounting()
            return MorningDecision.Allowed(null)
        }
        if (pkg in r.restricted) {
            stopCounting()
            return MorningDecision.Blocked(BlockReason.MorningRestricted(pkg, labelOf(pkg), window.end))
        }
        if (r.screenLimitEnabled && usedMs(window, now) >= r.screenLimitMs) {
            stopCounting()
            return MorningDecision.Blocked(BlockReason.MorningScreenLimit(pkg, labelOf(pkg), r.screenLimitMinutes, window.end))
        }
        if (screenOn && counting == null) {
            counting = Counting(idFactory(), now, clock.elapsedMillis())
            persist()
        }
        val remaining = if (r.screenLimitEnabled) (r.screenLimitMs - usedMs(window, now)).coerceAtLeast(0) else null
        // El tiempo restante nunca pasa del fin de la ventana.
        return MorningDecision.Allowed(remaining?.let { minOf(it, window.end - now) })
    }

    private fun usedMs(window: TimeRange, now: Long): Long =
        MorningStatusCalculator.usedMs(ledger, window, now, counting?.let { Interval(it.startWall, wallEnd(it)) })

    private fun wallEnd(c: Counting): Long = c.startWall + (clock.elapsedMillis() - c.startElapsed).coerceAtLeast(0)

    private fun persist() {
        val c = counting ?: return
        ledger.upsertSession(SessionRecord(c.id, SCREEN_KEY, c.startWall, wallEnd(c)))
    }

    private fun stopCounting() {
        persist()
        counting = null
    }

    companion object {
        const val SCREEN_KEY = "@morning_screen"
        const val UNLOCK_KEY = "@unlock"
    }
}

/** Estado del modo mañana a partir del historial (lo usan el servicio y la interfaz). */
object MorningStatusCalculator {

    fun usedMs(ledger: UsageLedger, window: TimeRange, now: Long, live: Interval? = null): Long {
        val intervals = ledger.sessionIntervals(MorningTracker.SCREEN_KEY) + listOfNotNull(live)
        return IntervalMath.unionDuration(intervals, window.start, minOf(now, window.end))
    }

    /** Estado si el modo mañana está activo ahora, o null. */
    fun status(rule: MorningRule, ledger: UsageLedger, now: Long, zone: ZoneId): MorningStatus? {
        val window = MorningSchedule.activeWindow(rule, now, zone, ledger.opensOf(MorningTracker.UNLOCK_KEY)) ?: return null
        return MorningStatus(window, usedMs(ledger, window, now), if (rule.screenLimitEnabled) rule.screenLimitMs else null)
    }

    /** Ventana de hoy aunque ya haya terminado (null si hoy no hubo o todavía no empezó). */
    fun todayWindow(rule: MorningRule, ledger: UsageLedger, now: Long, zone: ZoneId): TimeRange? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return MorningSchedule.windowFor(rule, today, zone, ledger.opensOf(MorningTracker.UNLOCK_KEY))
    }
}
