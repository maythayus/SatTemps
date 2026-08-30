package com.mikael.sattemps

import android.app.Application
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gnss = GnssRepository(application)
    private val compass = CompassRepository(application)
    private val cells = CellRepository(application, viewModelScope)
    private val timeSync = TimeSyncManager(viewModelScope)
    private val scanner = DirectionScanner()

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

    init {
        gnss.onGnssTimeOffset = timeSync::onGnssTimeOffset

        viewModelScope.launch {
            while (isActive) {
                _nowMillis.value = timeSync.state.value.nowMillis()
                delay(40L)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val location = gnss.state.value.location
                if (location != null) {
                    compass.updateDeclination(
                        location.latitude, location.longitude, location.altitude, System.currentTimeMillis()
                    )
                    _dishPointings.value =
                        GeoSatellites.all(location.latitude, location.longitude, location.altitude)
                }
                delay(5000L)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                if (scanner.snapshot().running) {
                    val dbm = cells.state.value.serving?.dbm
                    if (dbm != null) scanner.addSample(compass.state.value.trueHeadingDeg, dbm)
                    _scanState.value = scanner.snapshot()
                }
                delay(400L)
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

    fun start() {
        compass.start()
    }

    fun stop() {
        compass.stop()
    }

    fun forceTimeSync() = timeSync.forceNtpSync()

    fun toggleScan() {
        if (scanner.snapshot().running) scanner.stop() else scanner.start()
        _scanState.value = scanner.snapshot()
    }

    override fun onCleared() {
        gnss.stop()
        compass.stop()
        super.onCleared()
    }
}
