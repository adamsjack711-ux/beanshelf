import Foundation
import UIKit

/// Builds a bug report and hands it off to GitHub Issues or email.
///
/// Nothing is sent anywhere on its own — both paths open a prefilled composer the
/// person can read and edit before submitting. Diagnostics are the boring kind
/// (versions, device model, theme, how many bags are on the shelf); no bag data,
/// no photos, no account details, and the report sheet shows the exact block
/// before it goes anywhere.
enum BugReport {
    static let repoSlug = "adamsjack711-ux/beanshelf"

    /// Fallback for people without a GitHub account. Set to a public address —
    /// this string ships inside the app and lives in a public repo.
    static let reportEmail: String? = nil

    enum Kind: String, CaseIterable, Identifiable {
        case bug = "Something's broken"
        case idea = "Idea or request"
        var id: String { rawValue }

        var label: String { rawValue == "Something's broken" ? "bug" : "enhancement" }
        var titlePrefix: String { self == .bug ? "Bug" : "Idea" }
    }

    // MARK: - Diagnostics

    struct Diagnostics {
        let appVersion: String
        let build: String
        let system: String
        let device: String
        let theme: String
        let beanCount: Int

        var block: String {
            """
            Beanshelf \(appVersion) (\(build))
            \(system)
            \(device)
            Theme: \(theme)
            Bags on shelf: \(beanCount)
            """
        }
    }

    static func diagnostics(beanCount: Int, theme: String) -> Diagnostics {
        let info = Bundle.main.infoDictionary
        var sysinfo = utsname()
        uname(&sysinfo)
        let model = withUnsafePointer(to: &sysinfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(validatingUTF8: $0) ?? "unknown" }
        }
        return Diagnostics(
            appVersion: info?["CFBundleShortVersionString"] as? String ?? "?",
            build: info?["CFBundleVersion"] as? String ?? "?",
            system: "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)",
            device: model,
            theme: theme,
            beanCount: beanCount
        )
    }

    // MARK: - Composing

    /// The issue body. `steps` and `expected` are optional — an empty field is
    /// simply left out rather than shipping an empty heading.
    static func body(what: String, steps: String, expected: String, diagnostics: Diagnostics?) -> String {
        var out = ["### What happened\n\n\(what.trimmed)"]
        if !steps.trimmed.isEmpty { out.append("### Steps to reproduce\n\n\(steps.trimmed)") }
        if !expected.trimmed.isEmpty { out.append("### What you expected instead\n\n\(expected.trimmed)") }
        if let d = diagnostics { out.append("### Diagnostics\n\n```\n\(d.block)\n```") }
        out.append("<sub>Filed from Beanshelf for iOS.</sub>")
        return out.joined(separator: "\n\n")
    }

    static func title(kind: Kind, what: String) -> String {
        let first = what.trimmed
            .split(whereSeparator: \.isNewline).first.map(String.init)?.trimmed ?? ""
        let short = first.count > 70 ? String(first.prefix(69)) + "…" : first
        return "[\(kind.titlePrefix)] \(short.isEmpty ? "No description" : short)"
    }

    /// Prefilled "new issue" URL. Returns nil only if the text can't be encoded.
    static func githubURL(kind: Kind, what: String, steps: String, expected: String, diagnostics: Diagnostics?) -> URL? {
        var c = URLComponents(string: "https://github.com/\(repoSlug)/issues/new")
        c?.queryItems = [
            URLQueryItem(name: "title", value: title(kind: kind, what: what)),
            URLQueryItem(name: "body", value: body(what: what, steps: steps, expected: expected, diagnostics: diagnostics)),
            URLQueryItem(name: "labels", value: kind.label),
        ]
        return c?.url
    }

    /// mailto: equivalent, for people without a GitHub account.
    static func mailURL(kind: Kind, what: String, steps: String, expected: String, diagnostics: Diagnostics?) -> URL? {
        guard let address = reportEmail else { return nil }
        var c = URLComponents()
        c.scheme = "mailto"
        c.path = address
        c.queryItems = [
            URLQueryItem(name: "subject", value: title(kind: kind, what: what)),
            URLQueryItem(name: "body", value: body(what: what, steps: steps, expected: expected, diagnostics: diagnostics)),
        ]
        return c.url
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
