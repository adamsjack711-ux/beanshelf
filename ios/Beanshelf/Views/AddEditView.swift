import SwiftUI
import PhotosUI

struct AddEditView: View {
    let existing: Bean?
    let onSave: (Bean) -> Void
    let onBack: () -> Void

    @State private var name: String
    @State private var roaster: String
    @State private var origin: String
    @State private var roastLevel: String
    @State private var process: String
    @State private var notes: String
    @State private var variety: String
    @State private var elevation: String
    @State private var producer: String
    @State private var roastedOn: String
    @State private var rating: Double
    @State private var photoFile: String?
    @State private var backPhotoFile: String?

    @State private var scanning = false
    @State private var scannedFields: [String] = []
    // Uncertain scan results wait here for the user's confirmation sheet.
    @State private var unsureProposals: [(key: String, value: String)] = []
    // Manual crop target: filename being cropped + whether it's the back photo.
    @State private var cropTarget: CropTarget?
    // Tapping an existing photo only VIEWS it; the camera stays behind its buttons.
    @State private var viewerFile: ViewerFile?

    // One camera sheet and one gallery picker are shared by the front and back
    // photo slots; this flag routes the incoming image.
    @State private var capturingBack = false
    @State private var showCamera = false
    @State private var showGallery = false
    @State private var galleryItem: PhotosPickerItem?

    private struct CropTarget: Identifiable {
        let file: String
        let isBack: Bool
        var id: String { file }
    }

    private struct ViewerFile: Identifiable {
        let file: String
        var id: String { file }
    }

    init(existing: Bean?, onSave: @escaping (Bean) -> Void, onBack: @escaping () -> Void) {
        self.existing = existing
        self.onSave = onSave
        self.onBack = onBack
        _name = State(initialValue: existing?.name ?? "")
        _roaster = State(initialValue: existing?.roaster ?? "")
        _origin = State(initialValue: existing?.origin ?? "")
        _roastLevel = State(initialValue: existing?.roastLevel ?? "")
        _process = State(initialValue: existing?.process ?? "")
        _notes = State(initialValue: existing?.notes ?? "")
        _variety = State(initialValue: existing?.variety ?? "")
        _elevation = State(initialValue: existing?.elevation ?? "")
        _producer = State(initialValue: existing?.producer ?? "")
        _roastedOn = State(initialValue: existing?.roastedOn ?? "")
        _rating = State(initialValue: existing?.rating ?? 0)
        _photoFile = State(initialValue: existing?.photoFile)
        _backPhotoFile = State(initialValue: existing?.backPhotoFile)
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PhotoPickerBlock(
                        photoFile: photoFile,
                        onCamera: { openCamera(back: false) },
                        onGallery: { openGallery(back: false) },
                        onCrop: { if let f = photoFile { cropTarget = CropTarget(file: f, isBack: false) } },
                        onView: { if let f = photoFile { viewerFile = ViewerFile(file: f) } }
                    )

                    if photoFile != nil {
                        BackPhotoStrip(
                            backPhotoFile: backPhotoFile,
                            onCamera: { openCamera(back: true) },
                            onGallery: { openGallery(back: true) },
                            onCrop: { if let f = backPhotoFile { cropTarget = CropTarget(file: f, isBack: true) } },
                            onView: { if let f = backPhotoFile { viewerFile = ViewerFile(file: f) } }
                        )
                    }

                    scanStatus

                    Eyebrow(text: "The bag").padding(.top, 26).padding(.bottom, 10)
                    LabeledField(text: $name, label: "Bean name")
                    LabeledField(text: $roaster, label: "Roaster")
                    LabeledField(text: $origin, label: "Origin — country or farm")

                    Eyebrow(text: "Details").padding(.top, 22).padding(.bottom, 10)
                    LabeledField(text: $producer, label: "Producer — farmer or farm")
                    LabeledField(text: $variety, label: "Variety — e.g. Pacas, Bourbon")
                    LabeledField(text: $elevation, label: "Elevation — e.g. 1,650 masl")
                    LabeledField(text: $roastedOn, label: "Roasted on — e.g. May 26")

                    Eyebrow(text: "Roast").padding(.top, 22).padding(.bottom, 6)
                    ChoiceChips(options: roastLevels, selected: roastLevel) {
                        roastLevel = roastLevel == $0 ? "" : $0
                    }

                    Eyebrow(text: "Process").padding(.top, 18).padding(.bottom, 6)
                    ChoiceChips(options: processes, selected: process) {
                        process = process == $0 ? "" : $0
                    }

                    Eyebrow(text: "Tasting notes").padding(.top, 22).padding(.bottom, 10)
                    TextField("chocolate, black cherry, florals…", text: $notes, axis: .vertical)
                        .lineLimit(2...6)
                        .outlinedField()

                    Eyebrow(text: "Your rating").padding(.top, 22).padding(.bottom, 4)
                    HStack(spacing: 18) {
                        RoastStamp(rating: rating, size: 56)
                        RatingSlider(value: $rating)
                    }
                    if rating > 0 {
                        Button("Clear rating") { rating = 0 }
                            .font(Type.labelLarge)
                            .foregroundStyle(Palette.dim)
                            .padding(.top, 4)
                    }

                    saveButton
                }
                .padding(.horizontal, 20)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Palette.roast)
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in
                Task { await importAndScan(image, isBack: capturingBack) }
            }
            .ignoresSafeArea()
        }
        .fullScreenCover(item: $cropTarget) { target in
            CropEditorView(file: target.file) { newFile in
                cropTarget = nil
                if let newFile {
                    if target.isBack { backPhotoFile = newFile } else { photoFile = newFile }
                }
            }
        }
        .fullScreenCover(item: $viewerFile) { v in
            PhotoViewer(file: v.file) { viewerFile = nil }
        }
        .sheet(isPresented: Binding(
            get: { !unsureProposals.isEmpty },
            set: { if !$0 { unsureProposals = [] } }
        )) {
            ScanReviewSheet(
                proposals: unsureProposals,
                onDismiss: { unsureProposals = [] },
                onApply: { accepted in
                    let applied = accepted.filter { applyField($0.key, $0.value) }.map(\.key)
                    scannedFields += applied
                    unsureProposals = []
                }
            )
            .presentationDetents([.medium, .large])
            .presentationBackground(Palette.surface2)
        }
        .photosPicker(isPresented: $showGallery, selection: $galleryItem, matching: .images)
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            galleryItem = nil
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    await importAndScan(image, isBack: capturingBack)
                }
            }
        }
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Palette.parchment)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Back")
            Text(existing == nil ? "New bag" : "Edit bag")
                .font(Type.titleLarge)
                .foregroundStyle(Palette.parchment)
            Spacer()
        }
        .padding(.horizontal, 8)
    }

    @ViewBuilder
    private var scanStatus: some View {
        if scanning {
            HStack(spacing: 8) {
                ProgressView().tint(Palette.crema).scaleEffect(0.7)
                Text("Reading the label…")
                    .font(Type.bodySmall)
                    .foregroundStyle(Palette.dim)
            }
            .padding(.top, 4)
        } else if !scannedFields.isEmpty {
            Text("Filled from the label: \(scannedFields.joined(separator: ", ")) — double-check the details.")
                .font(Type.bodySmall)
                .foregroundStyle(Palette.crema)
                .padding(.top, 4)
        }
    }

    private var saveButton: some View {
        Button {
            onSave(Bean(
                id: existing?.id ?? UUID().uuidString,
                name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                roaster: roaster.trimmingCharacters(in: .whitespacesAndNewlines),
                origin: origin.trimmingCharacters(in: .whitespacesAndNewlines),
                roastLevel: roastLevel,
                process: process,
                notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
                variety: variety.trimmingCharacters(in: .whitespacesAndNewlines),
                elevation: elevation.trimmingCharacters(in: .whitespacesAndNewlines),
                producer: producer.trimmingCharacters(in: .whitespacesAndNewlines),
                roastedOn: roastedOn.trimmingCharacters(in: .whitespacesAndNewlines),
                rating: rating,
                photoFile: photoFile,
                backPhotoFile: backPhotoFile,
                createdAt: existing?.createdAt ?? nowMillis(),
                brews: existing?.brews ?? []
            ))
        } label: {
            Text(existing == nil ? "Put it on the shelf" : "Save changes")
                .font(Type.labelLarge)
                .foregroundStyle(name.isEmpty ? Palette.dim : Palette.roast)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(
                    name.isEmpty ? Palette.surfaceHigh : Palette.crema,
                    in: RoundedRectangle(cornerRadius: 12)
                )
        }
        .disabled(name.isEmpty)
        .padding(.top, 28)
        .padding(.bottom, 40)
    }

    private func openCamera(back: Bool) {
        capturingBack = back
        if CameraPicker.isAvailable {
            showCamera = true
        } else {
            // Simulator has no camera — fall through to the gallery.
            showGallery = true
        }
    }

    private func openGallery(back: Bool) {
        capturingBack = back
        showGallery = true
    }

    /// Applies one scanned value to its field ONLY if the user left it blank.
    private func applyField(_ key: String, _ value: String) -> Bool {
        func fill(_ current: inout String) -> Bool {
            guard current.isEmpty else { return false }
            current = value
            return true
        }
        switch key {
        case "name": return fill(&name)
        case "roaster": return fill(&roaster)
        case "origin": return fill(&origin)
        case "roast": return fill(&roastLevel)
        case "process": return fill(&process)
        case "notes": return fill(&notes)
        case "variety": return fill(&variety)
        case "elevation": return fill(&elevation)
        case "producer": return fill(&producer)
        case "roasted": return fill(&roastedOn)
        default: return false
        }
    }

    // Cut out the bag, show it, then OCR the label. Confident fields (keyword-
    // derived) fill blanks silently; guesses go to a confirmation sheet instead
    // of being written — the user removes mistakes before they land.
    private func importAndScan(_ image: UIImage, isBack: Bool) async {
        scanning = true
        defer { scanning = false }

        guard let imported = await PhotoStore.importImage(image) else {
            scannedFields = []
            return
        }
        let file = await BagCropper.cutOutBag(file: imported)
        if isBack { backPhotoFile = file } else { photoFile = file }

        guard let url = PhotoStore.url(for: file),
              let info = await LabelScanner.scan(fileURL: url) else {
            scannedFields = []
            return
        }
        var filled: [String] = []
        var ask: [(key: String, value: String)] = []
        let pairs: [(String, String?)] = [
            ("name", info.name), ("roaster", info.roaster), ("origin", info.origin),
            ("roast", info.roastLevel), ("process", info.process), ("notes", info.notes),
            ("variety", info.variety), ("elevation", info.elevation), ("producer", info.producer),
            ("roasted", info.roastedOn),
        ]
        for (key, value) in pairs {
            guard let value else { continue }
            if info.unsure.contains(key) {
                ask.append((key, value))
            } else if applyField(key, value) {
                filled.append(key)
            }
        }
        scannedFields = filled
        unsureProposals = ask
    }
}

/// Confirmation sheet for scan results the parser wasn't sure about. Nothing in
/// this list has touched the form yet — the user unchecks mistakes, then applies.
private struct ScanReviewSheet: View {
    let proposals: [(key: String, value: String)]
    let onDismiss: () -> Void
    let onApply: ([(key: String, value: String)]) -> Void

    @State private var checked: Set<String>

    init(proposals: [(key: String, value: String)],
         onDismiss: @escaping () -> Void,
         onApply: @escaping ([(key: String, value: String)]) -> Void) {
        self.proposals = proposals
        self.onDismiss = onDismiss
        self.onApply = onApply
        _checked = State(initialValue: Set(proposals.map(\.key)))
    }

    private let labels: [String: String] = [
        "name": "Bean name", "roaster": "Roaster", "origin": "Origin",
        "roast": "Roast level", "process": "Process", "notes": "Tasting notes",
        "variety": "Variety", "elevation": "Elevation", "producer": "Producer",
        "roasted": "Roast date",
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Check what I read")
                    .font(Type.titleLarge)
                    .foregroundStyle(Palette.parchment)
                Text("I'm not certain about these. Uncheck anything that's wrong, or fix it in the form after.")
                    .font(Type.bodyMedium)
                    .foregroundStyle(Palette.dim)
                    .padding(.top, 6)
                    .padding(.bottom, 10)

                ForEach(proposals, id: \.key) { proposal in
                    Button {
                        if checked.contains(proposal.key) {
                            checked.remove(proposal.key)
                        } else {
                            checked.insert(proposal.key)
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: checked.contains(proposal.key) ? "checkmark.square.fill" : "square")
                                .font(.system(size: 22))
                                .foregroundStyle(checked.contains(proposal.key) ? Palette.crema : Palette.dim)
                            VStack(alignment: .leading, spacing: 2) {
                                Eyebrow(text: labels[proposal.key] ?? proposal.key)
                                Text(proposal.value)
                                    .font(Type.bodyLarge)
                                    .foregroundStyle(Palette.parchment)
                                    .multilineTextAlignment(.leading)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }

                HStack {
                    Button("Skip all", action: onDismiss)
                        .font(Type.labelLarge)
                        .foregroundStyle(Palette.dim)
                    Spacer()
                    Button {
                        onApply(proposals.filter { checked.contains($0.key) })
                    } label: {
                        Text("Use checked")
                            .font(Type.labelLarge)
                            .foregroundStyle(Palette.roast)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 12)
                            .background(Palette.crema, in: Capsule())
                    }
                }
                .padding(.top, 16)
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 36)
        }
    }
}

private struct LabeledField: View {
    @Binding var text: String
    let label: String

    var body: some View {
        TextField(label, text: $text)
            .outlinedField()
            .padding(.bottom, 12)
    }
}

private struct PhotoPickerBlock: View {
    let photoFile: String?
    let onCamera: () -> Void
    let onGallery: () -> Void
    let onCrop: () -> Void
    let onView: () -> Void

    private var cutout: Bool { BagCropper.isCutout(photoFile) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Empty slot: tap opens the camera. Existing photo: tap just views it.
            Button(action: photoFile == nil ? onCamera : onView) {
                PhotoImage(
                    file: photoFile,
                    targetWidth: 900,
                    // Cutouts have transparent surroundings — never zoom-crop them.
                    contentMode: cutout ? .fit : .fill
                ) {
                    VStack(spacing: 0) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 34))
                            .foregroundStyle(Palette.crema)
                        Text("Photograph the bag")
                            .font(Type.titleMedium)
                            .foregroundStyle(Palette.parchment)
                            .padding(.top, 12)
                        Text("Tap to open the camera")
                            .font(Type.bodyMedium)
                            .foregroundStyle(Palette.dim)
                            .padding(.top, 4)
                    }
                }
                .padding(cutout ? 12 : 0)
                .frame(height: 280)
                .frame(maxWidth: .infinity)
                .background(Palette.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay {
                    if photoFile == nil {
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(
                                Palette.dim.opacity(0.6),
                                style: StrokeStyle(lineWidth: 1.5, dash: [7, 6])
                            )
                    }
                }
            }
            .buttonStyle(.plain)

            HStack(spacing: 8) {
                PhotoActionButton(systemName: "camera.fill", title: photoFile == nil ? "Camera" : "Retake", action: onCamera)
                PhotoActionButton(systemName: "photo.on.rectangle", title: "Gallery", action: onGallery)
                if photoFile != nil {
                    PhotoActionButton(systemName: "crop", title: "Crop", action: onCrop)
                }
            }
        }
        .padding(.top, 8)
    }
}

/// Optional back-of-bag capture: small thumbnail + actions; scanned for extra info.
private struct BackPhotoStrip: View {
    let backPhotoFile: String?
    let onCamera: () -> Void
    let onGallery: () -> Void
    let onCrop: () -> Void
    let onView: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Button(action: backPhotoFile == nil ? onCamera : onView) {
                PhotoImage(
                    file: backPhotoFile,
                    targetWidth: 300,
                    contentMode: BagCropper.isCutout(backPhotoFile) ? .fit : .fill
                ) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Palette.dim)
                }
                .frame(width: 64, height: 80)
                .background(Palette.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay {
                    if backPhotoFile == nil {
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(
                                Palette.dim.opacity(0.5),
                                style: StrokeStyle(lineWidth: 1, dash: [5, 4])
                            )
                    }
                }
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                Text(backPhotoFile == nil ? "Back of the bag (optional)" : "Back of the bag")
                    .font(Type.bodyMedium)
                    .foregroundStyle(Palette.parchment)
                Text(backPhotoFile == nil
                    ? "Scan it for tasting notes and farm details."
                    : "Scanned — details merged below.")
                    .font(Type.bodySmall)
                    .foregroundStyle(Palette.dim)
                HStack(spacing: 16) {
                    Button(backPhotoFile == nil ? "Camera" : "Retake", action: onCamera)
                    Button("Gallery", action: onGallery)
                    if backPhotoFile != nil {
                        Button("Crop", action: onCrop)
                    }
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.crema)
                .padding(.top, 2)
            }
            Spacer()
        }
        .padding(.top, 14)
    }
}

private struct PhotoActionButton: View {
    let systemName: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemName).font(.system(size: 13))
                Text(title)
            }
            .font(Type.labelLarge)
            .foregroundStyle(Palette.crema)
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
        }
    }
}
