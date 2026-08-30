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

data class GnssState(
    val location: Location? = null,
    val satellites: List<Satellite> = emptyList(),
    val satellitesUsed: Int = 0,
    val hasFix: Boolean = false
) {
    /** Satellite offrant le meilleur signal parmi ceux au dessus de l'horizon. */
    val bestSatellite: Satellite?
        get() = satellites.filter { it.elevationDeg > 5f }.maxByOrNull { it.cn0DbHz }

    /**
     * Direction ou le ciel est le mieux degage : moyenne vectorielle des azimuts
     * des satellites recus, ponderee par la qualite du signal.
     */
    val bestSkyAzimuthDeg: Float?
        get() {
            val visible = satellites.filter { it.cn0DbHz > 0f && it.elevationDeg > 5f }
            if (visible.isEmpty()) return null
            var x = 0.0
            var y = 0.0
            visible.forEach { sat ->
                val weight = sat.cn0DbHz.toDouble() * Math.cos(Math.toRadians(sat.elevationDeg.toDouble()))
                val rad = Math.toRadians(sat.azimuthDeg.toDouble())
                x += weight * Math.cos(rad)
                y += weight * Math.sin(rad)
            }
            if (x == 0.0 && y == 0.0) return null
            return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
        }

    val meanElevationDeg: Float?
        get() = satellites.filter { it.cn0DbHz > 0f }.map { it.elevationDeg }.average()
            .takeIf { !it.isNaN() }?.toFloat()
}

/** Ecoute le GPS : position, temps satellite et etat de la constellation. */
class GnssRepository(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GnssState())
    val state: StateFlow<GnssState> = _state.asStateFlow()

    /** Emis a chaque fix : temps GNSS - horloge systeme, en millisecondes. */
    var onGnssTimeOffset: ((Long) -> Unit)? = null

    private val locationListener = LocationListener { location ->
        if (location.provider == LocationManager.GPS_PROVIDER) {
            computeTimeOffset(location)?.let { onGnssTimeOffset?.invoke(it) }
        }
        _state.value = _state.value.copy(location = location, hasFix = true)
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

    @SuppressLint("MissingPermission")
    fun start() {
        runCatching {
            locationManager.registerGnssStatusCallback(gnssCallback, handler)
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                _state.value = _state.value.copy(location = it)
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5000L, 0f, locationListener, Looper.getMainLooper()
                )
            }
        }
    }

    fun stop() {
        runCatching {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }

    /**
     * Le fix GPS porte un horodatage UTC issu des satellites ; on le compare a
     * l'horloge du telephone en passant par l'horloge monotone pour eliminer la
     * latence de remontee du fix.
     */
    private fun computeTimeOffset(location: Location): Long? {
        val fixElapsedNanos = location.elapsedRealtimeNanos
        if (fixElapsedNanos <= 0L) return null
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        val fixAgeMillis = nowElapsedMillis - fixElapsedNanos / 1_000_000L
        if (fixAgeMillis > 10_000L) return null
        val gnssNow = location.time + fixAgeMillis
        return gnssNow - System.currentTimeMillis()
    }

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
}
