package com.personal.focusguard.util

import com.personal.focusguard.domain.tracking.BlockReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val es = Locale.forLanguageTag("es")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", es)
    private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE d/M HH:mm", es)

    /** "14:30", "mañana 04:00" o "lun 26/10 04:00". */
    fun moment(epochMs: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        // Redondeo hacia arriba al minuto: "se habilita 05:03" nunca debe ser antes de lo real.
        val t = Instant.ofEpochMilli(epochMs).atZone(zone).let {
            if (it.second > 0 || it.nano > 0) it.withSecond(0).withNano(0).plusMinutes(1) else it
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (t.toLocalDate()) {
            today -> t.format(timeFmt)
            today.plusDays(1) -> "mañana " + t.format(timeFmt)
            else -> t.format(dayTimeFmt)
        }
    }

    /** "1 h 05 min", "12 min", "45 s". */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 -> "%d h %02d min".format(h, m)
            m > 0 -> "$m min"
            else -> "${totalSec} s"
        }
    }

    fun minuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    fun blockTitle(reason: BlockReason): String = when (reason) {
        is BlockReason.UsageLimit -> "Tiempo de ${reason.appLabel} agotado"
        is BlockReason.OpenLimit -> "Sin aperturas de ${reason.appLabel}"
        is BlockReason.ShortForm -> "${reason.sectionName} bloqueado"
        is BlockReason.MorningRestricted -> "Modo mañana: ${reason.appLabel} restringida"
        is BlockReason.MorningScreenLimit -> "Modo mañana: tiempo de pantalla agotado"
    }

    fun blockMessage(reason: BlockReason, now: Long = System.currentTimeMillis()): String = when (reason) {
        is BlockReason.UsageLimit ->
            "Alcanzaste tu límite de ${reason.limitMinutes} min. Se restablece ${moment(reason.resetsAt, now)}."
        is BlockReason.OpenLimit -> {
            val next = reason.nextAvailableAt?.let { "La próxima apertura se habilita ${moment(it, now)}." } ?: ""
            "Usaste las ${reason.maxOpens} aperturas permitidas en este período. $next".trim()
        }
        is BlockReason.ShortForm ->
            "FocusGuard te sacó de ${reason.sectionName} en ${reason.appLabel}. El resto de la app sigue disponible."
        is BlockReason.MorningRestricted ->
            "Esta app está restringida durante el modo mañana. Se habilita ${moment(reason.endsAt, now)}."
        is BlockReason.MorningScreenLimit ->
            "Se agotó el tiempo de pantalla del modo mañana (${reason.limitMinutes} min). Hasta ${moment(reason.endsAt, now)} solo podés usar tus apps privilegiadas."
    }
}
