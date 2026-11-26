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

    init {
        setWriteAheadLoggingEnabled(true)
    }

    companion object {
        const val DATABASE_NAME = "gis_tracker.db"
        const val DATABASE_VERSION = 9

        const val TABLE_POINTS = "location_points"
        const val TABLE_SESSIONS = "tracking_sessions"
        const val TABLE_WAYPOINTS = "trail_waypoints"
        const val TABLE_GEOFENCES = "geofence_zones"
        const val TABLE_ALERTS = "geofence_alerts"
        const val TABLE_OFFLINE_MAPS = "offline_map_regions"
        const val TABLE_OFFLINE_PLACES = "offline_places"
        const val TABLE_TRIPS = "trips"
        const val TABLE_SAVED_PLACES = "saved_places"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRIPS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                start_date INTEGER NOT NULL,
                end_date INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

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
                waypoint_count INTEGER NOT NULL DEFAULT 0,
                tracking_mode TEXT NOT NULL,
                is_recording INTEGER NOT NULL DEFAULT 0,
                is_paused INTEGER NOT NULL DEFAULT 0,
                paused_accumulated_ms INTEGER NOT NULL DEFAULT 0,
                pause_started_ms INTEGER,
                trip_id TEXT,
                FOREIGN KEY (trip_id) REFERENCES $TABLE_TRIPS(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_trip ON $TABLE_SESSIONS(trip_id)")

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
            CREATE TABLE $TABLE_WAYPOINTS (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                name TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL NOT NULL DEFAULT 0.0,
                accuracy REAL NOT NULL DEFAULT 0.0,
                timestamp INTEGER NOT NULL,
                photo_uri TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_waypoints_session ON $TABLE_WAYPOINTS(session_id, timestamp)")

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
                last_transition INTEGER,
                is_enabled INTEGER NOT NULL DEFAULT 0
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
                downloaded_at INTEGER NOT NULL,
                is_corridor INTEGER NOT NULL DEFAULT 0,
                corridor_boxes_json TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_OFFLINE_PLACES (
                id TEXT PRIMARY KEY,
                region_id TEXT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                subtitle TEXT NOT NULL DEFAULT '',
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                FOREIGN KEY (region_id) REFERENCES $TABLE_OFFLINE_MAPS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_places_name ON $TABLE_OFFLINE_PLACES(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_places_region ON $TABLE_OFFLINE_PLACES(region_id)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_SAVED_PLACES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'Spot',
                note TEXT NOT NULL DEFAULT '',
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL NOT NULL DEFAULT 0.0,
                photo_uri TEXT NOT NULL DEFAULT '',
                trip_id TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (trip_id) REFERENCES $TABLE_TRIPS(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_places_name ON $TABLE_SAVED_PLACES(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_places_trip ON $TABLE_SAVED_PLACES(trip_id)")
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
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN waypoint_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN is_paused INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN paused_accumulated_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN pause_started_ms INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_WAYPOINTS (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    altitude REAL NOT NULL DEFAULT 0.0,
                    accuracy REAL NOT NULL DEFAULT 0.0,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_waypoints_session ON $TABLE_WAYPOINTS(session_id, timestamp)")
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_OFFLINE_PLACES (
                    id TEXT PRIMARY KEY,
                    region_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    subtitle TEXT NOT NULL DEFAULT '',
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    FOREIGN KEY (region_id) REFERENCES $TABLE_OFFLINE_MAPS(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_places_name ON $TABLE_OFFLINE_PLACES(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_offline_places_region ON $TABLE_OFFLINE_PLACES(region_id)")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE $TABLE_GEOFENCES ADD COLUMN is_enabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE_GEOFENCES SET is_inside = 0")
        }
        if (oldVersion < 7) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_TRIPS (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    notes TEXT NOT NULL DEFAULT '',
                    start_date INTEGER NOT NULL,
                    end_date INTEGER,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE $TABLE_SESSIONS ADD COLUMN trip_id TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_trip ON $TABLE_SESSIONS(trip_id)")
            db.execSQL("ALTER TABLE $TABLE_WAYPOINTS ADD COLUMN photo_uri TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SAVED_PLACES (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'Spot',
                    note TEXT NOT NULL DEFAULT '',
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    altitude REAL NOT NULL DEFAULT 0.0,
                    photo_uri TEXT NOT NULL DEFAULT '',
                    trip_id TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (trip_id) REFERENCES $TABLE_TRIPS(id) ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_places_name ON $TABLE_SAVED_PLACES(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_places_trip ON $TABLE_SAVED_PLACES(trip_id)")
        }
        if (oldVersion < 8) {
            db.execSQL("UPDATE $TABLE_SESSIONS SET trip_id = NULL WHERE trip_id IS NOT NULL AND trip_id NOT IN (SELECT id FROM $TABLE_TRIPS)")
            db.execSQL("UPDATE $TABLE_SAVED_PLACES SET trip_id = NULL WHERE trip_id IS NOT NULL AND trip_id NOT IN (SELECT id FROM $TABLE_TRIPS)")
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE $TABLE_OFFLINE_MAPS ADD COLUMN is_corridor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_OFFLINE_MAPS ADD COLUMN corridor_boxes_json TEXT NOT NULL DEFAULT ''")
        }
    }
}
