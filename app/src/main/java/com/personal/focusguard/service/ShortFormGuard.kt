package com.personal.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.personal.focusguard.AppGraph
import com.personal.focusguard.DiagnosticSnapshot
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.detection.DetectionResult
import com.personal.focusguard.detection.DetectionRules
import com.personal.focusguard.detection.NodeInfo
import com.personal.focusguard.detection.RuleSet
import com.personal.focusguard.detection.ShortFormDetector
import com.personal.focusguard.detection.ShortFormTarget
import com.personal.focusguard.detection.ViewerAllowance
import com.personal.focusguard.domain.tracking.BlockReason
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Detecta los visores de videos cortos (Reels, Shorts, TikTok) y saca al usuario de ellos.
 *
 * Secuencia ante una detección (una sola vez por intento, verificando cada paso):
 *   1. Acción global "Atrás".                      → verificar a los 900 ms
 *   2. Si sigue en el visor: pulsar la pestaña segura con ACTION_CLICK. → verificar
 *   3. Si sigue: pantalla de bloqueo (o "Inicio" del sistema si está desactivada).
 * Antes de mostrar la pantalla de bloqueo siempre se intenta salir del visor (Atrás y, si hace
 * falta, la pestaña segura) para que la app no quede en el visor debajo de la pantalla.
 * Protecciones anti-bucle: un análisis cada [MIN_SCAN_INTERVAL_MS] como máximo, enfriamiento
 * tras cada intento y escalado directo al bloqueo si hay demasiadas detecciones seguidas.
 */
class ShortFormGuard(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val graph: AppGraph,
    private val blocker: Blocker,
) {
    private enum class Phase { IDLE, BACK_SENT, SAFE_NAV_SENT, ESCALATING }

    /** Resultado de analizar la ventana activa. [pkg] es el paquete real (TikTok tiene varios). */
    private data class Evaluation(val pkg: String, val rules: RuleSet, val result: DetectionResult)

    private val collector = SnapshotCollector(service)
    private var scanScheduled = false
    private var lastScanAt = 0L
    private var phase = Phase.IDLE
    private var active: Evaluation? = null
    private var cooldownUntil = 0L
    private val recentAttempts = ArrayDeque<Long>()
    private var lastToastAt = 0L
    private val allowances = ShortFormTarget.entries.associateWith { ViewerAllowance() }

    private val scanRunnable = Runnable { scan() }
    private val verifyRunnable = Runnable { verify() }

    private fun settings(): AppSettings? = graph.settings.value

    /** Cambió la app en primer plano: termina las visitas a visores de otras apps. */
    fun onForegroundChanged(pkg: String) {
        allowances.forEach { (target, allowance) -> if (pkg !in target.packages) allowance.onViewerGone() }
    }

    /**
     * Desplazamiento en una app de videos cortos: si viene del pager del visor (deslizar al
     * siguiente video) se cuenta para el margen de videos permitidos.
     */
    fun onScroll(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val rules = DetectionRules.forPackage(pkg) ?: return
        val s = settings() ?: return
        if (s.isBlocking(rules.target) && s.platform(rules.target).allowed > 0) {
            val source = runCatching { event.source }.getOrNull()
            if (source != null) {
                val pagerIds = rules.pagerIds + s.extraIdsFor(rules.target)
                if (collector.isViewerPager(source, pagerIds, event.className, rules.pagerMinHeight)) {
                    allowances.getValue(rules.target).onPagerScrolled(event.fromIndex, SystemClock.uptimeMillis())
                }
                source.recycleCompat()
            }
        }
        onEvent(pkg)
    }

    /** Llamado con cada evento de una app de videos cortos; agrupa ráfagas en un análisis. */
    fun onEvent(pkg: String) {
        val rules = DetectionRules.forPackage(pkg) ?: return
        val s = settings() ?: return
        if (!s.isBlocking(rules.target)) return
        if (scanScheduled || phase != Phase.IDLE) return
        scanScheduled = true
        val sinceLast = SystemClock.uptimeMillis() - lastScanAt
        val delay = (MIN_SCAN_INTERVAL_MS - sinceLast).coerceAtLeast(EVENT_SETTLE_MS)
        handler.postDelayed(scanRunnable, delay)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(TOKEN)
        handler.removeCallbacks(scanRunnable)
        handler.removeCallbacks(verifyRunnable)
        scanScheduled = false
        phase = Phase.IDLE
    }

    private fun evaluateNow(): Evaluation? {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val rules = DetectionRules.forPackage(pkg) ?: return null
        val s = settings() ?: return null
        if (!s.isBlocking(rules.target)) return null
        val extra = s.extraIdsFor(rules.target)
        val nodes = collector.collect(root, pkg, rules, extra)
        val result = ShortFormDetector.evaluate(rules, nodes, extra)
        if (s.diagnostics) publishDiagnostics(pkg, result, collector.observedIds(root) + describeLists(nodes))
        return Evaluation(pkg, rules, result)
    }

    private fun scan() {
        scanScheduled = false
        lastScanAt = SystemClock.uptimeMillis()
        val eval = evaluateNow() ?: return
        val target = eval.rules.target
        val allowance = allowances.getValue(target)
        if (!eval.result.detected) {
            allowance.onViewerGone()
            return
        }
        Log.d("FocusGuard", "${target.sectionName} detectado: puntaje=${eval.result.score} señales=${eval.result.matchedSignals}")
        val s = settings() ?: return
        val allowed = s.platform(target).allowed
        if (!allowance.onViewerVisible(allowed, SystemClock.uptimeMillis())) {
            note(eval, "permitido: ${allowance.watched} de $allowed")
            return
        }
        onDetected(eval)
    }

    private fun onDetected(eval: Evaluation) {
        val s = settings() ?: return
        val now = SystemClock.uptimeMillis()
        if (now < cooldownUntil) return
        while (recentAttempts.isNotEmpty() && now - recentAttempts.first() > LOOP_WINDOW_MS) recentAttempts.removeFirst()
        recentAttempts.addLast(now)
        active = eval
        // La visita terminó: la próxima vez que entre al visor vuelve a tener su cantidad permitida.
        allowances.getValue(eval.rules.target).onViewerGone()

        if (recentAttempts.size > MAX_ATTEMPTS_IN_WINDOW) {
            escalate(eval, s, "demasiadas detecciones seguidas")
            return
        }
        when {
            s.autoAction -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                note(eval, "Atrás")
                phase = Phase.BACK_SENT
                handler.postDelayed(verifyRunnable, VERIFY_DELAY_MS)
            }
            s.blockScreenFallback -> escalate(eval, s, "acción automática desactivada")
            else -> {
                if (now - lastToastAt > 10_000) {
                    lastToastAt = now
                    Toast.makeText(service, "${eval.rules.target.sectionName} detectado", Toast.LENGTH_SHORT).show()
                }
                cooldownUntil = now + 5_000
            }
        }
    }

    /** true si sigue en el visor de la misma plataforma. */
    private fun stillInViewer(eval: Evaluation): Boolean {
        val current = evaluateNow() ?: return false
        return current.rules.target == eval.rules.target && current.result.detected
    }

    private fun clickSafe(eval: Evaluation): Boolean {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        return collector.clickSafeDestination(root, eval.pkg, eval.rules)
    }

    private fun verify() {
        val eval = active ?: run { phase = Phase.IDLE; return }
        val s = settings() ?: run { phase = Phase.IDLE; return }
        if (!stillInViewer(eval)) {
            note(eval, "salida verificada")
            phase = Phase.IDLE
            cooldownUntil = SystemClock.uptimeMillis() + SUCCESS_COOLDOWN_MS
            return
        }
        when (phase) {
            Phase.BACK_SENT -> {
                if (clickSafe(eval)) {
                    note(eval, "Atrás no alcanzó; pestaña segura")
                    phase = Phase.SAFE_NAV_SENT
                    handler.postDelayed(verifyRunnable, VERIFY_DELAY_MS)
                } else {
                    escalate(eval, s, "Atrás no alcanzó y no hay destino seguro")
                }
            }
            Phase.SAFE_NAV_SENT -> escalate(eval, s, "la navegación segura no alcanzó")
            Phase.IDLE, Phase.ESCALATING -> Unit
        }
    }

    /**
     * Pantalla de bloqueo (o Inicio del sistema). Primero sale del visor para que, al cerrar la
     * pantalla de bloqueo, la app no siga en Reels/Shorts: Atrás → (si sigue) pestaña segura →
     * pantalla de bloqueo.
     */
    private fun escalate(eval: Evaluation, s: AppSettings, why: String) {
        phase = Phase.ESCALATING
        cooldownUntil = SystemClock.uptimeMillis() + ESCALATE_COOLDOWN_MS
        val target = eval.rules.target
        if (!s.blockScreenFallback) {
            note(eval, "Inicio del sistema ($why)")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            phase = Phase.IDLE
            return
        }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        note(eval, "Atrás antes de la pantalla de bloqueo ($why)")
        postStep(EXIT_STEP_MS) {
            if (stillInViewer(eval) && clickSafe(eval)) note(eval, "pestaña segura antes de la pantalla de bloqueo")
            postStep(EXIT_STEP_MS / 2) {
                note(eval, "pantalla de bloqueo")
                blocker.block(BlockReason.ShortForm(eval.pkg, target.appLabel, target.sectionName))
                phase = Phase.IDLE
            }
        }
    }

    private fun postStep(delayMs: Long, step: () -> Unit) {
        handler.postAtTime({ step() }, TOKEN, SystemClock.uptimeMillis() + delayMs)
    }

    /** Resumen de listas desplazables para el diagnóstico (clase, tamaño, orientación). */
    private fun describeLists(nodes: List<NodeInfo>): List<String> = nodes.filter { it.className != null && it.scrollable }
        .map { n ->
            val cls = n.className!!.substringAfterLast('.')
            val orientation = when (n.vertical) { true -> "vertical"; false -> "horizontal"; null -> "?" }
            String.format(Locale.ROOT, "[lista %s alto=%.2f ancho=%.2f %s]", cls, n.heightFraction, n.widthFraction, orientation)
        }.distinct()

    private fun note(eval: Evaluation, action: String) {
        Log.d("FocusGuard", "${eval.rules.target.sectionName}: $action")
        if (settings()?.diagnostics != true) return
        graph.diagnostics.update { map ->
            val prev = map[eval.pkg] ?: return@update map
            map + (eval.pkg to prev.copy(action = action, timestamp = System.currentTimeMillis()))
        }
    }

    private fun publishDiagnostics(pkg: String, result: DetectionResult, ids: List<String>) {
        graph.diagnostics.update { map ->
            map + (pkg to DiagnosticSnapshot(System.currentTimeMillis(), pkg, result, ids, null))
        }
    }

    companion object {
        private val TOKEN = Any()
        private const val MIN_SCAN_INTERVAL_MS = 600L
        private const val EVENT_SETTLE_MS = 120L
        private const val VERIFY_DELAY_MS = 900L
        private const val EXIT_STEP_MS = 700L
        private const val SUCCESS_COOLDOWN_MS = 1_200L
        private const val ESCALATE_COOLDOWN_MS = 3_000L
        private const val LOOP_WINDOW_MS = 30_000L
        private const val MAX_ATTEMPTS_IN_WINDOW = 4
    }
}
