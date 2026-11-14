# GeoTrace — Group 14 Onboarding & System Guide

Welcome to **GeoTrace** (`com.geotrace`), Group 14's Android GIS & real-time telemetry application for **BSE SWE2105**.  
This guide gives every group member a clear mental model of **how the app works**, **common misconceptions to avoid**, **architectural rules**, and **how to build and test it on real phones**.

---

## 1. Plain-English Overview of GeoTrace

GeoTrace solves **SWE2105 Option 2 (Mobile Phone Tracking)** using real on-device sensors rather than coarse IP geolocation:

- **Bottom Tab 1 — `Map`**: 5-layer cartographic viewport (OSM Streets, Esri Satellite Imagery, Satellite + Labels Hybrid, OpenTopoMap, Humanitarian HOT) + **Offline Maps Manager** (`Map View`, `2 km`, `5 km`), telemetry HUD (coordinates, EMA-smoothed speed, gait state, accuracy ring, distance, waypoints), positioning mode selector, active trail recording (`REC`), bearing-rotated user chevron, trail `S`/`E` pins, plus live pins for your other devices, group members, P2P peers, geofence zones, and dBm-colored cell tower rays.
- **Bottom Tab 2 — `Cellular Hub`**: Live hardware radio telemetry from Android `TelephonyManager` (CID/NCI, LAC/TAC, MCC, MNC, signal dBm, LTE/5G/3G/2G, serving + neighbor cells) and optional coordinate resolution via the **OpenCellID API**.
- **Bottom Tab 3 — `Devices`**: Multi-device tracking & locating hub containing:
  1. **Off-Grid P2P Device Finder** (works with **zero internet and zero sign-in** via Bluetooth LE + Wi-Fi Direct).
  2. **My Other Devices** (all phones/tablets signed into **your** Google account, with **Remote Ring**).
  3. **Shared Tracking Groups** (invite-code groups for teammates/other users, with **Ghost Mode**).
- **GeoTrace Safe Menu (Top-Right Hamburger `☰`)**: Auto-expanded, scrollable bottom-sheet menu (skips half-height collapse so all cards are immediately visible without clipping) for:
  - **Saved Trails & Exports** (inspect SQLite tracks, export `.gpx`, `.kml`, `.csv` via `FileProvider`)
  - **Geofencing & Safe Zones** (circular entry/exit alerts with 15 m exit hysteresis)
  - **SWE2105 Research & Comparison** (in-app Option 1 vs Option 2 & Uganda legal summary)
  - **Telemetry & System Setup** (`Settings`: units, DD/DMS coordinates, map tiles, jitter filter, OpenCellID API key, and GitHub Releases **Application Updates**)

---

## 2. Do NOT Confuse These (Common Misconceptions)

### Misconception 1: "The Map only uses GPS"
- **Reality:** Single-device tracking supports **three positioning profiles** switchable from the Map pill:
  1. **High Accuracy (GPS):** 1 s multi-constellation GNSS (`~2–5 m`, `~25 mA`).
  2. **Balanced Power (Fused):** 5 s Google Fused Location combining GPS + Wi-Fi + Cell (`~5–15 m`, `~8 mA`).
  3. **Power Saver (Cell Only):** 30 s low-power Fused updates (`~100–1,000 m`, `< 2 mA`) paired with 10 s passive `TelephonyManager` cell polling, falling back to OpenCellID serving-tower coordinates when needed.
- Changing modes updates both **standby monitoring** (`TrackerViewModel`) and an **active recording foreground service** (`LocationForegroundService`), persists the selection across restarts (`geotrace_prefs`), and triggers a provider-tier transition guard so switching between `"cellular"` and GNSS does not log fake jump distance or speed spikes.

### Misconception 2: Confusing the 3 Multi-Device Systems
They solve different scenarios—do not mix up their names or backends:

| Feature | Who Can See Whom? | Internet & Google Sign-In Required? | Range & Transport | Backend / API |
| :--- | :--- | :--- | :--- | :--- |
| **My Other Devices** | Only **your own** phones/tablets signed into the **same Google account** | **Yes** (Internet + Google Sign-In) | Worldwide | Cloud Firestore `/users/{uid}/devices/{deviceId}` |
| **Shared Tracking Groups** | Multiple users/devices that join the **same `XXX-XXX` invite code** | **Yes** (Internet + Google Sign-In) | Worldwide | Cloud Firestore `/groups/{groupId}/live_locations/{uid}_{deviceId}` |
| **Off-Grid P2P Device Finder** | Any nearby GeoTrace phone with P2P Finder switched ON | **No** (works in Airplane Mode with Bluetooth/Wi-Fi Direct ON) | `~50–200 m` | Google Nearby Connections (`Strategy.P2P_CLUSTER`) |

- **How Off-Grid P2P Fits SWE2105 Option 2 (Academic Justification):** Unlike the removed Indoor BLE RSSI Radar (which attempted Bluetooth signal-strength indoor distance estimation), **Off-Grid P2P Device Finder does NOT replace GPS or cellular positioning**. Every peer marker, compass bearing, and distance comes from genuine Option 2 GNSS/fused coordinates computed on each phone—Nearby Connections (`P2P_CLUSTER`) simply replaces the internet/Firestore transport layer when cellular mobile data is unavailable or out of coverage in rural Uganda.
- **Removed Out-of-Scope Features:** Earlier prototypes explored an **Indoor BLE RSSI ("Warmer/Colder") Radar** and a **Background Device Beacon** toggle. Both were intentionally removed to keep the project focused and reliable. Do not re-add them or look for them in the UI.
- **Terminology:** The app uses general terms (**Devices & Tracking Groups**, **Tracking Group**, **Map**) rather than "Family Circle" or "GPS Map", because the app tracks personal fleets, field teams, or study assets alike.

### Misconception 3: "Are there still hardcoded/mock locations or updates?"
- **Live telemetry & cell towers:** **Zero mock data.** `OpenCellIdClient` previously had bundled fallback towers, which were removed—if no OpenCellID key is set (in Settings or `OPENCELLID_API_KEY`), tower coordinates simply remain unresolved while real radio IDs (`TelephonyManager`) still display.
- **Application Updates:** `AppUpdateManager` queries the live GitHub API (`releases/latest`). If you haven't published a GitHub Release yet, GitHub returns HTTP `404` and the app cleanly displays *"No GitHub releases have been published yet"*—it is not fake data.
- **Pre-seeded Geofences (Intentional Starter Zones):** On first launch when the SQLite `geofences` table is empty, `LocationRepository.loadGeofences()` seeds 7 Uganda sample safe zones (MUST Kihumuro, MUST Town Campus, Wandegeya) so geofencing can be demonstrated immediately. Users can edit, delete, or add their own zones at any time.

### Misconception 4: "GPS stopped counting distance / speed is stuck at 0.0 km/h while I test at my desk"
- **Reality:** This is intentional anti-drift filtering in `LocationRepository.recordLocation()`:
  - **Stationary Deadband (`1.5 m` default, configurable in Settings):** Movements smaller than the threshold are filtered out so indoor multipath GPS bounce doesn't falsely log kilometers of distance.
  - **Pedestrian Noise Floor (`0.6 m/s ≈ 2.1 km/h`) + EMA Smoothing (`α = 0.35`):** Prevents standing-still hand jitter from showing `3–4 km/h` while smoothing walking speed cleanly. Walk 10+ meters outdoors to test live distance/speed accumulation.

---

## 3. Critical Engineering Invariants (Read Before Editing Code)

### 1. Firestore Named Database Rule (CRITICAL)
GeoTrace uses a **named Firestore database** configured in `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`).
- **ALWAYS** initialize Firestore with `FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))`.
- **NEVER** call parameterless `Firebase.firestore` or `FirebaseFirestore.getInstance()`—that connects to `(default)` instead of the provisioned database and causes empty reads or permission failures.

### 2. Multi-Device Document Keys (`{uid}_{deviceId}`)
Inside a group's `live_locations` subcollection, documents are keyed by **`${uid}_${currentDeviceId}`** (not just `${uid}`), and `personalDevices` docs use `SetOptions.merge()` under `/users/{uid}/devices/{deviceId}`.
- **Why:** If you sign into two phones with the same Google account and join a group, keying only by `uid` causes Phone B to overwrite Phone A. Keeping `SetOptions.merge()` on `/users/{uid}/devices/{deviceId}` also ensures periodic GPS updates do not wipe out an incoming `ringRequestedAt` trigger from another device.

### 3. Google Sign-In & Keystore SHA-1 Trap (CRITICAL)
If tapping an account in **Sign in with Google** immediately fails with `"Sign-in cancelled"`:
- **Root Cause #1 — Keystore SHA-1 Mismatch:** Firebase & `app/google-services.json` authorize the certificate SHA-1 `a2:50:90:0a:d4:a5:ae:0b:c2:af:30:34:ae:23:2c:b4:c1:a0:9c:35` (which comes from **`debug.keystore.base64`** in the repo root).
  - Never commit raw `debug.keystore` or `release.keystore` to Git, and never run local Android Studio builds using your PC's random `~/.android/debug.keystore` unless you add that keystore's SHA-1/SHA-256 to Firebase Console too.
  - Before building locally on your computer, decode the shared project keystore once in the project root:
    ```bash
    # Linux / macOS
    base64 -d debug.keystore.base64 > debug.keystore

    # Windows PowerShell
    [IO.File]::WriteAllBytes("debug.keystore", [Convert]::FromBase64String((Get-Content debug.keystore.base64 -Raw)))
    ```
  - GitHub Actions (`.github/workflows/android-ci.yml`) already decodes `debug.keystore.base64` automatically.
- **Root Cause #2 — Google Cloud OAuth Consent Screen:** In Google Cloud Console (**APIs & Services → OAuth consent screen** for `gen-lang-client-0500925114`), if status is **Testing**, only emails listed under **Test users** can sign in on a phone. Either add teammates' Gmail addresses to **Test users** or publish the consent screen to **In production**.
- **Auth Invariant:** Never add `signInAnonymously()` or email/password auth in `app/src/main/`. Credential Manager with `GetSignInWithGoogleOption` is required by the Firebase security setup.

### 4. Build System & Environment Rules
- **Do not create `local.properties`** when working inside cloud build environments (`ANDROID_SDK_ROOT` is managed automatically).
- **Do not modify `signingConfigs`** in `app/build.gradle.kts` (`debugConfig` / `releaseConfig` assignments are required for reproducible builds).
- **Secrets belong in GitHub Secrets / Settings UI**, never hardcoded in Kotlin or `gradle` files (`OPENCELLID_API_KEY`, `SIGNING_*`, `GOOGLE_SERVICES_JSON_BASE64`). All CI secrets in `android-ci.yml` are **optional**—without them, CI uses `debug.keystore.base64` and `app/google-services.json`.

---

## 4. Source Code Map (`app/src/main/java/com/geotrace/`)

| Layer | File(s) | Responsibility |
| :--- | :--- | :--- |
| **Entry & Navigation** | `LocationTrackerApp.kt`, `MainActivity.kt` | Initializes `osmdroid`, repositories, and single-activity Compose host (`Screen` tabs + auto-expanded scrollable `GeoTrace Safe Menu` bottom sheet + incoming Ring alarm banner). |
| **Repositories** | `data/LocationRepository.kt`, `data/LocationDbHelper.kt`, `data/GroupRepository.kt` | SQLite trails/geofences/alerts/offline regions (`gis_tracker.db` v3), jitter deadband + EMA + tier-guard smoothing, Firestore sync (`groups`, `users/{uid}/devices`), Nearby Connections `P2P_CLUSTER` mesh (`v3` binary telemetry + `0x0A` Ring). |
| **Exporters** | `data/export/TrackExporter.kt` | Generates GPX 1.1, KML 2.2, and CSV files shared via `FileProvider`. |
| **Services** | `service/LocationForegroundService.kt`, `CellularDiagnosticManager.kt`, `OpenCellIdClient.kt`, `OfflineMapManager.kt`, `GeofenceManager.kt`, `FindDeviceAlarmManager.kt`, `AppUpdateManager.kt` | Foreground WakeLock location tracking, TelephonyManager radio reader, OpenCellID HTTP resolver, osmdroid `CacheManager` + `SqlTileWriter` offline region downloader, Android Geofencing API + BroadcastReceiver, high-priority Ring alarm + vibrator, GitHub Releases OTA APK updater. |
| **ViewModels** | `viewmodel/TrackerViewModel.kt`, `viewmodel/GroupViewModel.kt` | StateFlows for map telemetry, modes, cell towers, settings, offline download progress, groups, personal device fleet, P2P peers, and Ring triggers. |
| **UI Screens & Map** | `ui/screens/*Screen.kt`, `ui/components/MapTileSources.kt`, `MapViewComposable.kt`, `OfflineMapsDialog.kt` | Material 3 Compose screens; 5-layer `osmdroid` map viewport (`MapTileSources`) and offline region manager dialog. |

---

## 5. Quick Verification & Real-Phone Test Checklist

### Automated Checks
- **Firestore Security Rules Tests (Node + Firebase Emulator):**
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
- **Local Gradle Compilation:**
  ```bash
  ./gradlew testDebugUnitTest assembleDebug assembleRelease
  ```
  *(Inside AI Studio shell, use `gradle` instead of `./gradlew`. Note: `app/src/test` has no Kotlin unit test classes yet, so `testDebugUnitTest` validates test compilation cleanly while `firestore.test.js` executes the real security rules suite.)*

### 2-Phone Real-World Smoke Test
1. **Phone A & B (Online — Cloud Fleet & Groups):**
   - Sign in with the **same Google account** on both phones → confirm both appear under **My Other Devices** and on the **Map** → tap **Ring** on Phone A to ring Phone B.
   - Create a **Tracking Group** on Phone A, join with the `XXX-XXX` invite code on Phone B, and verify **Ghost Mode** hides coordinates when toggled ON.
2. **Phone A & B (Offline — Airplane Mode + Bluetooth ON):**
   - Turn OFF mobile data & Wi-Fi internet; enable Bluetooth + Location on both phones.
   - Switch **Off-Grid P2P Device Finder** ON on both phones → wait ~5–15 s for peer discovery/handshake → verify live distance, compass bearing, Map peer pin, and offline **P2P Ring**.
3. **1-Phone Field Test (Outdoors + Offline Maps):**
   - Switch map layers (`OSM Streets` → `Satellite Imagery` → `Satellite + Labels` → `Topographic Relief` → `Humanitarian HOT`), pre-download a `2 km` Offline Map Region around your test site, and confirm tiles render in Airplane Mode.
   - Switch modes (`High Accuracy` → `Balanced` → `Power Saver`), record a short walk with screen off for 30 s, stop recording, and export GPX/KML/CSV from **Saved Trails & Exports**.
