package com.personal.focusguard.detection

/**
 * Cuenta cuántos reels/shorts se vieron en la visita actual al visor, para permitir ver
 * [allowed] antes de sacar al usuario. La cantidad es POR VEZ: cada vez que se entra al visor
 * (después de salir uno mismo o de que FocusGuard lo saque) la cuenta empieza de cero.
 * Ejemplo con allowed = 1: un amigo te manda un reel, lo ves, salís a responderle; si te manda
 * otro, también lo podés ver. Si en cambio deslizás al siguiente reel, FocusGuard te saca.
 *
 * Un "deslizamiento" se detecta con los eventos de desplazamiento del pager del visor:
 *  - si el evento informa el índice del elemento visible, cuenta cada cambio de índice;
 *  - si no lo informa (algunos pagers propios), cuenta cada ráfaga de desplazamiento separada
 *    por al menos [burstGapMs] de quietud.
 * Los desplazamientos de los primeros [openSettleMs] de la visita se ignoran (animación de
 * apertura del visor).
 *
 * La visita termina cuando el visor deja de verse, la app sale del primer plano o FocusGuard
 * saca al usuario.
 */
class ViewerAllowance(
    private val openSettleMs: Long = 1_200,
    private val burstGapMs: Long = 600,
) {
    private var active = false
    private var sessionStart = 0L
    private var lastIndex: Int? = null
    private var lastScrollAt = Long.MIN_VALUE / 2

    var swipes = 0
        private set

    /** Reels vistos en la visita actual (el primero cuenta como 1). */
    val watched: Int get() = if (active) swipes + 1 else 0

    /**
     * El visor está en pantalla. Devuelve true si hay que sacar al usuario.
     * @param allowed reels permitidos por vez (0 = salir de inmediato).
     */
    fun onViewerVisible(allowed: Int, now: Long): Boolean {
        if (allowed <= 0) return true
        if (!active) {
            active = true
            sessionStart = now
            swipes = 0
            lastIndex = null
        }
        return watched > allowed
    }

    /** Evento de desplazamiento del pager del visor. [index] = primer elemento visible o -1. */
    fun onPagerScrolled(index: Int, now: Long) {
        if (!active) return
        if (now - sessionStart < openSettleMs) {
            if (index >= 0) lastIndex = index
            lastScrollAt = now
            return
        }
        if (index >= 0) {
            if (index != lastIndex) swipes++
            lastIndex = index
        } else if (now - lastScrollAt > burstGapMs) {
            swipes++
        }
        lastScrollAt = now
    }

    /** Terminó la visita: salió del visor, cambió de app o FocusGuard lo sacó. */
    fun onViewerGone() {
        active = false
        swipes = 0
        lastIndex = null
    }
}
