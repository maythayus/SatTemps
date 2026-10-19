package com.mikael.sattemps.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CellReading(
    val technology: String,
    val registered: Boolean,
    /** Puissance recue en dBm (RSRP pour LTE, SS-RSRP pour la 5G NR). */
    val dbm: Int?,
    /** Qualite (RSRQ) en dB. */
    val quality: Int?,
    /** Rapport signal/bruit (SINR) en dB. */
    val sinr: Int?,
    val identity: String,
    val bands: String?
)

/** Cause d'indisponibilite des informations radio, traduite dans l'interface. */
enum class MobileIssue {
    NO_TELEPHONY,
    PERMISSION_DENIED,
    NO_CELL_INFO,
    READ_FAILED
}

data class MobileState(
    val operator: String = "",
    val networkType: String = "",
    val nrConnected: Boolean = false,
    val cells: List<CellReading> = emptyList(),
    val issue: MobileIssue? = null,
    /** Detail technique du dernier echec, utile au diagnostic. */
    val errorDetail: String? = null
) {
    val serving: CellReading? get() = cells.firstOrNull { it.registered } ?: cells.firstOrNull()
}

/**
 * Lit les informations radio du modem : cellule servante 5G NR / LTE, puissance et
 * cellules voisines. Rafraichi en continu pour permettre la mesure directionnelle.
 */
class CellRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val telephony =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _state = MutableStateFlow(MobileState())
    val state: StateFlow<MobileState> = _state.asStateFlow()

    /**
     * Periode de lecture demandee : courte pendant la mesure directionnelle, longue
     * en veille pour epargner la batterie.
     */
    @Volatile
    var refreshIntervalMillis: Long = IDLE_INTERVAL_MILLIS

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                refresh()
                delay(refreshIntervalMillis)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        val manager = telephony
        if (manager == null) {
            _state.value = _state.value.copy(issue = MobileIssue.NO_TELEPHONY)
            return
        }
        try {
            val cells = manager.allCellInfo.orEmpty().mapNotNull { toReading(it) }
            _state.value = MobileState(
                operator = manager.networkOperatorName.orEmpty(),
                networkType = networkTypeName(),
                nrConnected = cells.any { it.technology.startsWith("5G") && it.registered },
                cells = cells.sortedByDescending { it.registered },
                issue = if (cells.isEmpty()) MobileIssue.NO_CELL_INFO else null
            )
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(issue = MobileIssue.PERMISSION_DENIED)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                issue = MobileIssue.READ_FAILED,
                errorDetail = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun toReading(info: CellInfo): CellReading? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> {
            val signal = info.cellSignalStrength as? CellSignalStrengthNr
            val identity = info.cellIdentity as? android.telephony.CellIdentityNr
            CellReading(
                technology = "5G NR",
                registered = info.isRegistered,
                dbm = signal?.ssRsrp?.takeIf { it != Int.MAX_VALUE },
                quality = signal?.ssRsrq?.takeIf { it != Int.MAX_VALUE },
                sinr = signal?.ssSinr?.takeIf { it != Int.MAX_VALUE },
                identity = "NCI ${identity?.nci ?: "?"} · PCI ${identity?.pci ?: "?"} · TAC ${identity?.tac ?: "?"}",
                bands = identity?.let { id ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        id.bands.joinToString(", ") { "n$it" }.ifEmpty { null }
                    } else null
                }
            )
        }

        info is CellInfoLte -> {
            val signal = info.cellSignalStrength
            val identity = info.cellIdentity
            CellReading(
                technology = "4G LTE",
                registered = info.isRegistered,
                dbm = signal.dbm.takeIf { it != Int.MAX_VALUE },
                quality = signal.rsrq.takeIf { it != Int.MAX_VALUE },
                sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    signal.rssnr.takeIf { it != Int.MAX_VALUE }
                } else null,
                identity = "CI ${identity.ci} · PCI ${identity.pci} · TAC ${identity.tac}",
                bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    identity.bands.joinToString(", ") { "B$it" }.ifEmpty { null }
                } else null
            )
        }

        info is CellInfoWcdma -> CellReading(
            technology = "3G UMTS",
            registered = info.isRegistered,
            dbm = info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
            quality = null,
            sinr = null,
            identity = "CID ${info.cellIdentity.cid} · LAC ${info.cellIdentity.lac}",
            bands = null
        )

        info is CellInfoGsm -> CellReading(
            technology = "2G GSM",
            registered = info.isRegistered,
            dbm = info.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
            quality = null,
            sinr = null,
            identity = "CID ${info.cellIdentity.cid} · LAC ${info.cellIdentity.lac}",
            bands = null
        )

        else -> null
    }

    @SuppressLint("MissingPermission")
    private fun networkTypeName(): String = try {
        when (telephony?.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Inconnu"
            else -> "Autre"
        }
    } catch (e: SecurityException) {
        "Inconnu"
    }

    companion object {
        /** Lecture rapide pendant la mesure 360°. */
        const val SCAN_INTERVAL_MILLIS = 1000L

        /** Lecture ralentie hors mesure. */
        const val IDLE_INTERVAL_MILLIS = 5000L
    }
}
