import Foundation
import SwiftUI

/// Full-screen screens that cover the bottom bar (pushed over a tab).
enum Overlay: Equatable {
    case leaderboard
    case detail(String)
    /// beanId == nil → new bag. Back returns to Detail when editing, else the tab.
    case edit(String?)
}

@MainActor
final class AppViewModel: ObservableObject {

    @Published private(set) var beans: [Bean] = []
    /// Transient banner text ("added to your shelf" after an import).
    @Published var banner: String?

    @Published var tab: AppTab = .shelf
    @Published var overlay: Overlay?
    /// Set by a beanshelf://u/<username> deep link; consumed by the Feed tab.
    @Published var pendingProfile: String?

    private let store = BeanStore()

    func openProfile(_ username: String) {
        pendingProfile = username
        overlay = nil
        tab = .feed
    }

    func openDetail(_ id: String) { overlay = .detail(id) }
    func openEdit(_ id: String?) { overlay = .edit(id) }
    func openLeaderboard() { overlay = .leaderboard }
    func closeOverlay() { overlay = nil }

    func goBack() {
        switch overlay {
        case .edit(let beanId):
            overlay = beanId.map { .detail($0) }
        case .some:
            overlay = nil // Detail or Leaderboard → back to the tab
        case nil: // on a tab root — fall back to the Shelf tab
            pendingProfile = nil
            tab = .shelf
        }
    }

    func notify(_ text: String) {
        showBanner(text)
    }

    /// Every brew across the shelf, newest first — used to prefill equipment.
    var allBrews: [Brew] {
        beans.flatMap(\.brews).sorted { $0.timestamp > $1.timestamp }
    }

    func load() async {
        beans = await store.load()
    }

    func bean(_ id: String) -> Bean? {
        beans.first { $0.id == id }
    }

    func upsert(_ bean: Bean) {
        let old = self.bean(bean.id)
        if let old, let oldPhoto = old.photoFile, oldPhoto != bean.photoFile {
            PhotoStore.delete(oldPhoto)
        }
        if let old, let oldBack = old.backPhotoFile, oldBack != bean.backPhotoFile {
            PhotoStore.delete(oldBack)
        }
        beans = (beans.filter { $0.id != bean.id } + [bean])
            .sorted { $0.createdAt > $1.createdAt }
        persist()
    }

    func delete(id: String) {
        if let bean = bean(id) {
            PhotoStore.delete(bean.photoFile)
            PhotoStore.delete(bean.backPhotoFile)
        }
        beans.removeAll { $0.id == id }
        persist()
    }

    /// Imports a friend's .beanshelf file onto the shelf.
    func importBean(url: URL) async {
        if let bean = await BeanPack.import(url: url) {
            beans = ([bean] + beans).sorted { $0.createdAt > $1.createdAt }
            persist()
            showBanner("\"\(bean.name.isEmpty ? "Bean" : bean.name)\" added to your shelf")
        } else {
            showBanner("That file isn't a bean pack")
        }
    }

    func addBrew(beanId: String, brew: Brew) {
        beans = beans.map {
            var bean = $0
            if bean.id == beanId { bean.brews.insert(brew, at: 0) }
            return bean
        }
        persist()
    }

    private func showBanner(_ text: String) {
        banner = text
        Task {
            try? await Task.sleep(for: .seconds(2.5))
            if banner == text { banner = nil }
        }
    }

    private func persist() {
        let snapshot = beans
        Task { await store.save(snapshot) }
    }
}
