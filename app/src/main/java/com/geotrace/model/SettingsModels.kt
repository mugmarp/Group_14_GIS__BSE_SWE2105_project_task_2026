package com.geotrace.model

enum class SpeedUnit(val label: String, val multiplier: Float) {
    KMH("km/h", 3.6f),
    MPH("mph", 2.23694f),
    MPS("m/s", 1.0f)
}

enum class CoordinateFormat(val label: String) {
    DECIMAL("Decimal Degrees (DD)"),
    DMS("Degrees Minutes Seconds (DMS)")
}

enum class MapTileType(val label: String, val description: String) {
    STANDARD("OpenStreetMap Standard", "High-clarity streets & place names (MAPNIK)"),
    TOPO("Topographic Relief", "Contour terrain and topological features"),
    HUMANITARIAN("Humanitarian (HOT)", "High-contrast road & infrastructure markers")
}

data class TrackerSettings(
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val mapTileType: MapTileType = MapTileType.STANDARD,
    val showAccuracyRing: Boolean = true,
    val followUserLocation: Boolean = true,
    val stationaryJitterThresholdMeters: Float = 1.5f,
    val keepScreenOn: Boolean = true,
    val showCellTowerPin: Boolean = true,
    val openCellIdApiKey: String = "",
    val autoBalancedTestMode: Boolean = com.geotrace.BuildConfig.DEBUG
)
