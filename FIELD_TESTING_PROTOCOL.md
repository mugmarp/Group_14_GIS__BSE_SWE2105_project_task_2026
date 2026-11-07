# SWE2105 GIS & TELECOMMUNICATIONS FIELD TESTING PROTOCOL
**Course Unit:** SWE2105 Geographic Information Systems (GIS)  
**Project Option:** Option 2 – Mobile Phone Tracking via GPS & Cellular Information  
**Institution:** Faculty of Applied Sciences and Technology (FAST), Mbarara University of Science & Technology (MUST)  
**Target Application:** GeoTrace (Package: `com.geotrace`, Release: `v1.0.0`)  
**Target Repository:** `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026`  

---

## 1. Executive Summary & Objective

This protocol outlines the scientific procedure for evaluating **GeoTrace**, an offline-first, battery-optimized Android GIS application designed for continuous spatial telemetry, deadband jitter filtration, geofencing enforcement, and dual-source position verification:
1. **Multi-Constellation GNSS (Global Navigation Satellite System)**: Android `FusedLocationProviderClient` measuring high-precision coordinates, altitude, bearing, speed, and 68% confidence error radius.
2. **Cellular Radio Base Station Telemetry**: Android `TelephonyManager` extracting serving and neighboring cell tower parameters (MCC, MNC, LAC/TAC, Cell-ID, CID, PSC/PCI, and RSSI/RSRP/RSRQ/CQI/TA) correlated with OpenCellID lookup for Ugandan PLMNs (MTN `641-10` and Airtel `641-22`).

---

## 2. Experimental Test Sites & Environmental Profiles

Field trials are partitioned across three distinct physical environments within Mbarara District to quantify multipath reflection, signal attenuation, and cellular handover behavior:

| Site Identifier | Location / Environment | Physical Characteristics | Expected Multipath / Obstruction | Target PLMNs |
| :--- | :--- | :--- | :--- | :--- |
| **Site A: Open Sky** | MUST Kihumuro Campus Sports Grounds (`0.6171°S, 30.6548°E`) | Unobstructed 360° celestial horizon, zero canopy | Baseline: Multipath < 2.5m, DOP < 1.4, RSSI > -65 dBm | MTN 4G / Airtel 4G |
| **Site B: Urban Canyon** | FAST Engineering Building & Kihumuro Lecture Halls | Multi-story reinforced concrete, rebar shielding, stairwells | Moderate: Multipath reflections, GNSS accuracy degradation (8–25m) | MTN LTE / Airtel UMTS |
| **Site C: Deep Indoor** | Kihumuro Hostel Basements / Indoor Corridors | Complete celestial obscuration, below-grade structures | Severe: Complete GNSS denial, cellular fallback to LAC/CID triangulation | MTN 3G/2G, Airtel 3G |

---

## 3. Quantitative Test Metrics & Log Sheet Structure

During each test run, the evaluators record telemetry at 1-second intervals via GeoTrace’s background foreground service (`LocationForegroundService`). Data is exported into CSV format for statistical analysis in Python/QGIS.

### Metric Definitions
- **$E_{GPS}$ (GNSS Accuracy):** Estimated horizontal radial error (meters) reported by the GNSS provider at 68% confidence.
- **$D_{error}$ (Euclidean Drift):** Radial difference between reported coordinate and surveyor-grade benchmark coordinates established at MUST Kihumuro geodetic marks:
  $$D_{error} = 2R \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta \phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta \lambda}{2}\right)}\right)$$
- **Stationary Deadband Filter Efficiency ($J_{rejected}$):**
  $$J_{rejected} = \sum_{k=1}^{N} d(P_k, P_{k-1}) \quad \forall d(P_k, P_{k-1}) < T_{threshold}$$
  where $T_{threshold} = 1.5\text{ m}$ (standard deadband).
- **Cellular Serving Range ($R_{cell}$):** Distance vector from GNSS fix $(Lat_{gps}, Lon_{gps})$ to OpenCellID base station center $(Lat_{bts}, Lon_{bts})$.

### Field Log Sheet Template

```
+----------------------------------------------------------------------------------------------------+
| SWE2105 FIELD RUN LOG SHEET                                                                        |
| Date: _______________   Time: __________   Evaluator: ____________________________________________ |
| Site: [ ] A (Kihumuro Grounds)  [ ] B (FAST Block)  [ ] C (Hostel Indoor)                          |
| Tracking Mode: [ ] High Accuracy (1s)  [ ] Balanced (5s)  [ ] Power Saver (15s)                    |
| Network Operator: [ ] MTN Uganda (641-10)  [ ] Airtel Uganda (641-22)                              |
+-------+--------------+---------------+----------+--------+---------+-----------+---------+---------+
| Sec   | GNSS Lat/Lon | Accuracy (m)  | Speed    | MCC/MNC| LAC/TAC | Cell ID   | RSSI    | Tower   |
|       |              |               | (m/s)    |        |         |           | (dBm)   | Dist(m) |
+-------+--------------+---------------+----------+--------+---------+-----------+---------+---------+
| 00:00 | -0.617124... | 3.2 m         | 0.00     | 641-10 | 1205    | 40291     | -68 dBm | 385 m   |
| 00:01 | -0.617124... | 3.1 m         | 0.00     | 641-10 | 1205    | 40291     | -68 dBm | 385 m   |
| 00:02 | [Jitter Cut] | 3.3 m         | 0.00     | 641-10 | 1205    | 40291     | -69 dBm | 385 m   |
| ...   |              |               |          |        |         |           |         |         |
+-------+--------------+---------------+----------+--------+---------+-----------+---------+---------+
```

---

## 4. Phase-by-Phase Testing Procedures

### Phase 1: Pre-Deployment & Self-Test Verification
1. Power on device running GeoTrace `v1.0.0`.
2. Navigate to **Settings** (`SettingsScreen.kt`) and confirm:
   - **Velocity Scalar Unit:** Set to `km/h` or `m/s`.
   - **Stationary Deadband Filter:** Enabled at `1.5m (Std)`.
   - **Accuracy Confidence Ring:** Toggled **ON**.
   - **Live Uganda Cell Tower Pinning:** Toggled **ON**.
3. Verify that the **OpenCellID Engine** status shows bundled Uganda cache active for `641-10` (MTN) and `641-22` (Airtel).

### Phase 2: Open Sky Static Baseline Trial (Site A)
1. Mount the device on a survey tripod at benchmark station `MUST-KH-01` (`-0.617120, 30.654810`).
2. Press **Start Recording** with session name `"MUST_Kihumuro_OpenSky_Baseline"`.
3. Keep device stationary for 10 minutes (600 epochs).
4. **Validation Criteria:**
   - Raw GNSS jitter must be suppressed by the deadband filter ($J_{rejected} > 0\text{ m}$ displayed on Settings counter).
   - Horizontal accuracy $E_{GPS} \le 4.5\text{ m}$.
   - Serving cell tower pin rendered on map with distance ray connecting phone pin to MTN/Airtel tower.

### Phase 3: Dynamic Walking Perimeter & Geofence Boundary Test (Site B)
1. Define a Geofence polygon around the FAST Engineering complex with radius $R = 40\text{ m}$.
2. Select **Walk Perimeter Tracking Mode** (3s sampling interval).
3. Walk along the perimeter, intentionally traversing the boundary twice.
4. **Validation Criteria:**
   - Visual and haptic alert triggered upon boundary crossing within $\le 1.8\text{ seconds}$ of crossing the $R$ threshold.
   - Polyline trail rendered with gradient velocity coding without discontinuous spikes.

### Phase 4: GNSS-Denied Transition & Cellular Fallback Trial (Site C)
1. Start outdoors in Site A, walk directly into the lowest floor of the FAST building.
2. Observe telemetry handoff as satellite SNR drops below tracking threshold.
3. **Validation Criteria:**
   - App retains continuous execution without crashing via `LocationForegroundService`.
   - Live badge transitions from GNSS Fix to `Cellular Serving Pin (±Range)`.
   - SQLite database records timestamped cellular radio snapshot (CID, LAC, Signal Strength) even when satellite coordinates are null or frozen.

---

## 5. Data Export & Verification Instructions

1. Upon stopping tracking session, tap **History** tab (`HistoryScreen.kt`).
2. Tap **Export GPX** to generate schema-compliant XML containing `<trk>` and `<trkpt>` nodes with `<ele>`, `<time>`, and `<hdop>`.
3. Tap **Export KML** to visualize 3D path extrusion in Google Earth Pro.
4. Tap **Export CSV** for raw statistical compilation in R / Python Pandas.
5. Ingest exported CSV into QGIS to verify spatial alignment against official Uganda National Forestry Authority (NFA) / Ministry of Lands basemaps.
