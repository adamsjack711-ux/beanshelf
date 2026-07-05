import Foundation

/// Coffee tasting-note lexicon: the full WCR Sensory Lexicon 2016 attribute set
/// (= the SCA Flavor Wheel node set), the Counter Culture Taster's Wheel, and
/// curated real-label descriptors (~300 terms). Used to recognize tasting-note
/// lines (so "Root Beer Float" is notes, not a roaster) and to synthesize notes
/// when no explicit notes line exists. Defect terms and generic words that would
/// false-positive on label copy are deliberately excluded.
enum FlavorWheel {

    // All lowercase; multi-word terms matched with word boundaries.
    static let terms: Set<String> = [
        // fruity — berries
        "fruity", "berry", "blackberry", "raspberry", "blueberry", "strawberry",
        "cranberry", "currant", "red currant", "black currant", "blackcurrant",
        "cassis", "gooseberry", "elderberry", "boysenberry", "marionberry",
        "mulberry", "huckleberry", "berry jam",
        // fruity — cherries & dried fruit
        "cherry", "black cherry", "sour cherry", "tart cherry", "dried cherry",
        "dried fruit", "raisin", "golden raisin", "sultana", "prune", "fig",
        "dried fig", "date", "dried apricot",
        // fruity — orchard & stone
        "apple", "green apple", "red apple", "baked apple", "pear", "asian pear",
        "quince", "peach", "white peach", "nectarine", "apricot", "plum",
        "stone fruit", "grape", "white grape", "red grape", "concord grape",
        "pomegranate", "rhubarb", "persimmon",
        // fruity — tropical
        "coconut", "pineapple", "mango", "papaya", "guava", "guava jam",
        "passion fruit", "passionfruit", "lychee", "star fruit", "tamarind",
        "kiwi", "banana", "jackfruit", "tropical fruit", "tropical", "melon",
        "watermelon", "honeydew", "cantaloupe",
        // fruity — citrus
        "citrus", "lemon", "meyer lemon", "lemonade", "lemon zest", "lime",
        "key lime", "grapefruit", "pomelo", "orange", "blood orange", "mandarin",
        "tangerine", "clementine", "kumquat", "yuzu", "calamansi", "bergamot",
        "candied orange", "orange zest", "marmalade",
        // fruity — general
        "jam", "jammy", "compote", "fruit punch", "red fruit", "dark fruit",
        // floral
        "floral", "rose", "rose hips", "jasmine", "chamomile", "lavender",
        "hibiscus", "magnolia", "honeysuckle", "orange blossom", "elderflower",
        "violet", "lilac", "cherry blossom", "wildflower", "potpourri",
        // sweet & confection
        "honey", "honeycomb", "caramel", "caramelized", "salted caramel", "toffee",
        "butterscotch", "molasses", "maple", "maple syrup", "brown sugar",
        "demerara", "turbinado", "muscovado", "panela", "piloncillo", "sugarcane",
        "sugar cane", "cane sugar", "burnt sugar", "golden syrup", "treacle",
        "vanilla", "vanillin", "marshmallow", "nougat", "marzipan", "praline",
        "fudge", "dulce de leche", "cotton candy", "bubblegum", "agave",
        "creme brulee", "shortcake",
        // nutty & cocoa
        "nutty", "almond", "hazelnut", "peanut", "peanut butter", "cashew",
        "pecan", "walnut", "macadamia", "pistachio", "chestnut", "cocoa",
        "cocoa powder", "cacao", "cacao nib", "cacao nibs", "cocoa nib",
        "cocoa nibs", "chocolate", "dark chocolate", "milk chocolate",
        "white chocolate", "bittersweet chocolate", "bakers chocolate",
        "baking chocolate", "hot chocolate", "brownie",
        // spice
        "baking spice", "brown spice", "cinnamon", "nutmeg", "clove", "allspice",
        "cardamom", "ginger", "candied ginger", "gingerbread", "chai", "anise",
        "star anise", "licorice", "fennel", "coriander", "black pepper",
        "white pepper", "peppercorn", "juniper", "curry", "sarsaparilla",
        "sassafras", "mulling spice",
        // roasted & cereal
        "toast", "malt", "malty", "barley", "grain", "wheat", "rye", "cereal",
        "graham", "graham cracker", "granola", "biscuit", "biscotti",
        "shortbread", "pastry", "pie crust", "brioche", "smoky", "tobacco",
        "pipe tobacco", "sweet tobacco", "cedar",
        // tea & herbal
        "black tea", "green tea", "earl grey", "oolong", "rooibos", "matcha",
        "sencha", "herbal", "tea-like", "lemongrass", "lemon verbena", "mint",
        "peppermint", "sage", "eucalyptus", "tomato leaf", "hops", "cascara",
        // wine & ferment
        "winey", "vinous", "wine", "red wine", "white wine", "mulled wine",
        "sangria", "champagne", "muscat", "merlot", "whiskey", "rum", "brandy",
        "cognac", "sherry", "port wine", "amaretto", "sake", "boozy", "funky",
        "fermented", "kombucha", "apple cider", "cider", "balsamic", "malic",
        "citric",
        // dairy & texture (only the ones printed as notes)
        "creamy", "cream", "sweet cream", "whipped cream", "buttercream",
        "butter", "brown butter", "buttery", "custard", "condensed milk",
        "malted milk", "yogurt", "eggnog", "silky", "velvety", "syrupy", "juicy",
        // other
        "cola", "cherry cola", "cream soda", "root beer", "root beer float",
        "earthy", "forest floor", "mineral", "leather", "pine", "cucumber",
        "olive oil",
    ]

    private static let regexes: [(term: String, regex: NSRegularExpression)] = terms
        .sorted { $0.count > $1.count } // prefer "black cherry" over "cherry"
        .map { term in
            (term, try! NSRegularExpression(
                pattern: "\\b\(NSRegularExpression.escapedPattern(for: term))\\b",
                options: .caseInsensitive
            ))
        }

    /// Distinct flavor terms present in the text, longest-match first, no overlaps.
    static func termsIn(_ text: String) -> [String] {
        var found: [String] = []
        var claimed: [NSRange] = []
        let full = NSRange(text.startIndex..., in: text)
        for (term, regex) in regexes {
            for m in regex.matches(in: text, options: [], range: full) {
                let r = m.range
                if !claimed.contains(where: { NSIntersectionRange($0, r).length > 0 }) {
                    claimed.append(r)
                    if !found.contains(term) { found.append(term) }
                }
            }
        }
        return found
    }

    static func score(_ text: String) -> Int { termsIn(text).count }
}
