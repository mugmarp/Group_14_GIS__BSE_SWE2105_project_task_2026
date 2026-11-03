# GIS Group 14 — SWE2105 Real-Time Location Tracker (Android App)

**Course:** BSE SWE2105 (Software Engineering)  
**Institution:** MUST (Mbarara University of Science and Technology)  
**Academic Year:** 2026  
**Implementation Choice:** **Option 2 (Mobile Phone Tracking via GPS & Cellular Triangulation)**

---

## 📱 Application Overview

A native Android application designed to track people or objects in real-time, built with **Kotlin** and **Jetpack Compose**. The app evaluates and implements **Option 2** (GPS GNSS vs Cell Tower Triangulation), featuring real-time trail recording, live telemetry HUD, telephony cell tower inspection, geofencing safe zones, GPX/KML export, and an interactive SWE2105 academic research comparator.

![Launcher Icon](app/src/main/res/drawable/gis_tracker_icon_1793733901193.jpg)

---

## ✨ Implemented Features

### 1. 🗺️ Real-Time Map & GPS Trail Tracking
- High-performance, offline-capable vector Canvas Map with panning, pinch-to-zoom, dynamic coordinate grid lines, and map scale indicator.
- Live telemetry HUD displaying instantaneous Speed (km/h), Total Distance (m/km), Altitude (m), Bearing, and Satellite Accuracy radius.
- Background location tracking powered by `FusedLocationProviderClient` with an active Android Foreground Service.
- Multi-mode tracking:
  - **High Accuracy (GPS - 1s updates, ~2–5m precision)**
  - **Balanced Power (Fused Wi-Fi / Cell - 10s updates, ~10–30m precision)**
  - **Power Saver (Coarse Cell Only - 30s updates, < 2 mA power consumption)**
- **Simulation Mode**: Built-in realistic movement simulator along Kampala Road / Kololo / Mulago to demonstrate real-time telemetry on devices or emulators.

### 2. 📡 Telephony Cellular Diagnostics & Triangulation
- Inspects real-time serving and neighbor base stations via Android `TelephonyManager`.
- Extracts Mobile Country Code (**MCC 641** Uganda), Mobile Network Code (**MNC 10** MTN, **MNC 22** Airtel), Cell ID (CID), Location Area Code (LAC/TAC), and RSSI/RSRP signal strength.
- Displays LTE Timing Advance (TA) with signal path-loss distance estimation.
- Side-by-side technical trade-off matrix: **GPS GNSS vs Cell Triangulation vs Cell ID Lookup**.

### 3. 🛡️ Geofencing & Safe Zones
- Monitors active perimeter boundaries (MUST Campus, Mulago Emergency Corridor, Wandegeya Boda High-Risk Junction, Kampala Safe Hub).
- Live distance calculation (Haversine formula) to each zone.
- Real-time transition detection (`ENTERED` / `EXITED`) with alert logging.
- Interactive dialog to create custom safe zones with custom radii (50m–1500m) and categories.

### 4. 📜 History, Session Management & File Export
- Complete offline persistence of all tracks and coordinates via SQLite.
- One-tap export to industry-standard GIS formats:
  - **GPX (GPS Exchange Format 1.1)** XML track points with elevation, timestamps, and speeds.
  - **KML (Keyhole Markup Language)** Google Earth 3D trajectory line strings.
  - **CSV (Comma-Separated Values)** tabular telemetry.
- Direct file sharing via Android system Share Intent.

### 5. 🚨 Emergency SOS Quick Share
- One-tap emergency coordinate broadcast formatting current GPS coordinates, accuracy, speed, active cellular tower ID, and a live Google Maps pin link for family or emergency response.

### 6. 📚 SWE2105 Academic & Legal Research
- Interactive Technology Comparator testing 6 positioning methods: GPS Fused, Raw GNSS, Cell ID Lookup, Carrier Triangulation, Wi-Fi WPS, and Option 1 (IP Geolocation).
- Comprehensive analysis of why IP geolocation fails for real-time tracking (DHCP delays, CGNAT carrier routing, VPN circumvention).
- Uganda Legal Analysis: **Regulation of Interception of Communications Act 2010 (Cap 101)** and **Data Protection & Privacy Act 2019 (Cap 97)**.

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
