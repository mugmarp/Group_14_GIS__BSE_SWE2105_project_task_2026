package com.example.gistracker

import android.app.Application
import com.example.gistracker.data.LocationRepository
import com.example.gistracker.service.CellularDiagnosticManager

class LocationTrackerApp : Application() {

    lateinit var repository: LocationRepository
        private set

    lateinit var cellManager: CellularDiagnosticManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = LocationRepository(this)
        cellManager = CellularDiagnosticManager(this)

        // Seed initial cell towers so UI displays data immediately
        val initialTowers = cellManager.readCurrentCellTowers()
        repository.updateCellTowers(initialTowers)
    }
}
