package com.personal.focusguard

import com.personal.focusguard.detection.ViewerAllowance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerAllowanceTest {

    @Test
    fun zeroAllowedExitsImmediately() {
        assertTrue(ViewerAllowance().onViewerVisible(0, 0))
    }

    @Test
    fun oneAllowed_watchSharedReelThenSwipeExits() {
        val a = ViewerAllowance()
        assertFalse(a.onViewerVisible(1, 0)) // abre el reel que le mandaron
        a.onPagerScrolled(0, 200) // animación de apertura: se ignora
        assertFalse(a.onViewerVisible(1, 30_000)) // lo mira entero
        a.onPagerScrolled(0, 30_100) // empieza a deslizar
        a.onPagerScrolled(1, 30_300) // llega al siguiente
        assertTrue(a.onViewerVisible(1, 30_400))
    }

    @Test
    fun indexBasedCountingIgnoresRepeatedEventsOfSameSwipe() {
        val a = ViewerAllowance()
        a.onViewerVisible(3, 0)
        listOf(1, 1, 1, 2, 2).forEachIndexed { i, idx -> a.onPagerScrolled(idx, 2_000L + i * 50) }
        assertFalse(a.onViewerVisible(3, 3_000)) // vio 3 (inicial + 2 deslizamientos)
        a.onPagerScrolled(3, 4_000)
        assertTrue(a.onViewerVisible(3, 4_100))
    }

    @Test
    fun withoutIndexCountsScrollBursts() {
        val a = ViewerAllowance()
        a.onViewerVisible(2, 0)
        // Ráfaga 1 (varios eventos seguidos) = un deslizamiento.
        listOf(5_000L, 5_050L, 5_120L, 5_200L).forEach { a.onPagerScrolled(-1, it) }
        assertFalse(a.onViewerVisible(2, 5_300))
        // Ráfaga 2 tras una pausa.
        listOf(9_000L, 9_060L).forEach { a.onPagerScrolled(-1, it) }
        assertTrue(a.onViewerVisible(2, 9_200))
    }

    @Test
    fun scrollBackToSameReelIsNotASwipe() {
        val a = ViewerAllowance()
        a.onViewerVisible(1, 0)
        a.onPagerScrolled(0, 100)
        a.onPagerScrolled(0, 2_000) // arrastre que rebota al mismo reel
        assertFalse(a.onViewerVisible(1, 2_100))
    }

    @Test
    fun leavingTheViewerStartsAFreshVisit() {
        val a = ViewerAllowance()
        a.onViewerVisible(1, 0)
        a.onPagerScrolled(0, 100)
        a.onViewerGone() // volvió al chat sin deslizar
        assertFalse(a.onViewerVisible(1, 10_000)) // otro reel enviado: se puede ver
    }

    @Test
    fun allowanceIsPerVisit_userLeavesAndOpensAnotherSharedReel() {
        val a = ViewerAllowance()
        // Ve el reel que le mandaron y sale a responder.
        assertFalse(a.onViewerVisible(1, 0))
        a.onViewerGone()
        // Le mandan otro: lo puede ver, y así cada vez.
        repeat(5) { i ->
            assertFalse(a.onViewerVisible(1, 10_000L * (i + 1)))
            a.onViewerGone()
        }
    }

    @Test
    fun afterBeingKickedOutTheNextVisitStartsFresh() {
        val a = ViewerAllowance()
        a.onViewerVisible(1, 0)
        a.onPagerScrolled(0, 2_000)
        a.onPagerScrolled(1, 2_200)
        assertTrue(a.onViewerVisible(1, 2_300)) // deslizó: lo saca
        a.onViewerGone() // FocusGuard lo sacó
        assertFalse(a.onViewerVisible(1, 5_000)) // nuevo reel enviado: lo puede ver
    }
}
