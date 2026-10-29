package com.personal.focusguard.domain.tracking

import com.personal.focusguard.domain.limits.Interval
import com.personal.focusguard.domain.limits.LimitEvaluator
import com.personal.focusguard.domain.limits.OpenStatus
import com.personal.focusguard.domain.limits.UsageStatus
import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.time.Clock
import java.util.UUID

/** Motivo por el que se impide el uso de una app. */
sealed interface BlockReason {
    val packageName: String
    val appLabel: String

    data class UsageLimit(
        override val packageName: String,
        override val appLabel: String,
        val limitMinutes: Int,
        val resetsAt: Long,
    ) : BlockReason

    data class OpenLimit(
        override val packageName: String,
        override val appLabel: String,
        val maxOpens: Int,
        val nextAvailableAt: Long?,
    ) : BlockReason

    data class ShortForm(
        override val packageName: String,
        override val appLabel: String,
        val sectionName: String,
    ) : BlockReason

    /** Modo mañana: app restringida hasta [endsAt]. */
    data class MorningRestricted(
        override val packageName: String,
        override val appLabel: String,
        val endsAt: Long,
    ) : BlockReason

    /** Modo mañana: se agotó el tiempo de pantalla; hasta [endsAt] solo apps privilegiadas. */
    data class MorningScreenLimit(
        override val packageName: String,
        override val appLabel: String,
        val limitMinutes: Int,
        val endsAt: Long,
    ) : BlockReason
}

sealed interface EntryDecision {
    /** Sigue en la misma app: no es una apertura nueva ni hay que reevaluar. */
    data object SameApp : EntryDecision
    data class Unmonitored(val packageName: String) : EntryDecision
    data class Allowed(val packageName: String, val countedAsOpen: Boolean, val usageRemainingMs: Long?) : EntryDecision
    data class Blocked(val reason: BlockReason) : EntryDecision
}

data class AppStatus(val usage: UsageStatus?, val opens: OpenStatus?)

/** Cálculo del estado de una app a partir del historial (lo comparten servicio e interfaz). */
object StatusCalculator {
    fun compute(
        rule: AppRules,
        ledger: UsageLedger,
        now: Long,
        clock: Clock,
        defaultResetMinute: Int,
        live: Interval? = null,
    ): AppStatus {
        val usage = if (rule.usage.enabled) {
            val intervals = ledger.sessionIntervals(rule.packageName).let { if (live != null) it + live else it }
            LimitEvaluator.usage(rule.usage, defaultResetMinute, intervals, now, clock.zone())
        } else null
        val opens = if (rule.opens.enabled) {
            LimitEvaluator.opens(rule.opens, ledger.opensOf(rule.packageName), now, clock.zone())
        } else null
        return AppStatus(usage, opens)
    }
}

/**
 * Máquina de estados de la app en primer plano. Recibe los cambios de app ya filtrados
 * (sin teclado, barra de sistema, etc.) y decide:
 *  - si la entrada es una APERTURA nueva (entrada desde otra app/launcher, no un cambio de
 *    pantalla interno ni un regreso inmediato),
 *  - si hay que bloquear por límite de tiempo o de aperturas,
 *  - y registra los intervalos de primer plano sin solaparlos.
 *
 * Las duraciones se miden con el reloj monotónico, por lo que un cambio de hora nunca genera
 * intervalos negativos ni duplica tiempo.
 */
class ForegroundTracker(
    private val ledger: UsageLedger,
    private val rules: () -> Map<String, AppRules>,
    private val defaultResetMinute: () -> Int,
    private val clock: Clock,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Active(val id: String, val pkg: String, val startWall: Long, val startElapsed: Long)

    var currentPackage: String? = null
        private set
    private var active: Active? = null
    private var blockedPackage: String? = null
    private var lastBlockReason: BlockReason? = null
    private val lastLeftElapsed = HashMap<String, Long>()
    private var resumePackage: String? = null
    private var firstObservation = true

    @Synchronized
    fun onForeground(pkg: String): EntryDecision {
        if (pkg == currentPackage) return EntryDecision.SameApp
        leaveCurrent()
        currentPackage = pkg

        // La primera app observada al (re)iniciar el servicio y la app que estaba abierta al
        // apagar la pantalla se consideran continuación de la visita, no aperturas nuevas.
        val resumed = firstObservation || resumePackage == pkg
        firstObservation = false
        resumePackage = null

        val rule = rules()[pkg]
        if (rule == null || !rule.isActive) return EntryDecision.Unmonitored(pkg)

        val now = clock.wallMillis()
        val nowElapsed = clock.elapsedMillis()
        val left = lastLeftElapsed[pkg]
        val isNewOpen = !resumed && (left == null || nowElapsed - left > REENTRY_GRACE_MS)

        val status = StatusCalculator.compute(rule, ledger, now, clock, defaultResetMinute())
        status.usage?.let { usage ->
            if (usage.exhausted) {
                return block(BlockReason.UsageLimit(pkg, rule.label, rule.usage.limitMinutes, usage.resetsAt))
            }
        }
        status.opens?.let { opens ->
            // Los intentos bloqueados no consumen aperturas: solo se registra si se permite.
            if (isNewOpen && opens.exhausted) {
                return block(BlockReason.OpenLimit(pkg, rule.label, rule.opens.maxOpens, opens.nextAvailableAt))
            }
        }
        if (isNewOpen && rule.opens.enabled) ledger.recordOpen(pkg, now)
        if (rule.usage.enabled) {
            val a = Active(idFactory(), pkg, now, nowElapsed)
            active = a
            ledger.upsertSession(SessionRecord(a.id, pkg, now, now))
        }
        return EntryDecision.Allowed(pkg, isNewOpen, status.usage?.remainingMs)
    }

    /**
     * Bloqueo decidido fuera del tracker (modo mañana): la app pasa a primer plano bloqueada, sin
     * contar apertura ni tiempo. Sirve tanto al entrar como si se bloquea mientras se usa.
     */
    @Synchronized
    fun blockExternally(reason: BlockReason): EntryDecision.Blocked {
        if (reason.packageName != currentPackage) {
            leaveCurrent()
            currentPackage = reason.packageName
            firstObservation = false
            resumePackage = null
        }
        return block(reason)
    }

    /** Pantalla apagada: se deja de contar tiempo; al desbloquear en la misma app no es apertura nueva. */
    @Synchronized
    fun onScreenOff() {
        resumePackage = currentPackage?.takeIf { it != blockedPackage }
        leaveCurrent()
    }

    /**
     * Cambio manual/automático de hora: la sesión activa se cierra con la hora anterior
     * (medida con el reloj monotónico) y continúa como una sesión nueva con la hora nueva.
     */
    @Synchronized
    fun onTimeChanged() {
        val a = active ?: return
        closeActive()
        val now = clock.wallMillis()
        val b = Active(idFactory(), a.pkg, now, clock.elapsedMillis())
        active = b
        ledger.upsertSession(SessionRecord(b.id, b.pkg, now, now))
    }

    /**
     * Latido periódico: persiste el fin de la sesión activa (para recuperarse si el proceso
     * muere) y reevalúa el límite de tiempo de la app actual.
     */
    @Synchronized
    fun checkCurrent(): EntryDecision? {
        val pkg = currentPackage ?: return null
        // Sigue en una app bloqueada (no funcionó salir): se vuelve a informar el bloqueo.
        if (pkg == blockedPackage) return lastBlockReason?.let { EntryDecision.Blocked(it) }
        val rule = rules()[pkg]
        if (rule == null || !rule.isActive) return null
        persistActive()
        val status = StatusCalculator.compute(rule, ledger, clock.wallMillis(), clock, defaultResetMinute(), liveInterval())
        val usage = status.usage
        if (usage != null && usage.exhausted) {
            return block(BlockReason.UsageLimit(pkg, rule.label, rule.usage.limitMinutes, usage.resetsAt))
        }
        return EntryDecision.Allowed(pkg, false, usage?.remainingMs)
    }

    @Synchronized
    fun statusOf(pkg: String): AppStatus? {
        val rule = rules()[pkg] ?: return null
        return StatusCalculator.compute(rule, ledger, clock.wallMillis(), clock, defaultResetMinute(), liveInterval()?.takeIf { active?.pkg == pkg })
    }

    /** Cierra la sesión activa (p. ej. al desconectarse el servicio). */
    @Synchronized
    fun flush() {
        persistActive()
    }

    private fun block(reason: BlockReason): EntryDecision.Blocked {
        closeActive()
        blockedPackage = reason.packageName
        lastBlockReason = reason
        return EntryDecision.Blocked(reason)
    }

    private fun leaveCurrent() {
        val pkg = currentPackage ?: return
        if (pkg != blockedPackage) lastLeftElapsed[pkg] = clock.elapsedMillis()
        closeActive()
        if (pkg == blockedPackage) {
            blockedPackage = null
            lastBlockReason = null
        }
        currentPackage = null
    }

    private fun liveInterval(): Interval? = active?.let { Interval(it.startWall, wallEnd(it)) }

    private fun wallEnd(a: Active): Long = a.startWall + (clock.elapsedMillis() - a.startElapsed).coerceAtLeast(0)

    private fun persistActive() {
        val a = active ?: return
        ledger.upsertSession(SessionRecord(a.id, a.pkg, a.startWall, wallEnd(a)))
    }

    private fun closeActive() {
        persistActive()
        active = null
    }

    companion object {
        /** Volver a la misma app dentro de este margen no cuenta como apertura nueva. */
        const val REENTRY_GRACE_MS = 5_000L
    }
}
