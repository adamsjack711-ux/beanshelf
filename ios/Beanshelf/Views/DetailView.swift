import SwiftUI
import UIKit

private let brewDateFormatter: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "MMM d, yyyy · h:mm a"
    return f
}()

struct DetailView: View {
    let bean: Bean
    let allBrews: [Brew] // every brew across the shelf, newest first — used to prefill equipment
    let onBack: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onLogBrew: (Brew) -> Void
    let notify: (String) -> Void

    @State private var showDelete = false
    @State private var showBrewSheet = false
    @State private var viewerFile: String?
    @State private var shareItem: ShareItem?
    @State private var renderingShare = false
    @Environment(\.openURL) private var openURL

    private struct ShareItem: Identifiable {
        let url: URL
        var id: String { url.path }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                hero
                titleBlock
                metaBlock
                buyRow
                if !bean.notes.isEmpty { notesBlock }
                if bean.backPhotoFile != nil { BackPhotoBlock(file: bean.backPhotoFile) }
                brewLogHeader
                if bean.brews.isEmpty {
                    Text("No brews yet. Log the first cup.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 8)
                } else {
                    ForEach(bean.brews) { BrewRow(brew: $0) }
                }
            }
            .padding(.bottom, 48)
        }
        .background(Palette.roast)
        .ignoresSafeArea(edges: .top)
        .alert("Take it off the shelf?", isPresented: $showDelete) {
            Button("Remove", role: .destructive, action: onDelete)
            Button("Keep it", role: .cancel) {}
        } message: {
            Text("This removes the bag, its photo, and its brew log.")
        }
        .sheet(isPresented: $showBrewSheet) {
            LogBrewSheet(allBrews: allBrews) { brew in
                showBrewSheet = false
                onLogBrew(brew)
            }
            .presentationDetents([.large])
            .presentationBackground(Palette.surface2)
        }
        .sheet(item: $shareItem) { item in
            ActivityView(items: [item.url])
                .presentationDetents([.medium, .large])
        }
        .fullScreenCover(item: Binding(
            get: { viewerFile.map { ViewerFile(file: $0) } },
            set: { viewerFile = $0?.file }
        )) { v in
            PhotoViewer(file: v.file) { viewerFile = nil }
        }
    }

    private struct ViewerFile: Identifiable {
        let file: String
        var id: String { file }
    }

    private var isCutout: Bool { BagCropper.isCutout(bean.photoFile) }

    private var hero: some View {
        PhotoImage(
            file: bean.photoFile,
            targetWidth: 1200,
            contentMode: isCutout ? .fit : .fill
        ) {
            ZStack {
                Palette.surfaceHigh
                Image(systemName: "cup.and.saucer.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(Palette.dim)
            }
        }
        .padding(isCutout ? 20 : 0)
        .frame(height: 380)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { viewerFile = bean.photoFile }
        // scrim into the page background so the photo melts into the roastery dark
        .overlay(alignment: .bottom) {
            LinearGradient(colors: [.clear, Palette.roast], startPoint: .top, endPoint: .bottom)
                .frame(height: 140)
                .allowsHitTesting(false)
        }
        .overlay(alignment: .top) {
            HStack {
                ScrimIconButton(systemName: "chevron.left", label: "Back", action: onBack)
                Spacer()
                shareMenu
                ScrimIconButton(systemName: "pencil", label: "Edit bag", action: onEdit)
                ScrimIconButton(systemName: "trash", label: "Remove bag") { showDelete = true }
            }
            .padding(.horizontal, 12)
            .padding(.top, 56)
        }
    }

    /// Post the bean: to the social feed, a share-card image for anywhere,
    /// or a .beanshelf pack for a friend.
    private var shareMenu: some View {
        Menu {
            Button("Post to feed") {
                guard let account = SocialClient.account else {
                    notify("Sign in first — Feed tab in the bottom bar")
                    return
                }
                Task {
                    renderingShare = true
                    do {
                        try await SocialClient.postBean(account, bean: bean)
                        notify("Posted to your feed")
                    } catch {
                        notify(error.localizedDescription)
                    }
                    renderingShare = false
                }
            }
            Button("Post a card (image)") {
                Task {
                    renderingShare = true
                    if let url = await ShareCard.render(bean: bean) {
                        shareItem = ShareItem(url: url)
                    }
                    renderingShare = false
                }
            }
            Button("Send bean to a friend") {
                Task {
                    renderingShare = true
                    if let url = await BeanPack.export(bean) {
                        shareItem = ShareItem(url: url)
                    }
                    renderingShare = false
                }
            }
        } label: {
            ZStack {
                if renderingShare {
                    ProgressView().tint(Palette.parchment).scaleEffect(0.7)
                } else {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Palette.parchment)
                }
            }
            .frame(width: 40, height: 40)
            .background(Palette.roast.opacity(0.55), in: Circle())
        }
        .accessibilityLabel("Share bean")
        .padding(4)
    }

    private var titleBlock: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                if !bean.roaster.isEmpty {
                    Eyebrow(text: bean.roaster, color: Palette.crema)
                }
                Text(bean.name)
                    .font(Type.headlineMedium)
                    .foregroundStyle(Palette.parchment)
            }
            Spacer()
            if bean.rating > 0 {
                RoastStamp(rating: bean.rating, size: 64)
                    .padding(.leading, 12)
            }
        }
        .padding(.leading, 24)
        .padding(.trailing, 20)
        .padding(.top, 2)
    }

    private var metaBlock: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 24) {
                MetaCell(label: "Origin", value: bean.origin)
                MetaCell(label: "Roast", value: bean.roastLevel)
                MetaCell(label: "Process", value: bean.process)
                Spacer()
            }
            if [bean.producer, bean.variety, bean.elevation].contains(where: { !$0.isEmpty }) {
                HStack(alignment: .top, spacing: 24) {
                    MetaCell(label: "Producer", value: bean.producer)
                    MetaCell(label: "Variety", value: bean.variety)
                    MetaCell(label: "Elevation", value: bean.elevation)
                    Spacer()
                }
            }
            if !bean.roastedOn.isEmpty {
                MetaCell(label: "Roasted", value: bean.roastedOn)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 20)
    }

    /// Restock actions: affiliate-tagged retailer links, or a maps search for stockists nearby.
    private var buyRow: some View {
        let query = [bean.roaster, bean.name, "coffee"].filter { !$0.isEmpty }.joined(separator: " ")
        return HStack(spacing: 4) {
            Menu {
                ForEach(Affiliate.shops(for: query)) { shop in
                    Button(shop.label) { openURL(shop.url) }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "cart.fill").font(.system(size: 13))
                    Text("Shop online")
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }
            Button {
                let near = (bean.roaster.isEmpty ? bean.name : bean.roaster) + " coffee"
                if let url = Affiliate.nearbyURL(for: near) { openURL(url) }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "storefront.fill").font(.system(size: 13))
                    Text("Find nearby")
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }
            Spacer()
        }
        .padding(.leading, 16)
        .padding(.bottom, 8)
    }

    private var notesBlock: some View {
        HStack(alignment: .top, spacing: 14) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Palette.crema)
                .frame(width: 3, height: 44)
            Text(bean.notes)
                .font(Type.bodyLarge)
                .italic()
                .foregroundStyle(Palette.parchment.opacity(0.85))
            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 20)
    }

    private var brewLogHeader: some View {
        HStack {
            Eyebrow(text: bean.brews.isEmpty ? "Brew log" : "Brew log · \(bean.brews.count)")
            Spacer()
            Button {
                showBrewSheet = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "cup.and.saucer.fill").font(.system(size: 13))
                    Text("Log a brew")
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
            }
        }
        .padding(.leading, 24)
        .padding(.trailing, 16)
        .padding(.top, 8)
    }
}

/// UIActivityViewController wrapper — the system share sheet.
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private struct ScrimIconButton: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Palette.parchment)
                .frame(width: 40, height: 40)
                .background(Palette.roast.opacity(0.55), in: Circle())
        }
        .accessibilityLabel(label)
        .padding(4)
    }
}

private struct MetaCell: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Eyebrow(text: label)
            Text(value.isEmpty ? "—" : value)
                .font(Type.bodyLarge)
                .foregroundStyle(Palette.parchment)
        }
    }
}

private struct BackPhotoBlock: View {
    let file: String?
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow(text: "The back")
            PhotoImage(file: file, targetWidth: 1000) { Palette.surface2 }
                .frame(height: expanded ? 420 : 160)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .onTapGesture { withAnimation(.easeInOut) { expanded.toggle() } }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 20)
    }
}

private struct BrewRow: View {
    let brew: Brew

    // Recipe line: "18g → 300g (1:16.7) · Ode @ 6"
    private var recipe: String {
        var parts: [String] = []
        switch (brew.doseG, brew.waterG) {
        case let (d?, w?):
            parts.append("\(formatGrams(d))g → \(formatGrams(w))g" + (brew.ratio.map { " (\($0))" } ?? ""))
        case let (d?, nil):
            parts.append("\(formatGrams(d))g dose")
        case let (nil, w?):
            parts.append("\(formatGrams(w))g water")
        default:
            break
        }
        if !brew.grinder.isEmpty || !brew.grindSize.isEmpty {
            parts.append([brew.grinder, brew.grindSize].filter { !$0.isEmpty }.joined(separator: " @ "))
        }
        return parts.joined(separator: "  ·  ")
    }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Text(brew.method)
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Palette.surface2, in: RoundedRectangle(cornerRadius: 6))
            VStack(alignment: .leading, spacing: 2) {
                if !brew.note.isEmpty {
                    Text(brew.note)
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.parchment)
                }
                if !recipe.isEmpty {
                    Text(recipe)
                        .font(Type.bodySmall)
                        .foregroundStyle(Palette.crema.opacity(0.8))
                }
                Text(brewDateFormatter.string(from: Date(timeIntervalSince1970: Double(brew.timestamp) / 1000)))
                    .font(Type.bodySmall)
                    .foregroundStyle(Palette.dim)
            }
            Spacer()
            if brew.rating > 0 {
                Text(formatRating(brew.rating))
                    .font(Type.titleMedium)
                    .foregroundStyle(Palette.stampInk)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 10)
    }
}

private struct LogBrewSheet: View {
    let allBrews: [Brew]
    let onLog: (Brew) -> Void

    @State private var method = brewMethods[0]
    @State private var rating: Double = 0
    @State private var note = ""
    @State private var dose = ""
    @State private var water = ""
    // Equipment rarely changes — prefill from the last brew; grind follows the method.
    @State private var grinder = ""
    @State private var grindSize = ""
    @State private var grindTouched = false
    // SwiftUI onChange fires on programmatic sets too — remember what the
    // prefill wrote so only real user edits mark the grind as touched.
    @State private var lastPrefilledGrind = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Log a brew")
                    .font(Type.titleLarge)
                    .foregroundStyle(Palette.parchment)

                Eyebrow(text: "Method")
                    .padding(.top, 18)
                    .padding(.bottom, 6)
                ChoiceChips(options: brewMethods, selected: method) {
                    method = $0
                    prefillForMethod($0)
                }

                HStack {
                    Eyebrow(text: "Recipe")
                    Spacer()
                    if let ratio = ratioPreview {
                        Text(ratio)
                            .font(Type.titleMedium)
                            .foregroundStyle(Palette.crema)
                    }
                }
                .padding(.top, 18)
                .padding(.bottom, 6)
                HStack(spacing: 12) {
                    TextField("Dose (g)", text: $dose)
                        .keyboardType(.decimalPad)
                        .outlinedField()
                    TextField("Water / yield (g)", text: $water)
                        .keyboardType(.decimalPad)
                        .outlinedField()
                }

                Eyebrow(text: "Equipment")
                    .padding(.top, 16)
                    .padding(.bottom, 6)
                HStack(spacing: 12) {
                    TextField("Grinder", text: $grinder)
                        .outlinedField()
                    TextField("Grind", text: $grindSize)
                        .onChange(of: grindSize) { _, new in
                            if new != lastPrefilledGrind { grindTouched = true }
                        }
                        .outlinedField()
                }

                Eyebrow(text: "The cup")
                    .padding(.top, 16)
                    .padding(.bottom, 4)
                HStack(spacing: 16) {
                    RoastStamp(rating: rating, size: 48)
                    RatingSlider(value: $rating)
                }

                TextField("Tasted like…", text: $note, axis: .vertical)
                    .lineLimit(2...6)
                    .outlinedField()
                    .padding(.top, 10)

                Button {
                    onLog(Brew(
                        id: UUID().uuidString,
                        method: method,
                        rating: rating,
                        note: note.trimmingCharacters(in: .whitespacesAndNewlines),
                        timestamp: nowMillis(),
                        doseG: Double(dose),
                        waterG: Double(water),
                        grinder: grinder.trimmingCharacters(in: .whitespaces),
                        grindSize: grindSize.trimmingCharacters(in: .whitespaces)
                    ))
                } label: {
                    Text("Log it")
                        .font(Type.labelLarge)
                        .foregroundStyle(Palette.roast)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Palette.crema, in: RoundedRectangle(cornerRadius: 12))
                }
                .padding(.top, 20)
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 36)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            grinder = allBrews.first { !$0.grinder.isEmpty }?.grinder ?? ""
            prefillForMethod(method)
        }
    }

    private var ratioPreview: String? {
        guard let d = Double(dose), let w = Double(water), d > 0, w > 0 else { return nil }
        return formatRatio(w / d)
    }

    private func prefillForMethod(_ m: String) {
        if grindTouched { return }
        guard let last = allBrews.first(where: { $0.method == m }) else { return }
        if !last.grindSize.isEmpty {
            lastPrefilledGrind = last.grindSize
            grindSize = last.grindSize
        }
        if dose.isEmpty, let d = last.doseG { dose = formatGrams(d) }
        if water.isEmpty, let w = last.waterG { water = formatGrams(w) }
    }
}
