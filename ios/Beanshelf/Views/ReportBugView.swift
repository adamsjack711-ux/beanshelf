import SwiftUI

/// "Report a bug" composer. Writes the report, shows exactly what will be sent,
/// then hands off to GitHub Issues (or email, where an address is configured).
struct ReportBugView: View {
    let beanCount: Int
    let onClose: () -> Void

    @ObservedObject private var theme = ThemeHolder.shared
    @Environment(\.openURL) private var openURL

    @State private var kind: BugReport.Kind = .bug
    @State private var what = ""
    @State private var steps = ""
    @State private var expected = ""
    @State private var includeDiagnostics = true
    @State private var showDiagnostics = false

    private var diagnostics: BugReport.Diagnostics {
        BugReport.diagnostics(beanCount: beanCount, theme: theme.palette.label)
    }

    private var attached: BugReport.Diagnostics? { includeDiagnostics ? diagnostics : nil }
    private var canSend: Bool { !what.trimmed.isEmpty }

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header

                    Eyebrow(text: "What kind").padding(.top, 20).padding(.bottom, 8)
                    ChoiceChips(
                        options: BugReport.Kind.allCases.map(\.rawValue),
                        selected: kind.rawValue,
                        onSelect: { picked in
                            kind = BugReport.Kind.allCases.first { $0.rawValue == picked } ?? .bug
                        }
                    )

                    field(
                        title: kind == .bug ? "What went wrong" : "What would you like",
                        hint: kind == .bug
                            ? "The more specific the better — what you tapped, what happened instead."
                            : "Describe the thing you wish Beanshelf did.",
                        text: $what,
                        minHeight: 110
                    )

                    if kind == .bug {
                        field(title: "Steps to reproduce", hint: "Optional. 1. Open the shelf  2. Tap a bag  3. …",
                              text: $steps, minHeight: 80)
                        field(title: "What you expected", hint: "Optional.",
                              text: $expected, minHeight: 60)
                    }

                    diagnosticsSection
                    actions
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Report a bug")
                .font(Type.headlineMedium)
                .foregroundStyle(Palette.parchment)
            Spacer()
            Button("Close", action: onClose)
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
        }
        .padding(.top, 12)
    }

    private func field(title: String, hint: String, text: Binding<String>, minHeight: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: title)
            Text(hint)
                .font(Type.bodySmall)
                .foregroundStyle(Palette.dim)
            TextEditor(text: text)
                .scrollContentBackground(.hidden)
                .frame(minHeight: minHeight)
                .font(Type.bodyMedium)
                .outlinedField()
        }
        .padding(.top, 20)
    }

    private var diagnosticsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle(isOn: $includeDiagnostics) {
                Text("Include diagnostics")
                    .font(Type.bodyLarge)
                    .foregroundStyle(Palette.parchment)
            }
            .tint(Palette.crema)

            Text("App and iOS version, device model, your theme, and how many bags are on your shelf. No bag details, no photos, no account info.")
                .font(Type.bodySmall)
                .foregroundStyle(Palette.dim)

            if includeDiagnostics {
                Button(showDiagnostics ? "Hide what's included" : "Show exactly what's included") {
                    withAnimation { showDiagnostics.toggle() }
                }
                .font(Type.labelSmall)
                .foregroundStyle(Palette.crema)

                if showDiagnostics {
                    Text(diagnostics.block)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(Palette.dim)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Palette.surface2))
                }
            }
        }
        .padding(.top, 24)
    }

    private var actions: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                if let url = BugReport.githubURL(kind: kind, what: what, steps: steps,
                                                 expected: expected, diagnostics: attached) {
                    openURL(url)
                }
            } label: {
                Text("Open a GitHub issue")
                    .font(Type.labelLarge)
                    .foregroundStyle(Palette.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Palette.crema))
            }
            .disabled(!canSend)
            .opacity(canSend ? 1 : 0.45)

            if BugReport.reportEmail != nil {
                Button {
                    if let url = BugReport.mailURL(kind: kind, what: what, steps: steps,
                                                   expected: expected, diagnostics: attached) {
                        openURL(url)
                    }
                } label: {
                    Text("Send as email instead")
                        .font(Type.labelLarge)
                        .foregroundStyle(Palette.crema)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.dim.opacity(0.4)))
                }
                .disabled(!canSend)
                .opacity(canSend ? 1 : 0.45)
            }

            Text("Opens a prefilled report you can read and edit before submitting. Nothing is sent from the app itself.")
                .font(Type.bodySmall)
                .foregroundStyle(Palette.dim)
        }
        .padding(.top, 28)
    }
}
