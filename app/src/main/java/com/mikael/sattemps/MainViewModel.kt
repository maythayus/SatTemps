package com.mikael.sattemps

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikael.sattemps.geo.DishPointing
import com.mikael.sattemps.geo.GeoSatellites
import com.mikael.sattemps.gnss.GnssRepository
import com.mikael.sattemps.gnss.GnssState
import com.mikael.sattemps.network.CellRepository
import com.mikael.sattemps.network.DirectionScanner
import com.mikael.sattemps.network.MobileState
import com.mikael.sattemps.network.ScanState
import com.mikael.sattemps.sensors.CompassRepository
import com.mikael.sattemps.sensors.CompassState
import com.mikael.sattemps.time.TimeSyncManager
import com.mikael.sattemps.time.TimeSyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rassemble les sources de mesure (GNSS, boussole, modem, temps de reference) et
 * cadence leur rafraichissement selon l'usage : plein regime quand l'ecran est
 * visible, ralenti en arriere plan ou en economie de batterie.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gnss = GnssRepository(application)
    private val compass = CompassRepository(application)
    private val cells = CellRepository(application, viewModelScope)
    private val timeSync = TimeSyncManager(viewModelScope)
    private val scanner = DirectionScanner()
    private val powerManager =
        application.getSystemService(Context.POWER_SERVICE) as? PowerManager

    val gnssState: StateFlow<GnssState> = gnss.state
    val compassState: StateFlow<CompassState> = compass.state
    val mobileState: StateFlow<MobileState> = cells.state
    val timeState: StateFlow<TimeSyncState> = timeSync.state

    /** Instant de reference precis, rafraichi pour un affichage a la milliseconde. */
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _dishPointings = MutableStateFlow<List<DishPointing>>(emptyList())
    val dishPointings: StateFlow<List<DishPointing>> = _dishPointings.asStateFlow()

    private var permissionsGranted = false
    private var foreground = false

    init {
        gnss.onGnssTimeOffset = timeSync::onGnssTimeOffset
        gnss.onGnssTimeLost = timeSync::onGnssTimeLost

        viewModelScope.launch {
            while (isActive) {
                _nowMillis.value = timeSync.state.value.nowMillis()
                delay(clockIntervalMillis())
            }
        }

        viewModelScope.launch {
            while (isActive) {
                gnss.refreshStatus()
                val location = gnss.state.value.location
                if (location != null) {
                    compass.updateDeclination(
                        location.latitude, location.longitude, location.altitude,
                        System.currentTimeMillis()
                    )
                    _dishPointings.value =
                        GeoSatellites.all(location.latitude, location.longitude, location.altitude)
                }
                delay(if (foreground) STATUS_INTERVAL_MILLIS else STATUS_IDLE_INTERVAL_MILLIS)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val running = scanner.snapshot().running
                if (running) {
                    cells.state.value.serving?.dbm?.let {
                        scanner.addSample(compass.state.value.trueHeadingDeg, it)
                    }
                    _scanState.value = scanner.snapshot()
                }
                delay(if (running) SCAN_SAMPLE_INTERVAL_MILLIS else SCAN_IDLE_INTERVAL_MILLIS)
            }
        }
    }

    fun onPermissionsGranted() {
        if (permissionsGranted) return
        permissionsGranted = true
        gnss.start()
        cells.start()
        start()
    }

    /** Ecran visible : capteurs actifs et cadence nominale. */
    fun start() {
        foreground = true
        compass.start()
    }

    /** Ecran masque : on relache la boussole, principale consommatrice de batterie. */
    fun stop() {
        foreground = false
        compass.stop()
    }

    fun forceTimeSync() = timeSync.forceNtpSync()

    /**
     * Demarre ou arrete la mesure directionnelle 360°, en accelerant la lecture du
     * modem pendant la mesure uniquement.
     */
    fun toggleScan() {
        if (scanner.snapshot().running) {
            scanner.stop()
            cells.refreshIntervalMillis = CellRepository.IDLE_INTERVAL_MILLIS
        } else {
            scanner.start()
            cells.refreshIntervalMillis = CellRepository.SCAN_INTERVAL_MILLIS
        }
        _scanState.value = scanner.snapshot()
    }

    /**
     * Cadence de l'horloge : 40 ms pour une lecture fluide a la milliseconde, ralentie
     * en arriere plan ou lorsque le telephone est en economie d'energie.
     */
    private fun clockIntervalMillis(): Long = when {
        !foreground -> CLOCK_BACKGROUND_INTERVAL_MILLIS
        powerManager?.isPowerSaveMode == true -> CLOCK_SAVER_INTERVAL_MILLIS
        else -> CLOCK_INTERVAL_MILLIS
    }

    override fun onCleared() {
        gnss.stop()
        compass.stop()
        super.onCleared()
    }

    private companion object {
        const val CLOCK_INTERVAL_MILLIS = 40L
        const val CLOCK_SAVER_INTERVAL_MILLIS = 200L
        const val CLOCK_BACKGROUND_INTERVAL_MILLIS = 1000L
        const val STATUS_INTERVAL_MILLIS = 5000L
        const val STATUS_IDLE_INTERVAL_MILLIS = 30_000L
        const val SCAN_SAMPLE_INTERVAL_MILLIS = 400L
        const val SCAN_IDLE_INTERVAL_MILLIS = 1000L
    }
}
