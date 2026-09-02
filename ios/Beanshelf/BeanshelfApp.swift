import SwiftUI
import UniformTypeIdentifiers

@main
struct BeanshelfApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @StateObject private var vm = AppViewModel()
    @StateObject private var theme = ThemeHolder.shared
    @State private var showImporter = false

    var body: some View {
        Group {
            // A full-screen overlay (Detail / Edit / Leaderboard) covers the bottom bar.
            if let overlay = vm.overlay {
                overlayView(overlay)
            } else {
                // Otherwise: the four tabs with a persistent bottom bar.
                VStack(spacing: 0) {
                    tabView
                        .frame(maxHeight: .infinity)
                    BottomBar(current: vm.tab) { vm.tab = $0 }
                }
            }
        }
        .background(Palette.roast.ignoresSafeArea())
        .overlay(alignment: .bottom) { bannerView }
        .preferredColorScheme(theme.palette.dark ? .dark : .light)
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.data, .json],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                Task { await vm.importBean(url: url) }
            }
        }
        // beanshelf://u/<username> → jump to that person's profile in the Feed tab.
        .onOpenURL { url in
            if url.scheme == "beanshelf", url.host == "u", !url.lastPathComponent.isEmpty {
                vm.openProfile(url.lastPathComponent)
            }
        }
        .task { await vm.load() }
    }

    @ViewBuilder
    private var tabView: some View {
        switch vm.tab {
        case .feed:
            SocialView(
                onBack: {
                    vm.pendingProfile = nil
                    vm.tab = .shelf
                },
                initialProfile: vm.pendingProfile
            )
            .id(vm.pendingProfile) // deep link re-targets the social stack

        case .shelf:
            ShelfView(
                beans: vm.beans,
                onAdd: { vm.openEdit(nil) },
                onOpen: { vm.openDetail($0.id) },
                onLeaderboard: { vm.openLeaderboard() },
                onImport: { showImporter = true }
            )

        case .profile:
            SocialView(
                onBack: { vm.tab = .shelf },
                startOnProfile: true
            )

        case .settings:
            SettingsView(beanCount: vm.beans.count)
        }
    }

    @ViewBuilder
    private func overlayView(_ overlay: Overlay) -> some View {
        switch overlay {
        case .leaderboard:
            LeaderboardView(
                beans: vm.beans,
                onBack: { vm.closeOverlay() },
                onOpen: { vm.openDetail($0.id) }
            )

        case .detail(let id):
            if let bean = vm.bean(id) {
                DetailView(
                    bean: bean,
                    allBrews: vm.allBrews,
                    onBack: { vm.closeOverlay() },
                    onEdit: { vm.openEdit(id) },
                    onDelete: {
                        vm.delete(id: id)
                        vm.closeOverlay()
                    },
                    onLogBrew: { vm.addBrew(beanId: id, brew: $0) },
                    notify: { vm.notify($0) }
                )
            } else {
                // Deleted underneath us — fall back to the tab.
                Color.clear.onAppear { vm.closeOverlay() }
            }

        case .edit(let id):
            AddEditView(
                existing: id.flatMap { vm.bean($0) },
                onSave: { bean in
                    vm.upsert(bean)
                    vm.openDetail(bean.id)
                },
                onBack: { vm.goBack() }
            )
        }
    }

    @ViewBuilder
    private var bannerView: some View {
        if let banner = vm.banner {
            Text(banner)
                .font(Type.bodyMedium)
                .foregroundStyle(Palette.parchment)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(Palette.surfaceHigh, in: Capsule())
                .shadow(color: .black.opacity(0.4), radius: 8, y: 3)
                .padding(.bottom, 100)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut, value: vm.banner)
        }
    }
}
