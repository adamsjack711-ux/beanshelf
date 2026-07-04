package dev.adamsjack.beanshelf.data

/**
 * Coffee tasting-note lexicon, based on the SCA Coffee Taster's Flavor Wheel /
 * WCR Sensory Lexicon vocabulary plus common specialty-label descriptors.
 * Used to recognize tasting-note lines (so "Root Beer Float" is notes, not a
 * roaster) and to synthesize notes when no explicit notes line exists.
 */
object FlavorWheel {

    // All lowercase; multi-word terms matched with word boundaries.
    val TERMS: Set<String> = setOf(
        // fruity — berries & dried fruit
        "berry", "blackberry", "raspberry", "blueberry", "strawberry", "cranberry",
        "currant", "blackcurrant", "red currant", "raisin", "prune", "date", "fig",
        "cherry", "black cherry", "sour cherry", "pomegranate", "jammy", "dried fruit",
        // fruity — citrus, stone & tropical
        "citrus", "orange", "blood orange", "tangerine", "mandarin", "grapefruit",
        "lemon", "lime", "lemongrass", "stone fruit", "peach", "pear", "apricot",
        "plum", "nectarine", "apple", "red apple", "green apple", "grape",
        "concord grape", "tropical", "tropical fruit", "pineapple", "mango", "papaya",
        "lychee", "melon", "watermelon", "cantaloupe", "kiwi", "banana", "coconut",
        "guava", "passionfruit", "passion fruit",
        // floral
        "floral", "jasmine", "rose", "hibiscus", "lavender", "chamomile",
        "elderflower", "honeysuckle", "orange blossom", "magnolia", "bergamot",
        // sweet & confection
        "honey", "caramel", "toffee", "butterscotch", "molasses", "maple", "maple syrup",
        "brown sugar", "vanilla", "marzipan", "nougat", "praline", "fudge",
        "marshmallow", "cotton candy", "shortcake", "custard", "cream", "creamy",
        "buttery", "syrupy", "juicy",
        // nutty & cocoa
        "nutty", "almond", "hazelnut", "peanut", "walnut", "pecan", "cashew",
        "chocolate", "dark chocolate", "milk chocolate", "cocoa", "cacao", "cocoa nibs",
        // spice
        "cinnamon", "clove", "nutmeg", "anise", "star anise", "cardamom", "ginger",
        "black pepper", "baking spice", "brown spice",
        // tea, wine & ferment
        "black tea", "green tea", "earl grey", "rooibos", "sencha", "oolong", "matcha",
        "winey", "red wine", "merlot", "champagne", "sake", "whiskey", "rum", "boozy",
        "fermented",
        // roasted & other
        "malt", "malty", "cereal", "graham", "graham cracker", "biscuit", "toast",
        "tobacco", "cedar", "sandalwood", "smoky", "cola", "root beer", "licorice",
        "mint", "eucalyptus", "apple cider", "cider",
    )

    private val regexes: List<Pair<String, Regex>> = TERMS
        .sortedByDescending { it.length } // prefer "black cherry" over "cherry"
        .map { it to Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    /** Distinct flavor terms present in the text, longest-match first, no overlaps. */
    fun termsIn(text: String): List<String> {
        val found = mutableListOf<String>()
        val claimed = mutableListOf<IntRange>()
        for ((term, rx) in regexes) {
            for (m in rx.findAll(text)) {
                if (claimed.none { it.first <= m.range.last && m.range.first <= it.last }) {
                    claimed += m.range
                    found += term
                }
            }
        }
        return found.distinct()
    }

    fun score(text: String): Int = termsIn(text).size
}
