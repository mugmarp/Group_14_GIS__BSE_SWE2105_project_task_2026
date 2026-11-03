# Architecture — GIS Group 14 SWE2105 Project

**Status:** ✅ **Decided** — Option 2 (Mobile Tracking), Android/Kotlin/Compose

---

## High-Level Architecture (Option 2 — Mobile Tracking)

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE APP (Android)                 │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose + Material3)                 │
│  └── Map View (MapLibre / osmdroid)                     │
├─────────────────────────────────────────────────────────┤
│  Domain Layer                                           │
│  ├── Location Service (FusedLocationProviderClient)     │
│  │   ├── Primary: GPS + WiFi + Cell fusion (adaptive)  │
│  │   └── Educational: Raw CellInfo + OpenCellID API     │
│  ├── Trail Recorder (adaptive intervals)                │
│  ├── Geofence Manager (home/school/work/custom)         │
│  └── Battery Optimizer (priority switching)             │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                             │
│  ├── Local DB (Room) — LocationPoint entities          │
│  ├── Export (GPX / KML / CSV)                           │
│  └── Sync (Firebase Realtime DB — multi-user sharing)  │
└─────────────────────────────────────────────────────────┘
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
│   ├── java/com/gisgroup14/locationtracker/
│   │   ├── MainActivity.kt
│   │   ├── LocationTrackerApp.kt
│   │   ├── data/
│   │   │   ├── db/ (Room entities, DAOs)
│   │   │   ├── repository/ (LocationRepository)
│   │   │   └── export/ (GpxExporter, KmlExporter)
│   │   ├── service/
│   │   │   ├── LocationForegroundService.kt
│   │   │   ├── BatteryOptimizer.kt
│   │   │   └── CellDiagnosticService.kt      // Optional: raw cell + OpenCellID
│   │   ├── ui/
│   │   │   ├── map/ (MapScreen, MapViewModel)
│   │   │   ├── tracking/ (TrackingScreen, TrackingViewModel)
│   │   │   ├── geofence/ (GeofenceScreen)
│   │   │   ├── sharing/ (SharingScreen)       // Family safety: share with contacts
│   │   │   ├── settings/ (SettingsScreen)
│   │   │   └── theme/ (Material3 theme)
│   │   └── di/ (Hilt modules)
│   └── res/
│       ├── values/strings.xml
│       └── xml/network_security_config.xml
└── build.gradle.kts
```

---

## Key Technical Decisions (from DECISION_LOG.md)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Tracking Approach** | Option 2: Mobile (GPS/WiFi/Cell) | Real-time capable, meter-level accuracy, meets task |
| **Platform** | Android (Kotlin + Jetpack Compose) | Full sensor access, Uganda market dominant, free tooling |
| **Map Library** | *Pending* (MapLibre vs osmdroid) | Compose-compatible, free, offline-capable |
| **Local Database** | *Pending* (Room recommended) | Android standard, type-safe, coroutines/Flow |
| **Backend/Sync** | *Pending* (Firebase Realtime DB recommended) | Free tier, real-time sync for family sharing, auth |
| **Background Strategy** | *Pending* (Adaptive: HIGH_ACCURACY moving, BALANCED stationary) | Battery-efficient, meets Android 10+ requirements |

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