# GIS Group 14 — SWE2105 Project Task 2026

**Project Title:** GeoTrace — Real-Time GNSS & Cellular Base Station Telemetry Tracker  
**Course:** BSE SWE2105 (Geographic Information Systems)  
**Academic Year:** 2026  
**Institution:** Faculty of Applied Sciences and Technology (FAST), Mbarara University of Science and Technology (MUST)  
**Selected Option:** **Option 2: Mobile Phone Tracking via GPS & Cellular Triangulation** (Accepted & Completed)  
**Package Namespace:** `com.geotrace`  
**GitHub Organization / Repository:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`  

---

## 1. Project Task & Problem Definition

> **GROUP PROJECT WORK (SWE2105)**  
> Design a web or mobile app that can track the location of people or objects in real-time. A group can select any of following capabilities for your implementation:
> - **Option 1:** Track the location of a laptop computer based on its IP address. Research how IP locations are determined and discuss the potential accuracy/limitations.
> - **Option 2:** Track the movement of a person carrying a mobile phone through GPS or cell tower triangulation. Discuss the pros and cons of each approach.

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
│   └── testing-plan.md                # Automated test execution matrix & QA strategy
│
├── .github/workflows/                 # Continuous Integration & Automated Delivery
│   └── android-ci.yml                 # Dual APK build (Debug & Release) + GitHub Releases CD
│
├── app/                               # Native Android application module (Kotlin / Compose)
│   ├── build.gradle.kts               # Module build configuration, ProGuard & BuildConfig
│   ├── src/main/AndroidManifest.xml   # Manifest permissions (GNSS, Telephony, Install Packages)
│   └── src/main/java/com/geotrace/    # Clean MVVM source code
│       ├── LocationTrackerApp.kt      # Application initialization & DI graph
│       ├── MainActivity.kt            # Edge-to-edge Compose host activity
│       ├── data/                      # Local SQLite persistence (Room/SQLite, Exporters)
│       ├── model/                     # Type-safe telemetry, cellular & geofence domain models
│       ├── service/                   # Hardware telemetry background services
│       │   ├── LocationForegroundService.kt  # Android foreground service with persistent HUD
│       │   ├── CellularDiagnosticManager.kt  # TelephonyManager cell radio scanner
│       │   ├── OpenCellIdClient.kt           # Uganda PLMN OpenCellID resolver (MTN/Airtel)
│       │   └── AppUpdateManager.kt           # GitHub Releases In-App update engine
│       ├── ui/                        # Jetpack Compose UI layer (Material 3 Daylight Palette)
│       │   ├── components/            # Cartographic OpenStreetMap viewport & telemetry HUD
│       │   ├── screens/               # Map, Diagnostics, Comparator, Geofencing, Settings
│       │   └── theme/                 # Tactical M3 typography, shape, and daylight color scheme
│       └── viewmodel/                 # TrackerViewModel (StateFlow & Coroutines state engine)
│
├── build.gradle.kts                   # Root Gradle project configuration
├── settings.gradle.kts                # Project module inclusion & dependency resolution
├── gradle.properties                  # JVM memory allocation & AndroidX configurations
└── .gitignore                         # Git exclusion rules (ignores build/, *.apk, .aistudio/artifacts)
```

---

## 3. Core Implemented System Capabilities

### 🗺️ Real-Time Map & Trajectory Tracking
- **Multi-Constellation GNSS:** Google Play Services `FusedLocationProviderClient` streaming continuous 1 Hz fixes (latitude, longitude, altitude, speed, bearing, and 68% confidence error radius).
- **Cartographic Viewport:** Real OpenStreetMap tiles rendered via `osmdroid` (`MAPNIK`, Carto Light, and OpenTopoMap) with smooth hardware-accelerated canvas pan, zoom, and auto-centering.
- **Stationary Deadband Filter:** On-device Euclidean deadband filter ($T = 1.5\text{ m}$) that eliminates fake multipath GPS jitter when standing still, saving kilometers of false trajectory drift.

### 📡 Live Uganda Cell Tower Pinning (OpenCellID)
- Real-time hardware interrogation of active and neighbor base transceiver stations via Android `TelephonyManager`.
- Resolves serving cell coordinates and estimated coverage radius for Ugandan PLMNs:
  - **MTN Uganda (`641-10`)**
  - **Airtel Uganda (`641-22`)**
- Renders the physical serving mast marker on the map with a semi-transparent coverage circle and a vector connecting ray from the device GPS position directly to the cell tower.
- Includes a bundled offline base station database covering MUST Kihumuro (FAST engineering block, Kihumuro hill), MUST Town Campus, and Mbarara Central.

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

### 📜 Academic History & Multi-Format Spatial Export
- 100% offline local SQLite persistence; zero personal data leakage to external cloud servers.
- One-tap export to industry-standard formats:
  - **GPX 1.1:** Schema-compliant XML for QGIS, Strava, and Garmin devices.
  - **KML 2.2:** OGC-compliant 3D trajectory markup for Google Earth visualization.
  - **CSV:** Tabular format for Pandas / R data analysis.

---

## 4. Statutory Legal & Privacy Compliance (Uganda Jurisdiction)

GeoTrace is architected to comply strictly with national regulatory frameworks in the Republic of Uganda (see [`ACADEMIC_EVALUATION.md`](ACADEMIC_EVALUATION.md)):
1. **Data Protection and Privacy Act, 2019 (Cap 97):** Zero-cloud architecture. Spatial telemetry is strictly retained in local application partitions (`/data/data/com.geotrace/databases/`).
2. **Uganda Communications Act, 2013 (Cap 106):** Passive cell identity collection via standard Android telephony APIs. No unauthorized RF transmissions or IMSI interception.
3. **Electronic Signatures Act, 2011 (Cap 101) & Electronic Transactions Act (Cap 103):** Immutable ISO-8601 UTC timestamps and verifiable spatial lineage for academic and evidentiary audits.

---

## 5. Building & Running the Application

### Option A: Direct APK Installation (Fastest)
Download the pre-compiled APKs directly from the repository's GitHub Releases tab:
- **Release APK (Optimized):** `app-release.apk`
- **Debug APK (Logging Enabled):** `app-debug.apk`

### Option B: Build from Source via Gradle
Ensure JDK 21 is installed, then build using the terminal:

```bash
# Clone the repository
git clone https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026.git
cd Group_14_GIS__BSE_SWE2105_project_task_2026

# Run automated unit tests
./gradlew testDebugUnitTest

# Assemble Debug and Release APKs
./gradlew assembleDebug assembleRelease
```
The compiled APK files will be located at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

---

## 6. Group Members & Academic Contribution

| Name | Student Number | Registration Number | GitHub Handle | Role / Core Contribution |
| :--- | :--- | :--- | :--- | :--- |
| **Mark Paul Vicram** | — | — | [`@mugmarp`](https://github.com/mugmarp) | Lead Architecture, Telemetry Engine, CI/CD Pipeline |
| *[Group Member 2]* | — | — | — | Mobile UI, Map Overlays & Geofencing |
| *[Group Member 3]* | — | — | — | OpenCellID Integration & Telephony Diagnostics |
| *[Group Member 4]* | — | — | — | Field Testing, Data Analysis & Academic Protocol |

**Repository URL:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`
