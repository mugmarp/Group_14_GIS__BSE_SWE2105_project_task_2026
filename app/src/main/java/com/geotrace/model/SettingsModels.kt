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

enum class MapTileType(val label: String, val description: String, val supportsOfflineBulkDownload: Boolean = true) {
    STANDARD("OSM Streets", "OpenStreetMap roads & places (auto-cached)", supportsOfflineBulkDownload = false),
    SATELLITE("Satellite Imagery", "Physical aerial imagery (Esri World Imagery)", supportsOfflineBulkDownload = true),
    HYBRID("Satellite + Labels", "Aerial imagery with street & place labels", supportsOfflineBulkDownload = true),
    TOPO("Topographic Relief", "Contour terrain and topological features", supportsOfflineBulkDownload = true),
    HUMANITARIAN("Humanitarian (HOT)", "High-contrast roads, buildings & campus paths", supportsOfflineBulkDownload = true)
}

data class OfflineMapRegion(
    val id: String,
    val name: String,
    val northLat: Double,
    val southLat: Double,
    val eastLng: Double,
    val westLng: Double,
    val tileType: MapTileType,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val estimatedSizeMb: Float,
    val downloadedAtMs: Long = System.currentTimeMillis()
) {
    val centerLat: Double get() = (northLat + southLat) / 2.0
    val centerLng: Double get() = (eastLng + westLng) / 2.0
}

data class OfflineDownloadProgress(
    val isDownloading: Boolean = false,
    val regionName: String = "",
    val completedTiles: Int = 0,
    val totalTiles: Int = 0,
    val currentZoom: Int = 0,
    val errorMessage: String? = null
)

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
    val autoBalancedTestMode: Boolean = com.geotrace.BuildConfig.DEBUG,
    val geofenceNotificationEnabled: Boolean = false
)
