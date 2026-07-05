import Foundation

/// Plain JSON-file persistence: Documents/beans.json. Small collection, no DB
/// needed. Same JSON shape as the Android app.
struct BeanStore {

    static var fileURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("beans.json")
    }

    func load() async -> [Bean] {
        let url = Self.fileURL
        return await Task.detached(priority: .userInitiated) {
            guard let data = try? Data(contentsOf: url) else { return [] }
            return (try? JSONDecoder().decode([Bean].self, from: data)) ?? []
        }.value
    }

    func save(_ beans: [Bean]) async {
        let url = Self.fileURL
        await Task.detached(priority: .utility) {
            guard let data = try? JSONEncoder().encode(beans) else { return }
            try? data.write(to: url, options: .atomic)
        }.value
    }
}
