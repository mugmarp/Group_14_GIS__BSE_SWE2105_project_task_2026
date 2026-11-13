package com.geotrace.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.geotrace.model.BlePeerProximity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Hardware Bluetooth Low Energy (BLE) RSSI Radar for indoor "Warmer / Colder" device locating.
 * Emits and scans compact manufacturer-specific GeoTrace frames (Manufacturer ID 0xFEFE)
 * containing magic bytes 'G','T', 6-char device bleTag, and short display name.
 */
class BleProximityFinder(context: Context) {

    companion object {
        private const val TAG = "BleProximityFinder"
        private const val MANUFACTURER_ID = 0xFEFE
        private const val MAGIC_0: Byte = 'G'.code.toByte()
        private const val MAGIC_1: Byte = 'T'.code.toByte()
        private const val PEER_STALE_MS = 18_000L
    }

    private val appCtx = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val bluetoothManager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var pruneJob: Job? = null
    private val peersMap = ConcurrentHashMap<String, BlePeerProximity>()
    private var selfBleTag: String = ""

    private val _isRadarActive = MutableStateFlow(false)
    val isRadarActive: StateFlow<Boolean> = _isRadarActive.asStateFlow()

    private val _bleStatusText = MutableStateFlow("BLE indoor proximity radar idle")
    val bleStatusText: StateFlow<String> = _bleStatusText.asStateFlow()

    private val _blePeers = MutableStateFlow<List<BlePeerProximity>>(emptyList())
    val blePeers: StateFlow<List<BlePeerProximity>> = _blePeers.asStateFlow()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE Proximity advertising started")
            updateStatusIfActive()
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE Proximity advertise error code=$errorCode")
            if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                _bleStatusText.value = "BLE scanning active (advertise unavailable: code $errorCode)"
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) {
                handleScanResult(r)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: code=$errorCode")
            _bleStatusText.value = "BLE scan error (code $errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    fun start(bleTag: String, displayName: String) {
        selfBleTag = bleTag.take(6).padEnd(6, '_')
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _isRadarActive.value = false
            _bleStatusText.value = "Bluetooth LE hardware unavailable on this device"
            return
        }
        if (!adapter.isEnabled) {
            _isRadarActive.value = false
            _bleStatusText.value = "Turn on Bluetooth for indoor BLE proximity radar"
            return
        }

        _isRadarActive.value = true
        _bleStatusText.value = "Starting BLE RSSI proximity radar..."

        try {
            startAdvertising(selfBleTag, displayName)
            startScanning()
            startPruneLoop()
        } catch (se: SecurityException) {
            _isRadarActive.value = false
            _bleStatusText.value = "Bluetooth Scan/Advertise permission required"
        } catch (e: Exception) {
            _isRadarActive.value = false
            _bleStatusText.value = "BLE Radar error: ${e.localizedMessage ?: "unknown"}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        _isRadarActive.value = false
        pruneJob?.cancel()
        pruneJob = null
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        peersMap.clear()
        _blePeers.value = emptyList()
        _bleStatusText.value = "BLE indoor proximity radar idle"
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(tag6: String, displayName: String) {
        val bleAdv = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        advertiser = bleAdv

        val nameBytes = displayName.trim().take(12).toByteArray(StandardCharsets.UTF_8)
        val tagBytes = tag6.toByteArray(StandardCharsets.US_ASCII)
        val payload = ByteArray(2 + tagBytes.size + nameBytes.size)
        payload[0] = MAGIC_0
        payload[1] = MAGIC_1
        System.arraycopy(tagBytes, 0, payload, 2, tagBytes.size)
        System.arraycopy(nameBytes, 0, payload, 2 + tagBytes.size, nameBytes.size)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(MANUFACTURER_ID, payload)
            .build()

        bleAdv.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val bleScan = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanner = bleScan

        val filter = ScanFilter.Builder()
            .setManufacturerData(MANUFACTURER_ID, byteArrayOf(MAGIC_0, MAGIC_1), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScan.startScan(listOf(filter), settings, scanCallback)
        updateStatusIfActive()
    }

    private fun handleScanResult(result: ScanResult) {
        if (!_isRadarActive.value) return
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
        if (data.size < 8 || data[0] != MAGIC_0 || data[1] != MAGIC_1) return

        val peerTag = String(data, 2, 6, StandardCharsets.US_ASCII).trimEnd('_')
        if (peerTag.isBlank() || peerTag == selfBleTag.trimEnd('_')) return

        val name = if (data.size > 8) {
            String(data, 8, data.size - 8, StandardCharsets.UTF_8).ifBlank { "Device $peerTag" }
        } else {
            "Device $peerTag"
        }

        val rssi = result.rssi
        val prev = peersMap[peerTag]
        val smoothed = if (prev != null) {
            (0.35f * rssi) + (0.65f * prev.smoothedRssi)
        } else {
            rssi.toFloat()
        }

        val (bucket, progress) = classifyRssi(smoothed)
        peersMap[peerTag] = BlePeerProximity(
            bleTag = peerTag,
            displayName = name,
            rssi = rssi,
            smoothedRssi = smoothed,
            proximityBucket = bucket,
            proximityProgress = progress,
            lastSeenMs = System.currentTimeMillis()
        )
        publishSortedPeers()
    }

    private fun startPruneLoop() {
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isActive && _isRadarActive.value) {
                delay(4000L)
                val now = System.currentTimeMillis()
                val removed = peersMap.entries.removeIf { (_, peer) -> (now - peer.lastSeenMs) > PEER_STALE_MS }
                if (removed) {
                    publishSortedPeers()
                }
            }
        }
    }

    private fun publishSortedPeers() {
        _blePeers.value = peersMap.values.sortedByDescending { it.smoothedRssi }
        updateStatusIfActive()
    }

    private fun updateStatusIfActive() {
        if (!_isRadarActive.value) return
        val count = peersMap.size
        _bleStatusText.value = if (count > 0) {
            "BLE Radar Active • Sensing $count nearby device(s) indoors"
        } else {
            "BLE Radar Active • Walk rooms to sense RSSI warmth (<1m to 15m)"
        }
    }

    private fun classifyRssi(smoothedRssi: Float): Pair<String, Float> {
        val progress = ((smoothedRssi + 100f) / 60f).coerceIn(0.05f, 1.0f)
        val label = when {
            smoothedRssi >= -54f -> "HOT • Right Here (< 1m)"
            smoothedRssi >= -68f -> "WARM • Very Close (1–3m)"
            smoothedRssi >= -82f -> "COOL • Nearby Room (3–10m)"
            else -> "COLD • Far / Weak (> 10m)"
        }
        return Pair(label, progress)
    }
}
