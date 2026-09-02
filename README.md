# Beanshelf

A coffee-bag tracker built like a shelf you can actually look at — Untappd × Delicious Library
for the bags of beans you've bought. Photograph the bag, let OCR fill in the roaster, origin,
process and roast date, then rate it, log your brews, and share the shelf with other people.

Three codebases, one app:

| Directory | What it is |
|-----------|------------|
| `app/` | **Android** — Kotlin + Jetpack Compose. ML Kit for label OCR. |
| `ios/` | **iOS** — SwiftUI, at feature parity with Android. Apple Vision for label OCR and bag auto-crop, fully on-device. |
| `server/` | **Social backend** — FastAPI + SQLite. Accounts, follows, posts, feed, per-user leaderboards. |

## Get the app

### iPhone / iPad

Beanshelf is distributed through [AltStore](https://altstore.io) / [SideStore](https://sidestore.io),
so no paid Apple developer account is involved — your own free Apple ID re-signs the
app on device.

1. Install AltStore or SideStore on your device.
2. Add this source: **`https://beans.beanshelf.ca/repo/apps.json`**
3. Find **Beanshelf** in the source and tap **Install**.

Or open [beans.beanshelf.ca/repo](https://beans.beanshelf.ca/repo) on the device and follow the link.

Requires iOS 17 or newer. Free Apple ID installs need re-signing every 7 days — AltStore
does that automatically while it can reach your Mac.

### Android

Building from source for now — see [Running it](#running-it) below.

## Features

- **Real bag photos, not stock art.** Bags are auto-cropped from a photo and stood up on a shelf.
- **OCR autofill.** Point the camera at a label; roaster, origin, varietal, process and roast date get filled in.
- **Ratings + flavour wheel.** Score a bag, tag what you tasted.
- **Brew log.** Method, dose, yield, time — per bag.
- **Social.** Follow people, post bags to a feed, cheer and comment, per-user leaderboards.
- **Six themes**, bottom-bar navigation, shareable bag cards.

## Running it

### Android

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### iOS

Needs full Xcode. The project is generated from `ios/project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd ios && xcodegen && open Beanshelf.xcodeproj
```

Pick your signing team, choose a simulator or device, Run. Details and caveats in [`ios/README.md`](ios/README.md).

### Server

```bash
cd server && python3 -m pip install fastapi uvicorn && uvicorn main:app --port 8000
```

See [`server/README.md`](server/README.md). `server/beanshelf.db` is runtime state and is
deliberately not tracked — the server creates it on first run.

## License

MIT — see [LICENSE](LICENSE).
