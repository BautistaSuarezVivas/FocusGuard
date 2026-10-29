package com.personal.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.personal.focusguard.domain.tracking.BlockReason
import com.personal.focusguard.ui.block.BlockActivity
import com.personal.focusguard.util.Format

/**
 * Impide seguir usando una app:
 *  1. Abre la pantalla de bloqueo de FocusGuard en su propia tarea, encima de la app.
 *     (Android permite que un servicio de accesibilidad activo inicie actividades.)
 *  2. Si al cabo de un momento la app sigue en primer plano (p. ej. un fabricante bloqueó el
 *     inicio de la actividad), ejecuta la acción global "Inicio" y muestra un aviso.
 */
class Blocker(private val service: AccessibilityService, private val handler: Handler) {

    private var lastBlockAt = 0L
    private var lastBlockPkg: String? = null

    /** Evita lanzar la pantalla repetidamente por la misma app en muy poco tiempo. */
    fun recentlyBlocked(pkg: String, withinMs: Long = 3_000): Boolean =
        lastBlockPkg == pkg && SystemClock.elapsedRealtime() - lastBlockAt < withinMs

    fun block(reason: BlockReason) {
        if (recentlyBlocked(reason.packageName, 700)) return
        lastBlockAt = SystemClock.elapsedRealtime()
        lastBlockPkg = reason.packageName
        val intent = BlockActivity.intent(service, reason).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
        val started = runCatching { service.startActivity(intent) }
            .onFailure { Log.w("FocusGuard", "No se pudo abrir la pantalla de bloqueo", it) }
            .isSuccess
        if (!started) {
            goHome(reason)
            return
        }
        handler.postDelayed({
            val fg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (fg == reason.packageName) goHome(reason)
        }, 1_200)
    }

    private fun goHome(reason: BlockReason) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Toast.makeText(service, Format.blockTitle(reason) + ". " + Format.blockMessage(reason), Toast.LENGTH_LONG).show()
    }
}
