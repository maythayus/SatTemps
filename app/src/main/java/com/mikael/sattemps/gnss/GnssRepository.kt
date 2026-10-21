package com.mikael.sattemps.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
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

/** Disponibilite de l'horloge brute du recepteur (`GnssClock`). */
enum class RawClockStatus {
    /** Android 11 minimum pour relier l'horloge GNSS a celle du telephone. */
    ANDROID_TOO_OLD,
    NOT_SUPPORTED,
    /** Abonnement accepte mais aucune mesure recue recemment. */
    WAITING,
    /** Mesures recues, mais le recepteur n'a pas encore estime le temps GPS (`fullBiasNanos`). */
    NO_GPS_TIME,
    /** Le recepteur ne fournit pas `elapsedRealtimeNanos` : impossible de dater la mesure. */
    NO_ELAPSED_REALTIME,
    ACTIVE
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
    val issue: GnssIssue? = null,
    val rawClock: RawClockStatus = RawClockStatus.WAITING,
    /** Nombre de signaux satellites dans la derniere mesure brute, `null` si aucune recente. */
    val rawSignalCount: Int? = null
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

    /** Emis a chaque mesure brute : decalage (ms), incertitude (ns), GPS - UTC (s). */
    var onGnssClockOffset: ((Long, Long, Int) -> Unit)? = null

    /** Emis lorsque les mesures brutes cessent d'arriver. */
    var onGnssClockLost: (() -> Unit)? = null

    private var lastClockElapsedMillis: Long? = null
    private var lastEventElapsedMillis: Long? = null

    private val measurementsCallback: GnssMeasurementsEvent.Callback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    onClockSample(clockSample(event), event.measurements.size)
                }

                @Deprecated("Remplace par GnssCapabilities a partir d'Android 12")
                override fun onStatusChanged(status: Int) {
                    if (status == STATUS_NOT_SUPPORTED) {
                        _state.value = _state.value.copy(rawClock = RawClockStatus.NOT_SUPPORTED)
                    }
                }
            }
        } else {
            null
        }

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
            registerMeasurements(manager)
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
            measurementsCallback?.let { manager.unregisterGnssMeasurementsCallback(it) }
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

        val now = SystemClock.elapsedRealtime()
        val clockAge = lastClockElapsedMillis?.let { now - it }
        if (!gpsEnabled || (clockAge != null && clockAge > FRESH_FIX_MILLIS)) {
            lastClockElapsedMillis = null
            onGnssClockLost?.invoke()
        }
        val eventAge = lastEventElapsedMillis?.let { now - it }
        if (!gpsEnabled || (eventAge != null && eventAge > FRESH_FIX_MILLIS)) {
            lastEventElapsedMillis = null
            if (_state.value.rawClock in RECEIVING_STATUSES) {
                _state.value =
                    _state.value.copy(rawClock = RawClockStatus.WAITING, rawSignalCount = null)
            }
        }

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

    @SuppressLint("MissingPermission")
    private fun registerMeasurements(manager: LocationManager) {
        val callback = measurementsCallback
        val status = when {
            callback == null -> RawClockStatus.ANDROID_TOO_OLD
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.gnssCapabilities.hasMeasurements() -> RawClockStatus.NOT_SUPPORTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                manager.registerGnssMeasurementsCallback(appContext.mainExecutor, callback) ->
                RawClockStatus.WAITING
            else -> RawClockStatus.NOT_SUPPORTED
        }
        _state.value = _state.value.copy(rawClock = status)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun clockSample(event: GnssMeasurementsEvent): GnssClockSample {
        val clock = event.clock
        return GnssClockSample(
            timeNanos = clock.timeNanos,
            fullBiasNanos = if (clock.hasFullBiasNanos()) clock.fullBiasNanos else null,
            biasNanos = if (clock.hasBiasNanos()) clock.biasNanos else null,
            biasUncertaintyNanos =
                if (clock.hasBiasUncertaintyNanos()) clock.biasUncertaintyNanos else null,
            leapSecond = if (clock.hasLeapSecond()) clock.leapSecond else null,
            elapsedRealtimeNanos =
                if (clock.hasElapsedRealtimeNanos()) clock.elapsedRealtimeNanos else null,
            elapsedRealtimeUncertaintyNanos =
                if (clock.hasElapsedRealtimeUncertaintyNanos()) {
                    clock.elapsedRealtimeUncertaintyNanos
                } else {
                    null
                }
        )
    }

    /**
     * L'horloge du recepteur est asservie aux horloges atomiques des satellites ; son
     * `elapsedRealtimeNanos` la relie a l'horloge monotone du telephone sans latence de remontee.
     */
    private fun onClockSample(sample: GnssClockSample, signalCount: Int) {
        lastEventElapsedMillis = SystemClock.elapsedRealtime()
        val status = GnssClockMath.classify(sample)
        if (_state.value.rawClock != status || _state.value.rawSignalCount != signalCount) {
            _state.value = _state.value.copy(rawClock = status, rawSignalCount = signalCount)
        }
        if (status != RawClockStatus.ACTIVE) return
        val sampleElapsed = sample.elapsedRealtimeNanos ?: return
        val time = GnssClockMath.utcAt(sample) ?: return
        val offset = GnssClockMath.systemOffsetMillis(
            time = time,
            sampleElapsedNanos = sampleElapsed,
            systemMillisNow = System.currentTimeMillis(),
            elapsedNowNanos = SystemClock.elapsedRealtimeNanos()
        )
        lastClockElapsedMillis = SystemClock.elapsedRealtime()
        onGnssClockOffset?.invoke(offset, time.uncertaintyNanos, time.gpsMinusUtcSeconds)
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

        val RECEIVING_STATUSES = setOf(
            RawClockStatus.NO_GPS_TIME,
            RawClockStatus.NO_ELAPSED_REALTIME,
            RawClockStatus.ACTIVE
        )
    }
}
