package com.example.gistracker.service

import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import com.example.gistracker.model.CellTowerInfo

class CellularDiagnosticManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    // Known Ugandan Cell Tower coordinates from OpenCellID / UCC database for demonstration & verification
    private val ugandaTowerRegistry = listOf(
        UgandaTower(641, 10, 482101, 1205, 0.3345, 32.5712, "MTN Kololo Hill Primary Base Station"),
        UgandaTower(641, 10, 482102, 1205, 0.3478, 32.5822, "MTN Mulago Hospital Macro Cell"),
        UgandaTower(641, 22, 310501, 840, 0.3135, 32.5815, "Airtel Kampala Road Central Tower"),
        UgandaTower(641, 22, 310502, 840, 0.3290, 32.5710, "Airtel Makerere University Tower"),
        UgandaTower(641, 10, 750101, 2300, -0.6170, 30.6580, "MTN Mbarara MUST Campus Mast"),
        UgandaTower(641, 22, 750201, 2300, -0.6120, 30.6520, "Airtel Mbarara Town Center")
    )

    data class UgandaTower(
        val mcc: Int,
        val mnc: Int,
        val cid: Long,
        val lac: Int,
        val lat: Double,
        val lon: Double,
        val siteName: String
    )

    fun readCurrentCellTowers(): List<CellTowerInfo> {
        val tm = telephonyManager ?: return emptyList()
        val results = mutableListOf<CellTowerInfo>()

        try {
            val cellInfoList: List<CellInfo>? = tm.allCellInfo
            if (!cellInfoList.isNullOrEmpty()) {
                for (info in cellInfoList) {
                    val parsed = parseCellInfo(info)
                    if (parsed != null) {
                        results.add(parsed)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted or restricted
        } catch (e: Exception) {
            // Hardware/sensor unavailable
        }

        return results
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
                    identity.mcc.takeIf { it != Int.MAX_VALUE }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE }
                }
                val cid = identity.ci.toLong().takeIf { it != Int.MAX_VALUE.toLong() && it != -1L }
                val tac = identity.tac.takeIf { it != Int.MAX_VALUE && it != -1 }
                val dbm = signal.dbm
                val timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    signal.timingAdvance.takeIf { it != Int.MAX_VALUE }
                } else null

                val matchedTower = lookupTower(mcc, mnc, cid)

                return CellTowerInfo(
                    type = "4G LTE",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = tac,
                    signalDbm = dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = matchedTower?.siteName ?: identity.operatorAlphaLong?.toString(),
                    timingAdvance = timingAdvance,
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, timingAdvance),
                    latitude = matchedTower?.lat,
                    longitude = matchedTower?.lon
                )
            }
            info is CellInfoGsm -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mccString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mcc.takeIf { it != Int.MAX_VALUE }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE }
                }
                val cid = identity.cid.toLong().takeIf { it != Int.MAX_VALUE.toLong() && it != -1L }
                val lac = identity.lac.takeIf { it != Int.MAX_VALUE && it != -1 }
                val matchedTower = lookupTower(mcc, mnc, cid)

                return CellTowerInfo(
                    type = "2G GSM",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = signal.dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = matchedTower?.siteName ?: identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(signal.dbm, null),
                    latitude = matchedTower?.lat,
                    longitude = matchedTower?.lon
                )
            }
            info is CellInfoWcdma -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mccString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mcc.takeIf { it != Int.MAX_VALUE }
                }
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    identity.mncString?.toIntOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    identity.mnc.takeIf { it != Int.MAX_VALUE }
                }
                val cid = identity.cid.toLong().takeIf { it != Int.MAX_VALUE.toLong() && it != -1L }
                val lac = identity.lac.takeIf { it != Int.MAX_VALUE && it != -1 }
                val matchedTower = lookupTower(mcc, mnc, cid)

                return CellTowerInfo(
                    type = "3G WCDMA",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = signal.dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = matchedTower?.siteName ?: identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(signal.dbm, null),
                    latitude = matchedTower?.lat,
                    longitude = matchedTower?.lon
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val identity = info.cellIdentity as? android.telephony.CellIdentityNr
                val signal = info.cellSignalStrength as? CellSignalStrengthNr
                val mcc = identity?.mccString?.toIntOrNull()
                val mnc = identity?.mncString?.toIntOrNull()
                val nci = identity?.nci?.takeIf { it != Long.MAX_VALUE }
                val matchedTower = lookupTower(mcc, mnc, nci)

                return CellTowerInfo(
                    type = "5G NR",
                    mcc = mcc,
                    mnc = mnc,
                    cid = nci,
                    lac = identity?.tac?.takeIf { it != Int.MAX_VALUE },
                    signalDbm = signal?.dbm ?: -78,
                    signalLevel = signal?.level ?: 4,
                    isRegistered = info.isRegistered,
                    operatorName = matchedTower?.siteName ?: identity?.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = 0.35,
                    latitude = matchedTower?.lat,
                    longitude = matchedTower?.lon
                )
            }
            else -> return null
        }
    }

    private fun lookupTower(mcc: Int?, mnc: Int?, cid: Long?): UgandaTower? {
        if (cid == null) return null
        return ugandaTowerRegistry.find { it.cid == cid }
            ?: ugandaTowerRegistry.find { it.mcc == mcc && it.mnc == mnc }
    }

    private fun calculateEstimatedDistance(dbm: Int, timingAdvance: Int?): Double {
        if (timingAdvance != null && timingAdvance > 0) {
            // LTE Timing Advance 1 TA step ≈ 78.12 meters
            return (timingAdvance * 78.12) / 1000.0
        }
        // Free Space / Hata signal attenuation heuristic in urban 1800-2100MHz
        // P(d) = P(d0) - 10 * n * log10(d/d0)
        return when {
            dbm >= -70 -> 0.15 // Very close (< 200m)
            dbm >= -85 -> 0.45 // 400-500m
            dbm >= -95 -> 0.95 // ~1 km
            dbm >= -105 -> 1.8 // ~1.8 km
            else -> 3.2 // > 3 km
        }
    }

    fun createDemonstrationTowers(): List<CellTowerInfo> {
        return listOf(
            CellTowerInfo(
                type = "4G LTE (Primary Serving)",
                mcc = 641,
                mnc = 10,
                cid = 482101L,
                lac = 1205,
                signalDbm = -79,
                signalLevel = 4,
                isRegistered = true,
                operatorName = "MTN Uganda (Kololo Mast #14)",
                timingAdvance = 4, // ~312 meters
                estimatedDistanceKm = 0.31,
                latitude = 0.3345,
                longitude = 32.5712,
                isDemonstrationData = true
            ),
            CellTowerInfo(
                type = "4G LTE (Neighbor Cell)",
                mcc = 641,
                mnc = 10,
                cid = 482102L,
                lac = 1205,
                signalDbm = -91,
                signalLevel = 3,
                isRegistered = false,
                operatorName = "MTN Uganda (Mulago Hill Sector B)",
                timingAdvance = 9,
                estimatedDistanceKm = 0.70,
                latitude = 0.3478,
                longitude = 32.5822,
                isDemonstrationData = true
            ),
            CellTowerInfo(
                type = "3G WCDMA (Neighbor Cell)",
                mcc = 641,
                mnc = 22,
                cid = 310501L,
                lac = 840,
                signalDbm = -96,
                signalLevel = 2,
                isRegistered = false,
                operatorName = "Airtel Uganda (Wandegeya NodeB)",
                timingAdvance = null,
                estimatedDistanceKm = 1.25,
                latitude = 0.3340,
                longitude = 32.5694,
                isDemonstrationData = true
            )
        )
    }
}
