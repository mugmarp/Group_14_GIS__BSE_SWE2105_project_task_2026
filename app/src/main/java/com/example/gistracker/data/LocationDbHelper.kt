package com.example.gistracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.gistracker.model.GeofenceAlert
import com.example.gistracker.model.GeofenceCategory
import com.example.gistracker.model.GeofenceZone
import com.example.gistracker.model.LocationPoint
import com.example.gistracker.model.TrackingMode
import com.example.gistracker.model.TrackingSession

class LocationDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "gis_tracker.db"
        const val DATABASE_VERSION = 1

        const val TABLE_POINTS = "location_points"
        const val TABLE_SESSIONS = "tracking_sessions"
        const val TABLE_GEOFENCES = "geofence_zones"
        const val TABLE_ALERTS = "geofence_alerts"
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

        db.execSQL("CREATE INDEX idx_points_session ON $TABLE_POINTS(session_id, timestamp)")

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

        // Seed with default initial Ugandan Safe Zones (Kampala & Mbarara context from research)
        seedDefaultGeofences(db)
    }

    private fun seedDefaultGeofences(db: SQLiteDatabase) {
        val defaultZones = listOf(
            GeofenceZone(
                id = "zone_must",
                name = "MUST University Campus (Mbarara)",
                latitude = -0.6167,
                longitude = 30.6583,
                radiusMeters = 400f,
                category = GeofenceCategory.SCHOOL,
                colorHex = "#00E5FF"
            ),
            GeofenceZone(
                id = "zone_mulago",
                name = "Mulago Hospital Emergency Corridor",
                latitude = 0.3392,
                longitude = 32.5768,
                radiusMeters = 500f,
                category = GeofenceCategory.SAFE_ZONE,
                colorHex = "#00E676"
            ),
            GeofenceZone(
                id = "zone_boda_hotspot",
                name = "Boda Boda High-Risk Junction (Wandegeya)",
                latitude = 0.3340,
                longitude = 32.5694,
                radiusMeters = 300f,
                category = GeofenceCategory.BODA_HOTSPOT,
                colorHex = "#FF5252"
            ),
            GeofenceZone(
                id = "zone_kampala_cbd",
                name = "Kampala Central Safe Transport Hub",
                latitude = 0.3136,
                longitude = 32.5811,
                radiusMeters = 600f,
                category = GeofenceCategory.WORK,
                colorHex = "#FFB300"
            )
        )

        for (z in defaultZones) {
            val cv = ContentValues().apply {
                put("id", z.id)
                put("name", z.name)
                put("latitude", z.latitude)
                put("longitude", z.longitude)
                put("radius", z.radiusMeters)
                put("category", z.category.name)
                put("color_hex", z.colorHex)
                put("alert_on_entry", if (z.alertOnEntry) 1 else 0)
                put("alert_on_exit", if (z.alertOnExit) 1 else 0)
                put("is_inside", if (z.isInside) 1 else 0)
                put("last_transition", z.lastTransitionTime)
            }
            db.insert(TABLE_GEOFENCES, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GEOFENCES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ALERTS")
        onCreate(db)
    }
}
