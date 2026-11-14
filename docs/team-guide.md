# GeoTrace — Team Guide & System Walkthrough (BSE SWE2105 Option 2)

Welcome to **GeoTrace** (`com.geotrace`), Group 14's Android GIS & real-time mobile tracking application built for **BSE SWE2105 (Geographic Information Systems)** at **Mbarara University of Science and Technology (MUST)**.

This guide explains **the course task we are solving**, **how every part of GeoTrace works end-to-end**, **why key engineering choices were made**, and **how the team builds, tests, and demonstrates the app**.

---

## 1. The SWE2105 Task & How GeoTrace Solves It

Our assignment asks us to **track the movement of a person carrying a mobile phone in real time using GPS or cell tower triangulation, and evaluate the pros and cons of both approaches**. (Option 1—IP address geolocation on a laptop—fails real-time movement tracking because IP leases remain static for hours/days at city or ISP-gateway level.)

GeoTrace implements **Option 2** natively on Android using real device hardware:
1. **Multi-Constellation GNSS Positioning (`FusedLocationProviderClient`):** Combines GPS, GLONASS, Galileo, and BeiDou satellites (fused with Wi-Fi and cell signals when needed) for continuous meter-level (`~2–5 m`) outdoor tracking.
2. **Cellular Base Station Telemetry (`TelephonyManager` + OpenCellID):** Reads the phone's serving and neighboring mobile mast radio identifiers (`MCC`, `MNC`, `LAC/TAC`, `CID/NCI`, and signal strength in `dBm`) and optionally resolves physical mast coordinates via the **OpenCellID REST API**.
3. **Side-by-Side Academic Evaluation:** Evaluates accuracy, Time-to-First-Fix (TTFF), indoor penetration, power draw, and Ugandan telecom/legal realities directly inside the app (`SWE2105 Research Analysis`) and in our supporting reports.

---

## 2. How Each Screen & Subsystem Works

### Tab 1: `Map` — Live Cartography, Modes & Telemetry HUD
- **5 Base & Hybrid Map Layers (`MapTileSources.kt`):** Switchable directly from the map layer button or Settings:
  - **OSM Streets (`STANDARD`):** OpenStreetMap road & POI layer (`MAPNIK`), auto-cached as you pan/zoom.
  - **Satellite Imagery (`SATELLITE`):** Physical aerial imagery via Esri World Imagery showing rooftops, compounds, dirt tracks, and masts.
  - **Satellite + Labels (`HYBRID`):** Esri Satellite combined with a transparent Carto Light street/place label overlay.
  - **Topographic Relief (`TOPO`):** OpenTopoMap elevation contours and terrain shading (capped cleanly at zoom Z17).
  - **Humanitarian (`HUMANITARIAN`):** Humanitarian OpenStreetMap Team (`HOT`) high-contrast campus paths and buildings.
- **Offline Maps (`OfflineMapManager.kt` & `OfflineMapsDialog.kt`):** Lets field testers pre-download the current viewport, a `2 km` radius, or a `5 km` radius (for Satellite, Hybrid, Topo, or HOT) into a private `512 MB` internal tile cache (`filesDir/osmdroid/tiles`). Useful when moving into zero-data areas around rural Mbarara.
- **Three Positioning Modes (HUD Mode Pill):**
  - **High Accuracy (1 s GNSS):** Uses `PRIORITY_HIGH_ACCURACY` for 1-second satellite trail recording (`~2–5 m`, `~25 mA`).
  - **Balanced Power (5 s Fused):** Uses `PRIORITY_BALANCED_POWER_ACCURACY` (GPS + Wi-Fi + Cell fusion, `~5–15 m`, `~8 mA`).
  - **Power Saver (Cell Only):** Uses low-power positioning paired with `10 s` cellular radio polling (`< 2 mA`), falling back to OpenCellID serving-mast coordinates (`"cellular"` provider) when resolved.
- **Anti-Drift Filtering & Speed Smoothing (`LocationRepository.kt`):**
  - **Stationary Deadband (`1.5 m` default):** Micro-jumps below the threshold while standing still are discarded so multipath satellite bounce does not log fake walking distance. Settings shows the cumulative rejected drift (`ELIMINATED MULTIPATH DRIFT`) as empirical proof.
  - **Pedestrian Gait & EMA Smoothing:** Clamps sub-walking noise (`< 0.6 m/s`), derives speed from $\Delta d / \Delta t$ when a provider omits Doppler velocity, smooths display speed with an Exponential Moving Average ($\alpha = 0.35$), and classifies gait (`Stationary`, `Slow Walk`, `Walking`, `Brisk Walk`, `Running`, `In Transit`).
  - **Provider-Tier Transition Guard:** Prevents fake jump distance or speed spikes when switching mid-trail between `"cellular"` tower coordinates and GNSS/fused coordinates.
- **GIS Map Symbology (`MapViewComposable.kt`):** Renders bearing-rotated direction chevrons, trail Start (`S`) and End (`E`) markers, translucent 68% GNSS accuracy rings, geofence circles, serving/neighbor mast badges, and `dBm`-colored signal rays from phone to mast.
- **Foreground Trail Recording (`REC`):** Tapping Record creates a session in local SQLite (`gis_tracker.db` v3) and launches `LocationForegroundService` with a `PARTIAL_WAKE_LOCK` so logging continues reliably even when the screen turns off in a pocket.

### Tab 2: `Cellular Hub` — Radio Diagnostics & Uganda Telecom Reality
- **Hardware Telemetry (`CellularDiagnosticManager.kt`):** Reads active SIM towers (`LTE`, `NR/5G`, `WCDMA/UMTS`, `GSM`), PLMN codes (e.g., **MTN Uganda `641-10`**, **Airtel Uganda `641-22`**), `LAC/TAC`, `CID/NCI`, `PCI/PSC`, and signal strength (`RSRP`/`RSSI` in `dBm`).
- **Coordinate Resolution (`OpenCellIdClient.kt`):** Queries the live OpenCellID API when an API key is set (in Settings or `OPENCELLID_API_KEY`). GeoTrace never invents fake tower coordinates—without a key or internet, raw hardware radio IDs remain visible while map tower pins stay off.
- **True Triangulation vs. Cell-ID Lookup:** In Uganda, telecom operators (MTN/Airtel) do not expose internal timing-advance / TDOA / angle-of-arrival network APIs to third-party apps (restricted under the **Uganda Communications Act, Cap 106** & **Regulation of Interception of Communications Act, Cap 101**). Standard Android apps perform **Single/Serving Cell-ID Lookup** against public/crowd-sourced databases like OpenCellID rather than true multi-tower network triangulation.

### Tab 3: `Devices` — Multi-Device Tracking, Groups & Off-Grid P2P
GeoTrace includes three distinct locating capabilities under one hub:

| Capability | Who Can See Whom | Network & Account Needed | Transport & Range | Backend / Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **My Other Devices** | Only **your own** phones/tablets signed into the same Google account | Internet + Google Sign-In | Global | Cloud Firestore `/users/{uid}/devices/{deviceId}` + **Remote Ring** (`FindDeviceAlarmManager`) |
| **Shared Tracking Groups** | Teammates or family who join the same `XXX-XXX` invite code | Internet + Google Sign-In | Global | Cloud Firestore `/groups/{groupId}/live_locations/{uid}_{deviceId}` + **Ghost Mode** pause |
| **Off-Grid P2P Device Finder** | Nearby GeoTrace phones with P2P Finder switched ON | **None** (works with Airplane Mode + Bluetooth/Wi-Fi Direct ON) | `~50–200 m` | Google Nearby Connections (`Strategy.P2P_CLUSTER`) exchanging real GNSS fixes + offline **P2P Ring** |

**Why composite keys (`{uid}_{deviceId}`) and `SetOptions.merge()` matter:** If a teammate signs into two phones with one Google account inside a group, keying by `uid` alone would overwrite one phone with the other. Merging updates on personal device documents also prevents routine GPS writes from clearing a pending Remote Ring request.

**24/7 Background Protection & Daily-Life Locating:** When signed into Google with **24/7 Background Protection** enabled (on by default, and toggleable in either `Devices → This Device & Privacy`, `Settings → Hardware Jitter & Power Management`, or via `Pause Protection` on the notification), `GroupRepository` maintains a process-wide Firestore listener for Remote Ring signals while `LocationForegroundService` runs in a lightweight balanced heartbeat (`PRIORITY_BALANCED_POWER_ACCURACY`, `60s / 25m`, no WakeLock). Swiping the app closed does **not** silence Remote Ring or stop your device from refreshing its last known coordinates, and stopping a high-precision `REC` trail smoothly downgrades back to Protection Mode (with `BOOT_COMPLETED` recovery after phone restarts when Background Location permission is granted). Off-grid Nearby P2P Discovery remains an explicit foreground/in-hub toggle so Bluetooth/Wi-Fi radios only advertise when wanted.

### Menu (`☰`) Modules
- **Safe Zones & Geofencing (`GeofenceScreen.kt` & `GeofenceManager.kt`):** Supports circular entry/exit monitoring with `15 m` exit hysteresis (preventing GPS noise flapping at the perimeter) plus optional OS-level `GeofencingClient` background alerts (`GeofenceBroadcastReceiver`). Includes updated Mbarara/MUST sample zones (`Kihumuro Campus`, `Main Town Campus`, `Kakoba Student Hostels`), non-destructive **`Reset Sample Zones`**, and **`Pick on Map`** so you can tap directly on Esri Satellite imagery to place a perimeter center on a specific building or gate.
- **Saved Trails & Exports (`HistoryScreen.kt` & `TrackExporter.kt`):** Inspects recorded SQLite sessions and shares `.gpx` (GPX 1.1), `.kml` (KML 2.2), and `.csv` files directly via Android `FileProvider` for QGIS, Google Earth, Python/Pandas, or Strava.
- **SWE2105 Research Analysis (`ResearchAnalysisScreen.kt`):** Interactive comparator summarizing Option 1 vs Option 2, GNSS vs Cellular metrics, and Ugandan data-privacy compliance (**Data Protection and Privacy Act, Cap 97**).
- **Telemetry & System Settings (`SettingsScreen.kt`):** Controls velocity units (`km/h`, `mph`, `m/s`), coordinate format (`DD` / `DMS`), map tile layer, Offline Maps Manager, accuracy ring, auto-center & **24/7 Background Protection** toggles, keep-screen-on, cell tower map pins, stationary deadband threshold (`1.5 m`, `3.0 m`, `Off`), OpenCellID API key, and in-app OTA updates (`AppUpdateManager.kt` checking GitHub Releases)—all persisted cleanly across restarts in `geotrace_prefs`.

---

## 3. Repository & Codebase Map

```
app/src/main/java/com/geotrace/
├── LocationTrackerApp.kt          # osmdroid 512 MB internal cache & repository initialization
├── MainActivity.kt                # Edge-to-edge Compose host, permissions, bottom nav & Safe Menu
├── data/
│   ├── LocationDbHelper.kt        # SQLite v3 (sessions, points + index, geofences, alerts, offline_map_regions)
│   ├── LocationRepository.kt      # Trail recording, deadband/EMA/tier-guard, geofence evaluation
│   ├── GroupRepository.kt         # Cloud Firestore (named DB) & Nearby Connections P2P mesh
│   └── export/TrackExporter.kt    # GPX 1.1, KML 2.2 & CSV file generators
├── model/                         # Domain models (Location, Cell, Geofence, Group, Settings/Offline)
├── service/                       # LocationForegroundService, CellularDiagnosticManager, OpenCellIdClient,
│                                  # OfflineMapManager, GeofenceManager, FindDeviceAlarmManager, AppUpdateManager
├── ui/
│   ├── components/                # MapTileSources.kt, MapViewComposable.kt, OfflineMapsDialog.kt
│   └── screens/                   # Map, Cellular Hub, Devices/Groups, Geofencing, History, Research, Settings
└── viewmodel/                     # TrackerViewModel & GroupViewModel
```

---

## 4. Team Presentation & Demo Guide

When demonstrating GeoTrace to lecturers or evaluators:
1. **Option 1 vs. Option 2 Rationale:** Open **SWE2105 Research Analysis** (`☰`) to show why IP geolocation fails real-time movement tracking and how GNSS + Cellular solves it under Ugandan infrastructure & law.
2. **Live Cartography & Anti-Drift:** On **Map**, switch between **OSM Streets**, **Satellite Imagery**, and **Satellite + Labels**, toggle **High Accuracy → Balanced → Power Saver**, and record a short outdoor walk to demonstrate real trail breadcrumbs, rotated direction chevron, and stationary deadband rejection.
3. **Cellular Telemetry:** Open **Cellular Hub** to display live serving & neighbor cell IDs (`MCC 641`, `MNC`, `LAC`, `CID`, `dBm`) from Android hardware.
4. **Multi-Device & Off-Grid P2P:** On two phones, demonstrate **My Other Devices** + **Remote Ring** online, and toggle **Off-Grid P2P Device Finder** with internet turned off (Bluetooth + Location on) to demonstrate peer coordinates, bearing, and offline Ring.
5. **Spatial Interoperability:** Stop a recorded trail, open **Saved Trails & Exports**, and export **CSV/GPX/KML** to show real GIS software integration (QGIS / Google Earth).

---

## 5. Building & Testing Locally

### Prerequisites & Project Configuration
- **Named Firestore Database:** Initialized via `R.string.firestore_database_id` (defined in `firebase_applet_config.xml`) so all reads/writes target the provisioned Firestore instance.
- **Consistent Keystore Signing for Google Sign-In:** Before building locally, decode the shared project debug keystore in the repository root so local APKs share the same SHA-1 certificate registered with Firebase Auth:
  ```bash
  # Linux / macOS
  base64 -d debug.keystore.base64 > debug.keystore

  # Windows PowerShell
  [IO.File]::WriteAllBytes("debug.keystore", [Convert]::FromBase64String((Get-Content debug.keystore.base64 -Raw)))
  ```
- **OAuth Access:** In Google Cloud Console (**OAuth consent screen**), while status is **Testing**, ensure each team tester's Google email is listed under **Test users** (or publish the app to production).

### Build & Verification Commands
```bash
# Verify Firestore security rules (requires Firebase Emulator on port 8085)
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js

# Compile unit tests and assemble Debug & Release APKs
./gradlew testDebugUnitTest assembleDebug assembleRelease
```
*(In cloud CI shells, use `gradle` directly instead of `./gradlew`, and never commit local `local.properties` or raw `debug.keystore` binaries.)*
