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
            if ("notes" !in fields && lower.count { it == ',' } >= 2 && line.text.length <= 48 &&
                !line.text.any { it.isDigit() }
            ) {
                fields["notes"] = line.text.trimEnd('.', ',')
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

        val producer = fields["producer"]
        val name = candidates.firstOrNull { it.text.contains(' ') }?.text
            ?: candidates.firstOrNull()?.text
            ?: producer
        val roaster = candidates.map { it.text }.firstOrNull { it != name && it.split(" ").size <= 2 }

        val info = LabelInfo(
            roaster = roaster?.let(::tidy),
            name = name?.let(::tidy),
            origin = fields["origin"]?.let(::tidy),
            process = fields["process"]?.let { v ->
                PROCESS_WORDS.entries.firstOrNull { v.lowercase().contains(it.key) }?.value ?: tidy(v.split(" ").first())
            },
            roastLevel = fields["roast"]?.let(::roastFrom),
            notes = fields["notes"]?.let { tidy(it.trimEnd('.')) },
            variety = fields["variety"]?.let(::tidy),
            elevation = fields["elevation"],
            producer = producer?.let(::tidy),
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
