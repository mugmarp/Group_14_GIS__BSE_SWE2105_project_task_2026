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
| **Unit Tests** | Haversine distance, deadband filter, formatting | `gradle testDebugUnitTest` | 100% pass, 0 regressions |
| **Debug Compilation** | App compilation, resource linking, manifest | `gradle assembleDebug` | Produces `app-debug.apk` |
| **Release Compilation** | ProGuard optimization, resource shrinking | `gradle assembleRelease` | Produces signed `app-release.apk` |
| **OpenCellID Resolution** | API query & offline baseline fallback | `OpenCellIdClientTest` | Correct coordinates for `641-10` & `641-22` |
| **In-App Update Engine** | GitHub Releases API parsing & install intent | `AppUpdateManagerTest` | Returns valid APK URI with `FileProvider` |

---

## 3. Automated Test Command Reference

Run all automated unit tests directly from the workspace root:
```bash
gradle testDebugUnitTest
```

Compile both release and debug installable packages:
```bash
gradle assembleDebug assembleRelease
```
