# Multi-User Location Sharing & Web Tracking Dashboard Architecture

> **Implementation Status Note:** Sections 3–6 below document the original architecture & web dispatcher proposal. For the **actual implemented Android production schemas and security rules**, see [`firebase-blueprint.json`](../firebase-blueprint.json), [`firestore.rules`](../firestore.rules), and [`docs/team-guide.md`](team-guide.md). Specifically, GeoTrace currently implements:
> - **Personal Multi-Device Fleet:** `/users/{userId}/devices/{deviceId}` (with `ringRequestedAt` / `ringRequestedBy` Remote Ring)
> - **Shared Tracking Groups:** `/groups/{groupId}` + `/groups/{groupId}/live_locations/{userId}_{deviceId}`
> - **Google Sign-In:** Jetpack Credential Manager + Firebase Auth (`GetSignInWithGoogleOption`)
> - **Named Database:** All queries target `R.string.firestore_database_id` (never `(default)`)
> - **Off-Grid P2P Mesh:** Google Nearby Connections (`Strategy.P2P_CLUSTER`, `com.geotrace.p2p.mesh`)

This document specifies the technical architecture, data contracts, security controls, and client/web blueprints for real-time multi-user location sharing.

---

## 1. Architectural Overview

GeoTrace's multi-user subsystem enables mobile clients in the field to stream geospatial telemetry securely to a centralized or peer-to-peer relay, allowing authorized web operators, dispatchers, and fellow team members to observe live locations, trail breadcrumbs, and cell diagnostics simultaneously.

```
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│     GeoTrace Android Client A   │       │     GeoTrace Android Client B   │
│  - FusedLocationProvider        │       │  - Cellular Diagnostic Monitor  │
│  - SQLite Offline Point Buffer  │       │  - WakeLock Background Service  │
│  - Adaptive Sync Engine         │       │  - Geofence Breach Monitor      │
└───────────────┬─────────────────┘       └───────────────┬─────────────────┘
                │ HTTPS / WebSocket                       │ HTTPS / WebSocket
                ▼                                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                       Real-Time Cloud Relay Layer                         │
│   (Firebase Firestore / App Engine / Supabase PostGIS / MQTT Broker)      │
│   - Ingestion rate limiter & authentication validator                     │
│   - Multi-tenant room isolation & temporal access token checks            │
│   - Real-time delta publish-subscribe channels                            │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │ WebSockets / SSE
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                GeoTrace Live Web Dispatcher (Leaflet / MapLibre)          │
│   - Multi-device live pins with bearing orientation vectors               │
│   - Live breadcrumb trails color-coded by velocity & elevation            │
│   - Cell tower serving mast triangulation lines & coverage buffers        │
│   - Fleet status drawer: Battery %, GPS accuracy, speed, last fix age     │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Architecture Comparison & Evaluation

| Option | Ingestion Protocol | Backend Stack | Real-Time Sync | Hosting / Pricing | Verdict & Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Option A: Firebase + Leaflet** *(Recommended)* | HTTPS / gRPC | Cloud Firestore + Firebase Auth | Built-in via Firestore snapshot listeners | Free ($0 on Spark tier), zero server maintenance, static Leaflet frontend | **Recommended for GeoTrace** — Minimal overhead, native Android SDK support, offline queueing, sub-second latency. |
| **Option B: Supabase PostGIS** | REST / WebSockets | PostgreSQL + PostGIS + Realtime | Postgres CDC (Change Data Capture) | Free tier available, self-hostable via Docker | Excellent for spatial spatial joins (`ST_Contains`, `ST_DWithin`), but requires manual schema management. |
| **Option C: MQTT + Traccar** | MQTT / CoAP (Binary) | Mosquitto + Node.js / Traccar | MQTT Topics (`fleet/device_id/telemetry`) | Requires VPS / cloud container ($5-$20/mo) | Standard for specialized IoT hardware (Teltonika, Queclink), but higher maintenance for mobile app teams. |
| **Option D: Offline P2P Mesh** | Wi-Fi Aware / BLE | On-Device Nearby Connections | Local ad-hoc mesh | Zero cloud infrastructure ($0) | Optimal for deep wilderness / disaster relief with zero cell coverage; limited range (100–300m). |

---

## 3. Data Schema & Telemetry Contracts

### 3.1 Device Manifest (`/devices/{deviceId}`)
Stores persistent hardware identity, live status, and connectivity health.

```json
{
  "deviceId": "gt_ug_pixel7_9a402",
  "callsign": "Field Scout Alpha",
  "teamId": "survey_team_jinja",
  "hardware": {
    "model": "Pixel 7 Pro",
    "androidVersion": 14,
    "appVersion": "1.0.0"
  },
  "status": {
    "isOnline": true,
    "batteryLevelPercent": 84,
    "isCharging": false,
    "networkType": "LTE",
    "trackingMode": "BALANCED",
    "lastPingTimestamp": 1731124800000
  },
  "currentLocation": {
    "latitude": 0.3476,
    "longitude": 32.5825,
    "accuracy": 4.2,
    "speed": 6.8,
    "bearing": 142.5,
    "altitude": 1190.0,
    "timestamp": 1731124798000
  },
  "servingCell": {
    "radio": "LTE",
    "mcc": 641,
    "mnc": 1,
    "lac": 1420,
    "cid": 90142,
    "signalDbm": -85
  }
}
```

### 3.2 Real-time Track Points (`/sessions/{sessionId}/points/{pointId}`)
High-resolution stream points appended during active tracking sessions.

```json
{
  "pointId": "pt_1731124805000",
  "sessionId": "ses_kampala_survey_01",
  "deviceId": "gt_ug_pixel7_9a402",
  "latitude": 0.3478,
  "longitude": 32.5828,
  "accuracy": 3.8,
  "speed": 7.1,
  "bearing": 144.0,
  "altitude": 1192.4,
  "provider": "fused",
  "timestamp": 1731124805000
}
```

---

## 4. Zero-Trust Cloud Security & Multi-Tenancy

When implementing with Firebase Firestore, strict zero-trust security rules prevent unauthorized tracking or spoofing:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Function to verify authenticated caller belongs to target organization or team
    function isTeamMember(teamId) {
      return request.auth != null && 
             request.auth.token.teamId == teamId;
    }
    
    function isDeviceOwner(deviceId) {
      return request.auth != null && 
             request.auth.uid == deviceId;
    }

    // Devices collection: devices can only write their own telemetry; team members can read
    match /devices/{deviceId} {
      allow read: if request.auth != null && 
                     (isTeamMember(resource.data.teamId) || isDeviceOwner(deviceId));
      allow write: if isDeviceOwner(deviceId);
    }

    // Sessions and live track points
    match /sessions/{sessionId} {
      allow read: if request.auth != null && isTeamMember(resource.data.teamId);
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && request.auth.uid == resource.data.createdBy;

      match /points/{pointId} {
        allow read: if request.auth != null && isTeamMember(get(/databases/$(database)/documents/sessions/$(sessionId)).data.teamId);
        allow create: if request.auth != null && request.auth.uid == request.resource.data.deviceId;
      }
    }
  }
}
```

---

## 5. Mobile Client Sync Strategy & Battery Optimization

Streaming every 1-second GPS coordinate directly over cellular radio causes cellular RF amplifiers to stay continuously in high-power state (consuming up to 300 mA). GeoTrace implements a tiered batching and deadband synchronization algorithm:

1. **Stationary Deadband Filter**:
   - If velocity $< 0.5 \text{ m/s}$ and displacement $< 5.0 \text{ m}$, do not emit continuous network packets.
   - Dispatch an ambient heartbeat ping only once every 60 seconds to maintain online visibility.

2. **Adaptive In-Flight Batching**:
   - Save points immediately to local SQLite (`location_points`).
   - Buffer live network transmissions in memory: dispatch in batches of 5–10 points every 10–15 seconds, or immediately if a high-priority geofence breach event occurs.

3. **Offline Resilience & Catch-Up**:
   - On connection drop (`NetworkCapabilities.NET_CAPABILITY_INTERNET` lost), points accumulate in SQLite.
   - Upon reconnect, the client pushes un-synced points in chunked transactions with chronological timestamps preserved.

---

## 6. Standalone Leaflet Web Dashboard Implementation

The web dispatcher can be deployed as a single, zero-build HTML/JS bundle hosted directly on GitHub Pages or any static CDN.

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>GeoTrace Dispatcher - Live Fleet Map</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <!-- Leaflet CSS -->
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <!-- Tailwind CSS CDN -->
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    #map { height: calc(100vh - 64px); }
    .heading-arrow {
      transform-origin: center center;
      transition: transform 0.3s ease;
    }
  </style>
</head>
<body class="bg-slate-900 text-slate-100 flex flex-col h-screen">

  <!-- Top Header Navigation -->
  <header class="h-16 bg-slate-800 border-b border-slate-700 px-6 flex items-center justify-between">
    <div class="flex items-center space-x-3">
      <div class="w-3 h-3 rounded-full bg-emerald-400 animate-pulse"></div>
      <h1 class="text-lg font-bold tracking-wide">GeoTrace Dispatcher</h1>
      <span class="text-xs bg-slate-700 text-slate-300 px-2 py-0.5 rounded font-mono">Uganda Fleet</span>
    </div>
    <div class="flex items-center space-x-4 text-xs font-mono">
      <div id="active-devices-count" class="text-slate-300">Active Devices: 0</div>
      <button onclick="refreshFleetView()" class="px-3 py-1 bg-amber-500 hover:bg-amber-600 text-slate-950 font-bold rounded">
        Fit All Devices
      </button>
    </div>
  </header>

  <!-- Main Map and Sidebar Container -->
  <div class="flex-1 flex overflow-hidden">
    <!-- Map Container -->
    <div id="map" class="flex-1"></div>

    <!-- Right Telemetry Panel -->
    <aside class="w-80 bg-slate-800 border-l border-slate-700 p-4 overflow-y-auto space-y-4">
      <h2 class="text-xs font-bold text-slate-400 uppercase tracking-wider">Device Fleet Roster</h2>
      <div id="device-roster-list" class="space-y-3">
        <!-- Dynamically rendered device cards -->
      </div>
    </aside>
  </div>

  <script>
    // Initialize Leaflet Map centered on Uganda
    const map = L.map('map').setView([0.3476, 32.5825], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors | GeoTrace GIS'
    }).addTo(map);

    const deviceMarkers = {};
    const deviceTrails = {};

    // Function to update a single device on the map
    function updateDeviceTelemetry(device) {
      const { deviceId, callsign, currentLocation, status } = device;
      if (!currentLocation) return;

      const latLng = [currentLocation.latitude, currentLocation.longitude];

      // Custom marker icon with orientation angle
      const iconHtml = `
        <div style="transform: rotate(${currentLocation.bearing || 0}deg);" class="heading-arrow w-8 h-8 flex items-center justify-center">
          <svg viewBox="0 0 24 24" class="w-8 h-8 text-amber-400 fill-current drop-shadow">
            <path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/>
          </svg>
        </div>`;

      const customIcon = L.divIcon({
        className: 'device-icon',
        html: iconHtml,
        iconSize: [32, 32],
        iconAnchor: [16, 16]
      });

      if (!deviceMarkers[deviceId]) {
        deviceMarkers[deviceId] = L.marker(latLng, { icon: customIcon }).addTo(map);
        deviceTrails[deviceId] = L.polyline([latLng], { color: '#f59e0b', weight: 3, opacity: 0.8 }).addTo(map);
      } else {
        deviceMarkers[deviceId].setLatLng(latLng);
        deviceMarkers[deviceId].setIcon(customIcon);
        deviceTrails[deviceId].addLatLng(latLng);
      }

      deviceMarkers[deviceId].bindPopup(`
        <div class="font-sans text-xs space-y-1">
          <strong class="text-sm font-bold">${callsign}</strong><br/>
          <span>Speed: ${(currentLocation.speed * 3.6).toFixed(1)} km/h</span><br/>
          <span>Acc: ±${Math.round(currentLocation.accuracy)}m | Alt: ${Math.round(currentLocation.altitude)}m</span><br/>
          <span>Battery: ${status.batteryLevelPercent}% (${status.networkType})</span>
        </div>
      `);
    }

    function refreshFleetView() {
      const allMarkers = Object.values(deviceMarkers);
      if (allMarkers.length > 0) {
        const group = new L.featureGroup(allMarkers);
        map.fitBounds(group.getBounds().pad(0.15));
      }
    }
  </script>
</body>
</html>
```

---

## 7. Implementation Status & Roadmap

1. **Phase 1 (Completed):** Local single-device GNSS/Fused/Cellular recording, `TelephonyManager` + OpenCellID resolution, GPX/KML/CSV export via `FileProvider`, `WakeLock` protection, and deadband/EMA speed smoothing.
2. **Phase 2 (Completed):** Named Cloud Firestore sync (`/users/{uid}/devices/{deviceId}` & `/groups/{groupId}/live_locations/{uid}_{deviceId}`), Google Sign-In via Credential Manager, Remote Ring alarm, invite-code groups, Ghost Mode, and off-grid Nearby Connections `P2P_CLUSTER` mesh.
3. **Phase 3 (Planned / Optional):** Deploy the standalone Leaflet Web Dispatcher (Section 6) if a browser fleet dashboard is required alongside the Android app.
4. **Phase 4 (Planned / Optional):** Shared cloud geofence synchronization across group members.
