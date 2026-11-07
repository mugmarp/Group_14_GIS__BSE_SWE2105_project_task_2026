package com.geotrace.service

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
import com.geotrace.model.CellTowerInfo

class CellularDiagnosticManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager



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

                return CellTowerInfo(
                    type = "4G LTE",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = tac,
                    signalDbm = dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    timingAdvance = timingAdvance,
                    estimatedDistanceKm = calculateEstimatedDistance(dbm, timingAdvance)
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

                return CellTowerInfo(
                    type = "2G GSM",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = signal.dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(signal.dbm, null)
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

                return CellTowerInfo(
                    type = "3G WCDMA",
                    mcc = mcc,
                    mnc = mnc,
                    cid = cid,
                    lac = lac,
                    signalDbm = signal.dbm,
                    signalLevel = signal.level,
                    isRegistered = info.isRegistered,
                    operatorName = identity.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = calculateEstimatedDistance(signal.dbm, null)
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
                val identity = info.cellIdentity as? android.telephony.CellIdentityNr
                val signal = info.cellSignalStrength as? CellSignalStrengthNr
                val mcc = identity?.mccString?.toIntOrNull()
                val mnc = identity?.mncString?.toIntOrNull()
                val nci = identity?.nci?.takeIf { it != Long.MAX_VALUE }

                return CellTowerInfo(
                    type = "5G NR",
                    mcc = mcc,
                    mnc = mnc,
                    cid = nci,
                    lac = identity?.tac?.takeIf { it != Int.MAX_VALUE },
                    signalDbm = signal?.dbm ?: -85,
                    signalLevel = signal?.level ?: 3,
                    isRegistered = info.isRegistered,
                    operatorName = identity?.operatorAlphaLong?.toString(),
                    estimatedDistanceKm = 0.35
                )
            }
            else -> return null
        }
    }

    private fun calculateEstimatedDistance(dbm: Int, timingAdvance: Int?): Double {
        if (timingAdvance != null && timingAdvance > 0) {
            // LTE Timing Advance 1 TA step ≈ 78.12 meters
            return (timingAdvance * 78.12) / 1000.0
        }
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
