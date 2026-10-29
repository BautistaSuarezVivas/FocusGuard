package com.personal.focusguard.detection

/**
 * Reglas de detección de los visores de vídeos cortos.
 *
 * ⚠ Instagram, YouTube y TikTok cambian sus interfaces con frecuencia. Este archivo concentra
 * TODO lo que depende de ellas para que actualizarlo sea simple. Además, desde Ajustes > Avanzado
 * se pueden agregar identificadores de vista extra sin recompilar (ver [ShortFormDetector]).
 * Usá la pantalla "Diagnóstico" de la app para ver qué identificadores expone la versión
 * instalada en tu teléfono.
 *
 * Funcionamiento: cada señal suma (o resta) puntos; se detecta el visor cuando el total alcanza
 * [RuleSet.threshold] y hay al menos una señal estructural (id, clase o pestaña seleccionada).
 * Las señales de texto sueltas ("débiles") tienen un tope, así que una palabra como "Reels" o
 * "Shorts" por sí sola NUNCA alcanza para bloquear.
 */
enum class ShortFormTarget(
    /** Paquetes de la app (TikTok tiene variantes regionales y Lite). */
    val packages: Set<String>,
    val appLabel: String,
    /** Nombre de la sección bloqueada, para mensajes ("Reels bloqueado"). */
    val sectionName: String,
    /** Nombre de la unidad para "N reels permitidos". */
    val unitPlural: String,
    /** Nombre completo para la interfaz ("Reels de Instagram"). */
    val displayName: String,
) {
    INSTAGRAM_REELS(setOf("com.instagram.android"), "Instagram", "Reels", "reels", "Reels de Instagram"),
    YOUTUBE_SHORTS(setOf("com.google.android.youtube"), "YouTube", "Shorts", "shorts", "Shorts de YouTube"),
    TIKTOK(
        setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        "TikTok", "Videos de TikTok", "tiktoks", "Videos de TikTok",
    ),
    ;

    /** Paquete principal (para íconos y mensajes). */
    val mainPackage: String get() = packages.first()
}

/** Señal por identificador de vista (la parte después de ":id/"). */
data class IdSignal(
    val name: String,
    val weight: Int,
    /** Alto mínimo del nodo respecto de la pantalla (0..1) para descartar miniaturas/estantes. */
    val minHeightFraction: Float = 0f,
    val requireSelected: Boolean = false,
)

enum class LabelMatch { EXACT, CONTAINS }

/** Señal por texto visible o content description (normalizados: minúsculas y sin acentos). */
data class LabelSignal(
    val key: String,
    val labels: List<String>,
    /** Negativo = indica una pantalla que NO es el visor. */
    val weight: Int,
    val match: LabelMatch = LabelMatch.CONTAINS,
    val requireSelected: Boolean = false,
)

/**
 * Señal por tipo de vista, para apps que no exponen ids estables (TikTok): una lista o pager
 * desplazable, vertical y a (casi) pantalla completa es el visor de videos cortos.
 */
data class ClassSignal(
    val key: String,
    val classContains: List<String>,
    val weight: Int,
    val minHeightFraction: Float,
    val minWidthFraction: Float = 0.9f,
)

data class RuleSet(
    val target: ShortFormTarget,
    val idSignals: List<IdSignal>,
    val labelSignals: List<LabelSignal>,
    /** Ids que indican una pantalla que NO es el visor (restan puntos). */
    val negativeIds: List<IdSignal> = emptyList(),
    val classSignals: List<ClassSignal> = emptyList(),
    val threshold: Int = 3,
    /** Tope de puntos que pueden aportar las señales de peso 1 (texto suelto). */
    val weakCap: Int = 2,
    /** Destino seguro al que navegar si "Atrás" no sacó al usuario del visor. */
    val safeNavIds: List<String>,
    val safeNavLabels: List<String>,
) {
    /** Ids del pager a pantalla completa del visor (para contar deslizamientos). */
    val pagerIds: List<String> get() = idSignals.filter { it.minHeightFraction >= 0.6f }.map { it.name }

    /** Alto mínimo para reconocer el pager del visor por su clase al contar deslizamientos. */
    val pagerMinHeight: Float get() = classSignals.minOfOrNull { it.minHeightFraction } ?: 0.85f
}

private val PAGER_CLASSES = listOf("ViewPager", "RecyclerView")

object DetectionRules {

    val instagram = RuleSet(
        target = ShortFormTarget.INSTAGRAM_REELS,
        idSignals = listOf(
            // Visor de Reels (pestaña Reels y reels abiertos desde el feed, perfiles o mensajes).
            IdSignal("clips_viewer_view_pager", weight = 4, minHeightFraction = 0.6f),
            IdSignal("clips_viewer_container", weight = 3, minHeightFraction = 0.6f),
            IdSignal("root_clips_layout", weight = 3, minHeightFraction = 0.6f),
            IdSignal("clips_video_container", weight = 2, minHeightFraction = 0.75f),
            // Controles laterales propios del visor.
            IdSignal("clips_ufi_more_button_component", weight = 1),
            IdSignal("clips_author_username", weight = 1),
            // Pestaña Reels seleccionada en la barra inferior.
            IdSignal("clips_tab", weight = 3, requireSelected = true),
        ),
        labelSignals = listOf(
            // Peso 2: los perfiles también tienen una pestaña "Reels" (cuadrícula, no visor), por lo que
            // esta señal necesita otra que la confirme.
            LabelSignal("tab_reels_selected", listOf("reels"), weight = 2, match = LabelMatch.EXACT, requireSelected = true),
            LabelSignal("audio_original", listOf("audio original", "original audio"), weight = 1),
            LabelSignal("use_audio", listOf("usar audio", "use audio", "usar este audio", "use this audio"), weight = 1),
            LabelSignal("remix", listOf("remix", "remezcla"), weight = 1, match = LabelMatch.EXACT),
        ),
        negativeIds = listOf(
            // Feed principal visible: un reel incrustado en el feed NO es el visor de Reels.
            IdSignal("row_feed_photo_profile_name", weight = -2),
            IdSignal("row_feed_profile_header", weight = -2),
            // Mensajes directos.
            IdSignal("direct_thread_header", weight = -2),
            IdSignal("row_thread_composer_edittext", weight = -2),
        ),
        safeNavIds = listOf("feed_tab"),
        safeNavLabels = listOf("inicio", "home"),
    )

    val youtube = RuleSet(
        target = ShortFormTarget.YOUTUBE_SHORTS,
        idSignals = listOf(
            // Visor de Shorts (pager vertical a pantalla completa).
            IdSignal("reel_recycler", weight = 4, minHeightFraction = 0.6f),
            IdSignal("reel_player_page_container", weight = 3, minHeightFraction = 0.6f),
            IdSignal("reel_watch_player", weight = 3, minHeightFraction = 0.6f),
            IdSignal("reel_player_overlay_v2", weight = 2),
            IdSignal("reel_time_bar", weight = 1),
            IdSignal("reel_progress_bar", weight = 1),
        ),
        labelSignals = listOf(
            // Pestaña "Shorts" de la barra inferior SELECCIONADA (no basta con que exista).
            // Peso 2: los canales también tienen una pestaña "Shorts" (cuadrícula, no visor).
            LabelSignal("tab_shorts_selected", listOf("shorts"), weight = 2, match = LabelMatch.EXACT, requireSelected = true),
            LabelSignal("remix", listOf("remix", "remezcla", "remezclar"), weight = 1, match = LabelMatch.EXACT),
            LabelSignal("use_sound", listOf("usar este sonido", "use this sound"), weight = 1),
        ),
        negativeIds = listOf(
            // Reproductor de vídeos normales.
            IdSignal("watch_player", weight = -2, minHeightFraction = 0.2f),
            IdSignal("player_view", weight = -1),
            IdSignal("watch_list", weight = -2),
        ),
        safeNavIds = emptyList(),
        safeNavLabels = listOf("inicio", "home"),
    )

    /**
     * TikTok: sus ids están ofuscados y cambian entre versiones, así que se reconoce el feed de
     * videos por estructura (pager vertical a casi pantalla completa) más etiquetas.
     * El destino seguro es la Bandeja de entrada (mensajes), sin videos.
     */
    val tiktok = RuleSet(
        target = ShortFormTarget.TIKTOK,
        idSignals = emptyList(),
        classSignals = listOf(
            // La bandeja de entrada y el perfil son listas más bajas (barra superior + inferior).
            ClassSignal("pager_fullscreen", PAGER_CLASSES, weight = 3, minHeightFraction = 0.88f),
        ),
        labelSignals = listOf(
            LabelSignal("tab_home_selected", listOf("home", "inicio", "para ti", "for you"), weight = 2, match = LabelMatch.EXACT, requireSelected = true),
            LabelSignal("like_video", listOf("like video", "me gusta el video", "dar me gusta"), weight = 1),
            LabelSignal("share_video", listOf("share video", "compartir video", "compartir el video"), weight = 1),
            LabelSignal("comments", listOf("read or add comments", "leer o agregar comentarios", "leer o anadir comentarios"), weight = 1),
            // Pantallas sin videos.
            LabelSignal("tab_inbox_selected", listOf("inbox", "bandeja de entrada"), weight = -3, match = LabelMatch.EXACT, requireSelected = true),
            LabelSignal("tab_profile_selected", listOf("profile", "perfil"), weight = -3, match = LabelMatch.EXACT, requireSelected = true),
        ),
        safeNavIds = emptyList(),
        safeNavLabels = listOf("bandeja de entrada", "inbox"),
    )

    val all = listOf(instagram, youtube, tiktok)

    fun forTarget(target: ShortFormTarget): RuleSet = all.first { it.target == target }

    fun forPackage(packageName: String?): RuleSet? =
        packageName?.let { pkg -> all.firstOrNull { pkg in it.target.packages } }
}
