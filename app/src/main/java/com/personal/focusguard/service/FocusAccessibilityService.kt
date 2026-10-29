package com.personal.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.personal.focusguard.AppGraph
import com.personal.focusguard.detection.DetectionRules
import com.personal.focusguard.domain.morning.MorningDecision
import com.personal.focusguard.domain.morning.MorningRule
import com.personal.focusguard.domain.morning.MorningTracker
import com.personal.focusguard.domain.tracking.EntryDecision
import com.personal.focusguard.domain.tracking.ForegroundTracker
import com.personal.focusguard.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Servicio de accesibilidad de FocusGuard. Toda la lógica corre en el hilo principal del
 * servicio (los callbacks de accesibilidad ya llegan ahí), así que no hay carreras entre eventos.
 *
 * Responsabilidades:
 *  - Detectar cambios de app en primer plano → [ForegroundTracker] (aperturas, tiempo, bloqueos).
 *  - Reenviar eventos de Instagram/YouTube a [ShortFormGuard] (Reels/Shorts).
 *  - Latido periódico para persistir tiempo y aplicar el límite aunque no lleguen eventos.
 *  - Reaccionar a pantalla apagada/encendida y a cambios de hora/zona horaria.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var graph: AppGraph
    private lateinit var filter: ForegroundFilter
    private lateinit var blocker: Blocker
    private lateinit var guard: ShortFormGuard
    private var tracker: ForegroundTracker? = null
    private var morning: MorningTracker? = null
    private lateinit var essentials: EssentialApps
    private val labels = HashMap<String, String>()
    private var pendingForeground: String? = null
    private val warned = HashSet<String>()
    private var lastPruneAt = 0L
    private var receiverRegistered = false

    private val heartbeat = object : Runnable {
        override fun run() {
            runCheck()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val limitCheck = Runnable { runCheck() }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    tracker?.onScreenOff()
                    morning?.onScreenOff()
                    handler.removeCallbacks(limitCheck)
                    guard.stop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val km = getSystemService(KeyguardManager::class.java)
                    if (km?.isKeyguardLocked != true) {
                        morning?.onUnlock()
                        resumeFromActiveWindow()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Desbloqueo: puede iniciar el modo mañana ("primer desbloqueo del día").
                    morning?.onUnlock()
                    resumeFromActiveWindow()
                    // Por si la app ya estaba registrada en primer plano antes de este aviso.
                    handler.postDelayed({ runCheck() }, 700)
                }
                Intent.ACTION_TIME_CHANGED -> {
                    tracker?.onTimeChanged()
                    runCheck()
                }
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    // Fuerza a java.time / TimeZone a releer la zona del sistema.
                    TimeZone.setDefault(null)
                    runCheck()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph = applicationContext.graph
        filter = ForegroundFilter(this)
        blocker = Blocker(this, handler)
        essentials = EssentialApps(this)
        guard = ShortFormGuard(this, handler, graph, blocker)
        registerSystemReceiver()
        graph.serviceConnected.value = true
        Log.d(TAG, "servicio conectado")

        scope.launch {
            // Recuperar el estado persistido antes de tomar decisiones.
            graph.rules.filterNotNull().first()
            graph.settings.filterNotNull().first()
            val now = graph.clock.wallMillis()
            graph.ledger.ensureLoaded(now)
            graph.ledger.prune(now)
            tracker = ForegroundTracker(
                ledger = graph.ledger,
                rules = { graph.rules.value.orEmpty() },
                defaultResetMinute = { graph.settings.value?.defaultResetMinute ?: 240 },
                clock = graph.clock,
            )
            morning = MorningTracker(
                ledger = graph.ledger,
                rule = { graph.settings.value?.morning ?: MorningRule() },
                clock = graph.clock,
                essentials = { essentials.get() },
                labelOf = ::appLabel,
            )
            val initial = pendingForeground ?: currentActivePackage()
            initial?.let { handleForeground(it) }
            handler.post(heartbeat)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (filter.isForegroundChange(pkg, event)) handleForeground(pkg)
                guard.onEvent(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (DetectionRules.forPackage(pkg) != null) guard.onEvent(pkg)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (DetectionRules.forPackage(pkg) != null) guard.onScroll(event)
            }
        }
    }

    private fun handleForeground(pkg: String) {
        val t = tracker
        if (t == null) {
            pendingForeground = pkg
            return
        }
        // Modo mañana primero: si bloquea, la entrada no cuenta como apertura ni como tiempo.
        var morningRemaining: Long? = null
        if (pkg != t.currentPackage) {
            when (val md = morning?.onForeground(pkg)) {
                is MorningDecision.Blocked -> {
                    val d = t.blockExternally(md.reason)
                    Log.d(TAG, "primer plano=$pkg decisión=$d")
                    guard.onForegroundChanged(pkg)
                    onBlocked(d)
                    return
                }
                is MorningDecision.Allowed -> morningRemaining = md.remainingMs
                else -> Unit
            }
        }
        val d = t.onForeground(pkg)
        if (d != EntryDecision.SameApp) {
            Log.d(TAG, "primer plano=$pkg decisión=$d")
            guard.onForegroundChanged(pkg)
        }
        when (d) {
            is EntryDecision.Allowed -> scheduleLimitCheck(minOfNotNull(d.usageRemainingMs, morningRemaining))
            is EntryDecision.Blocked -> onBlocked(d)
            is EntryDecision.Unmonitored -> scheduleLimitCheck(morningRemaining)
            EntryDecision.SameApp -> Unit
        }
    }

    private fun onBlocked(d: EntryDecision.Blocked) {
        handler.removeCallbacks(limitCheck)
        blocker.block(d.reason)
        // Si el usuario sigue (o vuelve enseguida) a la app bloqueada, se vuelve a bloquear.
        handler.postDelayed(limitCheck, REBLOCK_CHECK_MS)
    }

    private fun minOfNotNull(a: Long?, b: Long?): Long? = listOfNotNull(a, b).minOrNull()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        graph.rules.value?.get(pkg)?.label ?: runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** Reevalúa la app actual: persiste tiempo, bloquea si se agotó, avisa si queda poco. */
    private fun runCheck() {
        val t = tracker ?: return
        val md = morning?.check()
        if (md is MorningDecision.Blocked && md.reason.packageName == t.currentPackage) {
            Log.d(TAG, "modo mañana: ${md.reason}")
            val d = t.blockExternally(md.reason)
            if (!blocker.recentlyBlocked(d.reason.packageName, REBLOCK_CHECK_MS - 100)) blocker.block(d.reason)
            handler.removeCallbacks(limitCheck)
            handler.postDelayed(limitCheck, REBLOCK_CHECK_MS)
            return
        }
        val morningRemaining = (md as? MorningDecision.Allowed)?.remainingMs
        when (val d = t.checkCurrent()) {
            is EntryDecision.Blocked -> {
                Log.d(TAG, "límite alcanzado dentro de la app: ${d.reason}")
                handler.removeCallbacks(limitCheck)
                if (!blocker.recentlyBlocked(d.reason.packageName, REBLOCK_CHECK_MS - 100)) blocker.block(d.reason)
                handler.postDelayed(limitCheck, REBLOCK_CHECK_MS)
            }
            is EntryDecision.Allowed -> {
                scheduleLimitCheck(minOfNotNull(d.usageRemainingMs, morningRemaining))
                maybeWarn(t, d.packageName, d.usageRemainingMs)
            }
            else -> scheduleLimitCheck(morningRemaining)
        }
        val now = graph.clock.wallMillis()
        if (now - lastPruneAt > 60 * 60_000L) {
            lastPruneAt = now
            graph.ledger.prune(now)
        }
    }

    private fun scheduleLimitCheck(remainingMs: Long?) {
        handler.removeCallbacks(limitCheck)
        if (remainingMs != null && remainingMs < HEARTBEAT_MS) {
            handler.postDelayed(limitCheck, remainingMs + 250)
        }
    }

    private fun maybeWarn(t: ForegroundTracker, pkg: String, remainingMs: Long?) {
        if (remainingMs == null || graph.settings.value?.timeWarnings != true) return
        val usage = t.statusOf(pkg)?.usage ?: return
        val label = graph.rules.value?.get(pkg)?.label ?: pkg
        for (threshold in WARN_THRESHOLDS_MIN) {
            if (remainingMs in 1..threshold * 60_000L) {
                val key = "$pkg@${usage.period.start}@$threshold"
                if (warned.add(key)) {
                    val mins = (remainingMs + 59_999) / 60_000
                    Toast.makeText(this, "FocusGuard: te quedan $mins min de $label", Toast.LENGTH_SHORT).show()
                }
                break
            }
        }
    }

    private fun resumeFromActiveWindow() {
        handler.postDelayed({
            currentActivePackage()?.let { if (filter.isForegroundChange(it, null)) handleForeground(it) }
        }, 400)
    }

    private fun currentActivePackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    private fun registerSystemReceiver() {
        if (receiverRegistered) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Son difusiones del sistema: se reciben aunque el receptor no esté exportado.
        ContextCompat.registerReceiver(this, systemReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun shutdown() {
        Log.d(TAG, "servicio desconectado")
        tracker?.flush()
        handler.removeCallbacksAndMessages(null)
        if (::guard.isInitialized) guard.stop()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(systemReceiver) }.onFailure { Log.w("FocusGuard", "unregister", it) }
            receiverRegistered = false
        }
        if (::graph.isInitialized) graph.serviceConnected.value = false
    }

    companion object {
        private const val TAG = "FocusGuard"
        private const val HEARTBEAT_MS = 15_000L
        private const val REBLOCK_CHECK_MS = 1_600L
        private val WARN_THRESHOLDS_MIN = listOf(1L, 5L)
    }
}
