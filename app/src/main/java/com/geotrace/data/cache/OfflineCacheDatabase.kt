package com.geotrace.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedCellTowerEntity::class,
        CachedObservedCellEntity::class,
        CachedLocationFixEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OfflineCacheDatabase : RoomDatabase() {
    abstract fun cellTowerCacheDao(): CellTowerCacheDao
    abstract fun observedCellDao(): ObservedCellDao
    abstract fun locationFixCacheDao(): LocationFixCacheDao

    companion object {
        private const val DB_NAME = "geotrace_offline_cache.db"

        @Volatile
        private var INSTANCE: OfflineCacheDatabase? = null

        fun getInstance(context: Context): OfflineCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineCacheDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
