package com.personal.focusguard.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.personal.focusguard.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(64)
}

private fun loadIcon(context: Context, pkg: String): ImageBitmap? {
    IconCache.cache.get(pkg)?.let { return it }
    return runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
    }.getOrNull()?.also { IconCache.cache.put(pkg, it) }
}

/** Ícono de la app; si no está instalada, un círculo con la inicial de [fallbackLabel]. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, fallbackLabel: String? = null) {
    val context = LocalContext.current
    val bitmap by produceState(IconCache.cache.get(packageName), packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            fallbackLabel?.firstOrNull()?.let {
                Text(it.uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Etiqueta de color que distingue los límites de tiempo de los de aperturas. */
@Composable
fun LimitBadge(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Fila con interruptor. Tocarla alterna el valor; [onLongClick] (opcional) se ejecuta al mantenerla
 * presionada. [leading] es un contenido opcional al inicio (p. ej. el ícono de la app).
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = { onChange(!checked) })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/** Campo numérico con validación de rango; solo informa valores válidos. */
@Composable
fun NumberField(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    range: IntRange,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    var lastSent by rememberSaveable { mutableIntStateOf(value) }
    // Sincroniza solo cambios externos (p. ej. los chips de valores rápidos). Los valores que
    // emitió este mismo campo llegan un frame después: si se restauraran, al borrar rápido
    // reaparecería un dígito con el cursor al inicio (borrar "60" y escribir "2" daba "26").
    LaunchedEffect(value) {
        if (value != lastSent) {
            lastSent = value
            text = value.toString()
        }
    }
    val parsed = text.toIntOrNull()
    val error = parsed == null || parsed !in range
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.takeIf { it in range }?.let {
                lastSent = it
                onValue(it)
            }
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = error,
        supportingText = { if (error) Text("Entre ${range.first} y ${range.last}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Selector de hora (minuto del día) con diálogo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(Format.minuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
            text = { TimePicker(state = state) },
        )
    }
}
