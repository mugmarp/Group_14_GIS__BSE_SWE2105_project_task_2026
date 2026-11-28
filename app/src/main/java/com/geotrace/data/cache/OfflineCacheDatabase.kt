package com.geotrace.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedCellTowerEntity::class,
        CachedObservedCellEntity::class,
        CachedLocationFixEntity::class,
        CachedNavRouteEntity::class,
        OfflineRoutingPackEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OfflineCacheDatabase : RoomDatabase() {
    abstract fun cellTowerCacheDao(): CellTowerCacheDao
    abstract fun observedCellDao(): ObservedCellDao
    abstract fun locationFixCacheDao(): LocationFixCacheDao
    abstract fun navRouteCacheDao(): NavRouteCacheDao
    abstract fun routingPackDao(): OfflineRoutingPackDao

    companion object {
        private const val DB_NAME = "geotrace_offline_cache.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_nav_routes` (
                        `id` TEXT NOT NULL,
                        `destination_name` TEXT NOT NULL,
                        `destination_lat` REAL NOT NULL,
                        `destination_lon` REAL NOT NULL,
                        `profile` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `total_distance_meters` REAL NOT NULL,
                        `total_duration_seconds` REAL NOT NULL,
                        `is_active` INTEGER NOT NULL,
                        `route_json` TEXT NOT NULL,
                        `updated_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_nav_routes_updated_at_ms` ON `cached_nav_routes` (`updated_at_ms`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_routing_packs` (
                        `region_id` TEXT NOT NULL,
                        `region_name` TEXT NOT NULL,
                        `north_lat` REAL NOT NULL,
                        `south_lat` REAL NOT NULL,
                        `east_lon` REAL NOT NULL,
                        `west_lon` REAL NOT NULL,
                        `vertex_count` INTEGER NOT NULL,
                        `edge_count` INTEGER NOT NULL,
                        `file_size_bytes` INTEGER NOT NULL,
                        `file_path` TEXT NOT NULL,
                        `updated_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`region_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_offline_routing_packs_updated_at_ms` ON `offline_routing_packs` (`updated_at_ms`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: OfflineCacheDatabase? = null

        fun getInstance(context: Context): OfflineCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineCacheDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
