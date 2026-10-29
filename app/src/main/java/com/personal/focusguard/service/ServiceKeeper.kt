package com.personal.focusguard.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.personal.focusguard.graph

/**
 * Reactivación automática del servicio de accesibilidad.
 *
 * Android no permite que una app habilite su propio servicio de accesibilidad... salvo que el
 * usuario le otorgue desde una PC el permiso de desarrollo WRITE_SECURE_SETTINGS
 * (`adb shell pm grant com.personal.focusguard android.permission.WRITE_SECURE_SETTINGS`,
 * lo hace el script `instalar.bat`). Con ese permiso FocusGuard:
 *  - vuelve a habilitar el servicio si quedó deshabilitado,
 *  - lo "reinicia" (deshabilitar + habilitar) si figura habilitado pero Android no lo está
 *    ejecutando, algo que ocurre p. ej. al actualizar la app.
 * Solo toca la entrada de FocusGuard; los demás servicios de accesibilidad se conservan.
 */
object ServiceKeeper {

    private const val TAG = "FocusGuard"

    fun component(context: Context) = ComponentName(context, FocusAccessibilityService::class.java)

    fun canSelfEnable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun enabledList(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.filter { it.isNotBlank() }.orEmpty()

    private fun isOurs(entry: String, context: Context) = ComponentName.unflattenFromString(entry) == component(context)

    private fun write(context: Context, entries: List<String>) {
        val cr = context.contentResolver
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, entries.joinToString(":"))
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, if (entries.isEmpty()) 0 else 1)
    }

    /**
     * Garantiza que el servicio esté habilitado y en ejecución. Devuelve false si no hay permiso.
     * @param running si el servicio está conectado ahora mismo en este proceso.
     */
    fun ensureRunning(context: Context, running: Boolean): Boolean {
        if (!canSelfEnable(context)) return false
        if (running) return true
        val others = enabledList(context).filterNot { isOurs(it, context) }
        val ours = component(context).flattenToString()
        return runCatching {
            if (enabledList(context).any { isOurs(it, context) }) {
                // Habilitado pero sin ejecutarse: quitar y volver a poner fuerza a Android a enlazarlo.
                write(context, others)
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { write(context, enabledList(context).filterNot { isOurs(it, context) } + ours) }
                }, 700)
            } else {
                write(context, others + ours)
            }
            Log.d(TAG, "servicio de accesibilidad reactivado automáticamente")
            true
        }.getOrElse {
            Log.w(TAG, "no se pudo reactivar el servicio", it)
            false
        }
    }
}

/**
 * Tras actualizar la app (instalar un APK nuevo encima) Android a veces no vuelve a enlazar el
 * servicio de accesibilidad. Si hay permiso, se reactiva solo.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        // Se da tiempo a que Android enlace el servicio por su cuenta antes de intervenir.
        handler.postDelayed({
            val app = context.applicationContext
            if (app.graph.settings.value?.autoReenable != false) {
                ServiceKeeper.ensureRunning(app, running = app.graph.serviceConnected.value)
            }
            handler.postDelayed({ pending.finish() }, 1_500)
        }, 3_000)
    }
}
