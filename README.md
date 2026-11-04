# GIS Group 14 — SWE2105 Real-Time Location Tracker (Android App)

**Course:** BSE SWE2105 (Software Engineering)  
**Institution:** MUST (Mbarara University of Science and Technology)  
**Academic Year:** 2026  
**Implementation Choice:** **Option 2 (Mobile Phone Tracking via GPS & Cellular Triangulation)**

---

## 📱 Application Overview

> "This Android app tracks a person’s real-time movement using GPS and cell-tower information, displays the path on a real map, and allows comparison of GPS vs cellular positioning."

Built with **Kotlin** and **Jetpack Compose** for the BSE SWE2105 course project, the application strictly implements **Option 2 (Mobile Phone Tracking via GPS & Cellular Information)**, featuring live OpenStreetMap tracking, live base station diagnostics via Android `TelephonyManager`, a side-by-side positioning trade-off comparator, and GPX/KML/CSV export.

![Launcher Icon](app/src/main/res/drawable/gis_tracker_icon_1793733901193.jpg)

---

## ✨ Core Implemented Features

### 1. 🗺️ Real-Time Map & GPS Trail Tracking
- **Real OpenStreetMap Tiles:** Rendered via `osmdroid` (TileSourceFactory.MAPNIK) with dynamic street labels, buildings, zoom, pan, and real-time centering.
- **Genuine GNSS Positioning:** Powered by Google Play Services `FusedLocationProviderClient`. No hardcoded fallback coordinates or simulated routes. If GPS fix is pending, the app displays a clear *"Waiting for Satellite Signal"* state.
- **Continuous Background Tracking:** Background location persistence supported via `LocationForegroundService` (`FOREGROUND_SERVICE_TYPE_LOCATION`) with persistent notifications.
- **Telemetry HUD:** Instantaneous speed (km/h), distance (m/km), satellite accuracy radius (m), and recorded waypoint count.

### 2. 📡 Live Cellular Signal & Base Station Diagnostics
- Inspects real-time serving and neighbor base stations directly via Android `TelephonyManager`.
- Displays real Mobile Country Code (MCC), Mobile Network Code (MNC), Cell ID (CID), Tracking Area Code (TAC/LAC), and signal strength (dBm).
- LTE Timing Advance (TA) step path-loss distance estimation.
- Honest hardware reporting: if no cellular modem or SIM is present (e.g. emulator), the app reports that no base station signal is detected instead of fabricating dummy data.

### 3. ⚖️ GPS vs Cellular Positioning Comparator
- Direct side-by-side engineering comparison between GPS and Cellular positioning:
  - **Outdoor Accuracy** (GPS 2–5m vs Cell 50–500m)
  - **Indoor Penetration** (GPS fails vs Cellular penetrates walls)
  - **Active Power Consumption** (GPS 20–30 mA vs Cell < 2 mA)
  - **Time To First Fix (TTFF)** and global infrastructure dependencies.

### 4. 📜 History, Session Management & File Export
- Complete offline persistence of all recorded trails via local SQLite (`LocationDbHelper`).
- Export sessions to industry-standard GIS formats:
  - **GPX (GPS Exchange Format 1.1)** for QGIS, Strava, and Garmin devices.
  - **KML (Keyhole Markup Language)** for Google Earth 3D trajectory visualization.
  - **CSV (Comma-Separated Values)** for statistical data analysis.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 2.2
- **UI Framework:** Jetpack Compose (Material 3 Dark/Light)
- **Build System:** Gradle 9.3.1 (Kotlin DSL), Android Gradle Plugin 9.1.1
- **Location Services:** Google Play Services Location (`FusedLocationProviderClient`)
- **Cellular Telemetry:** Android `TelephonyManager` (`CellInfoLte`, `CellInfoGsm`, `CellInfoWcdma`, `CellInfoNr`)
- **Architecture:** Clean MVVM (`TrackerViewModel`, `LocationRepository`, `LocationDbHelper`)
- **Background Execution:** `LocationForegroundService` with `FOREGROUND_SERVICE_TYPE_LOCATION`

---

## 🚀 Building & Running

The project builds standard debug APKs:

```bash
gradle :app:assembleDebug
```

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`
