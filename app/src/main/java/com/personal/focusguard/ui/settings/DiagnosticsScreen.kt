package com.personal.focusguard.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.focusguard.ui.MainViewModel
import com.personal.focusguard.util.Format

/**
 * Muestra la última evaluación de Instagram y YouTube: puntaje, señales que coincidieron y los
 * identificadores de vista visibles. Sirve para verificar en el teléfono real que la detección
 * funciona y para ajustar las reglas si la interfaz cambió. No muestra ni guarda textos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val diag by vm.diagnostics.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de detección") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = { vm.clearDiagnostics() }) { Text("Limpiar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings?.diagnostics != true) {
                Text("Activá \"Modo diagnóstico\" en Ajustes y luego abrí Instagram o YouTube.", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Cómo usarlo: abrí Instagram/YouTube, navegá a la sección a probar, volvé aquí y mirá el último resultado. " +
                    "\"detectado = sí\" debería aparecer solo en el visor de Reels/Shorts.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (diag.isEmpty()) Text("Sin datos todavía.")
            diag.values.sortedBy { it.packageName }.forEach { d ->
                Card {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(d.result.target.appLabel + " – " + d.result.target.sectionName, fontWeight = FontWeight.Bold)
                            Text("Hora: ${Format.moment(d.timestamp)}")
                            Text("Puntaje: ${d.result.score} · detectado = ${if (d.result.detected) "sí" else "no"}")
                            d.action?.let { Text("Última acción: $it") }
                            Text("Señales: " + d.result.matchedSignals.ifEmpty { listOf("ninguna") }.joinToString(", "))
                            Text("Ids visibles (${d.observedIds.size}):", fontWeight = FontWeight.SemiBold)
                            Text(d.observedIds.joinToString(", "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
