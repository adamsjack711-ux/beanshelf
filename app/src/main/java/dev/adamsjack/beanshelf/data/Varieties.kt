package dev.adamsjack.beanshelf.data

/**
 * Coffee variety/cultivar catalog (WCR variety catalog + Ethiopian landrace and
 * JARC selections + names common on specialty labels). SAFE terms may be matched
 * anywhere; AMBIGUOUS terms collide with countries/regions/common words and are
 * only trusted when the label used an explicit "Variety:" keyword (that path
 * accepts any value, so they need no special handling here).
 */
object Varieties {

    val SAFE: Set<String> = setOf(
        // classics
        "typica", "bourbon", "red bourbon", "yellow bourbon", "pink bourbon",
        "orange bourbon", "bourbon sidra", "bourbon pointu", "laurina", "mokka",
        "mocca", "geisha", "gesha", "caturra", "catuai", "catuaí", "red catuai",
        "yellow catuai", "pacas", "pacamara", "maragogipe", "maragogype",
        "maracaturra", "mundo novo", "villa sarchi", "villa sarchí",
        // kenya & east africa
        "sl28", "sl-28", "sl 28", "sl34", "sl-34", "sl 34", "sl14", "sl17",
        "ruiru 11", "ruiru11", "batian", "k7",
        // colombia & central america
        "castillo", "tabi", "cenicafe 1", "cenicafé 1", "chiroso", "papayo",
        "sidra", "typica mejorado", "typica mejorada", "wush wush", "wushwush",
        "catimor", "sarchimor", "marsellesa", "obata", "obatã", "parainema",
        "lempira", "anacafe 14", "anacafé 14", "ih-90", "ih90", "costa rica 95",
        "venecia", "s795", "starmaya", "centroamericano", "milenio", "casiopea",
        "bernardina",
        // ethiopian landraces & JARC selections
        "heirloom", "ethiopian heirloom", "ethiopian landrace", "landrace",
        "74110", "74112", "74148", "74158", "74165", "kurume", "dega", "wolisho",
    )

    // Collide with countries/common words — only meaningful after "Variety:".
    val AMBIGUOUS: Set<String> = setOf("java", "colombia", "kent", "h1", "aji")

    private val SEPARATORS = Regex("[,&+/]|\\band\\b", RegexOption.IGNORE_CASE)

    /**
     * If the line is entirely variety terms (e.g. "PINK BOURBON" or
     * "Caturra & Castillo"), returns the cleaned variety string, else null.
     */
    fun matchLine(text: String): String? {
        val parts = text.split(SEPARATORS)
            .map { it.trim().trimEnd('.').lowercase() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return if (parts.all { it in SAFE }) {
            parts.joinToString(", ") { p ->
                p.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            }
        } else null
    }
}
