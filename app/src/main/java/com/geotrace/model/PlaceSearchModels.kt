package com.geotrace.model

enum class SearchResultSource(val badge: String) {
    COORDINATE("COORDS"),
    SAFE_ZONE("SAFE ZONE"),
    WAYPOINT("WAYPOINT"),
    OFFLINE_REGION("OFFLINE AREA"),
    OFFLINE_POI("OFFLINE POI"),
    DEVICE_OR_PEER("DEVICE"),
    RECENT("RECENT"),
    ONLINE_OSM("OSM")
}

data class SearchPlaceResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val source: SearchResultSource,
    val bboxNorth: Double? = null,
    val bboxSouth: Double? = null,
    val bboxEast: Double? = null,
    val bboxWest: Double? = null,
    val distanceMeters: Double? = null,
    val requestedAtMs: Long = 0L
)

data class OfflinePlacePoi(
    val id: String,
    val regionId: String,
    val name: String,
    val category: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)
