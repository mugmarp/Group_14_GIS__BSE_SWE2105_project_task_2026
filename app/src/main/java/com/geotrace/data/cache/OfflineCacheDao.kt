package com.geotrace.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CellTowerCacheDao {
    @Query(
        "SELECT * FROM cached_cell_towers " +
            "WHERE mcc = :mcc AND mnc = :mnc AND lac = :lac AND cid = :cid LIMIT 1"
    )
    suspend fun findTower(mcc: Int, mnc: Int, lac: Int, cid: Long): CachedCellTowerEntity?

    @Query("SELECT * FROM cached_cell_towers ORDER BY last_seen_at_ms DESC")
    fun observeAllTowers(): Flow<List<CachedCellTowerEntity>>

    @Query("SELECT COUNT(*) FROM cached_cell_towers")
    fun observeTowerCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_cell_towers")
    suspend fun getTowerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTower(entity: CachedCellTowerEntity)

    @Query(
        "UPDATE cached_cell_towers SET last_seen_at_ms = :seenAtMs " +
            "WHERE mcc = :mcc AND mnc = :mnc AND lac = :lac AND cid = :cid"
    )
    suspend fun touchTower(mcc: Int, mnc: Int, lac: Int, cid: Long, seenAtMs: Long)

    @Query(
        "DELETE FROM cached_cell_towers WHERE rowid IN (" +
            "SELECT rowid FROM cached_cell_towers ORDER BY last_seen_at_ms DESC LIMIT -1 OFFSET :maxKeep" +
            ")"
    )
    suspend fun pruneOldestTowers(maxKeep: Int)

    @Query("DELETE FROM cached_cell_towers")
    suspend fun clearAll()
}

@Dao
interface ObservedCellDao {
    @Query("SELECT * FROM cached_observed_cells ORDER BY is_registered DESC, signal_dbm DESC, observed_at_ms DESC")
    fun observeLatestCells(): Flow<List<CachedObservedCellEntity>>

    @Query("SELECT * FROM cached_observed_cells ORDER BY is_registered DESC, signal_dbm DESC, observed_at_ms DESC")
    suspend fun getLatestCells(): List<CachedObservedCellEntity>

    @Query("SELECT MAX(observed_at_ms) FROM cached_observed_cells")
    fun observeLatestObservedAtMs(): Flow<Long?>

    @Query("SELECT MAX(observed_at_ms) FROM cached_observed_cells")
    suspend fun getLatestObservedAtMs(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cells: List<CachedObservedCellEntity>)

    @Query("DELETE FROM cached_observed_cells")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceLatestSnapshot(cells: List<CachedObservedCellEntity>) {
        clearAll()
        if (cells.isNotEmpty()) {
            insertAll(cells)
        }
    }
}

@Dao
interface LocationFixCacheDao {
    @Query("SELECT * FROM cached_location_fixes ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestFix(): Flow<CachedLocationFixEntity?>

    @Query("SELECT * FROM cached_location_fixes ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestFix(): CachedLocationFixEntity?

    @Query("SELECT * FROM cached_location_fixes ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentFixes(limit: Int): Flow<List<CachedLocationFixEntity>>

    @Query("SELECT COUNT(*) FROM cached_location_fixes")
    fun observeFixCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_location_fixes")
    suspend fun getFixCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFix(entity: CachedLocationFixEntity)

    @Query(
        "DELETE FROM cached_location_fixes WHERE id NOT IN (" +
            "SELECT id FROM cached_location_fixes ORDER BY timestamp DESC LIMIT :maxKeep" +
            ")"
    )
    suspend fun pruneOldestFixes(maxKeep: Int)

    @Query("DELETE FROM cached_location_fixes")
    suspend fun clearAll()
}

@Dao
interface NavRouteCacheDao {
    @Query("SELECT * FROM cached_nav_routes WHERE is_active = 1 ORDER BY updated_at_ms DESC LIMIT 1")
    suspend fun getActiveRoute(): CachedNavRouteEntity?

    @Query("SELECT * FROM cached_nav_routes ORDER BY updated_at_ms DESC LIMIT :limit")
    fun observeRecentRoutes(limit: Int): Flow<List<CachedNavRouteEntity>>

    @Query("SELECT * FROM cached_nav_routes ORDER BY updated_at_ms DESC LIMIT :limit")
    suspend fun getRecentRoutes(limit: Int): List<CachedNavRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoute(entity: CachedNavRouteEntity)

    @Query("UPDATE cached_nav_routes SET is_active = 0 WHERE is_active = 1")
    suspend fun clearActiveFlags()

    @Query(
        "DELETE FROM cached_nav_routes WHERE id NOT IN (" +
            "SELECT id FROM cached_nav_routes ORDER BY updated_at_ms DESC LIMIT :maxKeep" +
            ")"
    )
    suspend fun pruneOldestRoutes(maxKeep: Int)

    @Query("DELETE FROM cached_nav_routes")
    suspend fun clearAll()
}

