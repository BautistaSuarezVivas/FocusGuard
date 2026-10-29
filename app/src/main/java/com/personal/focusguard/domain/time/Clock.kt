package com.personal.focusguard.domain.time

import java.time.ZoneId

/**
 * Fuente de tiempo inyectable.
 * - [wallMillis]: reloj de pared (epoch ms). Puede saltar si el usuario o la red cambian la hora.
 * - [elapsedMillis]: reloj monotónico desde el arranque. Nunca retrocede mientras el equipo está
 *   encendido; se usa para medir duraciones sin verse afectado por cambios de hora.
 */
interface Clock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
    fun zone(): ZoneId
}

object SystemClockSource : Clock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = android.os.SystemClock.elapsedRealtime()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
