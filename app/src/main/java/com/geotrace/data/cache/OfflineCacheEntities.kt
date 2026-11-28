package com.geotrace.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_cell_towers",
    primaryKeys = ["mcc", "mnc", "lac", "cid"]
)
data class CachedCellTowerEntity(
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cid: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "api_range_meters") val apiRangeMeters: Double,
    @ColumnInfo(name = "operator_name") val operatorName: String,
    @ColumnInfo(name = "radio_type") val radioType: String,
    @ColumnInfo(name = "address_estimate") val addressEstimate: String?,
    @ColumnInfo(name = "resolved_at_ms") val resolvedAtMs: Long,
    @ColumnInfo(name = "last_seen_at_ms") val lastSeenAtMs: Long
)

@Entity(
    tableName = "cached_observed_cells",
    indices = [Index(value = ["observed_at_ms"])]
)
data class CachedObservedCellEntity(
    @PrimaryKey @ColumnInfo(name = "cell_key") val cellKey: String,
    @ColumnInfo(name = "radio_type") val radioType: String,
    val mcc: Int?,
    val mnc: Int?,
    val cid: Long?,
    val lac: Int?,
    @ColumnInfo(name = "signal_dbm") val signalDbm: Int?,
    @ColumnInfo(name = "signal_level") val signalLevel: Int,
    @ColumnInfo(name = "is_registered") val isRegistered: Boolean,
    @ColumnInfo(name = "operator_name") val operatorName: String?,
    @ColumnInfo(name = "timing_advance") val timingAdvance: Int?,
    @ColumnInfo(name = "estimated_distance_km") val estimatedDistanceKm: Double?,
    val pci: Int?,
    val earfcn: Int?,
    @ColumnInfo(name = "observed_at_ms") val observedAtMs: Long
)

@Entity(
    tableName = "cached_location_fixes",
    indices = [Index(value = ["timestamp"])]
)
data class CachedLocationFixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val altitude: Double,
    val bearing: Float,
    val provider: String,
    val timestamp: Long,
    @ColumnInfo(name = "session_id") val sessionId: String
)

@Entity(
    tableName = "cached_nav_routes",
    indices = [Index(value = ["updated_at_ms"])]
)
data class CachedNavRouteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "destination_name") val destinationName: String,
    @ColumnInfo(name = "destination_lat") val destinationLat: Double,
    @ColumnInfo(name = "destination_lon") val destinationLon: Double,
    val profile: String,
    val source: String,
    @ColumnInfo(name = "total_distance_meters") val totalDistanceMeters: Double,
    @ColumnInfo(name = "total_duration_seconds") val totalDurationSeconds: Double,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "route_json") val routeJson: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)

@Entity(
    tableName = "offline_routing_packs",
    indices = [Index(value = ["updated_at_ms"])]
)
data class OfflineRoutingPackEntity(
    @PrimaryKey @ColumnInfo(name = "region_id") val regionId: String,
    @ColumnInfo(name = "region_name") val regionName: String,
    @ColumnInfo(name = "north_lat") val northLat: Double,
    @ColumnInfo(name = "south_lat") val southLat: Double,
    @ColumnInfo(name = "east_lon") val eastLon: Double,
    @ColumnInfo(name = "west_lon") val westLon: Double,
    @ColumnInfo(name = "vertex_count") val vertexCount: Int,
    @ColumnInfo(name = "edge_count") val edgeCount: Int,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long
)
