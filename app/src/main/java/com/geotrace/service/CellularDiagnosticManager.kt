package com.geotrace.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.geotrace.model.CellTowerInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CellularDiagnosticManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun requestFreshCellTowers(timeoutMs: Long = 2500L): List<CellTowerInfo> {
        val tm = telephonyManager ?: return emptyList()
        if (!hasFineLocationPermission()) return emptyList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val freshRaw = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<List<CellInfo>?> { cont ->
                    try {
                        tm.requestCellInfoUpdate(
                            context.mainExecutor,
                            object : TelephonyManager.CellInfoCallback() {
                                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                                    if (cont.isActive) cont.resume(cellInfo)
                                }

                                override fun onError(errorCode: Int, detail: Throwable?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            }
                        )
                    } catch (_: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (!freshRaw.isNullOrEmpty()) {
                return parseAndSortCellInfoList(freshRaw)
            }
        }
        return readCurrentCellTowers()
    }

    fun readCurrentCellTowers(): List<CellTowerInfo> {
        val tm = telephonyManager ?: return emptyList()
        if (!hasFineLocationPermission()) return emptyList()

        return try {
            val cellInfoList: List<CellInfo>? = tm.allCellInfo
            if (cellInfoList.isNullOrEmpty()) emptyList() else parseAndSortCellInfoList(cellInfoList)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseAndSortCellInfoList(cellInfoList: List<CellInfo>): List<CellTowerInfo> {
        val results = mutableListOf<CellTowerInfo>()
        val seenKeys = mutableSetOf<String>()

        for (info in cellInfoList) {
            val parsed = parseCellInfo(info) ?: continue
            val key = "${parsed.type}_${parsed.isRegistered}_${parsed.cid}_${parsed.pci}_${parsed.earfcn}_${parsed.signalDbm}"
            if (seenKeys.add(key)) {
                results.add(parsed)
            }
        }

        return results.sortedWith(
            compareByDescending<CellTowerInfo> { it.isRegistered }
                .thenByDescending { it.cid != null }
                .thenByDescending { it.signalDbm ?: Int.MIN_VALUE }
        )
    }

    private fun sanitizeLegacyCid(raw: Long): Long? {
        return raw.takeIf {
            it > 0L &&
                it != Int.MAX_VALUE.toLong() &&
                it != Long.MAX_VALUE &&
                it != 65535L // 0xFFFF: 2G/3G unavailable CID sentinel
        }
    }

    private fun sanitizeLteCid(raw: Long): Long? {
        return raw.takeIf {
            it > 0L &&
                it != Int.MAX_VALUE.toLong() &&
                it != Long.MAX_VALUE &&
                it != 268435455L // 0x0FFFFFFF: 3GPP LTE ECI unavailable sentinel
        }
    }

    private fun sanitizeTacLteOrLac(raw: Int): Int? {
        return raw.takeIf {
            it in 1 until 65535 && it != Int.MAX_VALUE
        }
    }

    private fun sanitizeTacNr(raw: Int): Int? {
        return raw.takeIf {
            it in 1 until 16777215 && it != Int.MAX_VALUE // 24-bit 5G NR TAC (0xFFFFFF unavailable)
        }
    }

    private fun parseCellInfo(info: CellInfo): CellTowerInfo? {
        when {
            info is CellInfoLte -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mccString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mcc.takeIf { it != Int.MAX_VALUE && it in 100..999 }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE && it in 0..999 }
                }
                val cid = sanitizeLteCid(identity.ci.toLong())
                val tac = sanitizeTacLteOrLac(identity.tac)
                val pci = identity.pci.takeIf { it in 0..503 }
                val earfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    identity.earfcn.takeIf { it in 0..262143 }
                } else null
                val dbm = signal.dbm.takeIf { it in -145..-30 } ?: return null
                val timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    signal.timingAdvance.takeIf { it in 0..1282 }
                } else null

                if (cid == null && pci == null) return null

                return CellTowerInfo(
                    type = "4G LTE",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = tac,
                    signalDbm = dbm,
                    signalLevel = signal.level.coerceIn(0, 4),
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    timingAdvance = timingAdvance,
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, timingAdvance),
                    pci = pci,
                    earfcn = earfcn
                )
            }
            info is CellInfoGsm -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mccString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mcc.takeIf { it != Int.MAX_VALUE && it in 100..999 }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE && it in 0..999 }
                }
                val cid = sanitizeLegacyCid(identity.cid.toLong())
                val lac = sanitizeTacLteOrLac(identity.lac)
                val bsic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    identity.bsic.takeIf { it in 0..63 }
                } else null
                val arfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    identity.arfcn.takeIf { it in 0..1023 }
                } else null
                val dbm = signal.dbm.takeIf { it in -140..-30 } ?: return null

                if (cid == null && bsic == null) return null

                return CellTowerInfo(
                    type = "2G GSM",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = dbm,
                    signalLevel = signal.level.coerceIn(0, 4),
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, null),
                    pci = bsic,
                    earfcn = arfcn
                )
            }
            info is CellInfoWcdma -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mccString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mcc.takeIf { it != Int.MAX_VALUE && it in 100..999 }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE && it in 0..999 }
                }
                val cid = sanitizeLegacyCid(identity.cid.toLong())
                val lac = sanitizeTacLteOrLac(identity.lac)
                val psc = identity.psc.takeIf { it in 0..511 }
                val uarfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    identity.uarfcn.takeIf { it in 0..16383 }
                } else null
                val dbm = signal.dbm.takeIf { it in -140..-30 } ?: return null

                if (cid == null && psc == null) return null

                return CellTowerInfo(
                    type = "3G WCDMA",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = dbm,
                    signalLevel = signal.level.coerceIn(0, 4),
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, null),
                    pci = psc,
                    earfcn = uarfcn
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val identity = info.cellIdentity as? android.telephony.CellIdentityNr
                val signal = info.cellSignalStrength as? CellSignalStrengthNr
                val mcc = identity?.mccString?.toIntOrNull()
                val mnc = identity?.mncString?.toIntOrNull()
                val nci = identity?.nci?.takeIf { it > 0L && it != Long.MAX_VALUE && it != 68719476735L }
                val tac = identity?.tac?.let { sanitizeTacNr(it) }
                val pci = identity?.pci?.takeIf { it in 0..1007 }
                val nrarfcn = identity?.nrarfcn?.takeIf { it in 0..3279165 }
                val dbm = signal?.ssRsrp?.takeIf { it in -145..-30 }
                    ?: signal?.csiRsrp?.takeIf { it in -145..-30 }
                    ?: signal?.dbm?.takeIf { it in -145..-30 }

                if (nci == null && pci == null) return null
                if (dbm == null && !info.isRegistered && nci == null) return null

                return CellTowerInfo(
                    type = "5G NR",
                    mcc = mcc,
                    mnc = mnc,
                    cid = nci,
                    lac = tac,
                    signalDbm = dbm,
                    signalLevel = if (dbm != null) (signal?.level?.coerceIn(0, 4) ?: 0) else 0,
                    isRegistered = info.isRegistered,
                    operatorName = identity?.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, null),
                    pci = pci,
                    earfcn = nrarfcn
                )
            }
            else -> return null
        }
    }

    private fun calculateEstimatedDistance(dbm: Int?, timingAdvance: Int?): Double? {
        if (timingAdvance != null && timingAdvance >= 0) {
            // LTE Timing Advance 1 TA step ≈ 78.12 meters (TA=0 -> within first 78m ring)
            return (timingAdvance.coerceAtLeast(1) * 78.12) / 1000.0
        }
        if (dbm == null) return null
        // Free Space / Hata signal attenuation heuristic in urban 1800-2100MHz
        return when {
            dbm >= -70 -> 0.15 // Very close (< 200m)
            dbm >= -85 -> 0.45 // 400-500m
            dbm >= -95 -> 0.95 // ~1 km
            dbm >= -105 -> 1.8 // ~1.8 km
            else -> 3.2 // > 3 km
        }
    }
}
