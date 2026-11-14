package com.geotrace.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.geotrace.model.GeofenceAlert
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession

class LocationDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "gis_tracker.db"
        const val DATABASE_VERSION = 3

        const val TABLE_POINTS = "location_points"
        const val TABLE_SESSIONS = "tracking_sessions"
        const val TABLE_GEOFENCES = "geofence_zones"
        const val TABLE_ALERTS = "geofence_alerts"
        const val TABLE_OFFLINE_MAPS = "offline_map_regions"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                distance REAL NOT NULL DEFAULT 0.0,
                avg_speed REAL NOT NULL DEFAULT 0.0,
                max_speed REAL NOT NULL DEFAULT 0.0,
                point_count INTEGER NOT NULL DEFAULT 0,
                tracking_mode TEXT NOT NULL,
                is_recording INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_POINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL NOT NULL,
                speed REAL NOT NULL,
                altitude REAL NOT NULL,
                bearing REAL NOT NULL,
                provider TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_session ON $TABLE_POINTS(session_id, timestamp)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_GEOFENCES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                radius REAL NOT NULL,
                category TEXT NOT NULL,
                color_hex TEXT NOT NULL,
                alert_on_entry INTEGER NOT NULL DEFAULT 1,
                alert_on_exit INTEGER NOT NULL DEFAULT 1,
                is_inside INTEGER NOT NULL DEFAULT 0,
                last_transition INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ALERTS (
                id TEXT PRIMARY KEY,
                zone_name TEXT NOT NULL,
                event_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_OFFLINE_MAPS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                north_lat REAL NOT NULL,
                south_lat REAL NOT NULL,
                east_lng REAL NOT NULL,
                west_lng REAL NOT NULL,
                tile_type TEXT NOT NULL,
                min_zoom INTEGER NOT NULL,
                max_zoom INTEGER NOT NULL,
                tile_count INTEGER NOT NULL,
                estimated_size_mb REAL NOT NULL,
                downloaded_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_points_session ON $TABLE_POINTS(session_id, timestamp)")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_OFFLINE_MAPS (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    north_lat REAL NOT NULL,
                    south_lat REAL NOT NULL,
                    east_lng REAL NOT NULL,
                    west_lng REAL NOT NULL,
                    tile_type TEXT NOT NULL,
                    min_zoom INTEGER NOT NULL,
                    max_zoom INTEGER NOT NULL,
                    tile_count INTEGER NOT NULL,
                    estimated_size_mb REAL NOT NULL,
                    downloaded_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
