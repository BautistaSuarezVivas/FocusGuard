package com.personal.focusguard

import com.personal.focusguard.data.AppSettings
import com.personal.focusguard.data.PlatformSettings
import com.personal.focusguard.detection.DetectionRules
import com.personal.focusguard.detection.NodeInfo
import com.personal.focusguard.detection.ShortFormDetector
import com.personal.focusguard.detection.ShortFormTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección estructural de TikTok con árboles simulados. NO reemplaza la verificación con
 * las apps reales (ver docs/PRUEBAS_MANUALES.md).
 */
class TikTokDetectionTest {

    private val tiktok = DetectionRules.tiktok

    private fun list(height: Float, width: Float = 1f, vertical: Boolean? = true, cls: String = "androidx.recyclerview.widget.RecyclerView") =
        NodeInfo(className = cls, scrollable = true, heightFraction = height, widthFraction = width, vertical = vertical)

    private fun tab(label: String, selected: Boolean) = NodeInfo(description = label, selected = selected, heightFraction = 0.05f)

    @Test
    fun tiktokFeedIsDetected() {
        val nodes = listOf(list(0.93f, cls = "androidx.viewpager.widget.ViewPager", vertical = null), tab("Inicio", true))
        assertTrue(ShortFormDetector.evaluate(tiktok, nodes).detected)
    }

    @Test
    fun tiktokInboxIsNotDetected() {
        val nodes = listOf(list(0.80f), tab("Bandeja de entrada", true))
        assertFalse(ShortFormDetector.evaluate(tiktok, nodes).detected)
    }

    @Test
    fun tiktokInboxWithTallListStillNotDetectedBecauseOfSelectedTab() {
        val nodes = listOf(list(0.90f), tab("Inbox", true))
        assertFalse(ShortFormDetector.evaluate(tiktok, nodes).detected)
    }

    @Test
    fun tiktokVideoOpenedFromSharedLinkIsDetected() {
        // Sin barra inferior: solo el pager y los botones laterales.
        val nodes = listOf(list(1f), NodeInfo(description = "Compartir video"))
        assertTrue(ShortFormDetector.evaluate(tiktok, nodes).detected)
    }

    @Test
    fun tiktokLabelsAloneNeverTrigger() {
        val nodes = listOf(NodeInfo(description = "Like video"), NodeInfo(description = "Share video"), NodeInfo(description = "Read or add comments"))
        assertFalse(ShortFormDetector.evaluate(tiktok, nodes).detected)
    }

    @Test
    fun nonScrollableFullScreenContainerIsNotAPager() {
        val node = NodeInfo(className = "androidx.recyclerview.widget.RecyclerView", scrollable = false, heightFraction = 1f, widthFraction = 1f)
        assertFalse(ShortFormDetector.evaluate(tiktok, listOf(node)).detected)
    }

    @Test
    fun horizontalFullScreenListIsNotTheFeed() {
        assertFalse(ShortFormDetector.evaluate(tiktok, listOf(list(1f, vertical = false))).detected)
    }

    @Test
    fun allTikTokPackagesMapToTheSameRules() {
        listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go").forEach {
            assertEquals(ShortFormTarget.TIKTOK, DetectionRules.forPackage(it)?.target)
        }
        // X se quitó: no tiene ids suficientes para reconocer sus videos cortos.
        assertEquals(null, DetectionRules.forPackage("com.twitter.android"))
    }

    @Test
    fun platformSettingsAreIndependent() {
        val s = AppSettings()
            .withPlatform(ShortFormTarget.TIKTOK) { it.copy(allowed = 2) }
            .withPlatform(ShortFormTarget.YOUTUBE_SHORTS) { it.copy(blocked = false, extraIds = "a, com.google.android.youtube:id/b") }
        assertEquals(2, s.platform(ShortFormTarget.TIKTOK).allowed)
        assertEquals(PlatformSettings(), s.platform(ShortFormTarget.INSTAGRAM_REELS))
        assertFalse(s.isBlocking(ShortFormTarget.YOUTUBE_SHORTS))
        assertTrue(s.isBlocking(ShortFormTarget.TIKTOK))
        assertFalse(s.copy(autoDetect = false).isBlocking(ShortFormTarget.TIKTOK))
        assertEquals(listOf("a", "b"), s.extraIdsFor(ShortFormTarget.YOUTUBE_SHORTS))
    }
}
