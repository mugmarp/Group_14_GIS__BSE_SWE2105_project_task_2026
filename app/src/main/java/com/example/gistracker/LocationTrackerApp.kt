package com.example.gistracker

import android.app.Application
import com.example.gistracker.data.LocationRepository
import com.example.gistracker.service.CellularDiagnosticManager
import org.osmdroid.config.Configuration

class LocationTrackerApp : Application() {

    lateinit var repository: LocationRepository
        private set

    lateinit var cellManager: CellularDiagnosticManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize OpenStreetMap (osmdroid) configuration
        Configuration.getInstance().userAgentValue = packageName

        repository = LocationRepository(this)
        cellManager = CellularDiagnosticManager(this)

        // Read real hardware cell towers if present
        val initialTowers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(initialTowers)
    }
}
