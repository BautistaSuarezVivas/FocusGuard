package com.personal.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * Decide si un cambio de ventana representa un cambio REAL de la app en primer plano.
 * Se descartan: teclado, barra de estado / panel de notificaciones, diálogos del sistema,
 * selector de "compartir", diálogos de permisos y ventanas flotantes de otras apps.
 */
class ForegroundFilter(private val service: AccessibilityService) {

    private val alwaysIgnored = setOf(
        "com.android.systemui",
        "android",
        "com.android.intentresolver",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // Diálogos de Google Play Services (selector de cuenta, inicio de sesión de Google, etc.)
        // que se abren encima de otras apps. Visto en el emulador: Calendario mostraba uno al
        // abrirse y se cortaba su conteo de tiempo.
        "com.google.android.gms",
    )

    private var imePackages: Set<String> = emptySet()
    private var imeRefreshedAt = 0L

    fun isForegroundChange(pkg: String, event: AccessibilityEvent?): Boolean {
        if (pkg in alwaysIgnored || pkg in imes()) return false
        if (event != null && hasInteractiveWindows()) {
            val window = runCatching { service.windows.firstOrNull { it.id == event.windowId } }.getOrNull()
            // Si se encuentra la ventana, solo cuentan las ventanas de aplicación (no superposiciones).
            if (window != null && window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return false
        }
        return true
    }

    private fun hasInteractiveWindows(): Boolean =
        (service.serviceInfo?.flags ?: 0) and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0

    private fun imes(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - imeRefreshedAt > 60_000) {
            imeRefreshedAt = now
            val imm = service.getSystemService(InputMethodManager::class.java)
            imePackages = runCatching { imm.enabledInputMethodList.map { it.packageName }.toSet() }.getOrDefault(emptySet())
        }
        return imePackages
    }
}
