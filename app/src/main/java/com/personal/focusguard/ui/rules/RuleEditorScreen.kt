package com.personal.focusguard.ui.rules

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.model.OpenWindowType
import com.personal.focusguard.domain.model.UsagePeriodType
import com.personal.focusguard.graph
import com.personal.focusguard.ui.components.AppIcon
import com.personal.focusguard.ui.components.ChoiceChips
import com.personal.focusguard.ui.components.LimitBadge
import com.personal.focusguard.ui.components.NumberField
import com.personal.focusguard.ui.components.SwitchRow
import com.personal.focusguard.ui.components.TimeOfDayField
import com.personal.focusguard.ui.theme.LimitColors
import com.personal.focusguard.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class RuleEditorViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = app.graph
    val packageName: String = checkNotNull(saved["pkg"])
    private val label: String = saved.get<String>("label")?.takeIf { it.isNotBlank() } ?: packageName

    val rules = MutableStateFlow<AppRules?>(null)
    val exists = MutableStateFlow(false)
    val defaultReset: Int get() = graph.settings.value?.defaultResetMinute ?: 240

    init {
        viewModelScope.launch {
            val existing = graph.ruleRepository.get(packageName)
            exists.value = existing != null
            rules.value = existing ?: AppRules(packageName, label)
        }
    }

    fun edit(transform: (AppRules) -> AppRules) {
        rules.value = rules.value?.let(transform)
    }

    fun save(onDone: () -> Unit) {
        val r = rules.value ?: return
        viewModelScope.launch {
            graph.ruleRepository.save(r)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            graph.ruleRepository.delete(packageName)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(onBack: () -> Unit, vm: RuleEditorViewModel = viewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val exists by vm.exists.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rules?.label ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (exists) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Quitar control") }
                },
            )
        },
    ) { padding ->
        val r = rules
        if (r == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(r.packageName, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(r.label, style = MaterialTheme.typography.titleLarge)
                    Text(r.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
            UsageSection(r, vm)
            OpensSection(r, vm)
            Button(onClick = { vm.save(onBack) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Quitar control") },
            text = { Text("Se eliminarán las reglas de tiempo y de aperturas de esta app.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun UsageSection(r: AppRules, vm: RuleEditorViewModel) {
    val u = r.usage
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitBadge("⏱ LÍMITE DE TIEMPO DE USO", LimitColors.time())
            SwitchRow(
                "Activado",
                u.enabled,
                { v -> vm.edit { it.copy(usage = it.usage.copy(enabled = v)) } },
                subtitle = "Cuenta solo el tiempo real con la app en primer plano y la pantalla encendida.",
            )
            if (u.enabled) {
                NumberField("Tiempo máximo", u.limitMinutes, { v -> vm.edit { it.copy(usage = it.usage.copy(limitMinutes = v)) } }, 1..1440, "min")
                Text("Período", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    listOf(UsagePeriodType.DAILY to "Diario", UsagePeriodType.WEEKLY to "Semanal (desde el lunes)", UsagePeriodType.EVERY_N_HOURS to "Cada N horas"),
                    u.periodType,
                ) { v -> vm.edit { it.copy(usage = it.usage.copy(periodType = v)) } }
                if (u.periodType == UsagePeriodType.EVERY_N_HOURS) {
                    NumberField("Duración del período", u.everyHours, { v -> vm.edit { it.copy(usage = it.usage.copy(everyHours = v)) } }, 1..24, "h")
                }
                SwitchRow(
                    "Usar hora de reinicio global (${Format.minuteOfDay(vm.defaultReset)})",
                    u.resetMinuteOfDay == null,
                    { useGlobal ->
                        vm.edit { it.copy(usage = it.usage.copy(resetMinuteOfDay = if (useGlobal) null else vm.defaultReset)) }
                    },
                )
                u.resetMinuteOfDay?.let { m ->
                    TimeOfDayField("Hora de reinicio de esta app", m) { v -> vm.edit { it.copy(usage = it.usage.copy(resetMinuteOfDay = v)) } }
                }
            }
        }
    }
}

@Composable
private fun OpensSection(r: AppRules, vm: RuleEditorViewModel) {
    val o = r.opens
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitBadge("↻ LÍMITE DE APERTURAS", LimitColors.opens())
            SwitchRow(
                "Activado",
                o.enabled,
                { v -> vm.edit { it.copy(opens = it.opens.copy(enabled = v)) } },
                subtitle = "Una apertura es entrar a la app desde otra app o el inicio. Los intentos bloqueados no se cuentan.",
            )
            if (o.enabled) {
                NumberField("Aperturas máximas", o.maxOpens, { v -> vm.edit { it.copy(opens = it.opens.copy(maxOpens = v)) } }, 1..500, "")
                Text("Tipo de ventana", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    listOf(OpenWindowType.ROLLING to "Ventana móvil", OpenWindowType.FIXED_BLOCKS to "Bloques fijos"),
                    o.windowType,
                ) { v -> vm.edit { it.copy(opens = it.opens.copy(windowType = v)) } }
                Text(
                    when (o.windowType) {
                        OpenWindowType.ROLLING -> "Cuenta las aperturas de los últimos X minutos. Cada apertura se libera X minutos después."
                        OpenWindowType.FIXED_BLOCKS -> "Cuenta por bloques consecutivos (p. ej. 10:00–11:00, 11:00–12:00). El contador vuelve a cero al empezar cada bloque."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField("Duración", o.windowMinutes, { v -> vm.edit { it.copy(opens = it.opens.copy(windowMinutes = v)) } }, 1..1440, "min")
                ChoiceChips(listOf(30 to "30 min", 45 to "45 min", 60 to "60 min", 120 to "120 min"), o.windowMinutes) { v ->
                    vm.edit { it.copy(opens = it.opens.copy(windowMinutes = v)) }
                }
                if (o.windowType == OpenWindowType.FIXED_BLOCKS) {
                    TimeOfDayField("Los bloques empiezan a las", o.anchorMinuteOfDay) { v ->
                        vm.edit { it.copy(opens = it.opens.copy(anchorMinuteOfDay = v)) }
                    }
                }
            }
        }
    }
}
