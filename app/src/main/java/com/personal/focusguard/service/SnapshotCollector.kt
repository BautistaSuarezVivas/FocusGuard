package com.personal.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.focusguard.detection.NodeInfo
import com.personal.focusguard.detection.RuleSet
import com.personal.focusguard.detection.ShortFormDetector

/**
 * Extrae del árbol de accesibilidad SOLO los nodos relevantes para las reglas, usando búsquedas
 * por id y por texto que resuelve la propia app (eficientes y sin recorrer todo el árbol).
 * Los textos se recortan y se descartan al terminar la evaluación.
 */
class SnapshotCollector(private val service: AccessibilityService) {

    private val screenHeight: Float get() = service.resources.displayMetrics.heightPixels.coerceAtLeast(1).toFloat()
    private val screenWidth: Float get() = service.resources.displayMetrics.widthPixels.coerceAtLeast(1).toFloat()

    /** @param pkg paquete real de la ventana (TikTok tiene varios paquetes posibles). */
    fun collect(root: AccessibilityNodeInfo, pkg: String, rules: RuleSet, extraIds: List<String>): List<NodeInfo> {
        val result = ArrayList<NodeInfo>()

        val ids = (rules.idSignals.map { it.name } + rules.negativeIds.map { it.name } + extraIds).distinct()
        for (name in ids) {
            val found = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$name") }.getOrNull().orEmpty()
            found.forEach { node ->
                result += toInfo(node, checkParentSelected = false)
                node.recycleCompat()
            }
        }

        if (rules.classSignals.isNotEmpty()) result += scrollableLists(root)

        val labels = rules.labelSignals.flatMap { it.labels }.distinct()
        for (label in labels) {
            val found = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull().orEmpty()
            found.take(MAX_PER_LABEL).forEach { node ->
                result += toInfo(node, checkParentSelected = true)
                node.recycleCompat()
            }
        }
        return result
    }

    /**
     * Listas/pagers desplazables (recorrido acotado del árbol). Solo para apps sin ids estables
     * (TikTok), donde el visor se reconoce por estructura.
     */
    private fun scrollableLists(root: AccessibilityNodeInfo, limit: Int = 400): List<NodeInfo> {
        val found = ArrayList<NodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val node = queue.removeFirst()
            visited++
            val cls = node.className?.toString().orEmpty()
            if (node.isScrollable && PAGER_CLASSES.any { cls.contains(it) }) found += toInfo(node, checkParentSelected = false)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            if (node !== root) node.recycleCompat()
        }
        queue.forEach { if (it !== root) it.recycleCompat() }
        return found
    }

    /** Lista de ids de vista visibles (solo nombres, sin textos) para la pantalla de diagnóstico. */
    fun observedIds(root: AccessibilityNodeInfo, limit: Int = 1500): List<String> {
        val ids = sortedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) ShortFormDetector.shortId(node.viewIdResourceName)?.let { ids += it }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            if (node !== root) node.recycleCompat()
        }
        queue.forEach { if (it !== root) it.recycleCompat() }
        return ids.toList()
    }

    /**
     * ¿El nodo que se desplazó es el pager del visor? Por id conocido o, si no hay id conocido,
     * por clase de lista/pager que ocupa casi toda la pantalla (excluye p. ej. comentarios).
     */
    fun isViewerPager(node: AccessibilityNodeInfo, pagerIds: List<String>, className: CharSequence?, minHeight: Float): Boolean {
        val id = ShortFormDetector.shortId(node.viewIdResourceName)
        if (id != null && id in pagerIds) return true
        val cls = (className ?: node.className)?.toString().orEmpty()
        if (PAGER_CLASSES.none { cls.contains(it) }) return false
        val info = toInfo(node, checkParentSelected = false)
        return info.vertical != false && info.heightFraction >= minHeight
    }

    /** Busca un destino seguro (p. ej. pestaña Inicio) y lo pulsa con ACTION_CLICK. */
    fun clickSafeDestination(root: AccessibilityNodeInfo, pkg: String, rules: RuleSet): Boolean {
        for (name in rules.safeNavIds) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$name") }.getOrNull().orEmpty()
            val target = nodes.firstOrNull { it.isVisibleToUser }
            val clicked = target?.let { clickNodeOrAncestor(it) } ?: false
            nodes.forEach { it.recycleCompat() }
            if (clicked) return true
        }
        for (label in rules.safeNavLabels) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull().orEmpty()
            val target = nodes.firstOrNull { n ->
                val info = toInfo(n, checkParentSelected = false)
                n.isVisibleToUser && info.heightFraction < 0.15f &&
                    (ShortFormDetector.normalize(info.description) == label || ShortFormDetector.normalize(info.text) == label)
            }
            val clicked = target?.let { clickNodeOrAncestor(it) } ?: false
            nodes.forEach { it.recycleCompat() }
            if (clicked) return true
        }
        return false
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun toInfo(node: AccessibilityNodeInfo, checkParentSelected: Boolean): NodeInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val parentSelected = checkParentSelected && !node.isSelected &&
            (node.parent?.let { p -> p.isSelected.also { p.recycleCompat() } } ?: false)
        return NodeInfo(
            viewId = ShortFormDetector.shortId(node.viewIdResourceName),
            text = node.text?.toString()?.take(MAX_TEXT),
            description = node.contentDescription?.toString()?.take(MAX_TEXT),
            selected = node.isSelected || parentSelected,
            visible = node.isVisibleToUser,
            heightFraction = bounds.height() / screenHeight,
            widthFraction = bounds.width() / screenWidth,
            className = node.className?.toString(),
            scrollable = node.isScrollable,
            vertical = node.collectionInfo?.let { ci ->
                when {
                    ci.rowCount > 1 && ci.columnCount <= 1 -> true
                    ci.columnCount > 1 && ci.rowCount <= 1 -> false
                    else -> null
                }
            },
        )
    }

    companion object {
        private val PAGER_CLASSES = listOf("ViewPager", "RecyclerView")
        private const val MAX_TEXT = 80
        private const val MAX_PER_LABEL = 20
    }
}

@Suppress("DEPRECATION")
fun AccessibilityNodeInfo.recycleCompat() {
    // A partir de Android 13 recycle() no hace nada; antes libera el objeto del pool.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) runCatching { recycle() }
}
