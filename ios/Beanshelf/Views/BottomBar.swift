import SwiftUI

/// The four persistent bottom-bar destinations.
enum AppTab: CaseIterable {
    case feed, shelf, profile, settings

    var label: String {
        switch self {
        case .feed: return "Feed"
        case .shelf: return "Shelf"
        case .profile: return "Profile"
        case .settings: return "Settings"
        }
    }

    var icon: String {
        switch self {
        case .feed: return "rectangle.stack.fill"
        case .shelf: return "storefront.fill"
        case .profile: return "person.fill"
        case .settings: return "gearshape.fill"
        }
    }
}

/// Simple persistent bottom bar: Feed · Shelf · Profile · Settings.
struct BottomBar: View {
    let current: AppTab
    let onSelect: (AppTab) -> Void

    var body: some View {
        VStack(spacing: 0) {
            // hairline divider in the accent's muted tone
            Palette.dim.opacity(0.18).frame(height: 1)
            HStack {
                ForEach(AppTab.allCases, id: \.self) { tab in
                    let selected = tab == current
                    Button {
                        onSelect(tab)
                    } label: {
                        VStack(spacing: 3) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 20))
                                .foregroundStyle(selected ? Palette.crema : Palette.dim)
                            Text(tab.label)
                                .font(Type.labelSmall)
                                .kerning(0.5)
                                .foregroundStyle(selected ? Palette.crema : Palette.dim)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(height: 62)
        }
        .background(Palette.surface2)
    }
}
