<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".assets/logo-inverse.svg">
    <img src=".assets/logo.svg" alt="PharosVPN" width="120" height="120">
  </picture>
</p>
# caravel-android

PharosVPN mobile client — Android implementation in Kotlin.

> Part of [PharosVPN](https://github.com/PharosVPN) — see [`docs/BUILD.md`](https://github.com/PharosVPN/docs/blob/main/BUILD.md) for the platform roadmap.

## Architecture

Shared Go core (via `gomobile`) + native Kotlin / Jetpack Compose UI. Mirrors the
macOS client (`caravel-mac`) in features and polish.

- `app/` — the Android app (Compose Material3, Android `VpnService`, Keystore)
  - `model/` — `.pharos` parsing + the on-disk profile store (cloud-sync §2/§5),
    region→coord table, the map model
  - `core/` — `CoreBridge` (the seam onto the gomobile `.aar`) + `SecureStore`
    (Keystore-backed `EncryptedSharedPreferences` for the account passphrase)
  - `vpn/` — `CaravelVpnService` (owns the TUN, runs the Go engine over the fd) +
    `TunnelBus` (in-process status/stats stream)
  - `ui/` — the Compose UI: the signature `LandMap` canvas, the profile list,
    the controller card, the connect detail, the sign-in sheet
- `app/libs/caravel.aar` — the gomobile-built Go engine (rebuildable, gitignored)

## Build

Uses the Android SDK at `~/Library/Android/sdk` and Android Studio's bundled JDK.

```sh
# 1) build the Go engine and drop it into app/libs (one time, and when go/ changes)
cd ../caravel && ./build-bindings.sh android
cp dist/caravel.aar ../caravel-android/app/libs/caravel.aar

# 2) build the debug APK
cd ../caravel-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk  (sideloadable)
```

See [`NOTES.md`](./NOTES.md) for the current engine-surface gap and exactly which
features are live today vs. waiting on the full `core` package.

## Status

Pre-alpha sideloadable debug APK (not Play-signed). The map, file import, and
profile list (plaintext bundles parsed natively), the controller pin, theme, and
icon are live. Cloud sync, controller reachability, and the tunnel itself light
up when the engine `.aar` grows the full `core` surface — see
[`NOTES.md`](./NOTES.md) (no app changes needed).

## License

Apache-2.0. Contributions under the DCO (`git commit -s`).
