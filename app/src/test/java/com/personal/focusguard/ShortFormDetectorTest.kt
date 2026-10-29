package com.personal.focusguard

import com.personal.focusguard.detection.DetectionRules
import com.personal.focusguard.detection.NodeInfo
import com.personal.focusguard.detection.ShortFormDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica de puntuación con árboles simulados. NO reemplazan la verificación en el
 * teléfono real con las versiones actuales de Instagram y YouTube (ver docs/PRUEBAS_MANUALES.md).
 */
class ShortFormDetectorTest {

    private val ig = DetectionRules.instagram
    private val yt = DetectionRules.youtube

    private fun tab(label: String, selected: Boolean) = NodeInfo(description = label, selected = selected, heightFraction = 0.05f)

    @Test
    fun instagramReelsViewerIsDetected() {
        val nodes = listOf(
            NodeInfo(viewId = "clips_viewer_view_pager", heightFraction = 0.92f),
            NodeInfo(text = "Audio original"),
            tab("Inicio", false), tab("Reels", true),
        )
        assertTrue(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun instagramReelOpenedFromDmWithoutTabIsDetected() {
        val nodes = listOf(NodeInfo(viewId = "clips_viewer_view_pager", heightFraction = 1f))
        assertTrue(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun instagramFeedWithReelsTabButtonIsNotDetected() {
        val nodes = listOf(
            NodeInfo(viewId = "row_feed_photo_profile_name", heightFraction = 0.03f),
            NodeInfo(text = "Reels"), // texto en un menú/botón
            tab("Inicio", true), tab("Reels", false),
        )
        assertFalse(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun instagramProfileReelsGridTabAloneIsNotDetected() {
        // La pestaña "Reels" de un perfil está seleccionada pero no hay visor.
        val nodes = listOf(tab("Reels", true), NodeInfo(viewId = "profile_header_container", heightFraction = 0.3f))
        assertFalse(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun onlyWeakTextNeverTriggers() {
        val nodes = listOf(NodeInfo(text = "Audio original"), NodeInfo(text = "Usar audio"), NodeInfo(text = "Remix"))
        val r = ShortFormDetector.evaluate(ig, nodes)
        assertFalse(r.detected)
    }

    @Test
    fun hiddenViewerIsIgnored() {
        val nodes = listOf(NodeInfo(viewId = "clips_viewer_view_pager", heightFraction = 0.9f, visible = false))
        assertFalse(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun smallClipsContainerInFeedIsIgnored() {
        val nodes = listOf(
            NodeInfo(viewId = "clips_video_container", heightFraction = 0.5f),
            NodeInfo(viewId = "row_feed_photo_profile_name", heightFraction = 0.03f),
        )
        assertFalse(ShortFormDetector.evaluate(ig, nodes).detected)
    }

    @Test
    fun youtubeShortsPlayerIsDetected() {
        val nodes = listOf(NodeInfo(viewId = "reel_recycler", heightFraction = 0.95f), tab("Shorts", true))
        assertTrue(ShortFormDetector.evaluate(yt, nodes).detected)
    }

    @Test
    fun youtubeHomeWithShortsTabAndShelfIsNotDetected() {
        val nodes = listOf(
            tab("Inicio", true), tab("Shorts", false),
            NodeInfo(viewId = "reel_shelf_item", heightFraction = 0.3f),
            NodeInfo(text = "Shorts"), // título del estante
        )
        assertFalse(ShortFormDetector.evaluate(yt, nodes).detected)
    }

    @Test
    fun youtubeNormalVideoIsNotDetected() {
        val nodes = listOf(
            NodeInfo(viewId = "watch_player", heightFraction = 0.3f),
            NodeInfo(viewId = "player_view", heightFraction = 0.3f),
            NodeInfo(text = "Shorts"),
        )
        assertFalse(ShortFormDetector.evaluate(yt, nodes).detected)
    }

    @Test
    fun youtubeChannelShortsTabGridIsNotDetected() {
        val nodes = listOf(NodeInfo(text = "Shorts", selected = true, heightFraction = 0.04f))
        assertFalse(ShortFormDetector.evaluate(yt, nodes).detected)
    }

    @Test
    fun userDefinedExtraIdEnablesDetection() {
        val nodes = listOf(NodeInfo(viewId = "new_shorts_pager_2027", heightFraction = 0.9f), tab("Shorts", true))
        assertFalse(ShortFormDetector.evaluate(yt, nodes).detected)
        assertTrue(ShortFormDetector.evaluate(yt, nodes, listOf("new_shorts_pager_2027")).detected)
    }

    @Test
    fun normalizationIgnoresCaseAndAccents() {
        assertEquals("musica original", ShortFormDetector.normalize("  Música   ORIGINAL "))
    }
}
