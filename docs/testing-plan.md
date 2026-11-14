# Testing & Quality Assurance Plan — GeoTrace SWE2105

**Application:** GeoTrace (`com.geotrace`)  
**Course:** BSE SWE2105 Geographic Information Systems  
**Institution:** MUST (Mbarara University of Science and Technology)  

---

## 1. Overview & Verification Strategy

The GeoTrace quality assurance architecture verifies both functional software behavior and spatial accuracy through four testing tiers:

1. **Local JVM & Unit Tests (`gradle testDebugUnitTest`)**:
   - Algorithmic validation of Haversine distance, speed unit conversion, and DMS/DD coordinate transformations.
   - Validation of the Euclidean deadband stationary filter ($T = 1.5\text{ m}$).
   - Validation of GPX, KML, and CSV spatial serializations.
2. **Build & Release Packaging Verification (`gradle assembleDebug assembleRelease`)**:
   - Clean compilation of both development debug APK and optimized ProGuard release APK.
   - ProGuard/R8 bytecode shrinkage and keep rules verification for OpenCellID JSON models.
3. **CI/CD Automation Pipeline (`.github/workflows/android-ci.yml`)**:
   - Automated testing on clean Ubuntu 24.04 runners on every commit and pull request.
   - Dynamic version injection and signed release packaging.
4. **Physical Empirical Field Testing (MUST Kihumuro)**:
   - For complete on-site instructions, test site profiles, and field log sheets, see **[`FIELD_TESTING_PROTOCOL.md`](../FIELD_TESTING_PROTOCOL.md)**.
   - For statutory legal compliance under Ugandan law (Cap 97, Cap 101, Cap 106) and mathematical analysis of GNSS vs. Cellular telemetry, see **[`ACADEMIC_EVALUATION.md`](../ACADEMIC_EVALUATION.md)**.

---

## 2. Test Execution Matrix

| Test Suite | Scope | Command / Tool | Success Criteria |
| :--- | :--- | :--- | :--- |
| **Firestore Security Rules** | Auth gating, `/users/{uid}/devices` isolation, `/groups` invite/member rules | `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js` | All 6 rule assertions pass |
| **Unit Tests** | Local JVM unit test pipeline | `./gradlew testDebugUnitTest` (`gradle testDebugUnitTest`) | Clean exit, 0 failures |
| **Debug Compilation** | App compilation, Google Services processing, resource linking | `./gradlew assembleDebug` (`gradle assembleDebug`) | Produces `app-debug.apk` |
| **Release Compilation** | Release packaging with project keystore config | `./gradlew assembleRelease` (`gradle assembleRelease`) | Produces signed `app-release.apk` |
| **Real-Device Multi-Phone QA** | Google Sign-In, My Other Devices + Ring, Shared Groups, Off-Grid P2P, OTA Updates | Physical Android devices (see `docs/team-guide.md`) | Live telemetry, Ring alarm & offline P2P verified |
| **Multi-Layer & Offline Maps QA** | Layer switches (`STANDARD`, `SATELLITE`, `HYBRID`, `TOPO` Z17 clamp, `HUMANITARIAN`), 2km/5km offline download/cancel/jump/clear | Physical device Map Settings / `OfflineMapsDialog` | Crisp aerial & label overlays, offline tiles render in Airplane Mode |
| **Mode Transition QA** | Rapid toggles between `High Accuracy`, `Balanced`, and `Power Saver (Cell Only)` during active & standby tracking | Physical device HUD Mode Pill | Zero phantom jump distance, clean GNSS ↔ `"cellular"` handoff |

---

## 3. Automated Test Command Reference

Verify Firestore security rules against the local emulator:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

Run unit tests and compile both installable packages (decode `debug.keystore.base64` first if running locally outside CI):
```bash
base64 -d debug.keystore.base64 > debug.keystore
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

---

## 4. QA Testing Power Optimization (Auto-Balanced GPS)

*Ref: `docs/issues-and-resolutions.md`*

To prevent rapid battery depletion and desk multipath jitter during prolonged QA testing:
- **Debug Builds**: Default tracking profile is automatically set to **`TrackingMode.BALANCED`** (5-second update intervals, 10-meter displacement threshold, ~8 mA power draw).
- **Settings Toggle**: Testers can switch between Auto-Balanced (5s) and High-Accuracy (1s GNSS) via the **Testing & Power Optimization** card in `SettingsScreen.kt`.
- **Full Trail Integrity**: Balanced testing continues to log complete location points to SQLite, render real-time breadcrumbs on the map, and output identical GPX/KML/CSV exports.

For full audit details on resolved issues and architecture decisions, see **[`docs/issues-and-resolutions.md`](issues-and-resolutions.md)** and **[`docs/team-guide.md`](team-guide.md)**.

