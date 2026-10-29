package com.personal.focusguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.focusguard.detection.ShortFormTarget
import com.personal.focusguard.domain.morning.MorningRule
import com.personal.focusguard.domain.morning.MorningStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Configuración de una plataforma de videos cortos (Reels, Shorts, TikTok). */
data class PlatformSettings(
    val blocked: Boolean = true,
    /** Videos que se pueden ver por visita al visor antes de que FocusGuard te saque (0 = ninguno). */
    val allowed: Int = 0,
    /** Ids de vista extra (separados por comas) definidos por el usuario. */
    val extraIds: String = "",
)

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val platforms: Map<ShortFormTarget, PlatformSettings> = emptyMap(),
    /** Si FocusGuard tiene el permiso otorgado desde la PC, reactivar su servicio cuando se apaga. */
    val autoReenable: Boolean = true,
    /** Analizar las pantallas de las apps de videos cortos. Si está apagado no se detecta nada. */
    val autoDetect: Boolean = true,
    /** Ejecutar "Atrás" (y navegación segura) al detectar el visor. */
    val autoAction: Boolean = true,
    /** Mostrar la pantalla de bloqueo si la acción automática no funcionó o está apagada. */
    val blockScreenFallback: Boolean = true,
    /** Hora de reinicio global de los límites de tiempo (minuto del día). 240 = 04:00. */
    val defaultResetMinute: Int = 240,
    /** Avisos breves (toast) cuando quedan 5 y 1 minutos. */
    val timeWarnings: Boolean = true,
    /** Guarda en memoria (no en disco) la última evaluación para la pantalla de diagnóstico. */
    val diagnostics: Boolean = false,
    val morning: MorningRule = MorningRule(),
) {
    fun platform(target: ShortFormTarget): PlatformSettings = platforms[target] ?: PlatformSettings()

    fun withPlatform(target: ShortFormTarget, transform: (PlatformSettings) -> PlatformSettings): AppSettings =
        copy(platforms = platforms + (target to transform(platform(target))))

    /** ¿Hay que detectar y bloquear esta plataforma? */
    fun isBlocking(target: ShortFormTarget): Boolean = autoDetect && platform(target).blocked

    fun extraIdsFor(target: ShortFormTarget): List<String> =
        platform(target).extraIds.split(',', '\n', ' ').map { it.trim().substringAfter(":id/") }.filter { it.isNotEmpty() }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val themeMode = stringPreferencesKey("theme_mode")
        val autoReenable = booleanPreferencesKey("auto_reenable")
        val autoDetect = booleanPreferencesKey("auto_detect")
        val autoAction = booleanPreferencesKey("auto_action")
        val blockScreen = booleanPreferencesKey("block_screen")
        val defaultReset = intPreferencesKey("default_reset_minute")
        val timeWarnings = booleanPreferencesKey("time_warnings")
        val diagnostics = booleanPreferencesKey("diagnostics")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningStart = stringPreferencesKey("morning_start")
        val morningEarliest = intPreferencesKey("morning_earliest")
        val morningLatest = intPreferencesKey("morning_latest")
        val morningFixed = intPreferencesKey("morning_fixed")
        val morningDuration = intPreferencesKey("morning_duration")
        val morningDays = stringSetPreferencesKey("morning_days")
        val morningLimitEnabled = booleanPreferencesKey("morning_limit_enabled")
        val morningLimit = intPreferencesKey("morning_limit_minutes")
        val morningRestricted = stringSetPreferencesKey("morning_restricted")
        val morningPrivileged = stringSetPreferencesKey("morning_privileged")
    }

    /** Claves por plataforma. Instagram y YouTube conservan las de la versión 1.1. */
    private class PlatformKeys(block: String, allowed: String, extra: String) {
        val block = booleanPreferencesKey(block)
        val allowed = intPreferencesKey(allowed)
        val extra = stringPreferencesKey(extra)
    }

    private val platformKeys = mapOf(
        ShortFormTarget.INSTAGRAM_REELS to PlatformKeys("block_reels", "reels_allowed", "extra_ig_ids"),
        ShortFormTarget.YOUTUBE_SHORTS to PlatformKeys("block_shorts", "shorts_allowed", "extra_yt_ids"),
        ShortFormTarget.TIKTOK to PlatformKeys("block_tiktok", "tiktok_allowed", "extra_tiktok_ids"),
    )

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val dp = PlatformSettings()
        return AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            themeMode = ThemeMode.entries.firstOrNull { it.name == p[Keys.themeMode] } ?: d.themeMode,
            platforms = platformKeys.mapValues { (_, k) ->
                PlatformSettings(
                    blocked = p[k.block] ?: dp.blocked,
                    allowed = p[k.allowed] ?: dp.allowed,
                    extraIds = p[k.extra] ?: dp.extraIds,
                )
            },
            autoReenable = p[Keys.autoReenable] ?: d.autoReenable,
            autoDetect = p[Keys.autoDetect] ?: d.autoDetect,
            autoAction = p[Keys.autoAction] ?: d.autoAction,
            blockScreenFallback = p[Keys.blockScreen] ?: d.blockScreenFallback,
            defaultResetMinute = p[Keys.defaultReset] ?: d.defaultResetMinute,
            timeWarnings = p[Keys.timeWarnings] ?: d.timeWarnings,
            diagnostics = p[Keys.diagnostics] ?: d.diagnostics,
            morning = readMorning(p),
        )
    }

    private fun readMorning(p: Preferences): MorningRule {
        val d = MorningRule()
        return MorningRule(
            enabled = p[Keys.morningEnabled] ?: d.enabled,
            start = MorningStart.entries.firstOrNull { it.name == p[Keys.morningStart] } ?: d.start,
            earliestMinute = p[Keys.morningEarliest] ?: d.earliestMinute,
            latestMinute = p[Keys.morningLatest] ?: d.latestMinute,
            fixedMinute = p[Keys.morningFixed] ?: d.fixedMinute,
            durationMinutes = p[Keys.morningDuration] ?: d.durationMinutes,
            days = p[Keys.morningDays]?.mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }?.toSet() ?: d.days,
            screenLimitEnabled = p[Keys.morningLimitEnabled] ?: d.screenLimitEnabled,
            screenLimitMinutes = p[Keys.morningLimit] ?: d.screenLimitMinutes,
            restricted = p[Keys.morningRestricted] ?: d.restricted,
            privileged = p[Keys.morningPrivileged] ?: d.privileged,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(read(p))
            p[Keys.onboardingDone] = n.onboardingDone
            p[Keys.themeMode] = n.themeMode.name
            platformKeys.forEach { (target, k) ->
                val ps = n.platform(target)
                p[k.block] = ps.blocked
                p[k.allowed] = ps.allowed.coerceIn(0, 50)
                p[k.extra] = ps.extraIds
            }
            p[Keys.autoReenable] = n.autoReenable
            p[Keys.autoDetect] = n.autoDetect
            p[Keys.autoAction] = n.autoAction
            p[Keys.blockScreen] = n.blockScreenFallback
            p[Keys.defaultReset] = n.defaultResetMinute
            p[Keys.timeWarnings] = n.timeWarnings
            p[Keys.diagnostics] = n.diagnostics
            val m = n.morning
            p[Keys.morningEnabled] = m.enabled
            p[Keys.morningStart] = m.start.name
            p[Keys.morningEarliest] = m.earliestMinute
            p[Keys.morningLatest] = m.latestMinute
            p[Keys.morningFixed] = m.fixedMinute
            p[Keys.morningDuration] = m.durationMinutes.coerceIn(1, 24 * 60)
            p[Keys.morningDays] = m.days.map { it.name }.toSet()
            p[Keys.morningLimitEnabled] = m.screenLimitEnabled
            p[Keys.morningLimit] = m.screenLimitMinutes.coerceIn(0, 24 * 60)
            p[Keys.morningRestricted] = m.restricted
            p[Keys.morningPrivileged] = m.privileged
        }
    }

    /** Restablece todo excepto el asistente inicial ya completado. */
    suspend fun resetToDefaults() {
        context.dataStore.edit { p ->
            p.clear()
            p[Keys.onboardingDone] = true
        }
    }
}
