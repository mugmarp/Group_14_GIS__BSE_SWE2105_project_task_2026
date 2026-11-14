# Decision Log — GIS Group 14 SWE2105 Project

This file records key architectural and strategic decisions made by the group. Update as decisions are made.

---

## Decision Template

When recording a decision, use this format:

```markdown
### [DATE] Decision: [Short Title]

**Status:** [Proposed / Accepted / Rejected / Superseded]

**Context:** [What problem are we solving? What constraints exist?]

**Options Considered:**
1. [Option A] — [Pros/Cons]
2. [Option B] — [Pros/Cons]
3. [Option C] — [Pros/Cons]

**Decision:** [What we chose]

**Rationale:** [Why this choice over others]

**Consequences:** [What this means for implementation, timeline, team]

**Action Items:**
- [ ] [Task for specific person]
```

---

## Decisions

### 2026-11-01 Decision: Project Option Selection

**Status:** Accepted

**Context:** The project task requires choosing between two tracking approaches:
- Option 1: Laptop tracking via IP address
- Option 2: Person tracking via mobile phone (GPS/cell/WiFi)

**Additional Uganda Context:** See `PROBLEM_CONTEXT.md` for detailed research on Uganda-specific problems this could address:
- **Boda boda safety crisis**: 50,000-80,000 drivers in Kampala; fatalities doubled 2011-2015; 10-20 trauma cases daily at Mulago; 41% of trauma ward patients from moto-taxi accidents; 10,000+ motorcycles stolen annually
- **Vehicle theft**: 999 motor vehicles stolen in 2024; 91% recovery rate with digital tracking (police ITMS); government mandating GPS trackers on all vehicles (controversial, privacy concerns)
- **Child/school transport safety**: Uganda Police safe transport guidelines; "2 school children lost daily"; National Safe School Zones Guide 2025; authorities proposing real-time monitoring
- **Mobile landscape**: Android ~70%+ market share; Samsung leading vendor; MTN/Airtel 4G nationwide, 5G in major cities (NSA); smartphone penetration ~15-25% (cost barrier ~UGX 350k); SafeBoda/Bolt/Uber have trip-sharing features
- **Existing solutions**: SafeBoda (ride-hailing + tracking + "zero data" mode), Bolt, Faras, commercial fleet trackers (Proxi, BlackKnight), government ITMS

**Options Considered:**
1. **Option 1: IP Address Tracking**
   - Pros: Simpler implementation, good for learning networking/APIs
   - Cons: Cannot do real-time tracking; accuracy fundamentally limited to city-level; IP changes infrequently; VPN defeats it
2. **Option 2: Mobile Phone Tracking (GPS/Cell/WiFi)**
   - Pros: True real-time tracking (1 Hz GPS); meter-level accuracy outdoors; indoor via WiFi; demonstrates mobile dev, sensor fusion, battery optimization, geospatial viz
   - Cons: Higher complexity; requires physical device testing; background location permissions tricky

**Decision:** Option 2 — Mobile Phone Tracking (GPS/Cell/WiFi)

**Rationale:** 
- Meets the core "real-time tracking" requirement; IP-based fundamentally cannot
- Aligns with Uganda's actual problems: boda boda safety, vehicle theft, child transport, family safety
- Android dominant in Uganda (~70%+), Kotlin/Compose gives full sensor access including raw cell tower data
- Portfolio value: demonstrable mobile app with map, trails, export — beats static IP lookup
- Zero cost: FusedLocationProvider, OSM/MapLibre, Firebase free tier all work in Uganda
- Testable on real devices: team can collect actual GPS traces in Kampala/Mbarara, measure accuracy vs ground truth

**Consequences:**
- 4-6 week project with appropriate engineering complexity for SWE2105
- Requires Android devices for testing (team should verify availability)
- Need to design for Ugandan constraints: intermittent connectivity, battery sensitivity, cost-conscious users, privacy awareness (government tracker mandate backlash)

**Action Items:**
- [x] All members read `FEASIBILITY_ANALYSIS.md` and `PROBLEM_CONTEXT.md`
- [x] Record final decision here
- [ ] Initialize Android/Kotlin/Compose project scaffold (Week 1)
- [ ] Define target problem from Uganda context (boda boda safety? school transport? vehicle anti-theft? family safety?)
- [ ] Update Decision Index below

---

### 2026-11-01 Decision: Target Platform

**Status:** Accepted

**Context:** Option 2 selected (Mobile Phone Tracking). Need to pick development platform for Uganda context.

**Options Considered:**
1. **Android (Kotlin + Jetpack Compose)** — Full sensor access (`TelephonyManager.getAllCellInfo()`), free tools, free maps (OSM/MapLibre), direct device testing, **Android ~70%+ in Uganda**, Samsung leading vendor, team likely has Android devices
2. **iOS (Swift + SwiftUI)** — Polish, but no raw cell tower API, $99/yr dev account for device testing, Mac required, low iOS share in Uganda
3. **Flutter (Dart)** — Cross-platform, but limited background location on iOS, platform channels needed for raw cell info, adds complexity
4. **React Native (TypeScript)** — Cross-platform, same iOS limitations, Expo managed workflow simplifies but ejects for background
5. **Web (PWA, Leaflet/MapLibre GL JS)** — Cross-platform, zero install, Browser Geolocation API (fused), but no background tracking, no raw cell access, requires reliable internet

**Decision:** Android (Kotlin + Jetpack Compose)

**Rationale:** 
- Uganda market: Android dominant (~70%+), Samsung leading vendor — matches user base
- Full sensor access: `TelephonyManager.getAllCellInfo()` for raw cell tower data (requirement discusses cell tower triangulation)
- Free tooling: Android Studio, Gradle, Kotlin, Compose — no Mac needed, no $99/yr fee
- Team skills: BSE students likely have Java/Kotlin exposure; Compose is modern declarative UI
- Offline-first: Works without constant internet (critical for Uganda intermittent connectivity)
- Background tracking: Foreground Service + `ACCESS_BACKGROUND_LOCATION` fully supported
- Testing: Direct device testing on team phones; no simulator limitations

**Consequences:** 
- Single-platform (Android) — acceptable for student project; web viewer can be added later for cross-platform dashboard
- Compose learning curve if team new to it; but better long-term than XML
- Map library choice now Compose-compatible (osmdroid Compose wrapper or MapLibre Compose)

**Action Items:**
- [ ] Verify all team members have Android devices for testing (min API 24/Android 7.0)
- [ ] Initialize project with Kotlin + Compose + Material3
- [ ] Spike: test osmdroid vs MapLibre in Compose (see Map Library decision)
- [ ] Set up GitHub Actions for CI builds (free, no local Gradle daemon issues)

---

### 2026-11-04 Decision: Map Library

**Status:** Accepted

**Context:** Need free, no-API-key map solution for student project.

**Options Considered:**
1. **osmdroid (OpenStreetMap for Android)** — Mature, free, no key, raster tiles, Kotlin/Java
2. **MapLibre Native / Mapbox Maps SDK (v11+)** — Vector tiles, free (MapLibre), modern, Compose support
3. **Google Maps SDK** — Requires API key (free tier generous but needs billing account), best docs
4. **Leaflet (Web) / React Leaflet** — Web only, simple, raster tiles

**Decision:** **osmdroid (`org.osmdroid:osmdroid-android`)** wrapped cleanly inside Jetpack Compose `AndroidView` (`MapViewComposable.kt`) with shared tile definitions in `MapTileSources.kt` and an internal **512 MB** cache (`filesDir/osmdroid/tiles`, trimming at 450 MB).

**Rationale:** Zero API key or billing account friction, mature offline `CacheManager` + `SqlTileWriter` archiving, multi-layer support (`MAPNIK`, Esri World Imagery, Carto Light Labels overlay, OpenTopoMap, Humanitarian HOT), and direct `Polyline`/`Polygon`/`Marker` drawing for trails, geofences, accuracy rings, and dBm-colored cell tower rays.

**Consequences:** Smooth multi-layer rendering across physical devices without external storage permissions; map overlays update deterministically from Kotlin `StateFlow`.

**Action Items:**
- [x] Integrate `osmdroid-android` with `MapViewComposable.kt` & `MapTileSources.kt`
- [x] Configure `UserAgentValue` (`$packageName/${BuildConfig.VERSION_NAME}`) and 512 MB private internal cache

---

### 2026-11-04 Decision: Local Database

**Status:** Accepted

**Context:** Need to store location points locally for offline-first trail recording.

**Options Considered:**
1. **Room (SQLite)** — Android standard, type-safe, Kotlin coroutines, LiveData/Flow
2. **Realm** — Object-oriented, reactive, but larger APK
3. **DataStore (Preferences/Proto)** — Not for relational location data
4. **Plain SQLite (`SQLiteOpenHelper`) + Preferences DataStore** — Zero KSP annotation processor overhead, direct SQL control, fast builds

**Decision:** **Native Android `SQLiteOpenHelper` (`LocationDbHelper.kt`, v3 with `(session_id, timestamp)` composite index) + Jetpack DataStore (`SettingsManager.kt`)**.

**Rationale:** Avoids KSP/Room annotation build complexity while giving deterministic SQL queries, atomic transactions, and fast $O(\log N)$ export generation alongside lightweight DataStore preferences.

**Consequences:** Clean separation between relational/spatial persistence (`gis_tracker.db` v3: `tracking_sessions`, `location_points`, `geofence_zones`, `geofence_alerts`, `offline_map_regions`) and user preferences (`geotrace_prefs` DataStore).

**Action Items:**
- [x] Implement `LocationDbHelper` (`tracking_sessions`, `location_points`, `geofence_zones`, `geofence_alerts`, `offline_map_regions`)
- [x] Add `idx_points_session` index (v2) and `offline_map_regions` table (v3) via non-destructive `onUpgrade` migrations

---

### 2026-11-09 Decision: Backend / Sync (Optional)

**Status:** Accepted

**Context:** Multi-user sharing, personal multi-device fleet lookup, remote alarm trigger, and off-grid P2P discovery.

**Options Considered:**
1. **Firebase Realtime Database** — Free tier, real-time sync
2. **Supabase (PostgreSQL)** — Free tier, SQL
3. **Cloud Firestore (Named Database) + Credential Manager Google Sign-In + Google Nearby Connections (`P2P_CLUSTER`)** — Serverless, zero-trust Firestore rules, Google Sign-In, plus zero-internet P2P fallback
4. **None (local-only)** — Local export only

**Decision:** **Hybrid Opt-In Architecture: Firebase Cloud Firestore (named database `R.string.firestore_database_id`) gated by Jetpack Credential Manager Google Sign-In, plus offline Google Nearby Connections (`P2P_CLUSTER`)**.

**Rationale:** Local SQLite stays private when signed out or in Ghost Mode, while signed-in users get real-time multi-device fleet visibility, invite-code tracking groups, and remote ring—plus Nearby P2P works without internet.

**Consequences:** Requires `firestore.rules`, composite `{uid}_{deviceId}` keys, `SetOptions.merge()`, and consistent SHA-1 keystore signing across local builds and GitHub Actions.

**Action Items:**
- [x] Provision Cloud Firestore, `firestore.rules`, and `firestore.test.js`
- [x] Implement `GroupRepository.kt`, `NearbyP2PManager.kt`, and `FindDeviceAlarmManager.kt`

---

### 2026-11-08 Decision: Background Tracking Strategy

**Status:** Accepted

**Context:** Android 10+ requires `ACCESS_BACKGROUND_LOCATION` and Foreground Service with `FOREGROUND_SERVICE_LOCATION` plus battery/accuracy balance.

**Options Considered:**
1. **Foreground Service + High Accuracy** — Continuous GPS (1s / 2m)
2. **Foreground Service + Balanced Power** — Fused WiFi/Cell/GPS (5s / 10m)
3. **Foreground Service with User-Selectable Tiers (`HIGH_ACCURACY`, `BALANCED`, `POWER_SAVER`) + WakeLock**
4. **WorkManager periodic** — Not real-time (~15 min minimum interval)

**Decision:** **Foreground Service (`LocationForegroundService.kt`) with `PARTIAL_WAKE_LOCK` during active trail recording, three user-selectable tiers (`High Accuracy (1s GNSS)`, `Balanced Fused (5s)`, `Power Saver (Cell Only)`), and a lightweight 24/7 Protection Mode (`ACTION_START_PROTECTION`)**.

**Rationale:** Gives evaluators full high-frequency precision when recording trails while keeping idle background locating & remote ring alerts battery-light (`PRIORITY_BALANCED_POWER_ACCURACY`, 60s/25m, no WakeLock). An earlier debug-only `autoBalancedTestMode` override was discarded so user-selected modes and persisted preferences remain 100% predictable across Debug and Release builds.

**Consequences:** Telemetry engine tracks active tier transitions cleanly, persists all `TrackerSettings` in `geotrace_prefs`, downgrades seamlessly from `REC` to 24/7 Protection Mode on stop, and auto-restarts Protection Mode on `BOOT_COMPLETED` when signed in with background location granted.

**Action Items:**
- [x] Implement `LocationForegroundService` with `PARTIAL_WAKE_LOCK` (active recording) and lightweight 24/7 Protection Mode (idle)
- [x] Expose tier selector chip on Map HUD and persist all `TrackerSettings` in `LocationRepository`
- [x] Guard `LocationRepository.recordLocation` & `stopCurrentSession` against `"cellular"` ↔ GNSS transition spikes

---

### 2026-11-14 Decision: Multi-Layer Satellite Imagery & Offline Region Downloads

**Status:** Accepted

**Context:** Field testing in rural Mbarara/Kihumuro and off-grid P2P demonstrations require physical aerial imagery (rooftops, dirt paths, masts) and guaranteed offline tile availability without Google Maps billing keys or risky broad storage permissions. Also, OSM standard `MAPNIK` enforces `FLAG_NO_BULK` prohibiting bulk tile scraping.

**Options Considered:**
1. **Google Maps SDK** — Requires billing account/API key and disallows raw offline bulk tile caching.
2. **osmdroid Custom XYZ Raster Sources + `CacheManager` Offline Regions** — Esri World Imagery (`{z}/{y}/{x}`), Carto Light Labels `TilesOverlay` (Hybrid), OpenTopoMap (capped Z17), and Humanitarian HOT stored in private `filesDir/osmdroid/tiles` (512 MB cap).

**Decision:** **5-Layer `MapTileSources.kt` + `OfflineMapManager.kt` (`CacheManager` + `SqlTileWriter`)** using private internal storage, $O(\text{zoomLevels})$ Web Mercator math for pre-download estimation, a 2,500-tile cap per region download, and SQLite `v3` (`offline_map_regions`) region metadata. Standard `MAPNIK` respects `supportsOfflineBulkDownload = false` (`FLAG_NO_BULK`) while remaining passively auto-cached on pan/zoom.

**Rationale:** Gives evaluators Google Maps-grade Satellite/Hybrid aerial clarity and true off-grid map regions with zero external API keys, zero `OutOfMemoryError` estimation risk, and full respect for OpenStreetMap tile usage policy.

**Consequences:** Users can pre-cache `Map View`, `2 km`, or `5 km` zones around field sites before entering dead zones; switching layers automatically clamps zoom (`newSource.maximumZoomLevel`) so Topo (Z17 max) never renders blank tiles; users can also tap Esri Satellite directly via `"Pick on Map"` inside `GeofenceEditDialog` to align Safe Zone centers with exact rooftops and gates.

**Action Items:**
- [x] Implement `MapTileSources.kt` (Esri Satellite, Carto Hybrid overlay, OpenTopoMap, Humanitarian HOT)
- [x] Implement `OfflineMapManager.kt`, `OfflineMapsDialog.kt`, and `LocationDbHelper` `v3` `offline_map_regions`
- [x] Implement `"Pick on Map"` geofence coordinate picker and `Reset Sample Zones`

---

## Decision Index

| Date | Decision | Status |
|------|----------|--------|
| 2026-11-01 | Project Option Selection | **Accepted** (Option 2: Mobile Phone Tracking) |
| 2026-11-01 | Target Platform | **Accepted** (Android / Kotlin / Jetpack Compose) |
| 2026-11-04 | Map Library | **Accepted** (osmdroid + 512 MB internal cache) |
| 2026-11-04 | Local Database | **Accepted** (SQLite `LocationDbHelper` v3 + DataStore) |
| 2026-11-09 | Backend / Sync | **Accepted** (Cloud Firestore + Credential Manager + Nearby P2P) |
| 2026-11-08 | Background Tracking Strategy | **Accepted** (`LocationForegroundService` 3-tier `REC` + 24/7 Protection Mode) |
| 2026-11-14 | Multi-Layer & Offline Cartography | **Accepted** (Esri Satellite, Hybrid Overlay, HOT, `OfflineMapManager`, Map Pick) |

---

## How to Use This Log

1. **Before each decision:** Read relevant section in `FEASIBILITY_ANALYSIS.md`
2. **During discussion:** Fill in "Options Considered" with honest pros/cons
3. **After agreement:** Complete "Decision", "Rationale", "Consequences"
4. **Create action items** with checkboxes for tracking
5. **Reference decisions** in commit messages: `feat: update tracking tier per DECISION_LOG.md`