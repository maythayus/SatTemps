package com.mikael.sattemps.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeSyncTest {

    @Test
    fun `sans reference on garde l'horloge systeme`() {
        val state = resolveTimeSource(TimeSyncState())

        assertEquals(TimeSource.SYSTEME, state.source)
        assertEquals(0L, state.offsetMillis)
        assertTrue(state.uncertaintyMillis >= 1000L)
    }

    @Test
    fun `le temps NTP est utilise en l'absence de GNSS`() {
        val state = resolveTimeSource(
            TimeSyncState(ntpOffsetMillis = -320L, ntpRoundTripMillis = 48L)
        )

        assertEquals(TimeSource.NTP, state.source)
        assertEquals(-320L, state.offsetMillis)
        assertEquals(24L, state.uncertaintyMillis)
    }

    @Test
    fun `l'incertitude NTP ne descend jamais a zero`() {
        val state = resolveTimeSource(
            TimeSyncState(ntpOffsetMillis = 5L, ntpRoundTripMillis = 0L)
        )

        assertEquals(1L, state.uncertaintyMillis)
    }

    @Test
    fun `le temps GNSS est prioritaire sur le temps NTP`() {
        val state = resolveTimeSource(
            TimeSyncState(
                ntpOffsetMillis = -320L,
                ntpRoundTripMillis = 48L,
                gnssOffsetMillis = -280L
            )
        )

        assertEquals(TimeSource.GNSS, state.source)
        assertEquals(-280L, state.offsetMillis)
        assertEquals(50L, state.uncertaintyMillis)
    }

    @Test
    fun `la perte du GNSS fait retomber sur le temps NTP`() {
        val gnss = resolveTimeSource(
            TimeSyncState(
                ntpOffsetMillis = -320L,
                ntpRoundTripMillis = 48L,
                gnssOffsetMillis = -280L
            )
        )

        val fallback = resolveTimeSource(gnss.copy(gnssOffsetMillis = null))

        assertEquals(TimeSource.NTP, fallback.source)
        assertEquals(-320L, fallback.offsetMillis)
    }

    @Test
    fun `la perte du GNSS sans NTP revient a l'horloge systeme`() {
        val fallback = resolveTimeSource(
            TimeSyncState(source = TimeSource.GNSS, offsetMillis = -280L)
        )

        assertEquals(TimeSource.SYSTEME, fallback.source)
        assertEquals(0L, fallback.offsetMillis)
    }

    @Test
    fun `le delai de reessai augmente a chaque echec puis reste borne`() {
        val nominal = retryDelayMillis(0)

        assertEquals(5 * 60 * 1000L, nominal)
        assertEquals(15_000L, retryDelayMillis(1))
        assertEquals(30_000L, retryDelayMillis(2))
        assertEquals(60_000L, retryDelayMillis(3))
        assertTrue(retryDelayMillis(MAX_BACKOFF_STEPS) <= 4 * 60 * 1000L)
    }
}
