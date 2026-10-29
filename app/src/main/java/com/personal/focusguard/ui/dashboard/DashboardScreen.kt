package com.personal.focusguard.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.detection.ShortFormTarget
import com.personal.focusguard.domain.morning.MorningStart
import com.personal.focusguard.domain.model.OpenWindowType
import com.personal.focusguard.domain.model.UsagePeriodType
import com.personal.focusguard.ui.AppCard
import com.personal.focusguard.ui.DashboardState
import com.personal.focusguard.ui.MainViewModel
import com.personal.focusguard.ui.Permissions
import com.personal.focusguard.ui.blockLabel
import com.personal.focusguard.ui.components.AppIcon
import com.personal.focusguard.ui.components.LimitBadge
import com.personal.focusguard.ui.components.SectionTitle
import com.personal.focusguard.ui.components.SwitchRow
import com.personal.focusguard.ui.stillAvailable
import com.personal.focusguard.ui.units
import com.personal.focusguard.ui.theme.LimitColors
import com.personal.focusguard.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenPlatform: (ShortFormTarget) -> Unit,
    onOpenMorning: () -> Unit,
    onAddApp: () -> Unit,
    onOpenApp: (String, String) -> Unit,
) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusGuard") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Ajustes") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddApp, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Controlar app") })
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProtectionCard(s) }
            item { MorningCard(s, onOpenMorning) }
            item { ShortFormCard(s.settings, vm, onOpenPlatform) }
            item { SectionTitle("Apps controladas") }
            if (s.apps.isEmpty()) {
                item {
                    Text(
                        "Todavía no controlás ninguna app. Tocá \"Controlar app\" para crear un límite de tiempo o de aperturas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.apps, key = { it.rules.packageName }) { card ->
                AppStatusCard(card, s.now) { onOpenApp(card.rules.packageName, card.rules.label) }
            }
        }
    }
}

@Composable
private fun ProtectionCard(s: DashboardState) {
    val context = LocalContext.current
    val p = s.permissions
    val active = p.accessibilityEnabled && p.serviceRunning
    val color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (active) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (active) "Protección activa" else "Protección inactiva",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                !p.accessibilityEnabled -> {
                    Text("El servicio de accesibilidad de FocusGuard está desactivado. Sin él no se bloquean los videos cortos ni se aplican los límites.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { Permissions.openAccessibilitySettings(context) }) { Text("Activar servicio") }
                }
                !p.serviceRunning -> {
                    Text("El servicio figura habilitado pero no está en ejecución (Android pudo detenerlo). Desactivalo y volvé a activarlo en Accesibilidad.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { Permissions.openAccessibilitySettings(context) }) { Text("Abrir Accesibilidad") }
                }
                else -> Text("Servicio de accesibilidad en ejecución.")
            }
            s.nextReset?.let {
                Spacer(Modifier.height(4.dp))
                Text("Próximo reinicio de contadores: ${Format.moment(it, s.now)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Estado del modo mañana. Tocar la tarjeta abre su configuración. */
@Composable
private fun MorningCard(s: DashboardState, onOpen: () -> Unit) {
    val rule = s.settings.morning
    val status = s.morningStatus
    val active = status != null
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = if (active) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (active) "Modo mañana activo" else "Modo mañana", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val today = s.morningToday
            val detail = when {
                !rule.enabled -> "Desactivado. Tocá para configurar las primeras horas del día."
                status != null -> buildString {
                    append("Hasta ${Format.moment(status.window.end, s.now)}")
                    status.remainingMs?.let { r ->
                        append(if (status.exhausted) " · pantalla agotada: solo apps privilegiadas" else " · quedan ${Format.duration(r)} de pantalla")
                    }
                }
                today != null && today.end <= s.now -> "Hoy terminó a las ${Format.moment(today.end, s.now)}."
                rule.start == MorningStart.FIXED_TIME -> "Empieza a las ${Format.minuteOfDay(rule.fixedMinute)} (${rule.durationMinutes} min)."
                else -> "Empieza con tu primer desbloqueo después de las ${Format.minuteOfDay(rule.earliestMinute)} (${rule.durationMinutes} min)."
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            status?.remainingMs?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (status.usedMs.toFloat() / (status.limitMs ?: 1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ShortFormCard(settings: AppSettings, vm: MainViewModel, onOpenPlatform: (ShortFormTarget) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ShortFormTarget.entries.forEach { target ->
                val p = settings.platform(target)
                val subtitle = when {
                    !settings.autoDetect -> "Detección automática desactivada en Ajustes"
                    p.blocked && p.allowed > 0 -> "Podés ver ${target.units(p.allowed)} por vez"
                    else -> target.stillAvailable
                }
                SwitchRow(
                    target.blockLabel,
                    p.blocked,
                    { v -> vm.updateSettings { it.withPlatform(target) { ps -> ps.copy(blocked = v) } } },
                    subtitle = subtitle,
                    onLongClick = { onOpenPlatform(target) },
                    leading = { AppIcon(target.mainPackage, 28.dp, fallbackLabel = target.appLabel) },
                )
            }
            Text(
                "Mantené presionada una app para ver su configuración.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun AppStatusCard(card: AppCard, now: Long, onClick: () -> Unit) {
    val r = card.rules
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(r.packageName)
                Spacer(Modifier.width(12.dp))
                Text(r.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!r.isActive) Text("Reglas desactivadas", style = MaterialTheme.typography.labelMedium)
            }
            card.status.usage?.let { u ->
                Spacer(Modifier.height(12.dp))
                val c = LimitColors.time()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LimitBadge("⏱ TIEMPO", c)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (u.exhausted) "Agotado" else "Quedan ${Format.duration(u.remainingMs)} de ${r.usage.limitMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (u.limitMs == 0L) 1f else (u.usedMs.toFloat() / u.limitMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = c,
                )
                Spacer(Modifier.height(4.dp))
                val period = when (r.usage.periodType) {
                    UsagePeriodType.DAILY -> "diario"
                    UsagePeriodType.WEEKLY -> "semanal"
                    UsagePeriodType.EVERY_N_HOURS -> "cada ${r.usage.everyHours} h"
                }
                Text("Período $period · se reinicia ${Format.moment(u.resetsAt, now)}", style = MaterialTheme.typography.bodySmall)
            }
            card.status.opens?.let { o ->
                Spacer(Modifier.height(12.dp))
                val c = LimitColors.opens()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LimitBadge("↻ APERTURAS", c)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (o.exhausted) "Sin aperturas" else "Quedan ${o.remaining} de ${o.max}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val window = when (r.opens.windowType) {
                    OpenWindowType.ROLLING -> "últimos ${r.opens.windowMinutes} min"
                    OpenWindowType.FIXED_BLOCKS -> "bloques de ${r.opens.windowMinutes} min"
                }
                val detail = when {
                    o.nextAvailableAt != null -> "próxima apertura ${Format.moment(o.nextAvailableAt, now)}"
                    o.blockEnd != null -> "nuevo bloque ${Format.moment(o.blockEnd, now)}"
                    else -> "se liberan a medida que pasan los minutos"
                }
                Text("${o.used} usadas en $window · $detail", style = MaterialTheme.typography.bodySmall, color = if (o.exhausted) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        }
    }
}
