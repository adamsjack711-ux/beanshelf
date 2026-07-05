import Foundation

/// Friend-to-friend bean sharing without a server: a `.beanshelf` file is one
/// JSON document with the bean's fields, brews, and both photos embedded as
/// base64. Send it over anything (AirDrop, Messages, email); the recipient's
/// Beanshelf imports it onto their own shelf. Same format as the Android app.
enum BeanPack {

    private static let version = 1

    static func export(_ bean: Bean) async -> URL? {
        await Task.detached(priority: .userInitiated) { () -> URL? in
            var root: [String: Any] = ["beanshelf": version]
            var b: [String: Any] = [
                "name": bean.name,
                "roaster": bean.roaster,
                "origin": bean.origin,
                "roastLevel": bean.roastLevel,
                "process": bean.process,
                "notes": bean.notes,
                "variety": bean.variety,
                "elevation": bean.elevation,
                "producer": bean.producer,
                "roastedOn": bean.roastedOn,
                "rating": bean.rating,
            ]
            b["brews"] = bean.brews.map { br -> [String: Any] in
                var o: [String: Any] = [
                    "method": br.method,
                    "rating": br.rating,
                    "note": br.note,
                    "timestamp": br.timestamp,
                    "grinder": br.grinder,
                    "grindSize": br.grindSize,
                ]
                o["doseG"] = br.doseG ?? NSNull()
                o["waterG"] = br.waterG ?? NSNull()
                return o
            }
            root["bean"] = b
            if let file = bean.photoFile, let url = PhotoStore.url(for: file),
               let data = try? Data(contentsOf: url) {
                root["frontPhoto"] = data.base64EncodedString()
                root["frontIsCutout"] = BagCropper.isCutout(file)
            }
            if let file = bean.backPhotoFile, let url = PhotoStore.url(for: file),
               let data = try? Data(contentsOf: url) {
                root["backPhoto"] = data.base64EncodedString()
                root["backIsCutout"] = BagCropper.isCutout(file)
            }

            guard let json = try? JSONSerialization.data(withJSONObject: root) else { return nil }
            var safeName = bean.name.isEmpty ? "bean" : bean.name
            safeName = safeName.replacingOccurrences(of: #"[^A-Za-z0-9 _-]"#, with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: " ", with: "-")
                .lowercased()
            if safeName.isEmpty { safeName = "bean" }
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("share", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let out = dir.appendingPathComponent("\(safeName).beanshelf")
            do {
                try json.write(to: out, options: .atomic)
                return out
            } catch {
                return nil
            }
        }.value
    }

    /// Reads a .beanshelf file into a NEW bean (fresh id, photos copied in).
    static func `import`(url: URL) async -> Bean? {
        await Task.detached(priority: .userInitiated) { () -> Bean? in
            let secured = url.startAccessingSecurityScopedResource()
            defer { if secured { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url),
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  (root["beanshelf"] as? Int ?? 0) >= 1,
                  let b = root["bean"] as? [String: Any]
            else { return nil }

            func savePhoto(_ key: String, _ cutoutKey: String) -> String? {
                guard let b64 = root[key] as? String, !b64.isEmpty,
                      let bytes = Data(base64Encoded: b64) else { return nil }
                let ext = (root[cutoutKey] as? Bool ?? false) ? "png" : "jpg"
                let name = "\(UUID().uuidString).\(ext)"
                guard let dest = PhotoStore.url(for: name),
                      (try? bytes.write(to: dest, options: .atomic)) != nil else { return nil }
                return name
            }

            let brews = (b["brews"] as? [[String: Any]] ?? []).map { br in
                Brew(
                    id: UUID().uuidString,
                    method: br["method"] as? String ?? "",
                    rating: br["rating"] as? Double ?? 0,
                    note: br["note"] as? String ?? "",
                    timestamp: (br["timestamp"] as? NSNumber)?.int64Value ?? 0,
                    doseG: br["doseG"] as? Double,
                    waterG: br["waterG"] as? Double,
                    grinder: br["grinder"] as? String ?? "",
                    grindSize: br["grindSize"] as? String ?? ""
                )
            }
            return Bean(
                id: UUID().uuidString,
                name: b["name"] as? String ?? "",
                roaster: b["roaster"] as? String ?? "",
                origin: b["origin"] as? String ?? "",
                roastLevel: b["roastLevel"] as? String ?? "",
                process: b["process"] as? String ?? "",
                notes: b["notes"] as? String ?? "",
                variety: b["variety"] as? String ?? "",
                elevation: b["elevation"] as? String ?? "",
                producer: b["producer"] as? String ?? "",
                roastedOn: b["roastedOn"] as? String ?? "",
                rating: b["rating"] as? Double ?? 0,
                photoFile: savePhoto("frontPhoto", "frontIsCutout"),
                backPhotoFile: savePhoto("backPhoto", "backIsCutout"),
                createdAt: nowMillis(),
                brews: brews
            )
        }.value
    }
}
