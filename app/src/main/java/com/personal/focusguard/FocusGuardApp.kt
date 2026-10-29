package com.personal.focusguard

import android.app.Application
import android.content.Context
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.data.RoomLedgerStore
import com.personal.focusguard.data.RuleRepository
import com.personal.focusguard.data.SettingsRepository
import com.personal.focusguard.data.db.FocusDatabase
import com.personal.focusguard.detection.DetectionResult
import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.time.Clock
import com.personal.focusguard.domain.time.SystemClockSource
import com.personal.focusguard.domain.tracking.UsageLedger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FocusGuardApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as FocusGuardApp).graph

/** Última evaluación de detección (solo en memoria, para la pantalla de diagnóstico). */
data class DiagnosticSnapshot(
    val timestamp: Long,
    val packageName: String,
    val result: DetectionResult,
    /** Identificadores de vista observados (sin textos). */
    val observedIds: List<String>,
    val action: String?,
)

/** Contenedor de dependencias (inyección manual: la app es pequeña). */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = SystemClockSource
    private val db = FocusDatabase.create(context)
    val settingsRepository = SettingsRepository(context)
    val ruleRepository = RuleRepository(db.appRuleDao())
    val ledger = UsageLedger(RoomLedgerStore(db.historyDao(), ioScope))

    /** null hasta que se lee la configuración de disco por primera vez. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    /** Reglas por paquete; null hasta la primera lectura de Room. */
    val rules: StateFlow<Map<String, AppRules>?> =
        ruleRepository.observeRules().map { list -> list.associateBy { it.packageName } }
            .stateIn(appScope, SharingStarted.Eagerly, null)

    /** true mientras el servicio de accesibilidad está conectado en este proceso. */
    val serviceConnected = MutableStateFlow(false)

    val diagnostics = MutableStateFlow<Map<String, DiagnosticSnapshot>>(emptyMap())
}
