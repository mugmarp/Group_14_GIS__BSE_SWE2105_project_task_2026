# GIS Group 14 — SWE2105 Real-Time Location Tracker (Android App)

**Course:** BSE SWE2105 (Software Engineering)  
**Institution:** MUST (Mbarara University of Science and Technology)  
**Academic Year:** 2026  
**Implementation Choice:** **Option 2 (Mobile Phone Tracking via GPS & Cellular Information)**

---

## 📱 Application Overview

> **Core Requirement:** *"This Android app tracks a person's real-time movement using GPS and cell-tower information, displays the path on a real map, and allows comparison of GPS vs cellular positioning."*

Built with **Kotlin** and **Jetpack Compose** for the BSE SWE2105 course project, the application implements **Option 2 (Mobile Phone Tracking via GPS & Cellular Information)** featuring:
- Live OpenStreetMap tracking with GPS trail
- Live base station diagnostics via Android `TelephonyManager`
- Side-by-side GPS vs Cellular positioning comparator
- GPX/KML/CSV export for evidence/analysis

---

## ✨ Core Implemented Features (Course Requirements Only)

### 1. 🗺️ Real-Time Map & GPS Trail Tracking
- **Real OpenStreetMap Tiles:** Rendered via `osmdroid` (TileSourceFactory.MAPNIK) with dynamic street labels, buildings, zoom, pan, and real-time centering
- **Genuine GNSS Positioning:** Powered by Google Play Services `FusedLocationProviderClient`. No hardcoded fallback coordinates or simulated routes. If GPS fix is pending, the app displays a clear *"Waiting for Satellite Signal"* state
- **Continuous Background Tracking:** Background location persistence via `LocationForegroundService` (`FOREGROUND_SERVICE_TYPE_LOCATION`) with persistent notifications
- **Telemetry HUD:** Instantaneous speed (km/h), distance (m/km), satellite accuracy radius (m), and recorded waypoint count

### 2. 📡 Live Cellular Signal & Base Station Diagnostics
- Inspects real-time serving and neighbor base stations directly via Android `TelephonyManager`
- Displays real Mobile Country Code (MCC), Mobile Network Code (MNC), Cell ID (CID), Tracking Area Code (TAC/LAC), and signal strength (dBm)
- LTE Timing Advance (TA) step path-loss distance estimation
- Honest hardware reporting: if no cellular modem or SIM is present (e.g. emulator), the app reports that no base station signal is detected instead of fabricating dummy data

### 3. ⚖️ GPS vs Cellular Positioning Comparator
Direct side-by-side engineering comparison between GPS and Cellular positioning:
- **Outdoor Accuracy** (GPS 2–5m vs Cell 50–500m)
- **Indoor Penetration** (GPS fails vs Cellular penetrates walls)
- **Active Power Consumption** (GPS 20–30 mA vs Cell < 2 mA)
- **Time To First Fix (TTFF)** and global infrastructure dependencies

### 4. 📜 History, Session Management & File Export
- Complete offline persistence of all recorded trails via local SQLite (`LocationDbHelper`)
- Export sessions to industry-standard GIS formats:
  - **GPX (GPS Exchange Format 1.1)** for QGIS, Strava, and Garmin devices
  - **KML (Keyhole Markup Language)** for Google Earth 3D trajectory visualization
  - **CSV (Comma-Separated Values)** for statistical data analysis

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3 Dark/Light)
- **Build System:** Gradle (Kotlin DSL), Android Gradle Plugin
- **Location Services:** Google Play Services Location (`FusedLocationProviderClient`)
- **Cellular Telemetry:** Android `TelephonyManager` (`CellInfoLte`, `CellInfoGsm`, `CellInfoWcdma`, `CellInfoNr`)
- **Architecture:** Clean MVVM (`TrackerViewModel`, `LocationRepository`, `LocationDbHelper`)
- **Background Execution:** `LocationForegroundService` with `FOREGROUND_SERVICE_TYPE_LOCATION`

---

## 🚀 Building & Running

```bash
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📁 Project Structure (Core Components Only)

```
app/src/main/java/com/example/gistracker/
├── MainActivity.kt                    # Entry point, navigation
├── LocationTrackerApp.kt              # Application class
├── model/
│   ├── LocationModels.kt              # LocationPoint, TrackSession data classes
│   └── SettingsModels.kt              # Settings data classes
├── data/
│   ├── LocationDbHelper.kt            # SQLite database (offline-first)
│   ├── LocationRepository.kt          # Data access abstraction
│   └── export/TrackExporter.kt        # GPX/KML/CSV export
├── service/
│   ├── LocationForegroundService.kt   # Background GPS tracking
│   └── CellularDiagnosticManager.kt   # Cell tower data extraction
├── ui/
│   ├── screens/
│   │   ├── MapScreen.kt               # Live OSM map + GPS trail
│   │   ├── CellularDiagnosticScreen.kt # Live cell tower data
│   │   ├── HistoryScreen.kt           # Session list + export
│   │   └── SettingsScreen.kt          # App preferences
│   ├── components/MapViewComposable.kt # osmdroid Compose wrapper
│   └── theme/                         # Material3 theming
└── viewmodel/TrackerViewModel.kt      # State management
```
