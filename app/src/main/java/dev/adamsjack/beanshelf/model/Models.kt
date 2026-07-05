package dev.adamsjack.beanshelf.model

/** One logged cup — the Untappd-style check-in against a bag. */
data class Brew(
    val id: String,
    val method: String,
    val rating: Float,      // 0f = unrated; otherwise 0.25..5.0 in quarter steps
    val note: String,       // how the cup tasted
    val timestamp: Long,
    val doseG: Float?,      // coffee in, grams
    val waterG: Float?,     // water in / yield out, grams
    val grinder: String,    // equipment: grinder name
    val grindSize: String,  // equipment: grind setting ("24 clicks", "3.5")
) {
    /** "1:16.7" derived from dose and water, or null. */
    val ratio: String?
        get() {
            val d = doseG ?: return null
            val w = waterG ?: return null
            if (d <= 0f || w <= 0f) return null
            val r = w / d
            return "1:" + if (r % 1f < 0.05f || r % 1f > 0.95f) "%.0f".format(r) else "%.1f".format(r)
        }
}

/** "18" or "18.5" — grams without trailing zeros. */
fun formatGrams(g: Float): String =
    if (g % 1f == 0f) "%.0f".format(g) else "%.1f".format(g)

data class Bean(
    val id: String,
    val name: String,
    val roaster: String,
    val origin: String,
    val roastLevel: String, // "" or one of ROAST_LEVELS
    val process: String,    // "" or one of PROCESSES
    val notes: String,
    val variety: String,    // e.g. "Pacas", "Heirloom"
    val elevation: String,  // e.g. "1,650 masl"
    val producer: String,   // farmer / farm / station
    val roastedOn: String,  // roast date as printed ("May 26", "27-05-2026")
    val rating: Float,      // 0f = unrated
    val photoPath: String?, // absolute path inside filesDir/photos (front of bag)
    val backPhotoPath: String?, // optional back-of-bag photo, also scanned for info
    val createdAt: Long,
    val brews: List<Brew>,
)

val ROAST_LEVELS = listOf("Light", "Medium", "Medium-Dark", "Dark")
val PROCESSES = listOf("Washed", "Natural", "Honey", "Anaerobic", "Other")
val BREW_METHODS = listOf("V60", "Espresso", "AeroPress", "French Press", "Moka", "Cold Brew", "Drip", "Other")

/** "4.0" / "4.25" — quarter-step display like Untappd. */
fun formatRating(r: Float): String =
    if (r % 1f == 0f) "%.1f".format(r) else "%.2f".format(r)
