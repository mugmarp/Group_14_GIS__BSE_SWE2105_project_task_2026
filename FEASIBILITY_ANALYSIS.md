# GIS Group 14 — SWE2105 Project Task Feasibility Analysis

**Task:** Design a web/mobile app for real-time location tracking of people/objects. Pick one:
1. **Laptop tracking via IP address** — research IP location determination, accuracy/limitations
2. **Person tracking via mobile phone** — GPS vs cell tower triangulation pros/cons

---

## OPTION 1: Laptop Tracking via IP Address

### How IP Geolocation Works
| Method | Description |
|--------|-------------|
| **Registry/WHOIS data** | RIR records (ARIN, RIPE, APNIC) show ISP/organization registered location |
| **BGP routing tables** | Network prefix announcements reveal geographic routing paths |
| **Active probing** | Traceroute/ping from multiple vantage points to infer location |
| **WiFi/cellular crowdsourcing** | Google/Apple/Mozilla databases map WiFi MACs & cell towers to GPS coordinates |
| **User-contributed data** | Browser Geolocation API opt-in submissions |
| **Commercial databases** | MaxMind, IP2Location, DB-IP aggregate above sources |

### Real-World Accuracy (2024-2025 Data)

| IP Type | Typical Accuracy | Confidence Area |
|---------|------------------|-----------------|
| **Static (business/hosting)** | ~1 km (privacy-capped) | Few km² |
| **Dynamic residential (ISP pool)** | City-level (66% within 50 km per MaxMind) | Metropolitan area |
| **Cellular / CGNAT** | **State to country level** | Entire state/country |
| **VPN/Proxy/Tor** | Infrastructure location, not user | Misleading |

**Key findings from independent studies (ipapi.is 2024, 18 providers):**
- Metropolitan-area correct (≤50 km): **50–75%**
- City-level correct (≤10 km): **15–35%**
- 75th percentile deviation: **128–288 km**
- Outliers >15,000 km: mobile carrier IPs geolocated to HQ, CGNAT, IPv6, stale reallocations

### Free API Options for Students
| API | Free Tier | Rate Limit | Notes |
|-----|-----------|------------|-------|
| **ip-api.com** | Unlimited | 45 req/min | No key needed, no HTTPS on free |
| **freeipapi.com** | Unlimited | Generous | No key, HTTPS, JSON/XML |
| **ipgeolocation.io** | 1,000/day | Daily cap | Requires key, production allowed |
| **MaxMind GeoLite** | 1,000/day | Daily cap | Requires account, web service |
| **Self-hosted GeoLite2** | Unlimited | None | Download DB, host locally (best for privacy) |

### Implementation Feasibility (Student Project)
| Aspect | Assessment |
|--------|------------|
| **Backend complexity** | Low — single HTTP request to API |
| **Frontend complexity** | Low — display lat/lon on map (Leaflet/MapLibre) |
| **Real-time tracking** | **Not feasible** — IP changes infrequently (DHCP lease hours/days); no push updates |
| **Accuracy for "tracking"** | **Poor** — city-level at best; useless for room/building level |
| **Privacy/ethics** | High concern — tracks network, not person; VPN defeats it |
| **Learning value** | Moderate — networking, APIs, geospatial visualization |

### Verdict: **NOT RECOMMENDED** for "real-time tracking"
- IP geolocation is **categorically unsuitable** for tracking exact location (BigDataCloud, MaxMind, IETF all state this)
- Cannot track movement — only snapshots when IP changes
- Accuracy fundamentally limited by ISP infrastructure, not algorithm

---

## OPTION 2: Mobile Phone Tracking — GPS vs Cell Tower Triangulation

### GPS/GNSS (Global Navigation Satellite System)

| Characteristic | Detail |
|----------------|--------|
| **Accuracy** | 2–5 m outdoors (multi-constellation: GPS + Galileo + GLONASS + BeiDou) |
| **Indoor/urban canyon** | Poor — signal blockage, multipath |
| **Power** | 20–30 mA active; cold start 30+ sec |
| **Coverage** | Global, no infrastructure dependency |
| **Android API** | `FusedLocationProviderClient` (Google Play Services) — fuses GPS + WiFi + cell |
| **iOS API** | `CLLocationManager` — similar fusion |

### Cell Tower Triangulation

| Characteristic | Detail |
|----------------|--------|
| **Accuracy** | 50–500 m (urban dense towers); **kilometers in rural** |
| **Indoor** | Works — signals penetrate buildings |
| **Power** | Lower than GPS active; adds ~1–2 min to connection |
| **Coverage** | Where cellular exists |
| **Mechanism** | Signal strength (RSSI), Timing Advance (TA), Angle of Arrival (AoA), OTDOA (LTE/5G) |
| **Android access** | `TelephonyManager.getAllCellInfo()` — requires `ACCESS_FINE_LOCATION` or `READ_PHONE_STATE` |
| **iOS access** | **Restricted** — no public API for raw cell info (CoreLocation uses it internally) |

### WiFi Triangulation (Bonus — Fused Provider uses this)

| Characteristic | Detail |
|----------------|--------|
| **Accuracy** | 10–20 m in dense WiFi areas |
| **Indoor** | Excellent — primary indoor positioning method |
| **Power** | Minimal — adds 1–2 sec scan time |
| **Dependency** | Requires WiFi AP database (Google/Apple/Mozilla/Combain/OpenWifi) |

### Pros/Cons Comparison

| Factor | GPS | Cell Triangulation | WiFi Triangulation |
|--------|-----|-------------------|-------------------|
| **Outdoor accuracy** | ★★★★★ (2–5 m) | ★★☆☆☆ (50–500 m) | ★★★☆☆ (10–20 m) |
| **Indoor accuracy** | ☆☆☆☆☆ | ★★★☆☆ | ★★★★★ |
| **Urban canyon** | ★★☆☆☆ | ★★★★☆ | ★★★★☆ |
| **Rural coverage** | ★★★★★ | ★★☆☆☆ | ☆☆☆☆☆ |
| **Battery impact** | High | Medium | Low |
| **Works offline** | Yes (after almanac) | No (needs network) | No (needs DB) |
| **Platform access** | Full (Android/iOS) | Android only (raw) | Android (full), iOS (via CoreLocation) |
| **Real-time capable** | Yes (1 Hz+ updates) | Slow (minutes) | Fast (seconds) |

### Implementation Feasibility (Student Project)

**Android (Recommended — full access):**
- `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` → GPS + WiFi + cell fusion
- `PRIORITY_BALANCED_POWER_ACCURACY` → WiFi + cell (no GPS), ~block-level
- Foreground Service for background tracking (Android 10+ requires `ACCESS_BACKGROUND_LOCATION`)
- Google Maps SDK / MapLibre / OSM for visualization
- **Cost: $0** (free APIs, free maps)

**iOS (Limited):**
- `CLLocationManager` — abstracted fusion, no raw cell access
- Background location modes in `Info.plist`
- MapKit for visualization
- **Cost: $0** (but requires Apple Developer account $99/yr for device testing)

**Web App Alternative (Cross-platform):**
- Browser Geolocation API → `navigator.geolocation.watchPosition()`
- Uses same fused providers (GPS + WiFi + cell on mobile)
- HTTPS required, user permission prompt
- Leaflet/MapLibre GL JS for map
- **Cost: $0**, works everywhere

### Real-World Architecture for Student Project

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE APP (Android/iOS/Web)         │
├─────────────────────────────────────────────────────────┤
│  FusedLocationProvider (Android) / CoreLocation (iOS)  │
│  navigator.geolocation (Web)                            │
│         │                                               │
│         ▼                                               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │   GPS       │    │   WiFi      │    │   Cell      │  │
│  │   (GNSS)    │    │   Scan      │    │   Info      │  │
│  └─────────────┘    └─────────────┘    └─────────────┘  │
│         │               │               │                │
│         └───────────────┼───────────────┘                │
│                         ▼                                 │
│              ┌─────────────────────┐                      │
│              │  Location Fusion    │  (OS handles this)   │
│              └─────────────────────┘                      │
│                         │                                 │
│         ┌───────────────┼───────────────┐                │
│         ▼               ▼               ▼                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │  Local DB   │ │  Map View   │ │  Background │        │
│  │  (Room/     │ │  (Maps SDK/ │ │  Sync to    │        │
│  │  SQLite)    │ │  Leaflet)   │ │  Server     │        │
│  └─────────────┘ └─────────────┘ └─────────────┘        │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
               ┌─────────────────────┐
               │  BACKEND (Optional) │
               │  Firebase / Supabase│
               │  / Custom (Node/    │
               │  Go/Python)         │
               └─────────────────────┘
```

---

## DECISION: **Option 2 (Mobile Phone Tracking)** — **Accepted**

### Why Option 2 Wins
| Criterion | Option 1 (IP) | Option 2 (Mobile) |
|-----------|---------------|-------------------|
| **Real-time tracking** | ❌ Impossible | ✅ Native (1 Hz GPS) |
| **Accuracy** | City-level (km) | Meter-level (GPS) / 10s m (WiFi) |
| **Indoor tracking** | ❌ No | ✅ WiFi triangulation |
| **Movement history** | ❌ No (IP static) | ✅ Continuous trail |
| **Technical depth** | Low (one API call) | High (sensors, fusion, background, maps) |
| **Learning outcomes** | Networking basics | Mobile dev, geospatial, battery optimization, privacy |
| **Portfolio value** | Low | **High** — demonstrable real app |
| **Feasibility (4–6 weeks)** | Trivial (too trivial) | **Achievable with scope management** |

### Recommended Scope for BSE Group Project (4–6 weeks)

| Week | Milestone |
|------|-----------|
| 1 | Project setup: Android (Kotlin/Compose) or Flutter or React Native; permissions; basic map |
| 2 | Foreground location service; `FusedLocationProviderClient`; store points locally (Room) |
| 3 | Map visualization: trail polyline, current marker, zoom-to-fit; background sync design |
| 4 | Battery optimization: priority switching, adaptive intervals, geofencing for stationary detection |
| 5 | Optional backend: Firebase Realtime DB / Supabase for multi-user sharing; auth |
| 6 | Polish: settings (accuracy/battery tradeoff), export GPX/KML, README, demo video |

### Required Permissions (Android)
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### Key Libraries (Android)
- `com.google.android.gms:play-services-location:21.2.0` — Fused Location
- `org.osmdroid:osmdroid-android:6.1.18` — Free OpenStreetMap (no API key)
- Or `com.mapbox.maps:android:11.7.0` — MapLibre (free, vector tiles)
- `androidx.room:room-runtime:2.6.1` — Local DB
- `com.google.firebase:firebase-database:20.3.1` — Optional real-time sync

---

## FINAL VERDICT

**Decision: Option 2 (Mobile Phone GPS/Cell/WiFi Tracking)** — **Accepted**

### Rationale:
1. **Technically feasible** for real-time tracking — GPS provides meter-level accuracy outdoors; fused provider handles indoor via WiFi
2. **Demonstrates appropriate complexity** for a BSE SWE2105 group project — sensor fusion, background services, battery optimization, geospatial visualization
3. **Zero cost** — all APIs free; OpenStreetMap/MapLibre free; Firebase free tier sufficient
4. **Portfolio-worthy** — working mobile app with map, trail, export beats a static IP lookup page
5. **Real data available** — you can test on actual devices, collect real traces, measure accuracy vs ground truth

**Avoid Option 1** — IP geolocation is fundamentally incapable of "tracking" in any meaningful sense. It's a lookup, not a tracking technology. The task explicitly asks to "discuss limitations" — you'd spend the project proving it doesn't work.

---

## Sources & References

### IP Geolocation
- BigDataCloud: "How accurate can IP Geolocation get?" (2025) — https://www.bigdatacloud.com/blog/how-accurate-can-ip-geolocation-get
- MaxMind Knowledge Base: "Geolocation accuracy" — https://support.maxmind.com/knowledge-base/articles/maxmind-geolocation-accuracy
- ipapi.is: "On IP Geolocation Accuracy: A Comparative Study" (2024) — https://ipapi.is/blog/ip-geolocation-accuracy.html
- IETF: "Gaps and problems in current IP-geolocation approaches" — https://www.ietf.org/slides/slides-ipgeows-paper-gaps-and-problems-in-current-ip-geolocation-approaches-00.pdf

### Mobile Location (GPS/Cell/WiFi)
- Blues Wireless: "Comparing GPS, Cell, and WiFi Triangulation for Location Tracking" (2026) — https://blues.com/blog/beyond-gps-leveraging-cell-wifi-triangulation-for-precise-iot-location-tracking/
- AvantGuard: "GPS Vs Cellular Locating Technology" — https://www.agmonitoring.com/blog/industry-news/gps-vs-cellular-locating-technology
- Android Developers: "Build location-aware apps" — https://developer.android.com/develop/sensors-and-location/location
- Google Developers: "Fused Location Provider API" — https://developers.google.com/location-context/fused-location-provider
- Anmol's Blog: "Guide to Android Location: GPS, Wi-Fi, Cell Towers, and FusedLocationProvider" — https://blog.anmolthedeveloper.com/android-location-guide-gps-wi-fi-cell-towers-and-the-fused-location-provider-api

### Free APIs
- ip-api.com — https://ip-api.com/
- freeipapi.com — https://freeipapi.com/
- ipgeolocation.io — https://ipgeolocation.io/
- MaxMind GeoLite — https://blog.maxmind.com/geolite-web-service-free-ip-geolocation-api/