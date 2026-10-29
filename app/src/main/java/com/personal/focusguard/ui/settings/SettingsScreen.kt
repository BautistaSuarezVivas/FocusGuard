package com.personal.focusguard.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.data.ThemeMode
import com.personal.focusguard.detection.ShortFormTarget
import com.personal.focusguard.ui.MainViewModel
import com.personal.focusguard.ui.Permissions
import com.personal.focusguard.ui.components.AppIcon
import com.personal.focusguard.ui.components.ChoiceChips
import com.personal.focusguard.ui.components.NumberField
import com.personal.focusguard.ui.components.SectionTitle
import com.personal.focusguard.ui.components.SwitchRow
import com.personal.focusguard.ui.components.TimeOfDayField
import com.personal.focusguard.ui.stillAvailable
import com.personal.focusguard.ui.summaryFor
import kotlinx.coroutines.delay

/** Secciones de Ajustes; cada una es una pantalla propia. */
enum class SettingsSection(val route: String, val title: String, val subtitle: String, val icon: ImageVector) {
    GENERAL("general", "Configuración general", "Tema, hora de reinicio y avisos", Icons.Default.Settings),
    SHORT_VIDEOS("videos", "Videos cortos", "Reels, Shorts y TikTok: qué bloquear y cuántos ver", Icons.Default.PlayArrow),
    MORNING("manana", "Modo mañana", "Primeras horas del día: apps restringidas, límite de pantalla y privilegiadas", Icons.Default.DateRange),
    PERMISSIONS("permisos", "Permisos y estado", "Servicio de accesibilidad, batería y reactivación", Icons.Default.Lock),
    ADVANCED("avanzado", "Configuración avanzada", "Diagnóstico de detección e ids extra", Icons.Default.Build),
    DATA("datos", "Datos y privacidad", "Borrar historial, restablecer y qué se guarda", Icons.Default.Info),
    ;

    companion object {
        fun fromRoute(route: String?) = entries.firstOrNull { it.route == route } ?: GENERAL
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Menú principal de Ajustes. */
@Composable
fun SettingsMenuScreen(vm: MainViewModel, onBack: () -> Unit, onOpenSection: (SettingsSection) -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    SettingsScaffold("Ajustes", onBack) {
        Spacer(Modifier.height(8.dp))
        val p = dashboard?.permissions
        SettingsSection.entries.forEachIndexed { i, section ->
            val warning = section == SettingsSection.PERMISSIONS && p != null &&
                (!p.accessibilityEnabled || !p.serviceRunning || !p.ignoringBatteryOptimizations)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenSection(section) }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(section.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (warning) {
                        Text("Hay recomendaciones pendientes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            if (i < SettingsSection.entries.lastIndex) HorizontalDivider()
        }
    }
}

/** Pantalla de una sección de Ajustes. */
@Composable
fun SettingsSectionScreen(
    vm: MainViewModel,
    section: SettingsSection,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    expandTarget: ShortFormTarget? = null,
    onPickMorningApps: (MorningAppList) -> Unit = {},
    onOpenGuide: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    SettingsScaffold(section.title, onBack) {
        val s = settings ?: return@SettingsScaffold
        when (section) {
            SettingsSection.GENERAL -> GeneralSection(s, vm)
            SettingsSection.SHORT_VIDEOS -> ShortVideosSection(s, vm, expandTarget)
            SettingsSection.MORNING -> MorningSection(s, vm, onPickMorningApps)
            SettingsSection.PERMISSIONS -> PermissionsSection(s, vm, onOpenGuide)
            SettingsSection.ADVANCED -> AdvancedSection(s, vm, onOpenDiagnostics)
            SettingsSection.DATA -> DataSection(vm, onOpenGuide)
        }
    }
}

@Composable
private fun GeneralSection(s: AppSettings, vm: MainViewModel) {
    SectionTitle("Apariencia")
    ChoiceChips(
        listOf(ThemeMode.SYSTEM to "Según el sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
        s.themeMode,
    ) { v -> vm.updateSettings { it.copy(themeMode = v) } }

    SectionTitle("Límites de tiempo")
    TimeOfDayField("Hora de reinicio global", s.defaultResetMinute) { v -> vm.updateSettings { it.copy(defaultResetMinute = v) } }
    Text("Se aplica a las reglas que no tienen una hora propia.", style = MaterialTheme.typography.bodySmall)
    SwitchRow(
        "Avisos de tiempo restante", s.timeWarnings, { v -> vm.updateSettings { it.copy(timeWarnings = v) } },
        subtitle = "Mensaje breve cuando quedan 5 y 1 minutos.",
    )
}

@Composable
private fun ShortVideosSection(s: AppSettings, vm: MainViewModel, expandTarget: ShortFormTarget?) {
    SectionTitle("Comportamiento")
    SwitchRow(
        "Detección automática", s.autoDetect, { v -> vm.updateSettings { it.copy(autoDetect = v) } },
        subtitle = "Analiza la estructura de pantalla de estas apps. Si se apaga, no se detecta nada.",
    )
    SwitchRow(
        "Acción automática (Atrás)", s.autoAction, { v -> vm.updateSettings { it.copy(autoAction = v) } },
        subtitle = "Al detectar el visor, ejecuta Atrás una vez, verifica y, si no alcanzó, va a una pestaña segura.",
    )
    SwitchRow(
        "Pantalla de bloqueo alternativa", s.blockScreenFallback, { v -> vm.updateSettings { it.copy(blockScreenFallback = v) } },
        subtitle = "Si la acción automática no logró salir (o está apagada), sale del visor y muestra la pantalla de bloqueo.",
    )

    SectionTitle("Apps")
    Text(
        "Videos permitidos por vez: 0 = te saca apenas entrás al visor. 1 = podés ver el video que te mandaron; al " +
            "deslizar al siguiente, te saca. Cada vez que entrás al visor la cuenta empieza de cero.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    ShortFormTarget.entries.forEach { target ->
        PlatformCard(target, s, vm, startExpanded = target == expandTarget)
    }
}

/**
 * Tarjeta desplegable de una plataforma. El encabezado muestra el estado y el interruptor; al
 * apagar el bloqueo la tarjeta se repliega y al encenderlo se despliega.
 */
@Composable
private fun PlatformCard(target: ShortFormTarget, s: AppSettings, vm: MainViewModel, startExpanded: Boolean) {
    val p = s.platform(target)
    var expanded by rememberSaveable(target.name) { mutableStateOf(startExpanded && p.blocked) }
    val requester = remember { BringIntoViewRequester() }
    // Llegando desde el panel (mantener presionado): mostrar esta tarjeta en pantalla.
    LaunchedEffect(startExpanded) {
        if (startExpanded) {
            delay(250)
            requester.bringIntoView()
        }
    }
    val setBlocked: (Boolean) -> Unit = { v ->
        vm.updateSettings { it.withPlatform(target) { ps -> ps.copy(blocked = v) } }
        expanded = v
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).bringIntoViewRequester(requester)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = p.blocked) { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(target.mainPackage, 32.dp, fallbackLabel = target.appLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(target.displayName, style = MaterialTheme.typography.titleMedium)
                Text(s.summaryFor(target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = p.blocked, onCheckedChange = setBlocked)
            Icon(
                if (expanded && p.blocked) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Replegar" else "Desplegar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (p.blocked) 1f else 0.38f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        AnimatedVisibility(visible = expanded && p.blocked) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    "${target.unitPlural.replaceFirstChar { it.uppercase() }} permitidos por vez", p.allowed,
                    { v -> vm.updateSettings { it.withPlatform(target) { ps -> ps.copy(allowed = v) } } }, 0..50, target.unitPlural,
                )
                Text(target.stillAvailable + ".", style = MaterialTheme.typography.bodySmall)
                if (target == ShortFormTarget.TIKTOK) {
                    ExperimentalNote(
                        "En TikTok casi todo es el visor: con 0 te lleva a la Bandeja de entrada (mensajes). Detección experimental.",
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun PermissionsSection(s: AppSettings, vm: MainViewModel, onOpenGuide: () -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val p = dashboard?.permissions
    SectionTitle("Estado")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusLine("Servicio de accesibilidad habilitado", p?.accessibilityEnabled)
            StatusLine("Servicio en ejecución", p?.serviceRunning)
            StatusLine("Sin optimización de batería (recomendado)", p?.ignoringBatteryOptimizations)
            StatusLine("Reactivación automática disponible (instalado con instalar.bat)", p?.canSelfEnable)
            Text(
                "FocusGuard no usa: acceso a datos de uso, notificaciones, alarmas exactas, superposición ni Internet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    SectionTitle("Servicio de accesibilidad")
    OutlinedButton(onClick = { Permissions.openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir Accesibilidad")
    }
    OutlinedButton(onClick = { Permissions.openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Info de la app (ajustes restringidos)")
    }
    if (p?.canSelfEnable == true) {
        SwitchRow(
            "Reactivar el servicio automáticamente", s.autoReenable,
            { v -> vm.updateSettings { it.copy(autoReenable = v) } },
            subtitle = "Si Android (o vos) apagan el servicio, FocusGuard lo vuelve a encender al abrirse o al actualizarse.",
        )
    }

    SectionTitle("Batería")
    if (p?.ignoringBatteryOptimizations == false) {
        Text(
            "Recomendado: excluir FocusGuard de la optimización de batería para que el fabricante no detenga el servicio. " +
                "En Xiaomi, Samsung, Huawei u Oppo revisá también \"Inicio automático\" y las restricciones en segundo plano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text("FocusGuard ya está excluida de la optimización de batería.", style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedButton(onClick = { Permissions.openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Ajustes de batería")
    }
    TextButton(onClick = onOpenGuide) { Text("Ver de nuevo el asistente de configuración") }
}

@Composable
private fun AdvancedSection(s: AppSettings, vm: MainViewModel, onOpenDiagnostics: () -> Unit) {
    SectionTitle("Diagnóstico")
    SwitchRow(
        "Modo diagnóstico", s.diagnostics, { v -> vm.updateSettings { it.copy(diagnostics = v) } },
        subtitle = "Muestra qué identificadores de vista y listas detecta FocusGuard. Solo en memoria, sin textos.",
    )
    OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Abrir diagnóstico") }

    SectionTitle("Ids extra de detección")
    Text(
        "Si una actualización de una app cambia la interfaz, podés agregar aquí el id del visor (separados por comas) " +
            "que veas en Diagnóstico. Cada id suma 3 puntos cuando ocupa al menos media pantalla.",
        style = MaterialTheme.typography.bodySmall,
    )
    ShortFormTarget.entries.forEach { target ->
        ExtraIdsField(target.displayName, s.platform(target).extraIds) { v ->
            vm.updateSettings { it.withPlatform(target) { ps -> ps.copy(extraIds = v) } }
        }
    }
}

@Composable
private fun DataSection(vm: MainViewModel, onOpenGuide: () -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }
    SectionTitle("Datos")
    OutlinedButton(onClick = { confirm = "history" }, modifier = Modifier.fillMaxWidth()) { Text("Borrar historial de uso y aperturas") }
    OutlinedButton(onClick = { confirm = "reset" }, modifier = Modifier.fillMaxWidth()) { Text("Restablecer toda la configuración") }

    SectionTitle("Privacidad")
    Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onOpenGuide) { Text("Ver de nuevo el asistente y la explicación de privacidad") }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "history") "Borrar historial" else "Restablecer configuración") },
            text = {
                Text(
                    if (what == "history") "Se borran todas las sesiones y aperturas registradas. Los contadores vuelven a cero."
                    else "Se eliminan todas las reglas, el historial y los ajustes.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "history") vm.clearHistory() else vm.resetConfiguration()
                    confirm = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean?) {
    val color = when (ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text("${if (ok == true) "✓" else "✗"}  $label", color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun ExtraIdsField(label: String, value: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        trailingIcon = { if (text != value) TextButton(onClick = { onSave(text.trim()) }) { Text("Guardar") } },
    )
}

const val PRIVACY_TEXT =
    "• FocusGuard funciona 100 % en el teléfono: no tiene permiso de Internet, ni cuentas, ni publicidad, ni analítica.\n" +
        "• El servicio de accesibilidad se usa solo para: saber qué app está en primer plano, reconocer los visores de videos " +
        "cortos mediante identificadores de vista, tipos de lista y etiquetas de botones, y ejecutar Atrás/Inicio.\n" +
        "• No se guardan textos de pantalla, mensajes, contraseñas ni capturas. Los textos se leen en memoria solo para comparar " +
        "con palabras clave y se descartan al instante.\n" +
        "• Solo se guarda: tus reglas, tus ajustes y, para las apps que controlás, los horarios de inicio/fin en primer plano y " +
        "los momentos de apertura (15 días como máximo).\n" +
        "• Las copias de seguridad en la nube están desactivadas.\n" +
        "• FocusGuard no es una herramienta de accesibilidad para personas con discapacidad."
