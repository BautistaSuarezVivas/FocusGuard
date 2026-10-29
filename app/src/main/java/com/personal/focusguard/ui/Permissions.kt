package com.personal.focusguard.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.personal.focusguard.service.FocusAccessibilityService
import com.personal.focusguard.service.ServiceKeeper

data class PermissionState(
    /** El usuario habilitó el servicio en Ajustes de accesibilidad. */
    val accessibilityEnabled: Boolean,
    /** El servicio está realmente conectado y funcionando en este momento. */
    val serviceRunning: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    /** Se otorgó WRITE_SECURE_SETTINGS desde la PC: FocusGuard puede reactivar su servicio. */
    val canSelfEnable: Boolean,
)

/**
 * Accesos especiales que usa FocusGuard. Deliberadamente NO se usan: acceso a datos de uso,
 * notificaciones, alarmas exactas, superposición sobre otras apps ni Internet.
 */
object Permissions {

    fun state(context: Context, serviceRunning: Boolean) = PermissionState(
        accessibilityEnabled = isAccessibilityEnabled(context),
        serviceRunning = serviceRunning,
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
        canSelfEnable = ServiceKeeper.canSelfEnable(context),
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FocusAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Abre Accesibilidad con la entrada de FocusGuard resaltada. (La página de detalle del
     * servicio, ACCESSIBILITY_DETAILS_SETTINGS, exige un permiso de sistema: probado en Android 16,
     * se rechaza con "Permission Denial".)
     */
    fun openAccessibilitySettings(context: Context) {
        val key = ServiceKeeper.component(context).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
        start(context, intent)
    }

    // Extras de la app Ajustes de AOSP para desplazarse hasta una preferencia y resaltarla.
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    /** Info de la app: ahí está "Permitir ajustes restringidos" (Android 13+, apps instaladas con APK). */
    fun openAppDetails(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))

    /** Lista de optimización de batería (no requiere permisos especiales). */
    fun openBatterySettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppDetails(context)
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
