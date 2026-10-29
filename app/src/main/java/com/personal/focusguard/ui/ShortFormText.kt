package com.personal.focusguard.ui

import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.detection.ShortFormTarget

/** Textos de interfaz por plataforma de videos cortos (compartidos por el panel y Ajustes). */
val ShortFormTarget.blockLabel: String get() = "Bloquear ${displayName.replace("Videos", "videos")}"

/** Qué sigue disponible en la app aunque se bloqueen sus videos cortos. */
val ShortFormTarget.stillAvailable: String
    get() = when (this) {
        ShortFormTarget.INSTAGRAM_REELS -> "El feed, mensajes y perfiles siguen disponibles"
        ShortFormTarget.YOUTUBE_SHORTS -> "Los videos normales siguen disponibles"
        ShortFormTarget.TIKTOK -> "Los mensajes siguen disponibles"
    }

/** "1 reel", "3 reels". */
fun ShortFormTarget.units(n: Int): String = "$n ${if (n == 1) unitPlural.removeSuffix("s") else unitPlural}"

/** Resumen corto del estado de la plataforma. */
fun AppSettings.summaryFor(target: ShortFormTarget): String {
    val p = platform(target)
    return when {
        !p.blocked -> "Sin bloqueo"
        !autoDetect -> "Detección automática desactivada"
        p.allowed == 0 -> "Bloqueado · te saca al entrar"
        else -> "Bloqueado · ${target.units(p.allowed)} por vez"
    }
}
