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
    val available: Boolean = true,
    /** Au moins une mesure a ete recue du capteur. */
    val hasReading: Boolean = false
) {
    /** Cap par rapport au nord geographique (vrai). */
    val trueHeadingDeg: Float
        get() = (magneticHeadingDeg + declinationDeg + 360f) % 360f

    /** Le capteur demande un etalonnage (mouvement en huit) avant d'etre fiable. */
    val needsCalibration: Boolean
        get() = available && hasReading &&
            accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

/**
 * Boussole basee sur le vecteur de rotation, lissee pour un affichage stable.
 *
 * L'absence de capteur, un tableau de valeurs non standard ou un refus
 * d'enregistrement sont traites sans faire tomber l'application : [state] indique
 * alors que la boussole est indisponible.
 */
class CompassRepository(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val rotationVector4 = FloatArray(4)
    private val rotationVector3 = FloatArray(3)
    private var smoothedSin = 0.0
    private var smoothedCos = 0.0
    private var registered = false

    private val _state = MutableStateFlow(CompassState(available = rotationSensor != null))
    val state: StateFlow<CompassState> = _state.asStateFlow()

    fun start() {
        val manager = sensorManager ?: return
        val sensor = rotationSensor ?: return
        if (registered) return
        registered = runCatching {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        if (!registered) _state.value = _state.value.copy(available = false)
    }

    fun stop() {
        val manager = sensorManager ?: return
        runCatching { manager.unregisterListener(this) }
        registered = false
    }

    /** Met a jour la declinaison magnetique a partir de la position courante. */
    fun updateDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long) {
        val declination = runCatching {
            GeomagneticField(
                latitude.toFloat(), longitude.toFloat(), altitude.toFloat(), timeMillis
            ).declination
        }.getOrNull() ?: return
        _state.value = _state.value.copy(declinationDeg = declination)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Certains constructeurs livrent plus de composantes que la norme : on ne
        // garde que le quaternion attendu par getRotationMatrixFromVector.
        val size = minOf(event.values.size, rotationVector4.size)
        if (size < 3) return
        val vector = if (size >= 4) rotationVector4 else rotationVector3
        for (i in vector.indices) vector[i] = event.values[i]

        val computed = runCatching {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)
            SensorManager.getOrientation(rotationMatrix, orientation)
        }.isSuccess
        if (!computed) return

        val azimuthRad = orientation[0].toDouble()
        smoothedSin = smoothedSin * (1 - SMOOTHING) + Math.sin(azimuthRad) * SMOOTHING
        smoothedCos = smoothedCos * (1 - SMOOTHING) + Math.cos(azimuthRad) * SMOOTHING
        val heading = ((Math.toDegrees(Math.atan2(smoothedSin, smoothedCos)) + 360.0) % 360.0)

        _state.value = _state.value.copy(
            magneticHeadingDeg = heading.toFloat(),
            hasReading = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _state.value = _state.value.copy(accuracy = accuracy)
    }

    private companion object {
        /** Filtre exponentiel : compromis entre stabilite et reactivite du cap. */
        const val SMOOTHING = 0.15
    }
}
