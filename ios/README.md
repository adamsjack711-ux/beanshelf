# Beanshelf for iOS

Native SwiftUI port of the Android app. Same three screens (Shelf → Detail →
Add/Edit), same "dark roastery" design, same `beans.json` data shape. Apple's
Vision framework stands in for ML Kit: `VNRecognizeTextRequest` does the label
OCR and objectness-based saliency does the bag auto-crop — both fully on-device.

## Requirements

- **Full Xcode** (15 or newer) from the Mac App Store — the Command Line Tools
  alone cannot build iOS apps. After installing, run:
  ```bash
  sudo xcode-select -s /Applications/Xcode.app
  ```
  and open Xcode once so it installs the iOS platform.
- An Apple ID for signing (a free one works — no paid developer account needed
  to run on your own iPhone; free-account installs re-sign every 7 days).

## Build & run

The Xcode project is generated from `project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(already installed via Homebrew). If `Beanshelf.xcodeproj` is missing or you
add/remove source files, regenerate it:

```bash
cd ios && xcodegen
```

Then:

1. `open ios/Beanshelf.xcodeproj`
2. Select the **Beanshelf** target → *Signing & Capabilities* → pick your team
   (add your Apple ID under Xcode ▸ Settings ▸ Accounts if it's not there).
3. Choose a simulator or your iPhone and hit **Run**.

On a physical iPhone you'll need Developer Mode on (Settings ▸ Privacy &
Security ▸ Developer Mode) and to trust the developer certificate on first
launch (Settings ▸ General ▸ VPN & Device Management).

The simulator has no camera; the camera buttons fall back to the photo library
there. OCR autofill and auto-crop work in the simulator via gallery imports.

## Layout

```
ios/
  project.yml            XcodeGen spec (bundle id dev.adamsjack.beanshelf, iOS 17+)
  Beanshelf/
    BeanshelfApp.swift   entry point + NavigationStack routing
    Models.swift         Bean/Brew (Codable, Android-compatible JSON keys)
    Theme.swift          dark-roastery palette + serif type scale
    Components.swift     RoastStamp, ShelfPlank, Eyebrow, chips, FlowLayout, PhotoImage
    AppViewModel.swift   observable store facade
    Data/
      BeanStore.swift    Documents/beans.json persistence
      PhotoStore.swift   import → downscale ≤1600px → Documents/photos; cached decode
      LabelScanner.swift Vision OCR + coffee-label heuristics (port of ML Kit version)
      BagCropper.swift   Vision saliency crop-to-bag (port of ML Kit object detect)
      Affiliate.swift    shop links — paste your Amazon tag / eBay campid here
    Views/
      ShelfView.swift    planks + tilted bag cards + FAB
      DetailView.swift   hero photo, meta, shop links, brew log, log-a-brew sheet
      AddEditView.swift  photo capture + OCR autofill + fields + rating
      CameraPicker.swift UIImagePickerController wrapper
```

One deliberate difference from Android: `photoPath` in `beans.json` holds just
a filename on iOS (resolved against `Documents/photos`), because iOS app
containers get a new absolute path on every reinstall. The decoder tolerates
either form.
