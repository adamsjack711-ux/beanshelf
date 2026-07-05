import Foundation

/// Shop-link builder. "Shop online" prefers retailers with affiliate programs;
/// paste your affiliate IDs below and every link the app opens will carry them.
/// Leave an ID blank and that retailer's link is still generated, just untagged.
enum Affiliate {

    // ── Your affiliate IDs ────────────────────────────────────────────────
    /// Amazon Associates tracking tag, e.g. "jackadams-20".
    static let amazonTag = ""

    /// eBay Partner Network campaign id (campid), e.g. "5338XXXXXX".
    static let ebayCampid = ""
    // ─────────────────────────────────────────────────────────────────────

    struct Shop: Identifiable {
        let label: String
        let url: URL
        let affiliated: Bool
        var id: String { label }
    }

    static func shops(for query: String) -> [Shop] {
        var shops: [Shop] = []

        var amazon = URLComponents(string: "https://www.amazon.com/s")!
        amazon.queryItems = [URLQueryItem(name: "k", value: query)]
            + (amazonTag.isEmpty ? [] : [URLQueryItem(name: "tag", value: amazonTag)])
        if let url = amazon.url {
            shops.append(Shop(
                label: amazonTag.isEmpty ? "Amazon" : "Amazon ✦",
                url: url,
                affiliated: !amazonTag.isEmpty
            ))
        }

        var ebay = URLComponents(string: "https://www.ebay.com/sch/i.html")!
        ebay.queryItems = [URLQueryItem(name: "_nkw", value: query)]
            + (ebayCampid.isEmpty ? [] : [
                URLQueryItem(name: "mkcid", value: "1"),
                URLQueryItem(name: "mkrid", value: "711-53200-19255-0"),
                URLQueryItem(name: "siteid", value: "0"),
                URLQueryItem(name: "campid", value: ebayCampid),
                URLQueryItem(name: "toolid", value: "10001"),
                URLQueryItem(name: "mkevt", value: "1"),
            ])
        if let url = ebay.url {
            shops.append(Shop(
                label: ebayCampid.isEmpty ? "eBay" : "eBay ✦",
                url: url,
                affiliated: !ebayCampid.isEmpty
            ))
        }

        var google = URLComponents(string: "https://www.google.com/search")!
        google.queryItems = [URLQueryItem(name: "q", value: "\(query) buy price")]
        if let url = google.url {
            shops.append(Shop(label: "Roaster's shop (web search)", url: url, affiliated: false))
        }

        return shops
    }

    /// Apple Maps search for stockists nearby ("Find nearby" on Android → geo:).
    static func nearbyURL(for query: String) -> URL? {
        var maps = URLComponents(string: "https://maps.apple.com/")!
        maps.queryItems = [URLQueryItem(name: "q", value: query)]
        return maps.url
    }
}
