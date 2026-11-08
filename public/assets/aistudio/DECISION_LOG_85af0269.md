# Decision Log — GIS Group 14 SWE2105 Project

This file records key architectural and strategic decisions made by the group for the core course requirements.

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
- Android dominant in Uganda (~70%+), Kotlin/Compose gives full sensor access including raw cell tower data
- Portfolio value: demonstrable mobile app with map, trails, export — beats static IP lookup
- Zero cost: FusedLocationProvider, OSM/MapLibre, Firebase free tier all work in Uganda
- Testable on real devices: team can collect actual GPS traces in Kampala/Mbarara, measure accuracy vs ground truth

**Consequences:**
- 4-6 week project with appropriate engineering complexity for SWE2105
- Requires Android devices for testing (team should verify availability)

**Action Items:**
- [x] Record final decision here
- [x] Initialize Android/Kotlin/Compose project scaffold

---

### 2026-11-01 Decision: Target Platform

**Status:** Accepted

**Context:** Option 2 selected. Need to pick development platform.

**Options Considered:**
1. **Android (Kotlin + Jetpack Compose)** — Full sensor access (`TelephonyManager.getAllCellInfo()`), free tools, free maps (OSM), direct device testing, Android ~70%+ in Uganda
2. **iOS (Swift + SwiftUI)** — No raw cell tower API, $99/yr dev account, Mac required
3. **Flutter (Dart)** — Limited background location on iOS, platform channels needed for raw cell info
4. **React Native (TypeScript)** — Same iOS limitations, Expo ejects for background
5. **Web (PWA)** — No background tracking, no raw cell access, requires reliable internet

**Decision:** Android (Kotlin + Jetpack Compose)

**Rationale:**
- Uganda market: Android dominant (~70%+), Samsung leading vendor
- Full sensor access: `TelephonyManager.getAllCellInfo()` for raw cell tower data (requirement discusses cell tower triangulation)
- Free tooling: Android Studio, Gradle, Kotlin, Compose — no Mac needed, no $99/yr fee
- Offline-first: Works without constant internet
- Background tracking: Foreground Service + `ACCESS_BACKGROUND_LOCATION` fully supported

**Consequences:**
- Single-platform (Android) — acceptable for student project
- Compose learning curve if team new to it; but better long-term than XML

**Action Items:**
- [x] Initialize project with Kotlin + Compose + Material3

---

### 2026-11-05 Decision: Map Library

**Status:** Accepted

**Context:** Need free, no-API-key map solution for student project, Compose-compatible.

**Options Considered:**
1. **osmdroid (OpenStreetMap for Android)** — Mature, free, no key, raster tiles, works with Compose via `MapViewComposable` wrapper
2. **MapLibre Native / Mapbox Maps SDK** — Vector tiles, free (MapLibre), modern, Compose support but larger binary
3. **Google Maps SDK** — Requires API key (needs billing account)

**Decision:** osmdroid

**Rationale:**
- Zero cost, no API key, no billing account
- Mature library, well-documented
- Raster tiles work offline with caching
- Simple Compose integration via `AndroidView` wrapper
- Lightweight APK impact

**Consequences:**
- Raster tiles (not vector) — acceptable for course requirement
- Tile caching strategy needed for offline use

**Action Items:**
- [x] Integrate osmdroid with Compose `MapViewComposable`
- [ ] Implement tile caching for offline support

---

### 2026-11-05 Decision: Local Database

**Status:** Accepted

**Context:** Need to store location points locally for offline-first trail recording and export.

**Options Considered:**
1. **Room (SQLite)** — Android standard, type-safe, Kotlin coroutines, Flow/LiveData
2. **Plain SQLite (`SQLiteOpenHelper`)** — Direct control, no annotation processor, simpler for basic CRUD
3. **DataStore** — Not for relational location data

**Decision:** Plain SQLite via `SQLiteOpenHelper` (`LocationDbHelper`)

**Rationale:**
- Simpler for this scope — only two tables (sessions, points)
- No annotation processor build step
- Direct control over schema and queries
- GPX/KML/CSV export straightforward from raw queries
- Demonstrates fundamental Android database skills

**Consequences:**
- Manual cursor mapping — acceptable for small schema
- No compile-time SQL verification — mitigated by tests

**Action Items:**
- [x] Implement `LocationDbHelper` with sessions and points tables
- [x] Implement `TrackExporter` for GPX/KML/CSV

---

### 2026-11-05 Decision: Backend / Sync

**Status:** Accepted — **None (Local-Only)**

**Context:** Multi-user sharing or cloud backup of traces.

**Options Considered:**
1. **Firebase Realtime Database** — Free tier, easy auth, real-time sync
2. **Supabase (PostgreSQL)** — Free tier, SQL, real-time, auth
3. **None (local-only)** — Simplest, privacy-first, export GPX/KML for sharing

**Decision:** None — Local-only with file export

**Rationale:**
- Course requirement: "Export the trail if useful for evidence" — file export satisfies this
- No backend = zero cost, zero maintenance, zero privacy concerns
- GPX/KML/CSV are industry-standard formats for evidence/analysis
- Avoids scope creep (auth, sync conflicts, offline/online conflicts)
- Privacy-first aligns with verified legal constraints (DPPA 2019)

**Consequences:**
- No real-time multi-user sharing — not required by course
- Manual file transfer for sharing — acceptable for evidence use case

**Action Items:**
- [x] Implement GPX/KML/CSV export in `TrackExporter`

---

### 2026-11-05 Decision: Background Tracking Strategy

**Status:** Accepted

**Context:** Android 10+ requires `ACCESS_BACKGROUND_LOCATION` and Foreground Service with `FOREGROUND_SERVICE_LOCATION`.

**Options Considered:**
1. **Foreground Service + HIGH_ACCURACY** — Continuous GPS, high battery, persistent notification
2. **Foreground Service + BALANCED_POWER_ACCURACY** — WiFi/Cell only, lower battery, ~block accuracy
3. **Adaptive: HIGH_ACCURACY when moving, BALANCED when stationary** — Best balance, uses ActivityRecognition
4. **WorkManager periodic** — Not real-time, ~15 min minimum interval

**Decision:** Foreground Service + `PRIORITY_HIGH_ACCURACY` (GPS) with user-controllable interval

**Rationale:**
- Course requirement: "real-time movement" — GPS priority essential for accuracy
- User can adjust update interval in Settings (1s, 5s, 10s, 30s) for battery tradeoff
- Simpler than ActivityRecognition integration — fewer failure modes
- Persistent notification shows tracking status (Android requirement)
- `LocationForegroundService` already implemented with proper lifecycle

**Consequences:**
- Higher battery consumption — mitigated by user-configurable interval
- Requires `ACCESS_BACKGROUND_LOCATION` permission — user must grant

**Action Items:**
- [x] Implement `LocationForegroundService` with configurable interval
- [x] Add interval setting in `SettingsScreen`
- [ ] Test battery life on physical devices at different intervals

---

### 2026-11-05 Decision: GPS vs Cellular Comparison Implementation

**Status:** Accepted

**Context:** Course requirement: "allows comparison of GPS vs cellular positioning (accuracy, update rate, indoor/outdoor behaviour)"

**Options Considered:**
1. **Side-by-side screen** — Live GPS coordinates vs Cell ID lookup coordinates with metrics
2. **Research analysis screen** — Long-form academic comparison with citations
3. **Tabbed comparison** — Quick metrics + visual map overlay

**Decision:** Side-by-side live comparison screen (`CellularDiagnosticScreen`)

**Rationale:**
- Directly satisfies "comparison" requirement with live data
- Shows real-time: GPS coordinates, Cell tower coordinates, accuracy radius, signal strength, update timestamps
- No simulated or hardcoded data — honest hardware reporting
- Educational value: student sees actual GPS vs Cell behavior in their environment

**Consequences:**
- Requires `TelephonyManager.getAllCellInfo()` permission (`READ_PHONE_STATE` or `ACCESS_FINE_LOCATION`)
- Cell ID lookup via OpenCellID API needs internet (cached for offline display)
- Emulator/device without SIM shows "No cellular modem detected" — honest reporting

**Action Items:**
- [x] Implement `CellularDiagnosticManager` extracting MCC/MNC/CID/TAC/RSSI/TA
- [x] Implement `CellularDiagnosticScreen` with live comparison UI
- [ ] Add OpenCellID API integration for Cell ID → coordinates lookup
- [ ] Verify comparison works indoors/outdoors on physical devices

---

## Decision Index

| Date | Decision | Status |
|------|----------|--------|
| 2026-11-01 | Project Option Selection | **Accepted** (Option 2) |
| 2026-11-01 | Target Platform | **Accepted** (Android/Kotlin/Compose) |
| 2026-11-05 | Map Library | **Accepted** (osmdroid) |
| 2026-11-05 | Local Database | **Accepted** (Plain SQLite) |
| 2026-11-05 | Backend / Sync | **Accepted** (None — Local-only) |
| 2026-11-05 | Background Tracking Strategy | **Accepted** (Foreground Service + HIGH_ACCURACY) |
| 2026-11-05 | GPS vs Cellular Comparison | **Accepted** (Live side-by-side screen) |

---

## How to Use This Log

1. **Before each decision:** Read relevant section in `FEASIBILITY_ANALYSIS.md`
2. **During discussion:** Fill in "Options Considered" with honest pros/cons
3. **After agreement:** Complete "Decision", "Rationale", "Consequences"
4. **Create action items** with checkboxes for tracking
5. **Reference decisions** in commit messages: `feat: add SQLite DB per DECISION_LOG.md#local-database`
