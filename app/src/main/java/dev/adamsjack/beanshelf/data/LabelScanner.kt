package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.BitmapFactory
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
 */
object LabelScanner {

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

    private val ROAST_REGEX = Regex("""\b(light|medium[-\s]?dark|medium|dark)\b(\s+roast)?""", RegexOption.IGNORE_CASE)
    private val NOTES_REGEX = Regex("""^(tasting\s+notes?|notes?\s+of|flavou?r\s+notes?|notes?)[:\s]+(.+)""", RegexOption.IGNORE_CASE)
    private val SKIP_REGEX = Regex(
        """^[\d.,\s]+$|^\d+\s?(g|kg|oz|lb)\b|www\.|@|\.com|net\s?wt|roasted\s+on|best\s+b(y|efore)|batch|lot\s?#""",
        RegexOption.IGNORE_CASE,
    )
    // Words that are label furniture, not names: never promote to name/roaster.
    private val STOPWORDS = setOf(
        "filter", "espresso", "omni", "coffee", "whole bean", "ground", "single origin",
        "specialty coffee", "arabica", "beans", "net weight",
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

    private fun parse(text: Text): LabelInfo {
        data class Line(val text: String, val height: Int, val order: Int)

        val lines = text.textBlocks
            .flatMap { it.lines }
            .mapIndexed { i, l -> Line(l.text.trim(), l.boundingBox?.height() ?: 0, i) }
            .filter { it.text.length >= 2 && !SKIP_REGEX.containsMatchIn(it.text) }

        var origin: String? = null
        var process: String? = null
        var roast: String? = null
        var notes: String? = null
        val consumed = mutableSetOf<Int>()

        lines.forEachIndexed { idx, line ->
            val lower = line.text.lowercase()

            if (process == null) {
                PROCESS_WORDS.entries.firstOrNull { lower.contains(it.key) }?.let {
                    process = it.value
                    consumed += idx
                }
            }
            if (roast == null) {
                ROAST_REGEX.find(lower)?.let { m ->
                    // Only trust roast words when the label says "roast" nearby,
                    // or the line is just the roast word itself.
                    if (m.groupValues[2].isNotBlank() || lower.trim() == m.groupValues[1]) {
                        roast = when (m.groupValues[1].replace(Regex("[-\\s]"), "").lowercase()) {
                            "light" -> "Light"
                            "medium" -> "Medium"
                            "mediumdark" -> "Medium-Dark"
                            "dark" -> "Dark"
                            else -> null
                        }
                        if (roast != null) consumed += idx
                    }
                }
            }
            if (notes == null) {
                NOTES_REGEX.find(line.text)?.let {
                    notes = it.groupValues[2].trim().trimEnd('.')
                    consumed += idx
                }
            }
            if (origin == null) {
                COUNTRIES.firstOrNull { c -> lower.contains(c.lowercase()) }?.let { country ->
                    // "Santa Bárbara," on the line above "Honduras" → join them.
                    val prev = lines.getOrNull(idx - 1)
                    origin = if (line.text.trim().equals(country, ignoreCase = true) &&
                        prev != null && prev.text.endsWith(",") && idx - 1 !in consumed
                    ) {
                        consumed += idx - 1
                        "${prev.text.trimEnd(',').trim()}, $country"
                    } else {
                        tidy(line.text.trim().trimEnd(','))
                    }
                    consumed += idx
                }
            }
        }

        // Untouched prominent lines → name (tallest, prefer multi-word) and roaster.
        val candidates = lines
            .filterIndexed { idx, l -> idx !in consumed && l.text.lowercase() !in STOPWORDS && l.text.length <= 40 }
            .sortedByDescending { it.height }

        val name = (candidates.firstOrNull { it.text.contains(' ') } ?: candidates.firstOrNull())
        val roaster = candidates.firstOrNull {
            it != name && it.text.split(" ").size <= 2
        }

        return LabelInfo(
            roaster = roaster?.text?.let(::tidy),
            name = name?.text?.let(::tidy),
            origin = origin,
            process = process,
            roastLevel = roast,
            notes = notes?.let(::tidy),
        )
    }

    /** ALL-CAPS label text → Title Case; anything else passes through. */
    private fun tidy(s: String): String =
        if (s == s.uppercase() && s.any { it.isLetter() }) {
            s.lowercase().split(" ").joinToString(" ") { w ->
                w.replaceFirstChar { it.uppercase() }
            }
        } else s
}
