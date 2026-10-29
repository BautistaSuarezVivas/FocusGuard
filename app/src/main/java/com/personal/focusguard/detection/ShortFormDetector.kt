package com.personal.focusguard.detection

import java.text.Normalizer
import java.util.Locale

/**
 * Vista mínima de un nodo de accesibilidad. Los textos solo viven en memoria durante la
 * evaluación; nunca se guardan.
 */
data class NodeInfo(
    /** Nombre de id sin el prefijo "paquete:id/". */
    val viewId: String? = null,
    val text: String? = null,
    val description: String? = null,
    val selected: Boolean = false,
    val visible: Boolean = true,
    val heightFraction: Float = 0f,
    val widthFraction: Float = 0f,
    val className: String? = null,
    val scrollable: Boolean = false,
    /** true/false si la lista informa su orientación (CollectionInfo); null si no se sabe. */
    val vertical: Boolean? = null,
)

data class DetectionResult(
    val target: ShortFormTarget,
    val score: Int,
    val matchedSignals: List<String>,
    val detected: Boolean,
)

object ShortFormDetector {

    fun normalize(s: CharSequence?): String? {
        if (s.isNullOrBlank()) return null
        val stripped = Normalizer.normalize(s.toString(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    }

    /** Nombre de id sin prefijo de paquete. */
    fun shortId(fullId: String?): String? = fullId?.substringAfter(":id/", fullId)

    /**
     * Evalúa la pantalla. [extraIds] son ids adicionales definidos por el usuario (peso 3,
     * nodo visible y de al menos 50 % del alto de pantalla).
     */
    fun evaluate(rules: RuleSet, nodes: List<NodeInfo>, extraIds: List<String> = emptyList()): DetectionResult {
        val visible = nodes.filter { it.visible }
        val matched = mutableListOf<String>()
        var strong = 0
        var weak = 0
        var negative = 0

        fun add(weight: Int, label: String) {
            when {
                weight < 0 -> negative += weight
                weight <= 1 -> weak += weight
                else -> strong += weight
            }
            matched += "$label($weight)"
        }

        val idSignals = rules.idSignals + extraIds.filter { it.isNotBlank() }
            .map { IdSignal(it.trim(), weight = 3, minHeightFraction = 0.5f) }

        for (sig in idSignals) {
            val hit = visible.any { n ->
                n.viewId == sig.name &&
                    n.heightFraction >= sig.minHeightFraction &&
                    (!sig.requireSelected || n.selected)
            }
            if (hit) add(sig.weight, "id:${sig.name}")
        }

        for (sig in rules.classSignals) {
            val hit = visible.any { n ->
                val cls = n.className ?: return@any false
                sig.classContains.any { cls.contains(it) } &&
                    n.scrollable && n.vertical != false &&
                    n.heightFraction >= sig.minHeightFraction && n.widthFraction >= sig.minWidthFraction
            }
            if (hit) add(sig.weight, "class:${sig.key}")
        }

        for (sig in rules.labelSignals) {
            val hit = visible.any { n ->
                (!sig.requireSelected || n.selected) &&
                    (labelMatches(sig, normalize(n.text)) || labelMatches(sig, normalize(n.description)))
            }
            if (hit) add(sig.weight, "label:${sig.key}")
        }

        for (sig in rules.negativeIds) {
            val hit = visible.any { n -> n.viewId == sig.name && n.heightFraction >= sig.minHeightFraction }
            if (hit) add(sig.weight, "neg:${sig.name}")
        }

        // Exigir al menos una señal estructural (id, clase o pestaña seleccionada): el texto
        // suelto nunca alcanza para bloquear.
        val score = strong + minOf(weak, rules.weakCap) + negative
        val detected = strong > 0 && score >= rules.threshold
        return DetectionResult(rules.target, score, matched, detected)
    }

    private fun labelMatches(sig: LabelSignal, value: String?): Boolean {
        if (value == null) return false
        return sig.labels.any { label ->
            when (sig.match) {
                LabelMatch.EXACT -> value == label
                LabelMatch.CONTAINS -> value.contains(label)
            }
        }
    }
}
