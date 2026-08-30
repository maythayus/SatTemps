package com.mikael.sattemps.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompassState(
    /** Cap magnetique du telephone en degres. */
    val magneticHeadingDeg: Float = 0f,
    /** Declinaison magnetique locale (a ajouter au cap magnetique). */
    val declinationDeg: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val available: Boolean = true
) {
    /** Cap par rapport au nord geographique (vrai). */
    val trueHeadingDeg: Float
        get() = (magneticHeadingDeg + declinationDeg + 360f) % 360f
}

/** Boussole basee sur le vecteur de rotation, lissee pour un affichage stable. */
class CompassRepository(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothedSin = 0.0
    private var smoothedCos = 0.0

    private val _state = MutableStateFlow(CompassState(available = rotationSensor != null))
    val state: StateFlow<CompassState> = _state.asStateFlow()

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    /** Met a jour la declinaison magnetique a partir de la position courante. */
    fun updateDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long) {
        val field = GeomagneticField(
            latitude.toFloat(), longitude.toFloat(), altitude.toFloat(), timeMillis
        )
        _state.value = _state.value.copy(declinationDeg = field.declination)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthRad = orientation[0].toDouble()

        val alpha = 0.15
        smoothedSin = smoothedSin * (1 - alpha) + Math.sin(azimuthRad) * alpha
        smoothedCos = smoothedCos * (1 - alpha) + Math.cos(azimuthRad) * alpha
        val heading = ((Math.toDegrees(Math.atan2(smoothedSin, smoothedCos)) + 360.0) % 360.0).toFloat()

        _state.value = _state.value.copy(magneticHeadingDeg = heading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _state.value = _state.value.copy(accuracy = accuracy)
    }
}
