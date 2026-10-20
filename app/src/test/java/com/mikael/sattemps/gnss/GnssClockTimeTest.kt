package com.mikael.sattemps.gnss

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GnssClockTimeTest {

    private val utcMillis = Instant.parse("2026-10-19T10:00:00Z").toEpochMilli()
    private val receiverNanos = 5_000_000_000L
    private val gpsNanos =
        (utcMillis - GnssClockMath.GPS_EPOCH_UNIX_MILLIS + 18_000L) * 1_000_000L

    private fun sample(
        bias: Double = 0.0,
        leap: Int? = null,
        biasUncertainty: Double? = 30.0,
        elapsedUncertainty: Double? = 40.0
    ) = GnssClockSample(
        timeNanos = receiverNanos,
        fullBiasNanos = receiverNanos - gpsNanos,
        biasNanos = bias,
        biasUncertaintyNanos = biasUncertainty,
        leapSecond = leap,
        elapsedRealtimeNanos = 1_000_000_000L,
        elapsedRealtimeUncertaintyNanos = elapsedUncertainty
    )

    @Test
    fun `le temps GPS moins 18 secondes donne l'UTC`() {
        val time = GnssClockMath.utcAt(sample())!!

        assertEquals(utcMillis * 1_000_000L, time.utcNanos)
        assertEquals(18, time.gpsMinusUtcSeconds)
    }

    @Test
    fun `le biais fin du recepteur est retranche`() {
        val time = GnssClockMath.utcAt(sample(bias = 250.0))!!

        assertEquals(utcMillis * 1_000_000L - 250L, time.utcNanos)
    }

    @Test
    fun `les secondes intercalaires du recepteur priment sur la valeur par defaut`() {
        val time = GnssClockMath.utcAt(sample(leap = 19))!!

        assertEquals((utcMillis - 1_000L) * 1_000_000L, time.utcNanos)
        assertEquals(19, time.gpsMinusUtcSeconds)
    }

    @Test
    fun `sans estimation du temps GPS aucun instant n'est calcule`() {
        assertNull(GnssClockMath.utcAt(sample().copy(fullBiasNanos = null)))
    }

    @Test
    fun `un temps GPS anterieur a 1980 est rejete`() {
        assertNull(GnssClockMath.utcAt(sample().copy(fullBiasNanos = receiverNanos + 1L)))
    }

    @Test
    fun `les incertitudes se combinent quadratiquement`() {
        assertEquals(50L, GnssClockMath.utcAt(sample())!!.uncertaintyNanos)
    }

    @Test
    fun `une incertitude inconnue reste prudente`() {
        val time = GnssClockMath.utcAt(
            sample(biasUncertainty = null, elapsedUncertainty = null)
        )!!

        assertEquals(1_000_000L, time.uncertaintyNanos)
    }

    @Test
    fun `l'ecart avec l'horloge systeme tient compte du temps ecoule depuis la mesure`() {
        val time = GnssClockMath.utcAt(sample())!!
        val offset = GnssClockMath.systemOffsetMillis(
            time = time,
            sampleElapsedNanos = 1_000_000_000L,
            systemMillisNow = utcMillis + 2L - 150L,
            elapsedNowNanos = 1_002_000_000L
        )

        assertEquals(150L, offset)
    }

    @Test
    fun `l'ecart est arrondi a la milliseconde la plus proche`() {
        val time = GnssClockMath.utcAt(sample(bias = -600_000.0))!!
        val offset = GnssClockMath.systemOffsetMillis(
            time = time,
            sampleElapsedNanos = 1_000_000_000L,
            systemMillisNow = utcMillis,
            elapsedNowNanos = 1_000_000_000L
        )

        assertEquals(1L, offset)
    }
}
