# GIS Group 14 — SWE2105 Handover for Google AI Studio

**Course:** BSE SWE2105 (Software Engineering)  
**Institution:** MUST (Mbarara University of Science and Technology)  
**Academic Year:** 2026  
**Implementation:** Option 2 — Mobile Phone Tracking via GPS & Cellular Information

---

## 📍 Repository References

### Primary Repo (Documentation & Decisions)
```
https://github.com/mugmarp/GIS_Group_14_BSE_SWE2105_project_task_2026
```
**Commit:** `9c6d492` — Contains all refocused documentation, decisions, and verified Uganda legal constraints

### Implementation Repo (Android App)
```
https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026
```
**Commit:** `69b0721` — Working Android app (osmdroid + Compose + Foreground Service + SQLite + TrackExporter)

---

## 🎯 Core Requirements (What to Implement)

> **Task:** *"Design an Android app that tracks a person's real-time movement using GPS and cell-tower information, displays the path on a real map, and allows comparison of GPS vs cellular positioning (accuracy, update rate, indoor/outdoor behaviour)."*

### 4 Core Features Only
1. **Real-Time Map & GPS Trail** — Live OSM map (osmdroid) with GPS trail polyline, current position marker, telemetry HUD
2. **Live Cellular Diagnostics** — `TelephonyManager.getAllCellInfo()` → MCC, MNC, CID, TAC, RSSI, TA
3. **GPS vs Cellular Comparator** — Side-by-side live comparison: coordinates, accuracy, update rate, indoor/outdoor behaviour
4. **Session History & Export** — GPX 1.1, KML 2.2, CSV export for evidence/analysis

---

## 🛠️ Tech Stack (Decided — All Accepted)

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Language** | Kotlin | Modern, type-safe, Compose-native |
| **UI** | Jetpack Compose + Material3 | Declarative, modern, dark/light theme |
| **Map** | osmdroid (TileSourceFactory.MAPNIK) | Free, no API key, raster tiles, Compose-compatible |
| **Location** | `FusedLocationProviderClient` (PRIORITY_HIGH_ACCURACY) | GPS + WiFi + Cell fusion by Google Play Services |
| **Cellular** | `TelephonyManager.getAllCellInfo()` | Raw MCC/MNC/CID/TAC/RSSI/TA for comparison screen |
| **Database** | Plain SQLite (`SQLiteOpenHelper`) | Simple schema, no annotation processor, direct control |
| **Background** | `LocationForegroundService` + `FOREGROUND_SERVICE_LOCATION` | Real-time GPS, user-configurable interval (1s–30s) |
| **Export** | `TrackExporter` — GPX 1.1, KML 2.2, CSV | Industry-standard formats for evidence |
| **Architecture** | MVVM (`TrackerViewModel`, `LocationRepository`) | Clean separation, StateFlow UI state |

---

## 📋 All Decisions (7 — ALL ACCEPTED)

| # | Decision | Choice | Status |
|---|----------|--------|--------|
| 1 | Project Option | Option 2: Mobile (GPS/Cell) | ✅ Accepted |
| 2 | Platform | Android / Kotlin / Jetpack Compose | ✅ Accepted |
| 3 | Map Library | osmdroid | ✅ Accepted |
| 4 | Local Database | Plain SQLite (`SQLiteOpenHelper`) | ✅ Accepted |
| 5 | Backend / Sync | **None** — Local-only | ✅ Accepted |
| 6 | Background Strategy | Foreground Service + HIGH_ACCURACY | ✅ Accepted |
| 7 | GPS vs Cell Comparison | Live side-by-side screen | ✅ Accepted |

**No pending decisions.** All technical choices finalized.

---

## 🏗️ Architecture (Core Only)

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE APP (Android)                 │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose + Material3)                 │
│  ├── MapScreen — Live OSM map + GPS trail              │
│  ├── CellularDiagnosticScreen — GPS vs Cell comparison │
│  ├── HistoryScreen — Session list + GPX/KML/CSV export │
│  └── SettingsScreen — Update interval, theme           │
├─────────────────────────────────────────────────────────┤
│  Domain Layer (MVVM)                                    │
│  ├── TrackerViewModel — State management               │
│  ├── LocationRepository — Data abstraction             │
│  ├── CellularDiagnosticManager — Cell tower extraction │
│  └── TrackExporter — GPX/KML/CSV export                │
├─────────────────────────────────────────────────────────┤
│  Service Layer                                          │
│  ├── LocationForegroundService — Background GPS        │
│  └── CellularDiagnosticManager — Cell tower data       │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                             │
│  ├── LocationDbHelper — SQLite (sessions + points)     │
│  └── TrackExporter — GPX/KML/CSV serialization         │
└─────────────────────────────────────────────────────────┘
```

### Core Data Models

```kotlin
data class LocationPoint(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,        // GPS accuracy radius (meters)
    val speed: Float,           // m/s
    val bearing: Float,         // degrees
    val provider: String,       // "gps" | "fused" | "network"
    val altitude: Double? = null
)

data class TrackSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val pointCount: Int = 0,
    val totalDistance: Double = 0.0,  // meters
    val name: String? = null
)
```

---

## 🔬 Location Provider Strategy

| Layer | Implementation | Purpose |
|-------|----------------|---------|
| **Primary (Production)** | `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` | Best available location fused from GPS + WiFi + Cell |
| **Cellular Diagnostics** | `TelephonyManager.getAllCellInfo()` → MCC/MNC/CID/TAC/RSSI/TA | Live base station data for GPS vs Cell comparison |
| **Comparison Logic** | Side-by-side: GPS coordinates vs Cell ID lookup (OpenCellID API) | Satisfies course requirement: "compare GPS vs cellular positioning" |

**Why:** FusedLocationProvider = production-grade, handles fusion internally, battery-optimized. Raw cell data = directly satisfies "cell tower triangulation" discussion requirement. OpenCellID API = demonstrates real-world cell positioning accuracy.

---

## 📦 Export Formats (Implemented)

| Format | Specification | Use Case |
|--------|---------------|----------|
| **GPX 1.1** | `<trk><trkseg><trkpt lat="..." lon="..."><ele>...</ele><time>...</time><speed>...</speed></trkpt></trkseg></trk>` | QGIS, Strava, Garmin, GPS devices |
| **KML 2.2** | `<Placemark><LineString><coordinates>lon,lat,alt ...</coordinates></LineString></Placemark>` | Google Earth 3D visualization |
| **CSV** | `timestamp,latitude,longitude,accuracy,speed,bearing,provider,altitude` | Excel, Python, R, statistical analysis |

---

## ⚖️ Verified Uganda Technical & Legal Constraints

### Legal Framework (Primary Sources)
| Act | Chapter | Key Provisions |
|-----|---------|----------------|
| **Uganda Communications Act 2013** | Chapter 103 | General telecom regulation; UCC licensing |
| **Regulation of Interception of Communications Act 2010** | Chapter 101 | Lawful interception — only authorized officers with warrant |
| **Data Protection and Privacy Act 2019** | Chapter 97 | Location data = personal data (Sec 2); consent required (Sec 7); unlawful disclosure offence (Sec 35, up to 10 years) |

### Cell Tower Triangulation Reality
| Approach | Feasibility | Accuracy | Legal |
|----------|-------------|----------|-------|
| **True triangulation (TDOA/AoA)** | ❌ Impossible — no telecom API, requires warrant | N/A | Illegal without warrant |
| **Cell ID Lookup (CID + OpenCellID)** | ✅ `TelephonyManager.getAllCellInfo()` + OpenCellID API | Kampala 200m–1km; Rural 5–20km | ✅ Legal — public DB + on-device CID |
| **Google FusedLocationProvider (Cell fallback)** | ✅ Built into Play Services | Similar (Google's proprietary cell DB) | ✅ Legal — user consents |

**Key Finding:** MTN/Airtel Uganda public APIs are **payments-only** (Mobile Money / Airtel Money). No LBS/triangulation APIs exist for developers.

### OpenCellID Uganda Coverage
- **MCC:** 641 | **MTN MNC:** 10 (PLMN 64110) | **Airtel MNC:** 22 (PLMN 64122)
- **Accuracy:** Kampala ~200m–1km | Regional ~1–5km | Rural ~5–20km+

---

## 📦 CI/CD Pipeline (For Reference)

### `.github/workflows/android-ci.yml`

```yaml
name: Android CI/CD Pipeline

on:
  push:
    branches: [ "main", "master" ]
    tags:
      - 'v*'
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Build & Test GIS Tracker APK
    runs-on: ubuntu-24.04
    permissions:
      contents: read

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v5
        with:
          fetch-depth: 0

      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Make Gradle Wrapper Executable
        run: chmod +x ./gradlew

      - name: Generate Missing Debug Keystore
        if: always() && github.event_name != 'pull_request'
        run: |
          if [ ! -f debug.keystore ]; then
            keytool -genkey -v \
              -keystore debug.keystore \
              -storepass android \
              -alias androiddebugkey \
              -keypass android \
              -keyalg RSA \
              -keysize 2048 \
              -validity 10000 \
              -dname "CN=Android Debug,O=Android,C=US"
          fi

      - name: Extract Version from Tag
        if: startsWith(github.ref, 'refs/tags/v')
        id: version
        run: |
          TAG=${GITHUB_REF_NAME}
          VERSION=${TAG#v}
          echo "VERSION_NAME=$VERSION" >> $GITHUB_OUTPUT
          IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
          VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
          echo "VERSION_CODE=$VERSION_CODE" >> $GITHUB_OUTPUT

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest --no-daemon --stacktrace

      - name: Assemble Debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace

      - name: Upload Debug APK Artifact
        uses: actions/upload-artifact@v5
        if: success()
        with:
          name: gistracker-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
          retention-days: 14

      - name: Assemble Release APK
        if: github.event_name != 'pull_request'
        env:
          ORG_GRADLE_PROJECT_versionName: ${{ steps.version.outputs.VERSION_NAME || github.run_number }}
          ORG_GRADLE_PROJECT_versionCode: ${{ steps.version.outputs.VERSION_CODE || github.run_number }}
          ORG_GRADLE_PROJECT_signingKeyAlias: ${{ secrets.SIGNING_KEY_ALIAS }}
          ORG_GRADLE_PROJECT_signingKeyPassword: ${{ secrets.SIGNING_KEY_PASSWORD }}
          ORG_GRADLE_PROJECT_signingStorePassword: ${{ secrets.SIGNING_STORE_PASSWORD }}
        run: |
          if [ -n "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" ]; then
            echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > release.keystore
            echo "ORG_GRADLE_PROJECT_signingStoreFile=release.keystore" >> $GITHUB_ENV
          fi
          ./gradlew assembleRelease --no-daemon --stacktrace

      - name: Upload Release APK Artifact
        if: github.event_name != 'pull_request' && success()
        uses: actions/upload-artifact@v5
        with:
          name: gistracker-release-apk
          path: app/build/outputs/apk/release/app-release.apk
          if-no-files-found: error
          retention-days: 30

  release:
    name: Create GitHub Release
    needs: build
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-24.04
    permissions:
      contents: write
    steps:
      - name: Download Release APK
        uses: actions/download-artifact@v5
        with:
          name: gistracker-release-apk
          path: ./release-apk

      - name: Verify APK Exists
        run: ls -la ./release-apk/app-release.apk

      - name: Generate Release Notes
        id: release_notes
        run: |
          TAG=${GITHUB_REF_NAME}
          VERSION=${TAG#v}
          cat <<EOF > release_notes.md
          ## GIS Tracker ${VERSION}
          
          ### Changes
          - See commit history for details
          
          ### Download
          - **Release APK**: \`app-release.apk\` (signed, minify enabled)
          - **Debug APK**: Available from workflow artifacts
          
          ### Installation
          1. Download \`app-release.apk\`
          2. Enable "Install from unknown sources" in Android settings
          3. Install and grant location permissions
          
          ### Built
          - Commit: \`${{ github.sha }}\`
          - Workflow: \`${{ github.run_id }}\`
          - Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
          EOF
          cat release_notes.md

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: GIS Tracker ${{ github.ref_name }}
          body_path: release_notes.md
          files: ./release-apk/app-release.apk
          draft: false
          prerelease: false
          generate_release_notes: false

  notify:
    name: Notify on New Release
    needs: release
    if: needs.release.result == 'success'
    runs-on: ubuntu-24.04
    steps:
      - name: Post Release Announcement to GitHub Discussion
        uses: peter-evans/create-or-update-comment@v4
        with:
          issue-number: 1
          body: |
            🚀 **New Release: ${{ github.ref_name }}**
            
            A new version of GIS Tracker is available!
            
            **Download:** [app-release.apk](${{ github.server_url }}/${{ github.repository }}/releases/tag/${{ github.ref_name }})
            
            **Built from:** ${{ github.sha }}
            **Date:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")

      - name: Trigger External Webhook (if configured)
        if: secrets.NOTIFY_WEBHOOK_URL != ''
        run: |
          curl -X POST "${{ secrets.NOTIFY_WEBHOOK_URL }}" \
            -H "Content-Type: application/json" \
            -d "{
              \"text\": \"🚀 New GIS Tracker Release: ${{ github.ref_name }}\",
              \"url\": \"${{ github.server_url }}/${{ github.repository }}/releases/tag/${{ github.ref_name }}\",
              \"version\": \"${{ github.ref_name }}\",
              \"commit\": \"${{ github.sha }}\"
            }"
```

### Required GitHub Secrets
| Secret | Purpose |
|--------|---------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `SIGNING_KEY_ALIAS` | Keystore key alias |
| `SIGNING_KEY_PASSWORD` | Key password |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `NOTIFY_WEBHOOK_URL` | Optional: Slack/Discord/Teams webhook |

---

## 📱 `app/build.gradle.kts` (Key Additions)

```kotlin
android {
    // Version override from CI environment
    val versionCodeOverride = System.getenv("ORG_GRADLE_PROJECT_versionCode")?.toIntOrNull()
    val versionNameOverride = System.getenv("ORG_GRADLE_PROJECT_versionName")

    defaultConfig {
        versionCode = versionCodeOverride ?: 1
        versionName = versionNameOverride ?: "1.0"
    }

    signingConfigs {
        create("releaseConfig") {
            val storeFile = System.getenv("ORG_GRADLE_PROJECT_signingStoreFile") ?: ""
            val storePassword = System.getenv("ORG_GRADLE_PROJECT_signingStorePassword") ?: ""
            val keyAlias = System.getenv("ORG_GRADLE_PROJECT_signingKeyAlias") ?: ""
            val keyPassword = System.getenv("ORG_GRADLE_PROJECT_signingKeyPassword") ?: ""

            if (storeFile.isNotBlank() && storePassword.isNotBlank() && keyAlias.isNotBlank() && keyPassword.isNotBlank()) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Fallback to debug config for local builds
                storeFile = file("${rootDir}/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("releaseConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 📋 6-Week Implementation Plan

| Week | Focus | Deliverable |
|------|-------|-------------|
| 1 | Project setup: Android (Kotlin/Compose); permissions; basic map (osmdroid) | Running app showing current location on map |
| 2 | Foreground location service; `FusedLocationProviderClient`; store points locally (SQLite) | Continuous trail saved to SQLite |
| 3 | Map visualization: trail polyline, current marker, zoom-to-fit; cellular diagnostic screen | Visual trail with current position + cell diagnostics |
| 4 | Battery optimization: configurable update interval; GPS vs Cell comparison screen | Live GPS vs Cell comparison |
| 5 | Export: GPX/KML/CSV; polish settings; demo video | Complete app with export |
| 6 | Testing on physical devices (indoor/outdoor GPS vs Cell comparison); final report | Verified accuracy comparison |

---

## 🔑 Required Permissions

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

---

## 📚 Key Libraries

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Standard Android SDK: SQLite, TelephonyManager, Foreground Service
}
```

---

## ✅ Handover Checklist

- [x] All 7 decisions **Accepted** (no pending)
- [x] Core requirements defined (4 features only)
- [x] Architecture documented (MVVM, osmdroid, SQLite, Local-only)
- [x] Verified Uganda legal constraints documented
- [x] CI/CD pipeline YAML provided (for reference)
- [x] `build.gradle.kts` additions documented
- [x] 6-week plan with milestones
- [x] Primary repo updated: `https://github.com/mugmarp/GIS_Group_14_BSE_SWE2105_project_task_2026` (commit `9c6d492`)
- [x] Implementation repo ready: `https://github.com/mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026` (commit `69b0721`)

---

**Ready for Google AI Studio implementation.** All decisions finalized, scope locked to core requirements only.
