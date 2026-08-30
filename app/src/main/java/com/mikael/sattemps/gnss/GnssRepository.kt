package com.mikael.sattemps.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Satellite(
    val constellation: String,
    val svid: Int,
    /** Azimut geographique du satellite, en degres (0 = nord vrai). */
    val azimuthDeg: Float,
    /** Elevation au dessus de l'horizon, en degres. */
    val elevationDeg: Float,
    /** Rapport porteuse sur bruit en dB-Hz : indicateur de qualite du signal. */
    val cn0DbHz: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean
)

/** Cause d'indisponibilite du recepteur, traduite dans l'interface. */
enum class GnssIssue {
    NO_SERVICE,
    PERMISSION_DENIED,
    RECEIVER_UNAVAILABLE,
    LOCATION_DISABLED,
    SEARCHING,
    SIGNAL_LOST
}

data class GnssState(
    val location: Location? = null,
    val satellites: List<Satellite> = emptyList(),
    val satellitesUsed: Int = 0,
    val hasFix: Boolean = false,
    /** Le service de localisation du telephone est actif. */
    val gpsEnabled: Boolean = true,
    /** Le recepteur est demarre et l'application ecoute les mises a jour. */
    val listening: Boolean = false,
    /** Age du dernier fix en secondes, `null` si aucun fix. */
    val fixAgeSeconds: Long? = null,
    /** Probleme courant empechant une mesure fiable, `null` si tout va bien. */
    val issue: GnssIssue? = null
) {
    /** Satellite offrant le meilleur signal parmi ceux au dessus de l'horizon. */
    val bestSatellite: Satellite? get() = SkyAnalysis.bestSatellite(satellites)

    /** Direction ou le ciel est le mieux degage, ponderee par la qualite du signal. */
    val bestSkyAzimuthDeg: Float? get() = SkyAnalysis.bestSkyAzimuthDeg(satellites)

    val meanElevationDeg: Float? get() = SkyAnalysis.meanElevationDeg(satellites)
}

/**
 * Ecoute le GPS : position, temps satellite et etat de la constellation.
 *
 * Toutes les entrees systeme sont protegees : un service de localisation desactive,
 * une permission revoquee ou un fabricant qui refuse l'acces GNSS se traduisent par
 * un message dans [state] plutot que par un plantage.
 */
class GnssRepository(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GnssState())
    val state: StateFlow<GnssState> = _state.asStateFlow()

    /** Emis a chaque fix : temps GNSS - horloge systeme, en millisecondes. */
    var onGnssTimeOffset: ((Long) -> Unit)? = null

    /** Emis lorsque le fix GNSS devient trop ancien pour servir de reference de temps. */
    var onGnssTimeLost: (() -> Unit)? = null

    private val locationListener = LocationListener { location ->
        if (location.provider == LocationManager.GPS_PROVIDER) {
            computeTimeOffset(location)?.let { onGnssTimeOffset?.invoke(it) }
        }
        _state.value = _state.value.copy(
            location = location,
            hasFix = true,
            fixAgeSeconds = 0L,
            issue = null
        )
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val satellites = (0 until status.satelliteCount).map { i ->
                Satellite(
                    constellation = constellationName(status.getConstellationType(i)),
                    svid = status.getSvid(i),
                    azimuthDeg = status.getAzimuthDegrees(i),
                    elevationDeg = status.getElevationDegrees(i),
                    cn0DbHz = status.getCn0DbHz(i),
                    usedInFix = status.usedInFix(i),
                    hasEphemeris = status.hasEphemerisData(i)
                )
            }
            _state.value = _state.value.copy(
                satellites = satellites.sortedByDescending { it.cn0DbHz },
                satellitesUsed = satellites.count { it.usedInFix }
            )
        }
    }

    /** Demarre l'ecoute du recepteur ; sans effet si la localisation est indisponible. */
    @SuppressLint("MissingPermission")
    fun start() {
        val manager = locationManager
        if (manager == null) {
            _state.value = _state.value.copy(listening = false, issue = GnssIssue.NO_SERVICE)
            return
        }

        val gpsEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        try {
            manager.registerGnssStatusCallback(gnssCallback, handler)
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                _state.value = _state.value.copy(location = it)
            }
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5000L, 0f, locationListener,
                    Looper.getMainLooper()
                )
            }
            _state.value = _state.value.copy(
                listening = true,
                gpsEnabled = gpsEnabled,
                issue = if (gpsEnabled) null else GnssIssue.LOCATION_DISABLED
            )
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(listening = false, issue = GnssIssue.PERMISSION_DENIED)
        } catch (e: IllegalArgumentException) {
            _state.value =
                _state.value.copy(listening = false, issue = GnssIssue.RECEIVER_UNAVAILABLE)
        }
    }

    fun stop() {
        val manager = locationManager ?: return
        runCatching {
            manager.removeUpdates(locationListener)
            manager.unregisterGnssStatusCallback(gnssCallback)
        }
        _state.value = _state.value.copy(listening = false)
    }

    /**
     * Reevalue l'age du fix et l'etat du service de localisation. Appele
     * periodiquement : un fix trop ancien ne peut plus servir de reference de temps.
     */
    fun refreshStatus() {
        val manager = locationManager ?: return
        val gpsEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        val ageSeconds = _state.value.location?.let { fixAgeMillis(it) / 1000L }
        val stale = ageSeconds != null && ageSeconds > STALE_FIX_SECONDS
        if (stale || !gpsEnabled) onGnssTimeLost?.invoke()

        _state.value = _state.value.copy(
            gpsEnabled = gpsEnabled,
            fixAgeSeconds = ageSeconds,
            hasFix = _state.value.location != null && !stale,
            issue = when {
                !gpsEnabled -> GnssIssue.LOCATION_DISABLED
                stale -> GnssIssue.SIGNAL_LOST
                _state.value.location == null && _state.value.listening -> GnssIssue.SEARCHING
                else -> null
            }
        )
    }

    /**
     * Le fix GPS porte un horodatage UTC issu des satellites ; on le compare a
     * l'horloge du telephone en passant par l'horloge monotone pour eliminer la
     * latence de remontee du fix.
     */
    private fun computeTimeOffset(location: Location): Long? {
        val fixElapsedNanos = location.elapsedRealtimeNanos
        if (fixElapsedNanos <= 0L) return null
        val ageMillis = fixAgeMillis(location)
        if (ageMillis > FRESH_FIX_MILLIS) return null
        val gnssNow = location.time + ageMillis
        return gnssNow - System.currentTimeMillis()
    }

    private fun fixAgeMillis(location: Location): Long =
        SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000L

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Inconnu"
    }

    private companion object {
        /** Au dela, le fix ne sert plus de reference de temps. */
        const val FRESH_FIX_MILLIS = 10_000L

        /** Au dela, on considere le signal GPS perdu. */
        const val STALE_FIX_SECONDS = 30L
    }
}
