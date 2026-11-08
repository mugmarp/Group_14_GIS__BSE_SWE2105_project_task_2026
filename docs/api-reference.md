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

### Pre-Seeded Offline Cache
When no internet connection is available or when the device runs in an offline testing zone, `OpenCellIdClient.kt` automatically falls back to bundled Ugandan base stations:
- **MUST Kihumuro FAST Campus (MTN 4G LTE):** `-0.6182°S, 30.6551°E`, Range: $750\text{ m}$, CID: `41201`
- **MUST Kihumuro Hill Sector (Airtel 4G LTE):** `-0.6198°S, 30.6575°E`, Range: $820\text{ m}$, CID: `41202`
- **MUST Town Campus / Mbarara Regional Referral (MTN 4G):** `-0.6125°S, 30.6582°E`, Range: $500\text{ m}$, CID: `41101`
- **Mbarara Central Roundabout (Airtel 4G):** `-0.6068°S, 30.6540°E`, Range: $650\text{ m}$, CID: `41102`
- **Mbarara High Street / Kakoba (MTN 3G/4G):** `-0.6090°S, 30.6610°E`, Range: $900\text{ m`, CID: `41103`

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
- `tag_name`: Semantic version tag (e.g., `v1.0.0`, `v1.0.1`) compared against local `BuildConfig.VERSION_NAME`.
- `name`: Human-readable release title.
- `body`: Markdown release notes and changelog rendered in the update prompt.
- `assets[].browser_download_url`: Direct URL to `app-release.apk`.
- Download handled via Android `DownloadManager` with installation delegated to `FileProvider` (`com.geotrace.fileprovider`).

---

## 3. Spatial Data Export Schemas

### 1. GPX 1.1 Specification (Topografix DTD)
- Conforms to standard `http://www.topografix.com/GPX/1/1`.
- Contains track metadata `<trk>`, track segment `<trkseg>`, and track points `<trkpt lat="..." lon="...">`.
- Each point records:
  - `<ele>`: Altitude in meters above mean sea level.
  - `<time>`: ISO 8601 UTC timestamp (`YYYY-MM-DDTHH:mm:ssZ`).
  - `<speed>`: Velocity in meters per second.
  - `<hdop>`: Horizontal dilution of precision derived from GPS accuracy radius.

### 2. KML 2.2 Specification (OGC Standard)
- Conforms to Open Geospatial Consortium (OGC) standard KML schema.
- Renders 3D extruded polyline (`<LineString>`) with `<altitudeMode>clampToGround</altitudeMode>`.
- Includes colored waypoint pins for start, finish, and intermediate geofence boundaries.

### 3. CSV Telemetry Schema
Comma-separated values for Python (Pandas) / R / QGIS spatial analysis:
```csv
timestamp,iso_time,latitude,longitude,accuracy_m,speed_mps,altitude_m,bearing_deg,provider,session_id
1731024000000,2026-11-08T00:00:00Z,-0.617124,30.654812,3.2,0.00,1432.5,182.4,fused,session_001
```
