#!/bin/bash
# Builds an UNSIGNED Beanshelf.ipa for AltStore/SideStore distribution.
# The store app re-signs it on-device with each user's own free Apple ID,
# so no Apple Developer Program membership is needed on either side.
#
# Usage: ./scripts/build-ipa.sh   (from ios/, or anywhere)
# Output: ios/dist/beanshelf-<version>.ipa
set -euo pipefail

IOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$IOS_DIR/build"
DIST="$IOS_DIR/dist"

cd "$IOS_DIR"
xcodegen >/dev/null

xcodebuild \
    -project Beanshelf.xcodeproj \
    -scheme Beanshelf \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -derivedDataPath "$BUILD" \
    CODE_SIGNING_ALLOWED=NO \
    build | grep -E "error:|warning: [^M]|BUILD" || true

APP="$BUILD/Build/Products/Release-iphoneos/Beanshelf.app"
[ -d "$APP" ] || { echo "build failed: $APP missing" >&2; exit 1; }

VERSION=$(defaults read "$APP/Info.plist" CFBundleShortVersionString)
mkdir -p "$DIST"
STAGE=$(mktemp -d)
mkdir "$STAGE/Payload"
cp -R "$APP" "$STAGE/Payload/"
IPA="$DIST/beanshelf-$VERSION.ipa"
rm -f "$IPA"
(cd "$STAGE" && zip -qr "$IPA" Payload)
rm -rf "$STAGE"

echo "Built $IPA ($(du -h "$IPA" | cut -f1 | xargs), version $VERSION)"
