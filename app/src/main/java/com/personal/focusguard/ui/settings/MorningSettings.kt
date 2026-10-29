package com.personal.focusguard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.domain.morning.MorningRule
import com.personal.focusguard.domain.morning.MorningStart
import com.personal.focusguard.ui.MainViewModel
import com.personal.focusguard.ui.apps.AppPickerViewModel
import com.personal.focusguard.ui.components.AppIcon
import com.personal.focusguard.ui.components.ChoiceChips
import com.personal.focusguard.ui.components.NumberField
import com.personal.focusguard.ui.components.SectionTitle
import com.personal.focusguard.ui.components.SwitchRow
import com.personal.focusguard.ui.components.TimeOfDayField
import java.time.DayOfWeek

/** Qué lista de apps del modo mañana se está editando. */
enum class MorningAppList(val route: String, val title: String) {
    RESTRICTED("restringidas", "Apps restringidas"),
    PRIVILEGED("privilegiadas", "Apps privilegiadas"),
    ;

    companion object {
        fun fromRoute(route: String?) = entries.firstOrNull { it.route == route } ?: RESTRICTED
    }
}

private fun MainViewModel.updateMorning(transform: (MorningRule) -> MorningRule) =
    updateSettings { it.copy(morning = transform(it.morning)) }

private val DAY_LABELS = listOf(
    DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "X", DayOfWeek.THURSDAY to "J",
    DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D",
)

@Composable
fun MorningSection(s: AppSettings, vm: MainViewModel, onPickApps: (MorningAppList) -> Unit) {
    val m = s.morning
    Text(
        "Durante las primeras horas del día el teléfono queda limitado: las apps restringidas no se pueden abrir y, " +
            "si activás el límite de pantalla, al agotarlo solo podés usar tus apps privilegiadas. El inicio, el teléfono " +
            "(llamadas y emergencias), el reloj y FocusGuard siempre funcionan.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    SwitchRow("Activar modo mañana", m.enabled, { v -> vm.updateMorning { it.copy(enabled = v) } })
    if (!m.enabled) return

    SectionTitle("Cuándo empieza")
    ChoiceChips(
        listOf(MorningStart.FIRST_UNLOCK to "Al primer desbloqueo", MorningStart.FIXED_TIME to "A una hora fija"),
        m.start,
    ) { v -> vm.updateMorning { it.copy(start = v) } }
    when (m.start) {
        MorningStart.FIRST_UNLOCK -> {
            TimeOfDayField("No antes de", m.earliestMinute) { v -> vm.updateMorning { it.copy(earliestMinute = v) } }
            TimeOfDayField("Si me desbloqueo después de esta hora, no aplica", m.latestMinute) { v ->
                vm.updateMorning { it.copy(latestMinute = v) }
            }
            Text(
                "Los desbloqueos anteriores a la primera hora (p. ej. a la madrugada) no cuentan.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        MorningStart.FIXED_TIME ->
            TimeOfDayField("Empieza a las", m.fixedMinute) { v -> vm.updateMorning { it.copy(fixedMinute = v) } }
    }
    NumberField("Duración", m.durationMinutes, { v -> vm.updateMorning { it.copy(durationMinutes = v) } }, 15..720, "min")
    ChoiceChips(listOf(60 to "1 h", 90 to "1,5 h", 120 to "2 h", 180 to "3 h"), m.durationMinutes) { v ->
        vm.updateMorning { it.copy(durationMinutes = v) }
    }

    SectionTitle("Días")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAY_LABELS.forEach { (day, label) ->
            FilterChip(
                selected = day in m.days,
                onClick = { vm.updateMorning { r -> r.copy(days = if (day in r.days) r.days - day else r.days + day) } },
                label = { Text(label) },
            )
        }
    }

    SectionTitle("Límite de pantalla")
    SwitchRow(
        "Limitar el tiempo con la pantalla encendida", m.screenLimitEnabled,
        { v -> vm.updateMorning { it.copy(screenLimitEnabled = v) } },
        subtitle = "Cuenta el tiempo en apps que no son privilegiadas. Al agotarse, solo podés usar las privilegiadas.",
    )
    if (m.screenLimitEnabled) {
        NumberField("Tiempo máximo", m.screenLimitMinutes, { v -> vm.updateMorning { it.copy(screenLimitMinutes = v) } }, 1..720, "min")
    }

    SectionTitle("Apps restringidas")
    Text("No se pueden abrir durante el modo mañana.", style = MaterialTheme.typography.bodySmall)
    AppList(m.restricted)
    OutlinedButton(onClick = { onPickApps(MorningAppList.RESTRICTED) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir apps restringidas") }

    SectionTitle("Apps privilegiadas")
    Text("Siempre accesibles; su tiempo no cuenta para el límite.", style = MaterialTheme.typography.bodySmall)
    AppList(m.privileged)
    OutlinedButton(onClick = { onPickApps(MorningAppList.PRIVILEGED) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir apps privilegiadas") }
}

@Composable
private fun AppList(packages: Set<String>) {
    if (packages.isEmpty()) {
        Text("Ninguna", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val vm: AppPickerViewModel = viewModel()
    val apps by vm.visible.collectAsStateWithLifecycle()
    val labels = apps.orEmpty().associate { it.packageName to it.label }
    Column(Modifier.padding(vertical = 4.dp)) {
        packages.sortedBy { (labels[it] ?: it).lowercase() }.forEach { pkg ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 28.dp, fallbackLabel = labels[pkg] ?: pkg)
                Spacer(Modifier.width(12.dp))
                Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Selector de varias apps para las listas del modo mañana. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningAppsPickerScreen(list: MorningAppList, vm: MainViewModel, onBack: () -> Unit, picker: AppPickerViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by picker.visible.collectAsStateWithLifecycle()
    val query by picker.query.collectAsStateWithLifecycle()
    val m = settings?.morning ?: return
    val selected = if (list == MorningAppList.RESTRICTED) m.restricted else m.privileged
    val other = if (list == MorningAppList.RESTRICTED) m.privileged else m.restricted

    fun toggle(pkg: String) = vm.updateMorning { r ->
        // Una app no puede ser restringida y privilegiada a la vez.
        when (list) {
            MorningAppList.RESTRICTED ->
                if (pkg in r.restricted) r.copy(restricted = r.restricted - pkg) else r.copy(restricted = r.restricted + pkg, privileged = r.privileged - pkg)
            MorningAppList.PRIVILEGED ->
                if (pkg in r.privileged) r.copy(privileged = r.privileged - pkg) else r.copy(privileged = r.privileged + pkg, restricted = r.restricted - pkg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = onBack) { Text("Listo") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { picker.query.value = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val all = apps
            if (all == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            // Las elegidas primero.
            val sorted = all.sortedByDescending { it.packageName in selected }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(sorted, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(app.packageName) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.packageName)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            if (app.packageName in other) {
                                Text(
                                    if (list == MorningAppList.RESTRICTED) "Ahora es privilegiada" else "Ahora es restringida",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Checkbox(checked = app.packageName in selected, onCheckedChange = { toggle(app.packageName) })
                    }
                }
            }
        }
    }
}
