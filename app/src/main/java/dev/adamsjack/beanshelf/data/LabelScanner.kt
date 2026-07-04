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
 * with a coffee-label keyword vocabulary into pre-fillable fields. Best-effort by
 * design: results only ever fill fields the user has left blank.
 *
 * Keyword-driven: KEY_SYNONYMS maps every label spelling ("Region:", "Altitude",
 * "Finca", "We taste"…) to a field. Handles inline "Key: Value" AND the two-line
 * layout where the keyword sits alone above its value. Logcat tag "LabelScanner"
 * shows every line seen + the final parse for tuning.
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
        val variety: String? = null,
        val elevation: String? = null,
        val producer: String? = null,
        /** Field keys the parser guessed at rather than derived from a keyword — the UI asks the user to confirm these. */
        val unsure: Set<String> = emptySet(),
    ) {
        fun isEmpty() = listOf(
            roaster, name, origin, process, roastLevel, notes, variety, elevation, producer,
        ).all { it == null }
    }

    // ── Keyword vocabulary ────────────────────────────────────────────────
    // regex fragment (case-insensitive) → target field. "skip" = consume the
    // line so its text can never leak into name/roaster, but keep no value.
    private val KEY_SYNONYMS = listOf(
        "origin|region|country|grown\\s+in" to "origin",
        "producer|farmer|farm|finca|estate|cooperative|co-?op|washing\\s+station|station" to "producer",
        "variet(?:y|al|ies)|cultivar" to "variety",
        "elevation|altitude|masl|m\\.a\\.s\\.l\\.?" to "elevation",
        "process(?:ing)?|fermentation" to "process",
        "(?:tasting|flavou?r|cupping)\\s+notes?|notes?\\s+of|we\\s+taste|tastes?\\s+like|notes?" to "notes",
        "roast\\s+(?:level|profile)|roasted\\s+for|roast" to "roast",
        "harvest|crop|lot|batch|importer|net\\s+weight|weight|roasted\\s+(?:on|in)|roast\\s+date|best\\s+(?:by|before)|brew\\s+(?:ratio|guide)|dose|www|instagram" to "skip",
    )

    private val KEYVAL_REGEX = Regex(
        "^(" + KEY_SYNONYMS.joinToString("|") { "(?:${it.first})" } + ")\\b\\s*[:\\-–]?\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )

    private fun fieldForKey(key: String): String {
        val k = key.lowercase().trim()
        return KEY_SYNONYMS.firstOrNull { Regex("^(?:${it.first})$", RegexOption.IGNORE_CASE).matches(k) }
            ?.second ?: "skip"
    }
    // ─────────────────────────────────────────────────────────────────────

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

    private val ROAST_REGEX = Regex("""\b(light|medium[-\s]?dark|medium|dark)\b(\s+roast)?""", RegexOption.IGNORE_CASE)
    // Standalone altitude line, e.g. "1850 masl" / "1700–1900 m".
    private val ELEVATION_REGEX = Regex("""^~?\d{3,4}(?:\s?[-–]\s?\d{3,4})?\s?(?:m|masl|m\.a\.s\.l\.?)$""", RegexOption.IGNORE_CASE)
    private val SKIP_REGEX = Regex(
        """^[\d.,\s]+$|^\d+\s?(g|kg|oz|lb)\b|www\.|@|\.com|net\s?wt""",
        RegexOption.IGNORE_CASE,
    )
    // Label furniture, never a name/roaster. Compared against the DE-SPACED lowercase line.
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

        val fields = mutableMapOf<String, String>()
        val consumed = mutableSetOf<Int>()

        fun putField(field: String, rawValue: String) {
            if (field == "skip") return
            val value = cleanValue(rawValue)
            if (value.isNotBlank() && field !in fields) fields[field] = value
        }

        // Pass 1 — keyword lines: inline "Key: Value" or keyword-only + next-line value.
        lines.forEachIndexed { idx, line ->
            if (idx in consumed) return@forEachIndexed
            val m = KEYVAL_REGEX.find(line.text) ?: return@forEachIndexed
            val field = fieldForKey(m.groupValues[1])
            var value = m.groupValues[2].trim()
            consumed += idx
            if (value.isBlank()) {
                // "REGION" alone → value lives on the next line (unless that's a keyword too).
                val next = lines.getOrNull(idx + 1)
                if (next != null && idx + 1 !in consumed && !KEYVAL_REGEX.containsMatchIn(next.text)) {
                    value = next.text.trim()
                    consumed += idx + 1
                }
            }
            putField(field, value)
        }

        // Pass 2 — free-form sweep over unconsumed lines.
        lines.forEachIndexed { idx, line ->
            if (idx in consumed) return@forEachIndexed
            val lower = line.text.lowercase()
            val squished = lower.replace(Regex("[\\s.]+"), "")

            if ("process" !in fields) {
                PROCESS_WORDS.keys.firstOrNull { squished.contains(it) }?.let {
                    fields["process"] = it
                    consumed += idx
                }
            }
            if ("elevation" !in fields && ELEVATION_REGEX.matches(line.text)) {
                fields["elevation"] = line.text
                consumed += idx
            }
            if ("roast" !in fields) {
                ROAST_REGEX.find(lower)?.let { m ->
                    if (lower.contains("roast") || squished in setOf("light", "medium", "mediumdark", "dark")) {
                        fields["roast"] = m.groupValues[1]
                        consumed += idx
                    }
                }
            }
            if ("origin" !in fields) {
                COUNTRIES.firstOrNull { c -> lower.contains(c.lowercase()) }?.let { country ->
                    val prev = lines.getOrNull(idx - 1)
                    fields["origin"] = if (line.text.trim().equals(country, ignoreCase = true) &&
                        prev != null && prev.text.endsWith(",") && idx - 1 !in consumed
                    ) {
                        consumed += idx - 1
                        "${prev.text.trimEnd(',').trim()}, $country"
                    } else {
                        val segment = line.text.split('|', '·', '•', ';')
                            .firstOrNull { it.contains(country, ignoreCase = true) } ?: line.text
                        cleanValue(segment).trimEnd(',')
                    }
                    consumed += idx
                }
            }
            if ("notes" !in fields && line.text.length <= 60 && !line.text.any { it.isDigit() }) {
                // Flavor-wheel detection: 2+ known descriptors, or 1 + list punctuation.
                val flavorScore = FlavorWheel.score(line.text)
                if (flavorScore >= 2 || (flavorScore >= 1 && lower.count { it == ',' } >= 2)) {
                    var note = line.text
                    consumed += idx
                    // Notes often wrap: absorb following lines that are also flavor-heavy.
                    var j = idx + 1
                    while (j < lines.size && j !in consumed &&
                        lines[j].text.length <= 60 && FlavorWheel.score(lines[j].text) >= 1 &&
                        !KEYVAL_REGEX.containsMatchIn(lines[j].text)
                    ) {
                        note += ", " + lines[j].text.trim().trimStart('&', '+').trim()
                        consumed += j
                        j++
                    }
                    fields["notes"] = note
                }
            }
        }

        // No explicit notes line → synthesize from flavor terms scattered on the label.
        var notesSynthesized = false
        if ("notes" !in fields) {
            val terms = lines.flatMap { FlavorWheel.termsIn(it.text) }.distinct()
            if (terms.size >= 2) {
                fields["notes"] = terms.joinToString(", ")
                notesSynthesized = true
            }
        }

        // Pass 3 — name & roaster from what's left. Junk guards: no key-value colons,
        // no digits, real words (3+ letters), confident OCR (garbled logos read low),
        // and flavor-heavy lines (those are tasting notes, not names).
        val candidates = lines
            .filterIndexed { idx, l ->
                val flavor = FlavorWheel.score(l.text)
                idx !in consumed &&
                    !l.text.contains(':') &&
                    !l.text.any { it.isDigit() } &&
                    l.text.count { it.isLetter() } >= 3 &&
                    l.text.length <= 40 &&
                    l.confidence >= 0.6f &&
                    flavor < 2 &&
                    !(flavor >= 1 && l.text.trimEnd().endsWith(",")) &&
                    l.text.lowercase().replace(Regex("[\\s.]+"), "") !in STOPWORDS
            }
            .sortedByDescending { it.height }

        var producer = fields["producer"]
        val name = candidates.firstOrNull { it.text.contains(' ') }?.text
            ?: candidates.firstOrNull()?.text
            ?: producer
        val roaster = candidates.map { it.text }.firstOrNull { it != name && it.split(" ").size <= 2 }

        // Cross-field sanity: a "producer" that echoes the origin is a mis-keyed region.
        val originVal = fields["origin"]
        if (producer != null && originVal != null) {
            val pWords = producer.lowercase().split(Regex("[,\\s]+")).filter { it.length > 2 }.toSet()
            val oWords = originVal.lowercase().split(Regex("[,\\s]+")).filter { it.length > 2 }.toSet()
            if (pWords.isNotEmpty() && (pWords intersect oWords).size * 2 >= pWords.size) producer = null
        }

        val unsure = buildSet {
            if (name != null) add("name")
            if (roaster != null) add("roaster")
            if (notesSynthesized) add("notes")
        }

        val info = LabelInfo(
            roaster = roaster?.let(::tidy),
            name = name?.let(::tidy),
            origin = originVal?.let(::tidy),
            process = fields["process"]?.let { v ->
                PROCESS_WORDS.entries.firstOrNull { v.lowercase().contains(it.key) }?.value ?: tidy(v.split(" ").first())
            },
            roastLevel = fields["roast"]?.let(::roastFrom),
            notes = fields["notes"]?.let { tidy(it.trimEnd('.', ',')) },
            variety = fields["variety"]?.let(::tidy),
            elevation = fields["elevation"],
            producer = producer?.let(::tidy),
            unsure = unsure,
        )
        Log.d(TAG, "parsed: $info")
        return info
    }

    /** Cuts a value at embedded furniture ("… |Elevation: 1850m Producer: …"). */
    private fun cleanValue(raw: String): String {
        var v = raw.split('|', '·', '•', ';').first().trim()
        val cutAt = Regex("""\b(elevation|altitude|masl|producer|variet|process|harvest|lot)\b""", RegexOption.IGNORE_CASE)
            .find(v)?.range?.first
        if (cutAt != null && cutAt > 0) v = v.substring(0, cutAt).trim()
        return v.trim().trimEnd(',', '-', '–', ':')
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
