package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device OCR over a bag photo (ML Kit, bundled model — fully offline), parsed
 * with coffee-label heuristics into pre-fillable fields. Best-effort by design:
 * results only ever fill fields the user has left blank.
 *
 * Tuned against real scans (see logcat tag "LabelScanner"):
 *  - back labels are structured "Key: Value" lines → parsed by key, not swallowed
 *  - altitude/variety/date furniture never becomes a name or roaster
 *  - low-confidence lines (garbled logo OCR) can't become a name or roaster
 *  - letter-spaced label text (W A S H E D) matches keywords via de-spacing
 */
object LabelScanner {

    private const val TAG = "LabelScanner"

    data class LabelInfo(
        val roaster: String? = null,
        val name: String? = null,
        val origin: String? = null,
        val process: String? = null,
        val roastLevel: String? = null,
        val notes: String? = null,
    ) {
        fun isEmpty() = listOf(roaster, name, origin, process, roastLevel, notes).all { it == null }
    }

    private val COUNTRIES = listOf(
        "Ethiopia", "Kenya", "Colombia", "Brazil", "Guatemala", "Honduras", "El Salvador",
        "Costa Rica", "Panama", "Peru", "Bolivia", "Mexico", "Nicaragua", "Rwanda", "Burundi",
        "Uganda", "Tanzania", "Yemen", "Indonesia", "Sumatra", "Java", "Sulawesi", "India",
        "Vietnam", "Ecuador", "Papua New Guinea", "Myanmar", "Thailand", "Timor", "Congo",
        "Malawi", "Zambia", "Jamaica", "Hawaii",
    )

    private val PROCESS_WORDS = mapOf(
        "washed" to "Washed", "natural" to "Natural", "honey" to "Honey",
        "anaerobic" to "Anaerobic", "carbonic" to "Anaerobic",
    )

    // Structured back-label lines: "Origin: Colombia", "Masl: 1700", "Notes: plum, cola"…
    private val KEYVAL_REGEX = Regex(
        """^(origin|region|producer|farm|variet(?:y|al|ies)|process|tasting\s+notes?|flavou?r\s+notes?|notes?|elevation|altitude|masl|roast(?:ed)?(?:\s+(?:on|date|level))?|lot|harvest|importer|weight)\s*[:\-–]\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    private val ROAST_REGEX = Regex("""\b(light|medium[-\s]?dark|medium|dark)\b(\s+roast)?""", RegexOption.IGNORE_CASE)
    private val SKIP_REGEX = Regex(
        """^[\d.,\s]+$|\b\d{3,4}\s?-\s?\d{3,4}\s?m\b|\b\d{3,4}\s?masl\b|^\d+\s?(g|kg|oz|lb)\b|www\.|@|\.com|net\s?wt|roasted\s+on|best\s+b(y|efore)|batch|lot\s?#""",
        RegexOption.IGNORE_CASE,
    )
    // Label furniture, never a name/roaster. Compared against the DE-SPACED lowercase
    // line so letter-spaced small caps ("F I L T E R") match too.
    private val STOPWORDS = setOf(
        "filter", "espresso", "omni", "coffee", "wholebean", "wholebeans", "ground",
        "singleorigin", "specialtycoffee", "arabica", "beans", "netweight", "decaf",
        "heirloom", "heirloomvarieties", "microlot", "filterroast", "espressoroast",
        "lightroast", "mediumroast", "darkroast", "coffeebeans",
    )

    suspend fun scan(context: Context, path: String): LabelInfo? {
        val bmp = BitmapFactory.decodeFile(path) ?: return null
        val result = suspendCancellableCoroutine<Text?> { cont ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } ?: return null
        return parse(result).takeIf { !it.isEmpty() }
    }

    private data class Line(val text: String, val height: Int, val confidence: Float)

    private fun parse(text: Text): LabelInfo {
        val lines = text.textBlocks
            .flatMap { it.lines }
            .map { l ->
                val c = runCatching { l.confidence }.getOrDefault(1f)
                Line(l.text.trim(), l.boundingBox?.height() ?: 0, if (c <= 0f) 1f else c)
            }
            .filter { it.text.length >= 2 && !SKIP_REGEX.containsMatchIn(it.text) }

        lines.forEach { Log.d(TAG, "line h=${it.height} conf=%.2f '${it.text}'".format(it.confidence)) }

        var origin: String? = null
        var process: String? = null
        var roast: String? = null
        var notes: String? = null
        var producer: String? = null
        val consumed = mutableSetOf<Int>()

        // Pass 1 — structured "Key: Value" lines (typical bag backs).
        lines.forEachIndexed { idx, line ->
            val m = KEYVAL_REGEX.find(line.text) ?: return@forEachIndexed
            consumed += idx
            val key = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
            val value = cleanValue(m.groupValues[2])
            if (value.isBlank()) return@forEachIndexed
            when {
                key.startsWith("origin") || key.startsWith("region") ->
                    if (origin == null) origin = value
                key.startsWith("producer") || key.startsWith("farm") ->
                    if (producer == null) producer = value
                key.contains("note") || key.contains("flavo") ->
                    if (notes == null) notes = value.trimEnd('.')
                key.startsWith("process") ->
                    if (process == null) process = PROCESS_WORDS.entries
                        .firstOrNull { value.lowercase().contains(it.key) }?.value ?: tidy(value.split(" ").first())
                key.startsWith("roast") ->
                    if (roast == null) roast = roastFrom(value)
                // variety / elevation / masl / lot / harvest / importer / weight: consumed, unused
            }
        }

        // Pass 2 — free-form keyword sweep over unconsumed lines.
        lines.forEachIndexed { idx, line ->
            if (idx in consumed) return@forEachIndexed
            val lower = line.text.lowercase()
            val squished = lower.replace(Regex("[\\s.]+"), "")

            if (process == null) {
                PROCESS_WORDS.entries.firstOrNull { squished.contains(it.key) }?.let {
                    process = it.value
                    consumed += idx
                }
            }
            if (roast == null) {
                roastFrom(line.text)?.let { r ->
                    // Only trust bare roast words when "roast" appears nearby.
                    if (lower.contains("roast") || squished in setOf("light", "medium", "mediumdark", "dark")) {
                        roast = r
                        consumed += idx
                    }
                }
            }
            if (origin == null) {
                COUNTRIES.firstOrNull { c -> lower.contains(c.lowercase()) }?.let { country ->
                    val prev = lines.getOrNull(idx - 1)
                    origin = if (line.text.trim().equals(country, ignoreCase = true) &&
                        prev != null && prev.text.endsWith(",") && idx - 1 !in consumed
                    ) {
                        consumed += idx - 1
                        "${prev.text.trimEnd(',').trim()}, $country"
                    } else {
                        // Long line → keep only the segment that holds the country.
                        val segment = line.text.split('|', '·', '•', ';')
                            .firstOrNull { it.contains(country, ignoreCase = true) } ?: line.text
                        cleanValue(segment).trimEnd(',')
                    }
                    consumed += idx
                }
            }
            if (notes == null && lower.count { it == ',' } >= 2 && line.text.length <= 48 &&
                !line.text.any { it.isDigit() }
            ) {
                notes = tidy(line.text.trimEnd('.', ','))
                consumed += idx
            }
        }

        // Pass 3 — name & roaster from what's left. Junk guards: no key-value colons,
        // no digits, real words (3+ letters), confident OCR (garbled logos read low).
        val candidates = lines
            .filterIndexed { idx, l ->
                idx !in consumed &&
                    !l.text.contains(':') &&
                    !l.text.any { it.isDigit() } &&
                    l.text.count { it.isLetter() } >= 3 &&
                    l.text.length <= 40 &&
                    l.confidence >= 0.6f &&
                    l.text.lowercase().replace(Regex("[\\s.]+"), "") !in STOPWORDS
            }
            .sortedByDescending { it.height }

        val name = candidates.firstOrNull { it.text.contains(' ') }
            ?: candidates.firstOrNull()
            ?: producer?.let { Line(it, 0, 1f) }
        val roaster = candidates.firstOrNull { it != name && it.text.split(" ").size <= 2 }

        val info = LabelInfo(
            roaster = roaster?.text?.let(::tidy),
            name = name?.text?.let(::tidy),
            origin = origin,
            process = process,
            roastLevel = roast,
            notes = notes?.let(::tidy),
        )
        Log.d(TAG, "parsed: $info (producer=$producer)")
        return info
    }

    /** Cuts a value at embedded furniture ("… |Elevation: 1850m Producer: …"). */
    private fun cleanValue(raw: String): String {
        var v = raw.split('|', '·', '•', ';').first().trim()
        val cutAt = Regex("""\b(elevation|altitude|masl|producer|variet|process|harvest|lot)\b""", RegexOption.IGNORE_CASE)
            .find(v)?.range?.first
        if (cutAt != null && cutAt > 0) v = v.substring(0, cutAt).trim()
        return tidy(v.trim().trimEnd(',', '-', '–', ':'))
    }

    private fun roastFrom(s: String): String? =
        ROAST_REGEX.find(s.lowercase())?.let { m ->
            when (m.groupValues[1].replace(Regex("[-\\s]"), "")) {
                "light" -> "Light"
                "medium" -> "Medium"
                "mediumdark" -> "Medium-Dark"
                "dark" -> "Dark"
                else -> null
            }
        }

    /** ALL-CAPS label text → Title Case; anything else passes through. */
    private fun tidy(s: String): String =
        if (s == s.uppercase() && s.any { it.isLetter() }) {
            s.lowercase().split(" ").joinToString(" ") { w ->
                w.replaceFirstChar { it.uppercase() }
            }
        } else s
}
