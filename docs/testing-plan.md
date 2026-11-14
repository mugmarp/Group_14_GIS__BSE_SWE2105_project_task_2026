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
| **24/7 Protection & Map Pick QA** | Sign in, enable 24/7 Background Protection, swipe app UI away, trigger Remote Ring from 2nd phone; test `Pick on Map` on Satellite view | Two physical phones + `GroupScreen` / `GeofenceScreen` | Background sync & Remote Ring fire with UI closed; tapped rooftop coordinate populates Zone Editor |

---

## 3. Automated Test Command Reference

Verify Firestore security rules against the local emulator:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

Run unit tests and compile both installable packages (decode `debug.keystore.base64` first if running locally outside CI):
```bash
base64 -d debug.keystore.base64 > debug.keystore
gradle testDebugUnitTest assembleDebug assembleRelease
```

---

## 4. Background Protection, Settings Persistence & Geofence QA Notes

*Ref: `docs/issues-and-resolutions.md` (`GT-ISSUE-04`, `GT-ISSUE-17`, `GT-ISSUE-18`)*

- **Default Profile & Full Settings Persistence:** GeoTrace starts in `TrackingMode.HIGH_ACCURACY` by default with zero hidden debug overrides; whichever tracking tier, map layer, speed unit, coordinate format, accuracy ring, or jitter threshold the user selects is persisted in `geotrace_prefs`.
- **24/7 Background Protection Mode:** When signed into Google and enabled in **Devices → This Device & Privacy**, `LocationForegroundService` stays active in a low-drain balanced heartbeat (`60s / 25m`, no WakeLock) even after `REC` stops or the app window is swiped away, keeping `My Other Devices`, active group telemetry, and Remote Ring listening alive (and auto-restarting on reboot when background location permission is granted).
- **Geofence Coordinate Verification:** Use `"Pick on Map"` inside the Zone Editor while viewing **Satellite Imagery** / **Satellite + Labels** to verify or refine Kihumuro (`-0.5982, 30.6178`), Main Town Campus (`-0.6163, 30.6568`), and Kakoba Hostels (`-0.6075, 30.6727`) centers.

For full audit details on resolved issues and architecture decisions, see **[`docs/issues-and-resolutions.md`](issues-and-resolutions.md)** and **[`docs/team-guide.md`](team-guide.md)**.

