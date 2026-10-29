package com.personal.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.focusguard.DiagnosticSnapshot
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.morning.MorningStatus
import com.personal.focusguard.domain.morning.MorningStatusCalculator
import com.personal.focusguard.domain.period.TimeRange
import com.personal.focusguard.domain.tracking.AppStatus
import com.personal.focusguard.domain.tracking.StatusCalculator
import com.personal.focusguard.graph
import com.personal.focusguard.service.ServiceKeeper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppCard(val rules: AppRules, val status: AppStatus)

data class DashboardState(
    val settings: AppSettings,
    val permissions: PermissionState,
    val apps: List<AppCard>,
    val now: Long,
    /** Modo mañana en curso (null si no está activo ahora). */
    val morningStatus: MorningStatus? = null,
    /** Ventana de hoy aunque haya terminado, para mostrar "terminó a las…". */
    val morningToday: TimeRange? = null,
) {
    /** Próximo reinicio entre todos los límites con período fijo. */
    val nextReset: Long?
        get() = apps.flatMap { listOfNotNull(it.status.usage?.resetsAt, it.status.opens?.blockEnd) }.minOrNull()
}

/** ViewModel compartido por el panel, ajustes y diagnóstico. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.graph
    private val permissionTick = MutableStateFlow(0)
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    init {
        // Si el servicio no está corriendo, el historial igualmente se carga para el panel.
        viewModelScope.launch { graph.ledger.ensureLoaded(graph.clock.wallMillis()) }
    }

    val settings: StateFlow<AppSettings?> = graph.settings

    val diagnostics: StateFlow<Map<String, DiagnosticSnapshot>> = graph.diagnostics

    val dashboard: StateFlow<DashboardState?> = combine(
        graph.rules.filterNotNull(),
        graph.settings.filterNotNull(),
        graph.ledger.version,
        ticker,
        combine(graph.serviceConnected, permissionTick) { connected, _ -> connected },
    ) { rules, settings, _, _, connected ->
        val now = graph.clock.wallMillis()
        DashboardState(
            settings = settings,
            permissions = Permissions.state(getApplication(), connected),
            apps = rules.values.sortedBy { it.label.lowercase() }.map {
                AppCard(it, StatusCalculator.compute(it, graph.ledger, now, graph.clock, settings.defaultResetMinute))
            },
            now = now,
            morningStatus = MorningStatusCalculator.status(settings.morning, graph.ledger, now, graph.clock.zone()),
            morningToday = MorningStatusCalculator.todayWindow(settings.morning, graph.ledger, now, graph.clock.zone()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Revisa permisos al volver a la app. Si el servicio no corre y hay permiso de reactivación
     * automática, lo reactiva sin pedirle nada al usuario.
     */
    fun refreshPermissions() {
        permissionTick.update { it + 1 }
        val app = getApplication<Application>()
        if (graph.serviceConnected.value || !ServiceKeeper.canSelfEnable(app)) return
        viewModelScope.launch {
            // Margen para que Android termine de enlazar el servicio si recién arranca el proceso.
            delay(2_500)
            val s = graph.settings.value
            if (!graph.serviceConnected.value && s?.autoReenable != false && ServiceKeeper.ensureRunning(app, false)) {
                delay(2_000)
                permissionTick.update { it + 1 }
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { graph.settingsRepository.update(transform) }
    }

    fun clearHistory() {
        viewModelScope.launch { graph.ledger.clear() }
    }

    fun resetConfiguration() {
        viewModelScope.launch {
            graph.ruleRepository.deleteAll()
            graph.settingsRepository.resetToDefaults()
            graph.ledger.clear()
        }
    }

    fun clearDiagnostics() = graph.diagnostics.update { emptyMap() }
}
