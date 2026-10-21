package com.mikael.sattemps.gnss

import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Valeurs brutes de `GnssClock` necessaires au calcul du temps satellite. */
data class GnssClockSample(
    /** Horloge interne du recepteur, en nanosecondes. */
    val timeNanos: Long,
    /** Ecart entre l'horloge du recepteur et le temps GPS, `null` si non estime. */
    val fullBiasNanos: Long?,
    val biasNanos: Double? = null,
    val biasUncertaintyNanos: Double? = null,
    /** Secondes intercalaires GPS - UTC annoncees par le recepteur. */
    val leapSecond: Int? = null,
    /** `elapsedRealtimeNanos` du telephone au meme instant que [timeNanos]. */
    val elapsedRealtimeNanos: Long? = null,
    val elapsedRealtimeUncertaintyNanos: Double? = null
)

data class GnssClockTime(
    /** Instant UTC de la mesure, en nanosecondes depuis l'epoque Unix. */
    val utcNanos: Long,
    val gpsMinusUtcSeconds: Int,
    /** Incertitude combinee recepteur + correlation avec l'horloge du telephone. */
    val uncertaintyNanos: Long
)

/**
 * Temps issu des horloges atomiques GNSS via l'horloge du recepteur :
 * `temps GPS = timeNanos - (fullBiasNanos + biasNanos)`.
 *
 * Fonctions pures, sans dependance Android, afin d'etre couvertes par des tests unitaires.
 */
object GnssClockMath {

    /** 6 janvier 1980 00:00:00 UTC, origine du temps GPS. */
    const val GPS_EPOCH_UNIX_MILLIS = 315_964_800_000L

    /** GPS - UTC depuis le 1er janvier 2017, utilise si le recepteur ne le fournit pas. */
    const val DEFAULT_LEAP_SECONDS = 18

    /** Incertitude retenue lorsque le recepteur ne l'indique pas. */
    const val UNKNOWN_UNCERTAINTY_NANOS = 1_000_000.0

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val NANOS_PER_SECOND = 1_000_000_000L

    /** Instant UTC de l'echantillon, `null` si le recepteur n'a pas encore estime le temps GPS. */
    fun utcAt(sample: GnssClockSample): GnssClockTime? {
        val fullBias = sample.fullBiasNanos ?: return null
        val gpsNanos = sample.timeNanos - fullBias - (sample.biasNanos ?: 0.0).roundToLong()
        if (gpsNanos <= 0L) return null
        val leap = sample.leapSecond ?: DEFAULT_LEAP_SECONDS
        val utcNanos = gpsNanos + GPS_EPOCH_UNIX_MILLIS * NANOS_PER_MILLI - leap * NANOS_PER_SECOND
        val biasUncertainty = sample.biasUncertaintyNanos ?: UNKNOWN_UNCERTAINTY_NANOS
        val elapsedUncertainty = sample.elapsedRealtimeUncertaintyNanos ?: 0.0
        val uncertainty = sqrt(
            biasUncertainty * biasUncertainty + elapsedUncertainty * elapsedUncertainty
        )
        return GnssClockTime(utcNanos, leap, ceil(uncertainty).toLong())
    }

    /** Explique pourquoi une mesure brute est exploitable ou non. */
    fun classify(sample: GnssClockSample): RawClockStatus = when {
        sample.fullBiasNanos == null -> RawClockStatus.NO_GPS_TIME
        sample.elapsedRealtimeNanos == null -> RawClockStatus.NO_ELAPSED_REALTIME
        utcAt(sample) == null -> RawClockStatus.NO_GPS_TIME
        else -> RawClockStatus.ACTIVE
    }

    /**
     * Decalage a ajouter a l'horloge systeme pour obtenir le temps GNSS, en millisecondes.
     * [systemMillisNow] et [elapsedNowNanos] doivent etre lus au meme moment.
     */
    fun systemOffsetMillis(
        time: GnssClockTime,
        sampleElapsedNanos: Long,
        systemMillisNow: Long,
        elapsedNowNanos: Long
    ): Long {
        val utcNowNanos = time.utcNanos + (elapsedNowNanos - sampleElapsedNanos)
        val offsetNanos = utcNowNanos - systemMillisNow * NANOS_PER_MILLI
        return (offsetNanos.toDouble() / NANOS_PER_MILLI).roundToLong()
    }
}
