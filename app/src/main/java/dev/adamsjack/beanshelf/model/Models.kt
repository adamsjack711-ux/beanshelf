package dev.adamsjack.beanshelf.model

/** One logged cup — the Untappd-style check-in against a bag. */
data class Brew(
    val id: String,
    val method: String,
    val rating: Float,      // 0f = unrated; otherwise 0.25..5.0 in quarter steps
    val note: String,
    val timestamp: Long,
)

data class Bean(
    val id: String,
    val name: String,
    val roaster: String,
    val origin: String,
    val roastLevel: String, // "" or one of ROAST_LEVELS
    val process: String,    // "" or one of PROCESSES
    val notes: String,
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
