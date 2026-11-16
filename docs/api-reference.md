# API Reference — GeoTrace SWE2105 GIS Platform

**Application:** GeoTrace (`com.geotrace`)  
**Course:** BSE SWE2105 (Geographic Information Systems)  
**Institution:** MUST (Mbarara University of Science and Technology)  
**GitHub Repository:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`  

---

## 1. Cellular Telemetry & OpenCellID API Specification

GeoTrace correlates local Android base station broadcasts with the **OpenCellID REST API** to convert raw RF cell identities into geographic coordinates and confidence circles.

### Request Endpoint
- **URL:** `https://opencellid.org/cell/get`
- **Method:** `GET`
- **Parameters:**

| Parameter | Type | Required | Description | Example (Uganda) |
| :--- | :--- | :--- | :--- | :--- |
| `key` | String | Yes | OpenCellID API Token (injected via `OPENCELLID_API_KEY` secret or manual override) | `pk.xxxxxxxxx` |
| `mcc` | Integer | Yes | Mobile Country Code (Uganda = `641`) | `641` |
| `mnc` | Integer | Yes | Mobile Network Code (`10` = MTN Uganda, `22` = Airtel Uganda) | `10` |
| `lac` | Integer | Yes | Location Area Code (2G/3G) or Tracking Area Code (4G LTE) | `101` |
| `cellid` | Long | Yes | Cell Identity (CID/CI/ECI) | `41201` |
| `radio` | String | Optional | Radio access technology (`GSM`, `UMTS`, `LTE`, `NR`) | `LTE` |
| `format` | String | Yes | Response format (`json`) | `json` |

### JSON Response Schema
```json
{
  "status": "ok",
  "lat": -0.618200,
  "lon": 30.655100,
  "mcc": 641,
  "mnc": 10,
  "lac": 101,
  "cellid": 41201,
  "averageSignalStrength": -82,
  "range": 750,
  "samples": 34,
  "changeable": 1,
  "created": 1583921820,
  "updated": 1698219400
}
```

### Resolution Policy in `OpenCellIdClient.kt`
- `OpenCellIdClient` resolves coordinates strictly from the live OpenCellID REST API using real `TelephonyManager` identifiers (`MCC`, `MNC`, `LAC`, `CID`).
- An in-memory `ConcurrentHashMap` caches responses for the active session to avoid duplicate HTTP requests.
- **No synthetic or hardcoded fallback tower coordinates** are returned when offline or when no API key is configured (`resolveCellLocation` returns `null`, while raw radio IDs remain visible in **Cellular Hub**).

---

## 2. In-App Update Engine API Specification

GeoTrace checks for software updates and distribution binaries directly from the official repository release channel.

### Endpoint
- **URL:** `https://api.github.com/repos/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026/releases/latest`
- **Method:** `GET`
- **Headers:**
  - `Accept: application/vnd.github.v3+json`
  - `User-Agent: GeoTrace-Android-<versionName>`

### Response Mapping in `AppUpdateManager.kt`
- `tag_name`: Semantic version tag (e.g., `v1.0.1`, stripping pre-release suffixes during numeric comparison) compared against local `BuildConfig.VERSION_NAME`.
- `name`: Human-readable release title.
- `body`: Release notes rendered in the **Settings → Application Updates** card.
- `assets[].browser_download_url`: Selects `.apk` asset (preferring release APKs such as `geotrace-release.apk`).
- **404 / Permission Handling:** HTTP `404` maps cleanly to `UpdateStatus.NoReleasesPublished`; on Android 8+, `packageManager.canRequestPackageInstalls()` is verified before launching `FileProvider` (`com.geotrace.fileprovider`) installation, with direct **Open GitHub Releases in Browser** fallback.

---

## 3. Cloud Firestore & Nearby P2P Data Contracts

### Named Cloud Firestore Database (`R.string.firestore_database_id`)
- **Personal Multi-Device Fleet:** `/users/{userId}/devices/{deviceId}` — stores `deviceName`, `deviceModel`, coordinates, accuracy, speed, bearing, battery, `trackingMode`, `ringRequestedAt`, `ringRequestedBy`, and `lastUpdated` (`SetOptions.merge()`).
- **Shared Tracking Groups:** `/groups/{groupId}` (`name`, `inviteCode`, `createdBy`, `memberIds`) and `/groups/{groupId}/live_locations/{userId}_{deviceId}` (`isSharingEnabled` respects Ghost Mode).

### Offline Nearby Connections (`com.geotrace.p2p.mesh`, `Strategy.P2P_CLUSTER`)
- **Location Frame (`0x03` header):** `[1B version=3][8B lat][8B lng][4B speed][4B bearing][4B batteryPct][1B tagLen][N B tag][UTF-8 displayName]`
- **Remote Ring Command (`0x0A` header):** `[1B cmd=10][UTF-8 callerName]` — triggers `FindDeviceAlarmManager` on the peer device offline.

---

## 4. Spatial Data Export Schemas (`TrackExporter.kt`)

All exports distinguish **automated Trackpoints** (`<trkpt>` / `TRACKPOINT`) from **manual field Waypoints** (`<wpt>` / `WAYPOINT`), enforce explicit extensions via `ExportFormat` (preventing `.txt` fallback coercion), and support both direct **Storage Access Framework (`Save File`)** and **`FileProvider` sharing**.

### 1. GPX (GPS Exchange Format) — GPX 1.1 (`application/gpx+xml`, `.gpx`)
- Conforms to Topografix schema `http://www.topografix.com/GPX/1/1` with custom telemetry namespace `xmlns:geotrace="https://geotrace.local/xmlschemas/Telemetry/v1"`.
- Exports `<metadata>` (`<name>`, `<desc>`, `<time>`), manual field pins as `<wpt lat="..." lon="...">` (`<ele>`, `<time>`, `<name>`, optional `<desc>`, `<sym>Flag</sym>`, `<extensions><geotrace:accuracy>`), and segmented tracks (`<trk>` → `<trkseg>`, split on > 5 km teleport gaps) containing `<trkpt lat="..." lon="...">`:
  - `<ele>`: Altitude in meters above mean sea level.
  - `<time>`: ISO 8601 UTC timestamp (`YYYY-MM-DDTHH:mm:ssZ`).
  - `<extensions>`:
    - `<geotrace:speed_mps>`: Velocity in meters per second.
    - `<geotrace:accuracy_m>`: Horizontal 68% error radius in meters.
    - `<geotrace:bearing>`: Heading in degrees.
    - `<geotrace:provider>`: Location source (`fused`, `gps`, `network`, `cellular`).

### 2. KML (Google Earth) — OGC KML 2.2 (`application/vnd.google-earth.kml+xml`, `.kml`)
- Conforms to OGC KML 2.2 (`http://www.opengis.net/kml/2.2`) with Google Earth extensions (`xmlns:gx="http://www.google.com/kml/ext/2.2"`).
- Places manual field pins in a `<Folder><name>Waypoints</name>` of timestamped `<Point>` Placemarks.
- Renders time-indexed path playback using `<gx:Track>` (or `<gx:MultiTrack>` for segmented sessions) with paired `<when>` UTC timestamps and `<gx:coord>lon lat alt</gx:coord>` tuples under `<altitudeMode>clampToGround</altitudeMode>`.

### 3. CSV (Raw Telemetry) — Tabular Schema (`text/csv`, `.csv`)
14-column comma-separated schema for Python (Pandas), R, Excel, and QGIS tabular joins (`RecordType` separates manual `WAYPOINT` pins from automated `TRACKPOINT` breadcrumbs):
```csv
RecordType,Label,Note,SessionName,Timestamp,ISO_Time,Latitude,Longitude,Altitude_m,Speed_mps,Speed_kmh,Accuracy_m,Bearing,Provider
WAYPOINT,Survey Benchmark,Corner stone,MUST_Walk,1731024005000,2026-11-08T00:00:05Z,-0.617124,30.654812,1432.5,0.0,0.0,3.2,0.0,manual_pin
TRACKPOINT,,,MUST_Walk,1731024006000,2026-11-08T00:00:06Z,-0.617130,30.654820,1432.6,1.25,4.5,3.1,182.4,fused
```

### 4. GeoJSON (GIS Vector) — RFC 7946 (`application/geo+json`, `.geojson`)
- Outputs a standard RFC 7946 `FeatureCollection`:
  - **Track Feature:** `LineString` (or `MultiLineString` across > 5 km gaps) with `featureType: "track"`, `sessionId`, `name`, `trackingMode`, `startTimeUtc`, `endTimeUtc`, `distanceMeters`, `durationSeconds`, `avgSpeedKmh`, `maxSpeedKmh`, and `trackpointCount`.
  - **Waypoint Features:** Individual `Point` features (`[lon, lat, alt]`) with `featureType: "waypoint"`, `id`, `name`, optional `note`, `accuracyMeters`, and `timestampUtc`.

---

## 5. Cartographic XYZ Tile Sources & Offline Cache Policy

Defined in `ui/components/MapTileSources.kt` and initialized in `LocationTrackerApp.kt` (`filesDir/osmdroid/tiles`, `512 MB` max, trims at `450 MB`, zero runtime storage permissions).

| `MapTileType` | `osmdroid` Source Name | URL / Scheme | Zoom Range | Bulk Pre-Download |
| :--- | :--- | :--- | :--- | :--- |
| **`STANDARD`** | `Mapnik` (`TileSourceFactory.MAPNIK`) | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | `Z0–Z19` | **Passive cache only** (`FLAG_NO_BULK` respected; `supportsOfflineBulkDownload = false`) |
| **`SATELLITE`** | `EsriWorldImagery` | `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}` (`.jpg`) | `Z0–Z19` | ✅ **Supported** (`OfflineMapManager`) |
| **`HYBRID`** | `EsriWorldImagery` + `CartoLightLabels` (`TilesOverlay`) | Esri imagery base + `https://{a,b,c}.basemaps.cartocdn.com/light_only_labels/{z}/{x}/{y}.png` | `Z0–Z19` | ✅ **Supported** (2-pass download: imagery + labels) |
| **`TOPO`** | `OpenTopoMap` | `https://{a,b,c}.tile.opentopomap.org/{z}/{x}/{y}.png` | `Z0–Z17` | ✅ **Supported** (`Z17` zoom cap enforced on layer switch) |
| **`HUMANITARIAN`** | `HumanitarianOSM` | `https://{a,b}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png` | `Z0–Z19` | ✅ **Supported** (`OfflineMapManager`) |

### Offline Map Pre-Download Guardrails (`OfflineMapManager.kt`)
- **Pre-Flight Estimation:** Computes Web Mercator tile counts in $O(\text{zoomLevels})$ arithmetic without allocating full tile-index lists in memory.
- **Download Cap:** Enforces `MAX_TILES_PER_DOWNLOAD = 2500` tiles per region download (`Map View`, `2 km`, or `5 km` presets).
- **Persistence:** Downloaded bounding boxes, layer type, zoom bounds, tile count, and estimated MB are stored in SQLite `gis_tracker.db` (`offline_map_regions` table; schema currently at `v4`), kept synchronized when clearing the tile cache.

