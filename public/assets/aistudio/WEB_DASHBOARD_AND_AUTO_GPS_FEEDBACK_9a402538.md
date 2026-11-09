Done. Created /home/myvps/Programming/GIS/WEB_DASHBOARD_AND_AUTO_GPS_FEEDBACK.md (6.4KB).



Feedback Summary

1. Web Dashboard for Track Viewing

Verdict: Good bonus feature — only if core 100% done (extra credit)

| Approach                      | Effort    | Recommendation                                          |
|-------------------------------|-----------|---------------------------------------------------------|
| Firebase + Leaflet (Option B) | 3-5 days  | Recommended — real-time, free Spark tier, auth built-in |
| Static hosting (Option A)     | 1 day     | Manual, not real-time                                   |
| Supabase (Option D)           | 1-2 weeks | Overkill                                                |
| Custom backend (Option C)     | 2-3 weeks | Not worth it                                            |

Minimal viable: Firebase Firestore sync on stopTracking() + Leaflet web page on Firebase Hosting. ~$0 on Spark plan.

Not in core requirements — scope creep risk. Only if core features 100% done.



2. Auto-Enable Balanced GPS for Testing

Verdict: Do now — 30 min fix, high impact

Problem: Lazy testers forget to switch from HIGH_ACCURACY (1s) → BALANCED (5s) → battery drain, inconsistent tests.

Recommended Fix (Option A + C): 30 min
kotlin
// In SettingsScreen.kt
val defaultIntervalMs = if (BuildConfig.DEBUG) 5000L else 1000L

// In TrackerViewModel.kt
val defaultPriority = if (BuildConfig.DEBUG)
    LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
else LocationRequest.PRIORITY_HIGH_ACCURACY


Changes:
- Default interval: 1s → 5s (debug builds)
- Default priority: HIGH_ACCURACY → BALANCED_POWER_ACCURACY (debug builds)
- User can still override via Settings picker

Why not full adaptive (ActivityRecognition)? Over-engineering, needs extra permission, ~50KB, failure modes. Manual picker already gives control.



Files to Update
| File                      | Change                               |
|---------------------------|--------------------------------------|
| SettingsScreen.kt         | Default interval 5000ms              |
| TrackerViewModel.kt       | Debug default priority BALANCED      |
| GEOTRACE_APP_TEST_PLAN.md | Add "Default test mode: 5s BALANCED" |



Summary
| Idea                  | Do?                         | Effort   | Priority |
|-----------------------|-----------------------------|----------|----------|
| Web dashboard         | If core done (extra credit) | 3-5 days | Low      |
| Auto BALANCED default | Do now                      | 30 min   | High     |



File created: /home/myvps/Programming/GIS/WEB_DASHBOARD_AND_AUTO_GPS_FEEDBACK.md
