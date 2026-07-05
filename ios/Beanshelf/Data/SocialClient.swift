import UIKit

/// Client for the Beanshelf social server (server/main.py in this repo).
/// URLSession + JSONSerialization — no dependencies. Sign-in state (server
/// URL, token, username) lives in UserDefaults. Same API as the Android app;
/// both platforms talk to the same server.
enum SocialClient {

    static let defaultServer = "https://beans.beanshelf.ca"

    struct Account: Codable {
        var serverUrl: String
        var token: String
        var username: String
        var display: String
    }

    struct FeedPost: Identifiable {
        let id: String
        let username: String
        let display: String
        let name: String
        let roaster: String
        let origin: String
        let variety: String
        let process: String
        let notes: String
        let rating: Double
        let photoUrl: URL? // absolute
        let createdAt: Int64
        let cheers: Int
        let iCheered: Bool
        let commentCount: Int
        let lastCommentUser: String?
        let lastCommentText: String?
    }

    struct UserHit: Identifiable {
        let username: String
        let display: String
        var following: Bool
        var id: String { username }
    }

    struct Profile {
        let username: String
        let display: String
        let followers: Int
        let following: Int
        let beans: Int
        let iFollow: Bool
        let isMe: Bool
        let profileUrl: String
    }

    struct Comment: Identifiable {
        let id: String
        let username: String
        let display: String
        let text: String
        let createdAt: Int64
    }

    struct SocialError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    private static let accountKey = "social.account"
    private static let lastServerKey = "social.lastServerUrl"

    static var account: Account? {
        guard let data = UserDefaults.standard.data(forKey: accountKey) else { return nil }
        return try? JSONDecoder().decode(Account.self, from: data)
    }

    static var lastServerUrl: String {
        UserDefaults.standard.string(forKey: lastServerKey) ?? ""
    }

    static func signOut() {
        UserDefaults.standard.removeObject(forKey: accountKey)
    }

    private static func save(_ a: Account) {
        if let data = try? JSONEncoder().encode(a) {
            UserDefaults.standard.set(data, forKey: accountKey)
        }
        UserDefaults.standard.set(a.serverUrl, forKey: lastServerKey)
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────
    private static func request(
        base: String,
        path: String,
        method: String,
        token: String?,
        body: [String: Any]?
    ) async throws -> Data {
        let trimmed = base.hasSuffix("/") ? String(base.dropLast()) : base
        guard let url = URL(string: trimmed + path) else {
            throw SocialError(message: "That server address doesn't look right")
        }
        var req = URLRequest(url: url, timeoutInterval: 15)
        req.httpMethod = method
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: req)
        } catch {
            throw SocialError(message: "Couldn't reach the server")
        }
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else {
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])
                .flatMap { $0["detail"] as? String }
            throw SocialError(message: (detail?.isEmpty == false ? detail! : "Server error (\(code))"))
        }
        return data
    }

    // ── auth ──────────────────────────────────────────────────────────────
    static func register(serverUrl: String, username: String, display: String, password: String) async throws -> Account {
        try await authCall(serverUrl: serverUrl, path: "/auth/register", username: username, display: display, password: password)
    }

    static func login(serverUrl: String, username: String, password: String) async throws -> Account {
        try await authCall(serverUrl: serverUrl, path: "/auth/login", username: username, display: "", password: password)
    }

    private static func authCall(
        serverUrl: String, path: String,
        username: String, display: String, password: String
    ) async throws -> Account {
        let data = try await request(base: serverUrl, path: path, method: "POST", token: nil, body: [
            "username": username.trimmingCharacters(in: .whitespaces).lowercased(),
            "password": password,
            "display": display.trimmingCharacters(in: .whitespaces),
        ])
        guard let res = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let token = res["token"] as? String,
              let user = res["username"] as? String
        else { throw SocialError(message: "Unexpected reply from the server") }
        let trimmed = serverUrl.hasSuffix("/") ? String(serverUrl.dropLast()) : serverUrl
        let account = Account(
            serverUrl: trimmed,
            token: token,
            username: user,
            display: (res["display"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? user
        )
        save(account)
        return account
    }

    // ── social ────────────────────────────────────────────────────────────
    static func search(_ a: Account, query: String) async throws -> [UserHit] {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let data = try await request(base: a.serverUrl, path: "/users/search?q=\(q)", method: "GET", token: a.token, body: nil)
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { o in
            guard let username = o["username"] as? String else { return nil }
            return UserHit(
                username: username,
                display: o["display"] as? String ?? username,
                following: o["following"] as? Bool ?? false
            )
        }
    }

    static func setFollowing(_ a: Account, username: String, follow: Bool) async throws {
        _ = try await request(
            base: a.serverUrl,
            path: "/follow/\(username)",
            method: follow ? "POST" : "DELETE",
            token: a.token,
            body: follow ? [:] : nil
        )
    }

    static func feed(_ a: Account) async throws -> [FeedPost] {
        parsePosts(a, try await request(base: a.serverUrl, path: "/feed", method: "GET", token: a.token, body: nil))
    }

    static func discover(_ a: Account) async throws -> [FeedPost] {
        parsePosts(a, try await request(base: a.serverUrl, path: "/discover", method: "GET", token: a.token, body: nil))
    }

    static func userLeaderboard(_ a: Account, username: String) async throws -> [FeedPost] {
        parsePosts(a, try await request(base: a.serverUrl, path: "/users/\(username)/leaderboard", method: "GET", token: a.token, body: nil))
    }

    static func profile(_ a: Account, username: String) async throws -> Profile {
        let data = try await request(base: a.serverUrl, path: "/users/\(username)/profile", method: "GET", token: a.token, body: nil)
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let user = o["username"] as? String
        else { throw SocialError(message: "Unexpected reply from the server") }
        return Profile(
            username: user,
            display: o["display"] as? String ?? user,
            followers: o["followers"] as? Int ?? 0,
            following: o["following"] as? Int ?? 0,
            beans: o["beans"] as? Int ?? 0,
            iFollow: o["iFollow"] as? Bool ?? false,
            isMe: o["isMe"] as? Bool ?? false,
            profileUrl: o["profileUrl"] as? String ?? ""
        )
    }

    private static func parsePeople(_ data: Data) -> [UserHit] {
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { o in
            guard let username = o["username"] as? String else { return nil }
            return UserHit(
                username: username,
                display: o["display"] as? String ?? username,
                following: o["following"] as? Bool ?? false
            )
        }
    }

    static func followers(_ a: Account, username: String) async throws -> [UserHit] {
        parsePeople(try await request(base: a.serverUrl, path: "/users/\(username)/followers", method: "GET", token: a.token, body: nil))
    }

    static func followingList(_ a: Account, username: String) async throws -> [UserHit] {
        parsePeople(try await request(base: a.serverUrl, path: "/users/\(username)/following", method: "GET", token: a.token, body: nil))
    }

    /// Returns the new cheer count.
    static func setCheer(_ a: Account, postId: String, on: Bool) async throws -> Int {
        let data = try await request(
            base: a.serverUrl,
            path: "/beans/\(postId)/cheers",
            method: on ? "POST" : "DELETE",
            token: a.token,
            body: on ? [:] : nil
        )
        let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        return o?["cheers"] as? Int ?? 0
    }

    static func comments(_ a: Account, postId: String) async throws -> [Comment] {
        let data = try await request(base: a.serverUrl, path: "/beans/\(postId)/comments", method: "GET", token: a.token, body: nil)
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { o in
            guard let id = o["id"] as? String else { return nil }
            return Comment(
                id: id,
                username: o["username"] as? String ?? "",
                display: o["display"] as? String ?? "",
                text: o["text"] as? String ?? "",
                createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0
            )
        }
    }

    static func addComment(_ a: Account, postId: String, text: String) async throws {
        _ = try await request(base: a.serverUrl, path: "/beans/\(postId)/comments", method: "POST", token: a.token, body: ["text": text])
    }

    private static func parsePosts(_ a: Account, _ data: Data) -> [FeedPost] {
        let arr = (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
        return arr.compactMap { o in
            guard let id = o["id"] as? String, let username = o["username"] as? String else { return nil }
            return FeedPost(
                id: id,
                username: username,
                display: o["display"] as? String ?? username,
                name: o["name"] as? String ?? "",
                roaster: o["roaster"] as? String ?? "",
                origin: o["origin"] as? String ?? "",
                variety: o["variety"] as? String ?? "",
                process: o["process"] as? String ?? "",
                notes: o["notes"] as? String ?? "",
                rating: o["rating"] as? Double ?? 0,
                photoUrl: (o["photoUrl"] as? String).flatMap { URL(string: a.serverUrl + $0) },
                createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0,
                cheers: o["cheers"] as? Int ?? 0,
                iCheered: o["iCheered"] as? Bool ?? false,
                commentCount: o["commentCount"] as? Int ?? 0,
                lastCommentUser: o["lastCommentUser"] as? String,
                lastCommentText: o["lastCommentText"] as? String
            )
        }
    }

    /// Posts a bean check-in with its photo downscaled to ~800px.
    static func postBean(_ a: Account, bean: Bean) async throws {
        var body: [String: Any] = [
            "name": bean.name,
            "roaster": bean.roaster,
            "origin": bean.origin,
            "variety": bean.variety,
            "process": bean.process,
            "notes": bean.notes,
            "rating": bean.rating,
        ]
        if let file = bean.photoFile,
           let url = PhotoStore.url(for: file),
           let image = UIImage(contentsOfFile: url.path) {
            let pixelW = image.size.width * image.scale
            let pixelH = image.size.height * image.scale
            let scale = min(1, 800 / max(pixelW, pixelH))
            let target = CGSize(width: pixelW * scale, height: pixelH * scale)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            // keep PNG for cutouts so transparency survives into the feed
            let isCutout = BagCropper.isCutout(file)
            format.opaque = !isCutout
            let scaled = UIGraphicsImageRenderer(size: target, format: format).image { _ in
                image.draw(in: CGRect(origin: .zero, size: target))
            }
            let data = isCutout ? scaled.pngData() : scaled.jpegData(compressionQuality: 0.82)
            if let data { body["photo_b64"] = data.base64EncodedString() }
        }
        _ = try await request(base: a.serverUrl, path: "/beans", method: "POST", token: a.token, body: body)
    }

    /// Fetch + cache a feed photo (keyed by post id) for display.
    static func fetchPhoto(post: FeedPost) async -> UIImage? {
        guard let url = post.photoUrl else { return nil }
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("feed", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let ext = url.path.hasSuffix(".png") ? "png" : "jpg"
        let cached = dir.appendingPathComponent("\(post.id).\(ext)")
        if let data = try? Data(contentsOf: cached), let image = UIImage(data: data) {
            return image
        }
        guard let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data) else { return nil }
        try? data.write(to: cached, options: .atomic)
        return image
    }
}

func relativeTime(_ ts: Int64) -> String {
    let mins = (nowMillis() - ts) / 60000
    switch mins {
    case ..<1: return "now"
    case ..<60: return "\(mins)m"
    case ..<(60 * 24): return "\(mins / 60)h"
    default: return "\(mins / (60 * 24))d"
    }
}
