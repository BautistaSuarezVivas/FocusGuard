package com.personal.focusguard.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.focusguard.ui.MainViewModel
import com.personal.focusguard.ui.Permissions
import com.personal.focusguard.ui.settings.PRIVACY_TEXT

/** Asistente inicial: explica las funciones y guía para habilitar solo lo necesario. */
@Composable
fun OnboardingScreen(vm: MainViewModel, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    val steps = 4
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Title("Bienvenido a FocusGuard")
                    Body("Una app personal para reducir distracciones. Hace tres cosas:")
                    Bullet("Te saca de los Reels de Instagram y de los Shorts de YouTube, sin bloquear el resto de esas apps.")
                    Bullet("Limita el TIEMPO de uso diario (u otro período) de las apps que elijas.")
                    Bullet("Limita cuántas veces podés ABRIR una app en un período (ventana móvil o bloques fijos).")
                    Body("Todo funciona en el teléfono, sin Internet ni cuentas.")
                }
                1 -> {
                    Title("Privacidad y uso de accesibilidad")
                    Body(PRIVACY_TEXT)
                }
                2 -> {
                    Title("Activar el servicio de accesibilidad")
                    Body(
                        "Es el único acceso imprescindible. Android lo exige para que una app pueda saber qué app está en pantalla " +
                            "y ejecutar Atrás/Inicio por vos.",
                    )
                    Bullet(
                        "Si instalaste con instalar.bat desde la PC, esto ya está hecho: debería figurar \"Servicio habilitado\" abajo.",
                    )
                    Bullet("Si no: tocá \"Abrir Accesibilidad\". En Android 11+ se abre directo la página de FocusGuard: activá el interruptor y tocá Permitir.")
                    Bullet(
                        "Si la opción aparece gris o dice \"Ajuste restringido\" (Android 13+ con APK instalado a mano): " +
                            "abrí \"Info de la app\" → menú ⋮ arriba a la derecha → \"Permitir ajustes restringidos\", y volvé a intentar.",
                    )
                    val p = dashboard?.permissions
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            val ok = p?.accessibilityEnabled == true
                            Text(
                                if (ok) "✓ Servicio habilitado" else "✗ Servicio todavía desactivado",
                                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Button(onClick = { Permissions.openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir Accesibilidad")
                    }
                    OutlinedButton(onClick = { Permissions.openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Info de la app (ajustes restringidos)")
                    }
                }
                3 -> {
                    Title("Batería (recomendado)")
                    Body(
                        "Algunos fabricantes (Xiaomi, Samsung, Huawei, Oppo…) detienen servicios en segundo plano para ahorrar batería. " +
                            "Si FocusGuard deja de funcionar al rato, excluilo de la optimización de batería y, si tu teléfono lo tiene, " +
                            "habilitá \"Inicio automático\" y bloqueá la app en Recientes.",
                    )
                    val ignoring = dashboard?.permissions?.ignoringBatteryOptimizations == true
                    Text(
                        if (ignoring) "✓ FocusGuard ya está excluida de la optimización" else "Optimización de batería activa",
                        color = if (ignoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { Permissions.openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ajustes de batería")
                    }
                    Body("FocusGuard no necesita permisos de notificaciones, alarmas exactas ni acceso a datos de uso: los períodos se recalculan solos con cada evento, aunque el teléfono se reinicie o cambie la hora.")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Atrás") } else Spacer(Modifier)
            Button(onClick = { if (step < steps - 1) step++ else onFinish() }) {
                Text(if (step < steps - 1) "Siguiente" else "Empezar")
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Bullet(text: String) = Text("•  $text", style = MaterialTheme.typography.bodyLarge)
