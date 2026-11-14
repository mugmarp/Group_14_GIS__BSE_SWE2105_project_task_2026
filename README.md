# GIS Group 14 — SWE2105 Project Task 2026

**Project Title:** GeoTrace — Real-Time GNSS & Cellular Base Station Telemetry Tracker  
**Course:** BSE SWE2105 (Geographic Information Systems)  
**Academic Year:** 2026  
**Institution:** Faculty of Applied Sciences and Technology (FAST), Mbarara University of Science and Technology (MUST)  
**Project:** Mobile Phone Tracking via GPS & Cellular Triangulation  
**Package Namespace:** `com.geotrace`  
**GitHub Organization / Repository:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`  

---

## 1. Project Task & Problem Definition

> **GROUP PROJECT WORK (SWE2105)**  
> Design a web or mobile app that can track the location of people or objects in real-time. A group can select any of following capabilities for your implementation:
> - **Option 1:** Track the location of a laptop computer based on its IP address. Research how IP locations are determined and discuss the potential accuracy/limitations.
> - **Option 2:** Track the movement of a person carrying a mobile phone through GPS or cell tower triangulation. Discuss the pros and cons of each approach.

### Quick Comparison: Option 1 vs. Option 2

| Aspect | Option 1: IP Address Tracking | Option 2: Mobile Phone Tracking (GeoTrace) |
| :--- | :--- | :--- |
| **Real-Time Capability** | ❌ **Impossible** (IP leases update over hours/days; static) | ✅ **Native Continuous** (1 Hz real-time trajectory updates) |
| **Typical Accuracy** | City to Regional level ($5\text{--}150\text{ km}$ error) | **$2\text{--}5\text{ m}$** (GNSS) / **$150\text{--}800\text{ m}$** (Cellular Mast) |
| **Indoor Positioning** | ❌ No (tied to ISP router gateway node) | ✅ **Yes** (Continuous cellular base station signal lock) |
| **Continuous Trail Tracking**| ❌ Impossible (No continuous motion vectors) | ✅ **Yes** (High-fidelity polyline with velocity coloring) |
| **Technical & Engineering Depth** | Low (Single external HTTP GET IP API call) | **High** (Android GNSS sensors, RF Telephony, Deadband, M3 UI) |
| **Uganda Field Relevance** | Negligible (Mobile ISPs route via distant Kampala CGNAT) | **High** (Addresses boda boda security, fleet safety & rural mobility) |
| **Academic & Portfolio Value** | Low | **High** (Production-grade, battery-optimized Android GIS application) |

### Decision Rationale: Why Option 2 Was Chosen
Following comprehensive feasibility research (see [`FEASIBILITY_ANALYSIS.md`](FEASIBILITY_ANALYSIS.md)), Group 14 formally selected and implemented **Option 2**:
1. **Real-Time Dynamic Cadence:** Native 1 Hz continuous updates vs. static IP lookup.
2. **Meter-Level Spatial Precision:** 2–5m outdoor GNSS accuracy with cellular fallback.
3. **Engineering Depth:** Implementation of Android hardware telephony (`TelephonyManager`), Fused GNSS positioning (`FusedLocationProviderClient`), stationary deadband jitter suppression, dual-source OpenCellID Uganda base station triangulation, and geofencing.
4. **Uganda Context Relevance:** Solves real-world mobility challenges in Mbarara and Kampala (boda boda route tracking, vehicle security, school commute safety, and indoor cell tower coverage transitions).

---

## 2. Complete Repository Structure

```
Group_14_GIS__BSE_SWE2105_project_task_2026/
├── README.md                          # Master course submission overview & documentation
├── task                               # Original task brief from SWE2105 instructor
├── FEASIBILITY_ANALYSIS.md            # Technical feasibility evaluation (Option 1 vs Option 2)
├── DECISION_LOG.md                    # Engineering decision log & architecture trade-offs
├── FIELD_TESTING_PROTOCOL.md          # MUST Kihumuro field testing protocol & log sheets
├── ACADEMIC_EVALUATION.md             # Theoretical evaluation & Uganda statutory legal compliance
│
├── docs/                              # Detailed engineering specifications
│   ├── architecture.md                # System component diagrams & MVVM domain architecture
│   ├── api-reference.md               # OpenCellID REST API & GitHub Releases API schemas
│   ├── testing-plan.md                # Automated test execution matrix & QA strategy
│   ├── multi-user-tracking.md         # Multi-user architecture & implemented schema notes
│   ├── issues-and-resolutions.md      # Technical audit & resolved issues log
│   └── team-guide.md                  # Group member onboarding & common pitfalls guide
│
├── .github/workflows/                 # Continuous Integration & Automated Delivery
│   └── android-ci.yml                 # Dual APK build (Debug & Release) + GitHub Releases CD
│
├── firebase-blueprint.json            # Firestore schema definition
├── firebase.json                      # Firebase emulator configuration
├── firestore.rules                    # Zero-trust Firestore security rules
├── firestore.test.js                  # Node security rules verification tests
├── debug.keystore.base64              # Base64 project debug keystore (matches Firebase SHA-1)
│
├── app/                               # Native Android application module (Kotlin / Compose)
│   ├── build.gradle.kts               # Module build configuration, ProGuard & BuildConfig
│   ├── google-services.json           # Firebase project configuration (com.geotrace)
│   ├── src/main/AndroidManifest.xml   # Manifest permissions (GNSS, Telephony, Nearby P2P, Install)
│   └── src/main/java/com/geotrace/    # Clean MVVM source code
│       ├── LocationTrackerApp.kt      # Application initialization & dependency graph
│       ├── MainActivity.kt            # Edge-to-edge Compose host activity & navigation menu
│       ├── data/                      # Local SQLite persistence, Firestore & P2P GroupRepository, Exporters
│       ├── model/                     # Telemetry, cellular, geofence, group & P2P domain models
│       ├── service/                   # Hardware & platform services
│       │   ├── LocationForegroundService.kt  # WakeLock foreground tracking service
│       │   ├── CellularDiagnosticManager.kt  # TelephonyManager cell radio scanner
│       │   ├── OpenCellIdClient.kt           # Live OpenCellID REST API coordinate resolver
│       │   ├── GeofenceManager.kt            # Android Geofencing API + BroadcastReceiver
│       │   ├── FindDeviceAlarmManager.kt     # Remote Ring alarm & vibration controller
│       │   ├── OfflineMapManager.kt          # osmdroid CacheManager + SqlTileWriter offline regions
│       │   └── AppUpdateManager.kt           # GitHub Releases OTA APK updater
│       ├── ui/                        # Jetpack Compose UI layer (Material 3 Daylight Palette)
│       │   ├── components/            # MapViewComposable, MapTileSources & OfflineMapsDialog
│       │   ├── screens/               # Map, Cellular Hub, Devices/Groups, Geofencing, History, Research, Settings
│       │   └── theme/                 # Material 3 typography, shapes & daylight color scheme
│       └── viewmodel/                 # TrackerViewModel & GroupViewModel (StateFlow engines)
│
├── build.gradle.kts                   # Root Gradle project configuration
├── settings.gradle.kts                # Project module inclusion & dependency resolution
├── gradle.properties                  # JVM memory allocation & AndroidX configurations
└── .gitignore                         # Git exclusion rules (ignores build/, *.apk, .aistudio/artifacts)
```

---

## 3. Core Implemented System Capabilities

### 🗺️ Real-Time Map, Multi-Layer Imagery & Offline Regions
- **Multi-Constellation GNSS:** Google Play Services `FusedLocationProviderClient` streaming continuous 1 Hz fixes (latitude, longitude, altitude, speed, bearing, and 68% confidence error radius) with provider-tier transition guards so switching between `POWER_SAVER` cellular fixes and GNSS never logs false distance or speed jumps.
- **5-Layer Cartographic Viewport:** Rendered via `osmdroid` (`MapTileSources.kt`) with persistent `mapTileType` selection, zoom clamping, and smooth pan/zoom/auto-centering:
  - **OSM Streets (`STANDARD`):** `MAPNIK` standard road and POI map (auto-cached).
  - **Satellite Imagery (`SATELLITE`):** Physical aerial/rooftop/terrain imagery via Esri World Imagery (`{z}/{y}/{x}`).
  - **Satellite + Labels (`HYBRID`):** Esri Satellite combined with a transparent Carto Light Labels `TilesOverlay`.
  - **Topographic Relief (`TOPO`):** OpenTopoMap physical contours and elevation shading (up to Z17).
  - **Humanitarian (`HUMANITARIAN`):** Humanitarian OpenStreetMap Team (`HOT`) high-contrast road & building layer.
- **Offline Maps Manager (`OfflineMapManager.kt` & `OfflineMapsDialog.kt`):** Pre-download bounded field regions (`Map View`, `2 km`, or `5 km` radius) for Satellite, Hybrid (2-pass imagery + labels), Topo, and Humanitarian layers into a persistent **512 MB private internal tile cache** (`filesDir/osmdroid/tiles`, zero storage permissions), backed by SQLite `v3` `offline_map_regions` tracking, $O(\text{zoomLevels})$ tile/MB estimates, and a 2,500-tile guardrail.
- **GIS Symbology & Stationary Deadband Filter:** Cached bearing-rotated user direction chevrons, trail Start (`S`) and End (`E`) pins, plus an on-device Euclidean deadband filter ($T = 1.5\text{ m}$) eliminating stationary multipath GPS jitter.

### 📡 Live Cell Tower Telemetry & Pinning (OpenCellID)
- Real-time hardware interrogation of active and neighbor base transceiver stations via Android `TelephonyManager`.
- Resolves physical serving and neighbor cell coordinates and estimated coverage radius strictly via the live **OpenCellID API** when an API key is configured (via `OPENCELLID_API_KEY` or in **Settings**—no synthetic fallback coordinates are used), including Ugandan networks such as **MTN Uganda (`641-10`)** and **Airtel Uganda (`641-22`)**.
- Renders custom serving/neighbor mast badges on the map with a signal-strength (`dBm`) color-coded coverage circle and connecting vector ray from the device location.

### ⚖️ GPS vs. Cellular Telemetry Comparator
- Direct side-by-side engineering evaluation tool built into the app:
  - **Outdoor Accuracy:** GPS ($2\text{--}5\text{ m}$) vs. Cellular ($150\text{--}800\text{ m}$).
  - **Indoor Penetration:** GPS fails in basements vs. Cellular penetrates thick concrete.
  - **Battery Current:** GPS continuous tracking ($\sim 25\text{ mA}$) vs. Cellular passive register ($< 2\text{ mA}$).
  - **Time-to-First-Fix (TTFF):** GPS cold start ($20\text{--}45\text{ s}$) vs. Cellular instantaneous ($< 100\text{ ms}$).

### 🔄 In-App Update Engine & GitHub Releases CI/CD
- **Automated Update Engine (`AppUpdateManager.kt`):** Background semantic version check against GitHub Releases API (`mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`).
- **Direct Download & Installation:** Uses Android `DownloadManager` and secure `FileProvider` (`com.geotrace.fileprovider`) with `android.permission.REQUEST_INSTALL_PACKAGES`.
- **Automated CI/CD (`.github/workflows/android-ci.yml`):** Compiles both **Debug** and **Release** APKs on every commit and publishes tagged GitHub Releases automatically.

### 👥 Multi-Device Fleet, Shared Groups & Off-Grid P2P
- **My Other Devices:** Sign in with the same Google account across multiple phones/tablets via Jetpack Credential Manager + Firebase Auth to track them under `/users/{uid}/devices/{deviceId}` and trigger a remote **Find/Ring Alarm**.
- **Shared Tracking Groups:** Create or join `XXX-XXX` invite-code groups backed by Cloud Firestore with **Ghost Mode** privacy controls and per-device keying (`{uid}_{deviceId}`).
- **Off-Grid P2P Device Finder (SWE2105 Option 2 Offline Transport):** Uses Google Nearby Connections (`Strategy.P2P_CLUSTER`) over BLE + Wi-Fi Direct (`~50–200 m`) to exchange **genuine GNSS/fused coordinates**, battery telemetry, and offline Ring commands when cellular internet is unavailable—keeping real-time Option 2 tracking operational offline without replacing GPS/cell positioning.

### 📜 Spatial History, Exports & Hybrid Persistence
- **Local-First SQLite Trails:** Recorded trails, waypoints, and geofences are stored locally on-device (`gis_tracker.db`); cloud sharing is strictly opt-in behind Google Sign-In and Ghost Mode controls.
- One-tap export via `FileProvider` to industry-standard formats:
  - **GPX 1.1:** Schema-compliant XML for QGIS, Strava, and Garmin devices.
  - **KML 2.2:** OGC-compliant 3D trajectory markup for Google Earth visualization.
  - **CSV:** Tabular format for Pandas / R data analysis.

---

## 4. Statutory Legal & Privacy Compliance (Uganda Jurisdiction)

GeoTrace is architected to align with national regulatory frameworks in the Republic of Uganda (see [`ACADEMIC_EVALUATION.md`](ACADEMIC_EVALUATION.md)):
1. **Data Protection and Privacy Act, 2019 (Cap 97):** Recorded trails remain strictly local in SQLite (`/data/data/com.geotrace/databases/`); cloud fleet & group location sharing require explicit Google Sign-In, invite-code opt-in, and enforce per-user/group isolation via `firestore.rules` plus an instant **Ghost Mode** toggle.
2. **Uganda Communications Act, 2013 (Cap 106):** Passive cell identity collection via standard Android telephony APIs. No unauthorized RF transmissions or IMSI interception.
3. **Electronic Signatures Act, 2011 (Cap 101) & Electronic Transactions Act (Cap 103):** Immutable ISO-8601 UTC timestamps and verifiable spatial lineage for academic and evidentiary audits.

---

## 5. Building & Running the Application

> For a detailed teammate walkthrough and common pitfalls (especially Google Sign-In SHA-1 matching and Firestore database IDs), see **[`docs/team-guide.md`](docs/team-guide.md)**.

### Option A: Direct APK Installation (Fastest)
Download the pre-compiled APKs directly from the repository's **GitHub Releases** tab (or CI workflow artifacts):
- **Release APK:** `geotrace-release.apk`
- **Debug APK (Logging Enabled):** `geotrace-debug.apk`

### Option B: Build from Source via Gradle
Ensure JDK 21 and Android SDK are installed, decode the shared debug keystore (so Google Sign-In matches Firebase's registered SHA-1), then build:

```bash
# Clone the repository
git clone https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026.git
cd Group_14_GIS__BSE_SWE2105_project_task_2026

# Decode project debug.keystore so Google Sign-In works on device
# (Make sure debug.keystore.base64 is pushed to GitHub so CI signs APKs with the same SHA-1)
base64 -d debug.keystore.base64 > debug.keystore

# Run Firestore rules tests (requires Firebase emulators on port 8085)
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js

# Verify unit test compilation & assemble APKs
./gradlew testDebugUnitTest assembleDebug assembleRelease
```
The compiled APK files will be located at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

---

## 6. Group Members & Academic Contribution

| Name | Registration Number | GitHub Handle | Role / Core Contribution |
| :--- | :--- | :--- | :--- |
| **MUGENDAWALA MARK PAUL** | 2025/BSE/108/PS | [`@mugmarp`](https://github.com/mugmarp) | Architecture, Telemetry Engine, CI/CD Pipeline |
| *[Group Member 2]* | — | — | Mobile UI, Map Overlays & Geofencing |
| *[Group Member 3]* | — | — | OpenCellID Integration & Telephony Diagnostics |
| *[Group Member 4]* | — | — | Field Testing, Data Analysis & Academic Protocol |

**Repository URL:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`
