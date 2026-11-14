# Architecture — GIS Group 14 SWE2105 Project

**Status:** ✅ **Decided** — Option 2 (Mobile Tracking), Android/Kotlin/Compose

---

## High-Level Architecture (Option 2 — Mobile Tracking)

```
┌──────────────────────────────────────────────────────────────────┐
│                       MOBILE APP (Android)                       │
├──────────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose + Material 3)                         │
│  ├── MapViewComposable + MapTileSources                          │
│  │   (OSM Streets, Esri Satellite, Hybrid Labels, Topo, HOT)     │
│  ├── OfflineMapsDialog (2km/5km/Viewport Pre-Downloader)         │
│  └── Screens (Map, Cellular Hub, Devices, Geofence, History,     │
│               Research, Settings)                                │
├──────────────────────────────────────────────────────────────────┤
│  ViewModel & Service Layer                                       │
│  ├── TrackerViewModel & GroupViewModel                           │
│  ├── LocationForegroundService (WakeLock + Adaptive Modes)       │
│  ├── CellularDiagnosticManager + OpenCellIdClient                │
│  ├── OfflineMapManager (CacheManager + SqlTileWriter, 512 MB)    │
│  ├── GeofenceManager + GeofenceBroadcastReceiver                 │
│  ├── FindDeviceAlarmManager + AppUpdateManager                   │
│  └── Nearby Connections P2P Mesh (Strategy.P2P_CLUSTER)          │
├──────────────────────────────────────────────────────────────────┤
│  Data & Cloud Layer                                              │
│  ├── Local SQLite (LocationDbHelper v3 / LocationRepository)     │
│  ├── Export (TrackExporter: GPX / KML / CSV via FileProvider)    │
│  └── Cloud Firestore (Named DB) + Credential Manager Google Auth │
└──────────────────────────────────────────────────────────────────┘
```

### Location Provider Strategy

| Layer | Implementation | Purpose |
|-------|----------------|---------|
| **Primary (Production)** | `FusedLocationProviderClient` with adaptive priority | Best available location automatically fused from GPS + WiFi + Cell by Google Play Services |
| **Adaptive Priority** | `HIGH_ACCURACY` when moving (GPS), `BALANCED_POWER_ACCURACY` when stationary (WiFi/Cell) | Battery-efficient real-time tracking |
| **Educational/Diagnostic (Optional)** | `TelephonyManager.getAllCellInfo()` → OpenCellID / Mozilla Location Service API | Demonstrates "true cell triangulation" for task discussion; shows raw CID/LAC/MCC/MNC vs fused result |

**Why this approach:**
- FusedLocationProvider = production-grade, handles all fusion internally, battery-optimized
- Raw cell + OpenCellID = satisfies task requirement to "discuss cell tower triangulation pros/cons" with real data
- No custom triangulation math needed — OpenCellID/Mozilla do the server-side lookup

---

## Android Package Structure (Kotlin/Compose)

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/geotrace/
│   │   ├── MainActivity.kt
│   │   ├── LocationTrackerApp.kt               // App-level manual DI container
│   │   ├── data/
│   │   │   ├── LocationDbHelper.kt             // SQLite schema & indexes (gis_tracker.db v3)
│   │   │   ├── LocationRepository.kt           // Trails, deadband/EMA/tier-guard, geofences, offline regions
│   │   │   ├── GroupRepository.kt              // Cloud Firestore (named DB) & Nearby P2P mesh
│   │   │   └── export/TrackExporter.kt         // GPX 1.1, KML 2.2 & CSV generators
│   │   ├── model/
│   │   │   ├── LocationModels.kt               // TrackingMode, LocationPoint, TrackingSession
│   │   │   ├── CellModels.kt                   // CellTowerInfo, CellTowerLocation
│   │   │   ├── GeofenceModels.kt               // GeofenceZone, GeofenceAlert
│   │   │   ├── GroupModels.kt                  // Group, MemberLocation, PersonalDevice, P2PPeer
│   │   │   └── SettingsModels.kt               // TrackerSettings, MapTileType, OfflineMapRegion, OfflineDownloadProgress
│   │   ├── service/
│   │   │   ├── LocationForegroundService.kt    // Foreground location + WakeLock
│   │   │   ├── CellularDiagnosticManager.kt    // TelephonyManager radio scanner
│   │   │   ├── OpenCellIdClient.kt             // Live OpenCellID REST API lookup
│   │   │   ├── OfflineMapManager.kt            // osmdroid CacheManager + SqlTileWriter region downloader
│   │   │   ├── GeofenceManager.kt              // Google Play GeofencingClient + BroadcastReceiver
│   │   │   ├── FindDeviceAlarmManager.kt       // Remote Ring audio/vibration alarm
│   │   │   └── AppUpdateManager.kt             // GitHub Releases API + DownloadManager
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   ├── MapTileSources.kt           // 5 base/overlay tile sources (Esri, Carto, Topo, HOT, OSM)
│   │   │   │   ├── MapViewComposable.kt        // osmdroid map, rotated chevrons, S/E trail pins, dBm cell rays
│   │   │   │   └── OfflineMapsDialog.kt        // Offline region estimator, progress & saved region manager
│   │   │   ├── screens/                        // MapScreen, CellularDiagnosticScreen, GroupScreen,
│   │   │   │                                   // GeofenceScreen, HistoryScreen, ResearchAnalysisScreen, SettingsScreen
│   │   │   └── theme/                          // Material 3 Theme, Color, Typography, Shape
│   │   └── viewmodel/
│   │       ├── TrackerViewModel.kt
│   │       └── GroupViewModel.kt
│   └── res/
│       ├── values/strings.xml & firebase_applet_config.xml
│       └── xml/file_paths.xml
└── build.gradle.kts
```

---

## Key Technical Decisions (Implemented)

| Decision | Implemented Choice | Rationale |
|----------|-------------------|-----------|
| **Tracking Approach** | Option 2: Mobile (GNSS / Fused / Cell) | Real-time capable, meter-level accuracy, battery-selectable modes |
| **Platform** | Android (Kotlin + Jetpack Compose + Material 3) | Full hardware telemetry access, reactive UI |
| **Map Library** | `osmdroid` (`6.1.18`) + `MapTileSources` + `OfflineMapManager` | Native Compose wrapper, 5 base/hybrid layers (OSM, Esri Satellite, Hybrid Labels, Topo, HOT), 512 MB internal cache & bounded offline region downloads |
| **Local Database** | SQLite (`LocationDbHelper`, indexed `v3`) | Lightweight, zero KSP overhead, fast `(session_id, timestamp)` index seeks + `offline_map_regions` |
| **Cloud Backend / Auth** | Cloud Firestore (Named DB) + Firebase Auth + Credential Manager (Google Sign-In) | Zero server ops, real-time snapshot listeners, strict `firestore.rules` |
| **Off-Grid Mesh** | Google Nearby Connections (`Strategy.P2P_CLUSTER`) | Zero-internet BLE + Wi-Fi Direct peer coordinates & Remote Ring |
| **Background Strategy** | Adaptive (`HIGH_ACCURACY` 1s / `BALANCED` 5s / `POWER_SAVER` 30s/10s cell) + `PARTIAL_WAKE_LOCK` | Battery-tunable, Doze-resilient, with provider-tier transition guards preventing cross-tier jump spikes |

---

## Components to Document

- [x] Location data model (`LocationPoint` schema)
- [ ] Permission flow diagram
- [ ] Background service lifecycle
- [ ] Battery optimization strategy (adaptive priority)
- [ ] Map rendering pipeline (MapLibre/osmdroid in Compose)
- [ ] Export format specification (GPX/KML)
- [ ] Sync protocol (Firebase Realtime DB)
- [ ] Privacy policy / data retention
- [ ] Cell diagnostic screen (raw cell + OpenCellID comparison)

---

## Appendix: Verified Uganda Technical & Legal Constraints

### Legal Framework (Primary Sources)

| Act | Chapter | Key Provisions |
|-----|---------|----------------|
| **Uganda Communications Act 2013** | Chapter 103 | General telecom regulation; UCC licensing; operator obligations | [ULII](https://ulii.org/akn/ug/act/2013/1/eng@2013-01-18) |
| **Regulation of Interception of Communications Act 2010** | Chapter 101 | Lawful interception — only authorized officers with warrant can intercept communications; telecoms must maintain interception capability | [ULII](https://ulii.org/akn/ug/act/2010/18/eng@2023-12-31) |
| **Data Protection and Privacy Act 2019** | Chapter 97 | Location data = personal data (Sec 2); consent required (Sec 7); security measures mandatory (Sec 20); unlawful disclosure offence (Sec 35, up to 10 years) | [ULII](https://ulii.org/akn/ug/act/2019/9/eng@2019-05-03/source) |

**Implications for Cell Tower Triangulation:**
- **True triangulation (TDOA/AoA from 3+ towers)** requires access to MTN/Airtel internal network measurement systems — **not available to developers**
- **Only law enforcement** (UPF, CMI, ISO) with **valid court warrant** can compel triangulation under Interception Act 2010
- **No public API exists** — neither MTN nor Airtel exposes tower measurement data
- **Location data is "personal data"** under DPPA 2019 — collecting/sharing without consent is an offence (Sec 35)

### Telecom Developer APIs (Verified)

| Operator | Public APIs | Location/Triangulation API? |
|----------|-------------|----------------------------|
| **MTN Uganda** | Mobile Money only | ❌ No |
| **Airtel Uganda** | Airtel Money only | ❌ No |

*Sources: [MTN Open API](https://www.mtn.co.ug/helppersonal/mtn-open-api/), [Airtel Developer Portal](https://developers.airtel.africa/)*

### OpenCellID Uganda Coverage

| Metric | Value |
|--------|-------|
| **MCC (Uganda)** | 641 ([ITU E.212](https://www.itu.int/dms_pub/itu-t/opb/sp/T-SP-E.212B-2014-PDF-E.pdf)) |
| **MTN Uganda** | MNC 10 (PLMN 64110) — GSM/UMTS/LTE |
| **Airtel Uganda** | MNC 22 (PLMN 64122) — GSM/UMTS |
| **CellMapper** | Crowd-sourced tower maps for [MTN](https://www.cellmapper.net/map?MCC=641&MNC=10) & [Airtel](https://www.cellmapper.net/map?MCC=641&MNC=1) |
| **OpenCellID** | Country CSV + API lookups — [downloads](https://www.opencellid.org/downloads.php) (free token required) |

**Estimated Accuracy (tower density):**
- Kampala (dense): ~200m–1km
- Regional towns: ~1–5km
- Rural (Karamoja, villages): ~5–20km+

*ResearchGate: Uganda avg 0.40 towers/km² (highly urban-skewed)*

### Cell ID Lookup vs True Triangulation — Our Implementation

| Approach | Feasibility | Accuracy | Legal |
|----------|-------------|----------|-------|
| **True triangulation (TDOA/AoA)** | ❌ Impossible — no telecom API, requires warrant | N/A | Illegal without warrant |
| **Cell ID Lookup (CID + OpenCellID)** | ✅ `TelephonyManager.getAllCellInfo()` + OpenCellID API | Kampala 200m–1km; Rural 5–20km | ✅ Legal — public DB + on-device CID |
| **Google FusedLocationProvider (Cell fallback)** | ✅ Built into Play Services | Similar (Google's proprietary cell DB) | ✅ Legal — user consents to location permission |

**Our Strategy:**
1. **Primary:** `FusedLocationProviderClient` with adaptive priority (Google handles GPS+WiFi+Cell fusion)
2. **Educational/Diagnostic:** `TelephonyManager.getAllCellInfo()` → extract MCC/MNC/LAC/CID → query OpenCellID API → display "Cell ID Lookup" position vs Fused position
3. **No triangulation math** — OpenCellID server returns tower coordinates; we display serving cell's location
4. **Report discusses:** "True triangulation vs Cell ID Lookup — legal/technical reality in Uganda"

---

### Multi-User Location Sharing & Web Tracking (Firebase Cloud Backbone)

*Adopted: November 8–9, 2026*

GeoTrace has officially selected **Firebase (Cloud Firestore + Firebase Authentication + Firebase Hosting)** for cloud multi-user location sharing and real-time Leaflet web dispatching. For full technical specifications on data models, zero-trust security rules, offline queueing, and the Leaflet web dispatcher, see [docs/multi-user-tracking.md](multi-user-tracking.md).

For the complete technical audit and resolved issues log covering `FileProvider` export streaming, `WakeLock` background retention, SQLite indexing, and Auto-Balanced testing profiles, see [docs/issues-and-resolutions.md](issues-and-resolutions.md).