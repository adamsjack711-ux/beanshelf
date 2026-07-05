import Foundation

/// Coffee variety/cultivar catalog (WCR variety catalog + Ethiopian landrace and
/// JARC selections + names common on specialty labels). SAFE terms may be matched
/// anywhere; AMBIGUOUS terms collide with countries/regions/common words and are
/// only trusted when the label used an explicit "Variety:" keyword (that path
/// accepts any value, so they need no special handling here).
enum Varieties {

    static let safe: Set<String> = [
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
    ]

    // Collide with countries/common words — only meaningful after "Variety:".
    static let ambiguous: Set<String> = ["java", "colombia", "kent", "h1", "aji"]

    private static let separators = try! NSRegularExpression(
        pattern: #"[,&+/]|\band\b"#, options: .caseInsensitive
    )

    /// If the line is entirely variety terms (e.g. "PINK BOURBON" or
    /// "Caturra & Castillo"), returns the cleaned variety string, else nil.
    static func matchLine(_ text: String) -> String? {
        let ns = text as NSString
        var parts: [String] = []
        var cursor = 0
        for m in separators.matches(in: text, options: [], range: NSRange(location: 0, length: ns.length)) {
            parts.append(ns.substring(with: NSRange(location: cursor, length: m.range.location - cursor)))
            cursor = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: cursor))

        let cleaned = parts
            .map { part -> String in
                var p = part.trimmingCharacters(in: .whitespaces)
                while p.hasSuffix(".") { p.removeLast() }
                return p.lowercased()
            }
            .filter { !$0.isEmpty }
        guard !cleaned.isEmpty, cleaned.allSatisfy({ safe.contains($0) }) else { return nil }
        return cleaned
            .map { p in
                p.split(separator: " ")
                    .map { $0.prefix(1).uppercased() + $0.dropFirst() }
                    .joined(separator: " ")
            }
            .joined(separator: ", ")
    }
}
