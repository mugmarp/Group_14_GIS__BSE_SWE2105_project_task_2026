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

        // Initialize OpenStreetMap (osmdroid) configuration
        org.osmdroid.config.Configuration.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName

        repository = LocationRepository(this)
        cellManager = CellularDiagnosticManager(this)
        groupRepository = com.geotrace.data.GroupRepository(this)

        // Read real hardware cell towers if present
        val initialTowers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(initialTowers)
    }
}
