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
        val seenKeys = mutableSetOf<String>()

        try {
            val cellInfoList: List<CellInfo>? = tm.allCellInfo
            if (!cellInfoList.isNullOrEmpty()) {
                for (info in cellInfoList) {
                    val parsed = parseCellInfo(info)
                    if (parsed != null) {
                        val key = "${parsed.type}_${parsed.isRegistered}_${parsed.cid}_${parsed.pci}_${parsed.earfcn}_${parsed.signalDbm}"
                        if (seenKeys.add(key)) {
                            results.add(parsed)
                        }
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

    private fun sanitizeCid(raw: Long): Long? {
        return raw.takeIf {
            it > 0L &&
                it != Int.MAX_VALUE.toLong() &&
                it != Long.MAX_VALUE &&
                it != 268435455L && // 0x0FFFFFFF: 3GPP LTE ECI unavailable sentinel
                it != 65535L        // 0xFFFF: 2G/3G unavailable CID sentinel
        }
    }

    private fun sanitizeLac(raw: Int): Int? {
        return raw.takeIf {
            it in 1 until 65535 && it != Int.MAX_VALUE
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
                val cid = sanitizeCid(identity.ci.toLong())
                val tac = sanitizeLac(identity.tac)
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
                    signalLevel = signal.level,
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
                val cid = sanitizeCid(identity.cid.toLong())
                val lac = sanitizeLac(identity.lac)
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
                    signalLevel = signal.level,
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
                val cid = sanitizeCid(identity.cid.toLong())
                val lac = sanitizeLac(identity.lac)
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
                    signalLevel = signal.level,
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
                val tac = identity?.tac?.let { sanitizeLac(it) }
                val pci = identity?.pci?.takeIf { it in 0..1007 }
                val nrarfcn = identity?.nrarfcn?.takeIf { it in 0..3279165 }
                val dbm = signal?.dbm?.takeIf { it in -145..-30 } ?: -85

                if (nci == null && pci == null) return null

                return CellTowerInfo(
                    type = "5G NR",
                    mcc = mcc,
                    mnc = mnc,
                    cid = nci,
                    lac = tac,
                    signalDbm = dbm,
                    signalLevel = signal?.level ?: 3,
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
