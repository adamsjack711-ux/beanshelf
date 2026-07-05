import Foundation

/// One logged cup — the Untappd-style check-in against a bag.
struct Brew: Identifiable, Equatable, Codable {
    var id: String
    var method: String
    var rating: Double      // 0 = unrated; otherwise 0.25...5.0 in quarter steps
    var note: String        // how the cup tasted
    var timestamp: Int64    // ms since epoch, same unit as the Android app
    var doseG: Double?      // coffee in, grams
    var waterG: Double?     // water in / yield out, grams
    var grinder: String     // equipment: grinder name
    var grindSize: String   // equipment: grind setting ("24 clicks", "3.5")

    /// "1:16.7" derived from dose and water, or nil.
    var ratio: String? {
        guard let d = doseG, let w = waterG, d > 0, w > 0 else { return nil }
        return formatRatio(w / d)
    }

    init(id: String, method: String, rating: Double, note: String, timestamp: Int64,
         doseG: Double? = nil, waterG: Double? = nil, grinder: String = "", grindSize: String = "") {
        self.id = id
        self.method = method
        self.rating = rating
        self.note = note
        self.timestamp = timestamp
        self.doseG = doseG
        self.waterG = waterG
        self.grinder = grinder
        self.grindSize = grindSize
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        method = try c.decodeIfPresent(String.self, forKey: .method) ?? ""
        rating = try c.decodeIfPresent(Double.self, forKey: .rating) ?? 0
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        timestamp = try c.decodeIfPresent(Int64.self, forKey: .timestamp) ?? 0
        doseG = try c.decodeIfPresent(Double.self, forKey: .doseG)
        waterG = try c.decodeIfPresent(Double.self, forKey: .waterG)
        grinder = try c.decodeIfPresent(String.self, forKey: .grinder) ?? ""
        grindSize = try c.decodeIfPresent(String.self, forKey: .grindSize) ?? ""
    }
}

/// "1:17" / "1:16.7" — drop the decimal when it rounds clean.
func formatRatio(_ r: Double) -> String {
    let frac = r.truncatingRemainder(dividingBy: 1)
    return "1:" + (frac < 0.05 || frac > 0.95 ? String(format: "%.0f", r) : String(format: "%.1f", r))
}

/// "18" or "18.5" — grams without trailing zeros.
func formatGrams(_ g: Double) -> String {
    g.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", g) : String(format: "%.1f", g)
}

struct Bean: Identifiable, Equatable, Codable {
    var id: String
    var name: String
    var roaster: String
    var origin: String
    var roastLevel: String  // "" or one of roastLevels
    var process: String     // "" or one of processes
    var notes: String
    var variety: String     // e.g. "Pacas", "Heirloom"
    var elevation: String   // e.g. "1,650 masl"
    var producer: String    // farmer / farm / station
    var roastedOn: String   // roast date as printed ("May 26", "27-05-2026")
    var rating: Double      // 0 = unrated
    var photoFile: String?  // filename inside Documents/photos (front of bag)
    var backPhotoFile: String?  // optional back-of-bag photo, also scanned for info
    var createdAt: Int64
    var brews: [Brew]

    // Same JSON keys as the Android app's beans.json. Android stores absolute
    // paths; iOS containers move between installs, so only the filename is
    // meaningful — strip any directory part on decode.
    enum CodingKeys: String, CodingKey {
        case id, name, roaster, origin, roastLevel, process, notes
        case variety, elevation, producer, roastedOn, rating
        case photoFile = "photoPath"
        case backPhotoFile = "backPhotoPath"
        case createdAt, brews
    }

    init(id: String, name: String, roaster: String, origin: String, roastLevel: String,
         process: String, notes: String, variety: String = "", elevation: String = "",
         producer: String = "", roastedOn: String = "", rating: Double,
         photoFile: String?, backPhotoFile: String?, createdAt: Int64, brews: [Brew]) {
        self.id = id
        self.name = name
        self.roaster = roaster
        self.origin = origin
        self.roastLevel = roastLevel
        self.process = process
        self.notes = notes
        self.variety = variety
        self.elevation = elevation
        self.producer = producer
        self.roastedOn = roastedOn
        self.rating = rating
        self.photoFile = photoFile
        self.backPhotoFile = backPhotoFile
        self.createdAt = createdAt
        self.brews = brews
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        roaster = try c.decodeIfPresent(String.self, forKey: .roaster) ?? ""
        origin = try c.decodeIfPresent(String.self, forKey: .origin) ?? ""
        roastLevel = try c.decodeIfPresent(String.self, forKey: .roastLevel) ?? ""
        process = try c.decodeIfPresent(String.self, forKey: .process) ?? ""
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        variety = try c.decodeIfPresent(String.self, forKey: .variety) ?? ""
        elevation = try c.decodeIfPresent(String.self, forKey: .elevation) ?? ""
        producer = try c.decodeIfPresent(String.self, forKey: .producer) ?? ""
        roastedOn = try c.decodeIfPresent(String.self, forKey: .roastedOn) ?? ""
        rating = try c.decodeIfPresent(Double.self, forKey: .rating) ?? 0
        photoFile = (try c.decodeIfPresent(String.self, forKey: .photoFile))
            .map { URL(fileURLWithPath: $0).lastPathComponent }
        backPhotoFile = (try c.decodeIfPresent(String.self, forKey: .backPhotoFile))
            .map { URL(fileURLWithPath: $0).lastPathComponent }
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        brews = try c.decodeIfPresent([Brew].self, forKey: .brews) ?? []
    }
}

let roastLevels = ["Light", "Medium", "Medium-Dark", "Dark"]
let processes = ["Washed", "Natural", "Honey", "Anaerobic", "Other"]
let brewMethods = ["V60", "Espresso", "AeroPress", "French Press", "Moka", "Cold Brew", "Drip", "Other"]

/// "4.0" / "4.25" — quarter-step display like Untappd.
func formatRating(_ r: Double) -> String {
    r.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.1f", r) : String(format: "%.2f", r)
}

func nowMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}
