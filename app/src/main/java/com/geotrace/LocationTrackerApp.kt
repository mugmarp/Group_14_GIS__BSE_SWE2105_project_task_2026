package com.geotrace

import android.app.Application
import com.geotrace.data.LocationRepository
import com.geotrace.service.CellularDiagnosticManager
import org.osmdroid.config.Configuration

class LocationTrackerApp : Application() {

    lateinit var repository: LocationRepository
        private set

    lateinit var cellManager: CellularDiagnosticManager
        private set

    lateinit var groupRepository: com.geotrace.data.GroupRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize OpenStreetMap (osmdroid) configuration with persistent internal tile storage
        val osmCfg = Configuration.getInstance()
        osmCfg.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        val osmBasePath = java.io.File(filesDir, "osmdroid")
        val osmTileCache = java.io.File(osmBasePath, "tiles")
        osmBasePath.mkdirs()
        osmTileCache.mkdirs()
        osmCfg.osmdroidBasePath = osmBasePath
        osmCfg.osmdroidTileCache = osmTileCache
        osmCfg.userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
        osmCfg.tileFileSystemCacheMaxBytes = 512L * 1024L * 1024L
        osmCfg.tileFileSystemCacheTrimBytes = 450L * 1024L * 1024L
        osmCfg.tileDownloadThreads = 2
        osmCfg.tileDownloadMaxQueueSize = 40

        repository = LocationRepository(this)
        cellManager = CellularDiagnosticManager(this)
        groupRepository = com.geotrace.data.GroupRepository(this)

        // Read real hardware cell towers if present
        val initialTowers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(initialTowers)
    }
}
