<!-- SPDX-License-Identifier: Apache-2.0 -->
# Build notes & core gaps

This app consumes the shared Go engine as a gomobile `.aar` (we never modify
`caravel/go`). Real gaps are tracked here.

## The engine `.aar` and how to (re)build it

```
cd ../caravel && ./build-bindings.sh android   # → caravel/dist/caravel.aar
cp ../caravel/dist/caravel.aar app/libs/caravel.aar
./gradlew :app:assembleDebug                    # JAVA_HOME = Android Studio's JBR
```

The `.aar` is a rebuildable artifact and is **gitignored** (`app/libs/*.aar`).
Build it once after cloning. The app's `compileOnly` + reflective `CoreBridge`
means the project still *compiles* without it, but a runnable APK needs it
packaged (it carries `libgojni.so`).

## CORE GAP — the exported engine surface is still the C1 stub

As of this writing the built `caravel.aar` only exports the C1 validation stub:

```
core.Core.version() -> String
core.Core.newTunnel(endpoint) -> core.Tunnel   // Tunnel.start()/stop() are no-ops
```

The **full surface this client is written against** (per the engine contract)
is **not implemented in `caravel/go` yet**:

```
Core.initStore(dir)
Core.importBundle(path) -> name
Core.syncAndStore(pharosidBytes, email, pass) -> name   // login/sync; replace-all
Core.listProfiles() -> JSON
Core.controllerStatus(bundleName) -> JSON
Core.reachable(pharosidBytes, timeoutMs) -> bool
Core.logout() -> count
Core.connect(bundleName, profileName, protoPref, tunFd) -> Session
Session.stats() -> JSON ; Session.stop()
```

### How the app handles the gap (no UI changes when it lands)

`core/CoreBridge.kt` is the single seam. It declares the full surface and binds
to whatever the running `.aar` exports **via reflection**:

- Methods that exist are called directly.
- Methods that don't throw `CoreBridge.EngineUnavailable`; callers catch it and
  show a clear *"needs the full engine — rebuild caravel.aar"* state instead of
  crashing or faking a tunnel.

So **today, with the stub `.aar`:**

| Feature | Status |
|---|---|
| Launch, theme, the **map** (land, graticule, arcs, pins, flow, zoom) | ✅ works |
| **Import** a `.pharos` | ✅ works (stored + parsed natively) |
| Profile **list / select / badges / cascade path / node IPs** (plaintext bundles) | ✅ works (parsed in Kotlin, mirroring `profile/pharos.go`) |
| Map **controller pin + solid control-plane line** (from `control{}`) | ✅ works |
| Engine version chip | ✅ works (`Core.version()`) |
| Enable / disable / delete (file-store markers) | ✅ works |
| **Cloud sync / Sync now / Log out** | ⏳ needs `syncAndStore`/`logout` |
| **Controller reachability dot + last-synced** | ⏳ needs `controllerStatus`/`reachable` |
| **Connect / live stats** (the tunnel) | ⏳ needs `connect`/`Session` |

When `caravel/go` grows the full `core` package and the `.aar` is rebuilt, the
reflective bindings resolve and these light up with **no app code change**. If
the final method *signatures* differ slightly from the assumed shape, the only
edits are in `CoreBridge.kt` (one method each).

## Other notes

- **Keystore:** the account passphrase is the only persisted secret, in a
  Keystore-backed `EncryptedSharedPreferences` (`core/SecureStore.kt`) — the
  Android half of the cloud-sync §4 contract.
- **File-store conventions** (`model/ProfileStore.kt`) are reimplemented in
  Kotlin per docs/cloud-sync.md §2/§5 (`.pharos` / `.pharosid` / `.synced` /
  `.disabled`, replace-all on sync, logout purge). The `.synced` marker is
  expected to be written by the engine's `syncAndStore`; until that exists, the
  store treats any bundle with a `.synced` sidecar as cloud.
- **Launcher icon** is a true VectorDrawable port of `caravel-mac/.assets/icon.svg`
  (no PNG rasterizer was available); maroon adaptive background + cream beacon
  mark + monochrome layer.
- **TUN address** is a sane default (`10.86.0.2/32`, full default route). The
  engine's `connect` is the authority on the real tunnel address; when it lands
  we may pass the resolved address back to the Builder (a `connect` that also
  reports the address, or a small `resolveTun` helper, would be the clean way).
