package com.personal.focusguard.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.AlarmClock
import android.telecom.TelecomManager

/**
 * Apps que el modo mañana nunca bloquea ni cuenta: el inicio (launcher), el teléfono y las
 * emergencias, el reloj (para que una alarma nunca quede bloqueada) y FocusGuard. Se detectan
 * con las apps predeterminadas del teléfono, sin lista fija de nombres.
 */
class EssentialApps(private val context: Context) {

    private var cached: Set<String> = emptySet()
    private var refreshedAt = 0L

    fun get(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (cached.isEmpty() || now - refreshedAt > REFRESH_MS) {
            cached = compute()
            refreshedAt = now
        }
        return cached
    }

    private fun compute(): Set<String> {
        val pm = context.packageManager
        val set = mutableSetOf(context.packageName)
        fun addHandlers(intent: Intent) = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0).forEach { set += it.activityInfo.packageName }
        }
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        addHandlers(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let { set += it }
        set += EMERGENCY_PACKAGES
        return set
    }

    companion object {
        private const val REFRESH_MS = 5 * 60_000L
        private val EMERGENCY_PACKAGES = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
        )
    }
}
