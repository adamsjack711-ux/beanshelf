import UIKit
import Vision
import os

/// On-device OCR over a bag photo (Apple Vision — fully offline), parsed with a
/// coffee-label keyword vocabulary into pre-fillable fields. Best-effort by
/// design: results only ever fill fields the user has left blank.
///
/// Keyword-driven: keySynonyms maps every label spelling ("Region:", "Altitude",
/// "Finca", "We taste"…) to a field. Handles inline "Key: Value" AND the two-line
/// layout where the keyword sits alone above its value. Console log category
/// "LabelScanner" shows every line seen + the final parse for tuning.
/// Direct port of the Android ML Kit LabelScanner v2.
enum LabelScanner {

    private static let log = Logger(subsystem: "dev.adamsjack.beanshelf", category: "LabelScanner")

    struct LabelInfo {
        var roaster: String?
        var name: String?
        var origin: String?
        var process: String?
        var roastLevel: String?
        var notes: String?
        var variety: String?
        var elevation: String?
        var producer: String?
        var roastedOn: String?
        /// Field keys the parser guessed at rather than derived from a keyword —
        /// the UI asks the user to confirm these.
        var unsure: Set<String> = []

        var isEmpty: Bool {
            [roaster, name, origin, process, roastLevel, notes,
             variety, elevation, producer, roastedOn].allSatisfy { $0 == nil }
        }
    }

    // ── Keyword vocabulary ────────────────────────────────────────────────
    // regex fragment (case-insensitive) → target field. "skip" = consume the
    // line so its text can never leak into name/roaster, but keep no value.
    private static let keySynonyms: [(pattern: String, field: String)] = [
        (#"origin|region|country|grown\s+in"#, "origin"),
        (#"producer|farmer|farm|finca|estate|cooperative|co-?op|washing\s+station|station"#, "producer"),
        (#"variet(?:y|al|ies)|cultivar"#, "variety"),
        (#"elevation|altitude|masl|m\.a\.s\.l\.?"#, "elevation"),
        (#"process(?:ing)?|fermentation"#, "process"),
        (#"(?:tasting|flavou?r|cupping)\s+notes?|notes?\s+of|we\s+taste|tastes?\s+like|notes?"#, "notes"),
        // roastdate MUST precede the bare "roast" fragment or "Roast Date" mis-keys.
        (#"roast\s+date|roasted\s+on|roast\s+day|roasted"#, "roastdate"),
        (#"roast\s+(?:level|profile)|roasted\s+for|roast"#, "roast"),
        (#"harvest|crop|lot|batch|importer|net\s+weight|weight|best\s+(?:by|before)|brew\s+(?:ratio|guide)|dose|www|instagram"#, "skip"),
    ]

    private static let keyvalRegex: NSRegularExpression = {
        let alternation = keySynonyms.map { "(?:\($0.pattern))" }.joined(separator: "|")
        return try! NSRegularExpression(
            pattern: "^(\(alternation))\\b\\s*[:\\-–]?\\s*(.*)$",
            options: .caseInsensitive
        )
    }()

    private static func fieldForKey(_ key: String) -> String {
        let k = key.lowercased().trimmingCharacters(in: .whitespaces)
        for (pattern, field) in keySynonyms {
            let anchored = try! NSRegularExpression(pattern: "^(?:\(pattern))$", options: .caseInsensitive)
            if anchored.firstMatch(in: k, options: [], range: NSRange(k.startIndex..., in: k)) != nil {
                return field
            }
        }
        return "skip"
    }
    // ─────────────────────────────────────────────────────────────────────

    private static let countries = [
        "Ethiopia", "Kenya", "Colombia", "Brazil", "Guatemala", "Honduras", "El Salvador",
        "Costa Rica", "Panama", "Peru", "Bolivia", "Mexico", "Nicaragua", "Rwanda", "Burundi",
        "Uganda", "Tanzania", "Yemen", "Indonesia", "Sumatra", "Java", "Sulawesi", "India",
        "Vietnam", "Ecuador", "Papua New Guinea", "Myanmar", "Thailand", "Timor", "Congo",
        "Malawi", "Zambia", "Jamaica", "Hawaii",
    ]

    private static let processWords: [(key: String, value: String)] = [
        ("washed", "Washed"), ("natural", "Natural"), ("honey", "Honey"),
        ("anaerobic", "Anaerobic"), ("carbonic", "Anaerobic"),
    ]

    private static let roastRegex = try! NSRegularExpression(
        pattern: #"\b(light|medium[-\s]?dark|medium|dark)\b(\s+roast)?"#, options: .caseInsensitive
    )
    // Standalone altitude line, e.g. "1850 masl" / "1700–1900 m".
    private static let elevationRegex = try! NSRegularExpression(
        pattern: #"^~?\d{3,4}(?:\s?[-–]\s?\d{3,4})?\s?(?:m|masl|m\.a\.s\.l\.?)$"#, options: .caseInsensitive
    )
    private static let skipRegex = try! NSRegularExpression(
        pattern: #"^[\d.,\s]+$|^\d+\s?(g|kg|oz|lb)\b|www\.|@|\.com|net\s?wt"#, options: .caseInsensitive
    )
    // Label furniture, never a name/roaster. Compared against the DE-SPACED lowercase line.
    private static let stopwords: Set<String> = [
        "filter", "espresso", "omni", "coffee", "wholebean", "wholebeans", "ground",
        "singleorigin", "specialtycoffee", "arabica", "beans", "netweight", "decaf",
        "heirloom", "heirloomvarieties", "microlot", "filterroast", "espressoroast",
        "lightroast", "mediumroast", "darkroast", "coffeebeans",
    ]

    static func scan(fileURL: URL) async -> LabelInfo? {
        guard let image = UIImage(contentsOfFile: fileURL.path),
              let cg = image.cgImage else { return nil }

        let observations: [VNRecognizedTextObservation] = await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true
                let handler = VNImageRequestHandler(cgImage: cg, options: [:])
                try? handler.perform([request])
                cont.resume(returning: request.results ?? [])
            }
        }
        guard !observations.isEmpty else { return nil }
        let info = parse(observations)
        return info.isEmpty ? nil : info
    }

    private struct Line {
        let text: String
        let height: CGFloat
        let confidence: Float
    }

    private static func parse(_ observations: [VNRecognizedTextObservation]) -> LabelInfo {
        // Vision's boundingBox is normalised with origin at the bottom-left;
        // sort top-to-bottom to mirror ML Kit's block order.
        let lines: [Line] = observations
            .compactMap { obs -> (VNRecognizedText, CGRect)? in
                guard let top = obs.topCandidates(1).first else { return nil }
                return (top, obs.boundingBox)
            }
            .sorted { $0.1.maxY > $1.1.maxY }
            .map { top, box in
                Line(
                    text: top.string.trimmingCharacters(in: .whitespacesAndNewlines),
                    height: box.height,
                    confidence: top.confidence <= 0 ? 1 : top.confidence
                )
            }
            .filter { $0.text.count >= 2 && !matches(skipRegex, $0.text) }

        for l in lines {
            log.debug("line h=\(String(format: "%.3f", l.height)) conf=\(String(format: "%.2f", l.confidence)) '\(l.text)'")
        }

        var fields: [String: String] = [:]
        var consumed = Set<Int>()

        func putField(_ field: String, _ rawValue: String) {
            if field == "skip" { return }
            let value = cleanValue(rawValue)
            if !value.isEmpty && fields[field] == nil { fields[field] = value }
        }

        // Pass 1 — keyword lines: inline "Key: Value" or keyword-only + next-line value.
        for (idx, line) in lines.enumerated() {
            if consumed.contains(idx) { continue }
            guard let m = firstMatch(keyvalRegex, line.text) else { continue }
            let field = fieldForKey(group(m, 1, in: line.text))
            var value = group(m, 2, in: line.text).trimmingCharacters(in: .whitespaces)
            consumed.insert(idx)
            if value.isEmpty {
                // "REGION" alone → value lives on the next line (unless that's a keyword too).
                if idx + 1 < lines.count, !consumed.contains(idx + 1),
                   firstMatch(keyvalRegex, lines[idx + 1].text) == nil {
                    value = lines[idx + 1].text.trimmingCharacters(in: .whitespaces)
                    consumed.insert(idx + 1)
                }
            }
            putField(field, value)
        }

        // Pass 2 — free-form sweep over unconsumed lines.
        for (idx, line) in lines.enumerated() {
            if consumed.contains(idx) { continue }
            let lower = line.text.lowercased()
            let squished = despace(lower)

            if fields["process"] == nil {
                if let hit = processWords.first(where: { squished.contains($0.key) }) {
                    fields["process"] = hit.key
                    consumed.insert(idx)
                }
            }
            if fields["elevation"] == nil, matchesWhole(elevationRegex, line.text) {
                fields["elevation"] = line.text
                consumed.insert(idx)
            }
            if fields["variety"] == nil {
                // A line that is entirely variety terms ("PINK BOURBON", "Caturra & Castillo").
                if let v = Varieties.matchLine(line.text) {
                    fields["variety"] = v
                    consumed.insert(idx)
                }
            }
            if fields["roast"] == nil {
                if let m = firstMatch(roastRegex, lower) {
                    if lower.contains("roast") || ["light", "medium", "mediumdark", "dark"].contains(squished) {
                        fields["roast"] = group(m, 1, in: lower)
                        consumed.insert(idx)
                    }
                }
            }
            if fields["origin"] == nil {
                if let country = countries.first(where: { lower.contains($0.lowercased()) }) {
                    // "Santa Bárbara," on the line above "Honduras" → join them.
                    let prev = idx > 0 ? lines[idx - 1] : nil
                    if line.text.trimmingCharacters(in: .whitespaces).lowercased() == country.lowercased(),
                       let prev, prev.text.hasSuffix(","), !consumed.contains(idx - 1) {
                        consumed.insert(idx - 1)
                        let head = prev.text.trimmingCharacters(in: CharacterSet(charactersIn: ", "))
                        fields["origin"] = "\(head), \(country)"
                    } else {
                        let segment = line.text
                            .components(separatedBy: CharacterSet(charactersIn: "|·•;"))
                            .first { $0.range(of: country, options: .caseInsensitive) != nil } ?? line.text
                        var v = cleanValue(segment)
                        while v.hasSuffix(",") { v.removeLast() }
                        fields["origin"] = v
                    }
                    consumed.insert(idx)
                }
            }
            if fields["notes"] == nil, line.text.count <= 60, !line.text.contains(where: { $0.isNumber }) {
                // Flavor-wheel detection: 2+ known descriptors, or 1 + list punctuation.
                let flavorScore = FlavorWheel.score(line.text)
                if flavorScore >= 2 || (flavorScore >= 1 && lower.filter { $0 == "," }.count >= 2) {
                    var note = line.text
                    consumed.insert(idx)
                    // Notes often wrap: absorb following lines that are also flavor-heavy.
                    var j = idx + 1
                    while j < lines.count, !consumed.contains(j),
                          lines[j].text.count <= 60, FlavorWheel.score(lines[j].text) >= 1,
                          firstMatch(keyvalRegex, lines[j].text) == nil {
                        var extra = lines[j].text.trimmingCharacters(in: .whitespaces)
                        while extra.hasPrefix("&") || extra.hasPrefix("+") { extra.removeFirst() }
                        note += ", " + extra.trimmingCharacters(in: .whitespaces)
                        consumed.insert(j)
                        j += 1
                    }
                    fields["notes"] = note
                }
            }
        }

        // A roast date must actually look like a date ("Roasted in Canada" doesn't).
        if let v = fields["roastdate"] {
            let dateish = v.contains(where: { $0.isNumber })
                || v.range(of: "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec",
                           options: [.regularExpression, .caseInsensitive]) != nil
            if !dateish { fields["roastdate"] = nil }
        }

        // No explicit notes line → synthesize from flavor terms scattered on the
        // label. Only unconsumed lines: a "Pink Bourbon" variety line must not
        // leak "bourbon" into the notes.
        var notesSynthesized = false
        if fields["notes"] == nil {
            var terms: [String] = []
            for (idx, line) in lines.enumerated() where !consumed.contains(idx) {
                for t in FlavorWheel.termsIn(line.text) where !terms.contains(t) {
                    terms.append(t)
                }
            }
            if terms.count >= 2 {
                fields["notes"] = terms.joined(separator: ", ")
                notesSynthesized = true
            }
        }

        // Pass 3 — name & roaster from what's left. Junk guards: no key-value colons,
        // no digits, real words (3+ letters), confident OCR (garbled logos read low),
        // and flavor-heavy lines (those are tasting notes, not names).
        let candidates = lines.enumerated()
            .filter { idx, l in
                let flavor = FlavorWheel.score(l.text)
                return !consumed.contains(idx)
                    && !l.text.contains(":")
                    && !l.text.contains(where: { $0.isNumber })
                    && l.text.filter({ $0.isLetter }).count >= 3
                    && l.text.count <= 40
                    && l.confidence >= 0.6
                    && flavor < 2
                    && !(flavor >= 1 && l.text.trimmingCharacters(in: .whitespaces).hasSuffix(","))
                    && !stopwords.contains(despace(l.text.lowercased()))
            }
            .map(\.element)
            .sorted { $0.height > $1.height }

        var producer = fields["producer"]
        let name = candidates.first { $0.text.contains(" ") }?.text
            ?? candidates.first?.text
            ?? producer
        let roaster = candidates.map(\.text).first { $0 != name && $0.split(separator: " ").count <= 2 }

        // Cross-field sanity: a "producer" that echoes the origin is a mis-keyed region.
        if let p = producer, let o = fields["origin"] {
            let pWords = Set(p.lowercased().components(separatedBy: CharacterSet(charactersIn: ", ")).filter { $0.count > 2 })
            let oWords = Set(o.lowercased().components(separatedBy: CharacterSet(charactersIn: ", ")).filter { $0.count > 2 })
            if !pWords.isEmpty, pWords.intersection(oWords).count * 2 >= pWords.count { producer = nil }
        }

        var unsure = Set<String>()
        if name != nil { unsure.insert("name") }
        if roaster != nil { unsure.insert("roaster") }
        if notesSynthesized { unsure.insert("notes") }

        let info = LabelInfo(
            roaster: roaster.map(tidy),
            name: name.map(tidy),
            origin: fields["origin"].map(tidy),
            process: fields["process"].map { v in
                processWords.first { v.lowercased().contains($0.key) }?.value
                    ?? tidy(String(v.split(separator: " ").first ?? ""))
            },
            roastLevel: fields["roast"].flatMap(roastFrom),
            notes: fields["notes"].map { v in
                var t = v
                while t.hasSuffix(".") || t.hasSuffix(",") { t.removeLast() }
                return tidy(t)
            },
            variety: fields["variety"].map(tidy),
            elevation: fields["elevation"],
            producer: producer.map(tidy),
            roastedOn: fields["roastdate"],
            unsure: unsure
        )
        log.debug("parsed: name=\(info.name ?? "-") roaster=\(info.roaster ?? "-") origin=\(info.origin ?? "-") notes=\(info.notes ?? "-") unsure=\(unsure)")
        return info
    }

    /// Cuts a value at embedded furniture ("… |Elevation: 1850m Producer: …").
    private static func cleanValue(_ raw: String) -> String {
        var v = raw.components(separatedBy: CharacterSet(charactersIn: "|·•;")).first ?? raw
        v = v.trimmingCharacters(in: .whitespaces)
        if let r = v.range(
            of: #"\b(elevation|altitude|masl|producer|variet|process|harvest|lot)\b"#,
            options: [.regularExpression, .caseInsensitive]
        ), r.lowerBound != v.startIndex {
            v = String(v[..<r.lowerBound]).trimmingCharacters(in: .whitespaces)
        }
        v = v.trimmingCharacters(in: .whitespaces)
        while let last = v.last, ",-–:".contains(last) { v.removeLast() }
        return v.trimmingCharacters(in: .whitespaces)
    }

    private static func roastFrom(_ s: String) -> String? {
        guard let m = firstMatch(roastRegex, s.lowercased()) else { return nil }
        let word = group(m, 1, in: s.lowercased())
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: " ", with: "")
        switch word {
        case "light": return "Light"
        case "medium": return "Medium"
        case "mediumdark": return "Medium-Dark"
        case "dark": return "Dark"
        default: return nil
        }
    }

    /// ALL-CAPS label text → Title Case; anything else passes through.
    private static func tidy(_ s: String) -> String {
        guard s == s.uppercased(), s.contains(where: { $0.isLetter }) else { return s }
        return s.lowercased()
            .split(separator: " ")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    /// Collapses whitespace and dots — letterspaced label text ("F I L T E R").
    private static func despace(_ s: String) -> String {
        s.replacingOccurrences(of: #"[\s.]+"#, with: "", options: .regularExpression)
    }

    private static func matches(_ re: NSRegularExpression, _ s: String) -> Bool {
        firstMatch(re, s) != nil
    }

    private static func matchesWhole(_ re: NSRegularExpression, _ s: String) -> Bool {
        guard let m = firstMatch(re, s) else { return false }
        return m.range.length == (s as NSString).length
    }

    private static func firstMatch(_ re: NSRegularExpression, _ s: String) -> NSTextCheckingResult? {
        re.firstMatch(in: s, options: [], range: NSRange(s.startIndex..., in: s))
    }

    private static func group(_ m: NSTextCheckingResult, _ i: Int, in s: String) -> String {
        guard m.numberOfRanges > i, let r = Range(m.range(at: i), in: s) else { return "" }
        return String(s[r])
    }
}
