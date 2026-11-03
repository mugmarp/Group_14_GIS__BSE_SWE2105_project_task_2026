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

### [DATE] Decision: Map Library

**Status:** Pending

**Context:** Need free, no-API-key map solution for student project.

**Options Considered:**
1. **osmdroid (OpenStreetMap for Android)** — Mature, free, no key, raster tiles, Kotlin/Java
2. **MapLibre Native / Mapbox Maps SDK (v11+)** — Vector tiles, free (MapLibre), modern, Compose support
3. **Google Maps SDK** — Requires API key (free tier generous but needs billing account), best docs
4. **Leaflet (Web) / React Leaflet** — Web only, simple, raster tiles

**Decision:** [TO BE DECIDED]

**Rationale:** [TO BE FILLED]

**Consequences:** Affects UI framework choice (Compose vs XML vs Web)

**Action Items:**
- [ ] Spike: test osmdroid vs MapLibre in Compose
- [ ] Decide based on developer experience

---

### [DATE] Decision: Local Database

**Status:** Pending

**Context:** Need to store location points locally for offline-first trail recording.

**Options Considered:**
1. **Room (SQLite)** — Android standard, type-safe, Kotlin coroutines, LiveData/Flow
2. **Realm** — Object-oriented, reactive, but larger APK
3. **DataStore (Preferences/Proto)** — Not for relational location data
4. **Plain SQLite / File (JSON/GPX)** — Simple export, no query capability

**Decision:** [TO BE DECIDED]

**Rationale:** [TO BE FILLED]

**Consequences:** Room integrates best with Android architecture; GPX export easy from any

**Action Items:**
- [ ] Default to Room unless team has strong preference

---

### [DATE] Decision: Backend / Sync (Optional)

**Status:** Pending

**Context:** Multi-user sharing or cloud backup of traces.

**Options Considered:**
1. **Firebase Realtime Database** — Free tier (1 GB, 100 concurrent), easy auth, real-time sync
2. **Supabase (PostgreSQL)** — Free tier (500 MB), SQL, real-time, auth
3. **Custom (Node/Go/Python + PostgreSQL)** — Full control, but hosting cost/effort
4. **None (local-only)** — Simplest, privacy-first, export GPX/KML for sharing

**Decision:** [TO BE DECIDED]

**Rationale:** [TO BE FILLED]

**Consequences:** Adds scope; recommend defer to Week 5 if time permits

**Action Items:**
- [ ] Start local-only; add backend only if ahead of schedule

---

### [DATE] Decision: Background Tracking Strategy

**Status:** Pending

**Context:** Android 10+ requires `ACCESS_BACKGROUND_LOCATION` (user-granted, Play Store review) and Foreground Service with `FOREGROUND_SERVICE_LOCATION`.

**Options Considered:**
1. **Foreground Service + High Accuracy** — Continuous GPS, high battery, shows persistent notification
2. **Foreground Service + Balanced Power** — WiFi/Cell only, lower battery, ~block accuracy
3. **Adaptive: High Accuracy when moving, Balanced when stationary** — Best balance, uses activity recognition
4. **WorkManager periodic + FusedLocation** — Not real-time, ~15 min minimum interval

**Decision:** [TO BE DECIDED]

**Rationale:** [TO BE FILLED]

**Consequences:** Directly impacts battery life and Play Store compliance

**Action Items:**
- [ ] Implement adaptive (Option 3) as default
- [ ] Expose accuracy/battery tradeoff in settings

---

## Decision Index

| Date | Decision | Status |
|------|----------|--------|
| 2026-11-01 | Project Option Selection | **Accepted** (Option 2) |
| 2026-11-01 | Target Platform | **Accepted** (Android/Kotlin/Compose) |
| — | Map Library | Pending |
| — | Local Database | Pending |
| — | Backend / Sync | Pending |
| — | Background Tracking Strategy | Pending |

---

## How to Use This Log

1. **Before each decision:** Read relevant section in `FEASIBILITY_ANALYSIS.md`
2. **During discussion:** Fill in "Options Considered" with honest pros/cons
3. **After agreement:** Complete "Decision", "Rationale", "Consequences"
4. **Create action items** with checkboxes for tracking
5. **Reference decisions** in commit messages: `feat: add Room DB per DECISION_LOG.md#local-database`