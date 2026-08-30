package com.mikael.sattemps.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val HOST = "time.example.org"

/**
 * Construit une reponse serveur : le serveur recoit la requete a [receiveTime] et
 * repond a [transmitTime], en millisecondes Unix.
 *
 * Les millisecondes utilisees dans les tests sont des multiples de 125 : la fraction
 * NTP les encode alors exactement, sans troncature.
 */
private fun serverResponse(
    originateTime: Long,
    receiveTime: Long,
    transmitTime: Long,
    mode: Int = 4,
    stratum: Int = 2
): ByteArray {
    val buffer = ByteArray(48)
    buffer[0] = (mode or (3 shl 3)).toByte()
    buffer[1] = stratum.toByte()
    NtpTimestamp.write(buffer, SntpClient.ORIGINATE_OFFSET, originateTime)
    NtpTimestamp.write(buffer, SntpClient.RECEIVE_OFFSET, receiveTime)
    NtpTimestamp.write(buffer, SntpClient.TRANSMIT_OFFSET, transmitTime)
    return buffer
}

class SntpClientTest {

    @Test
    fun `une horloge en avance donne un decalage negatif`() {
        val systemTime = 1_700_000_010_000L
        val serverTime = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(
                originateTime = systemTime,
                receiveTime = serverTime + 125L,
                transmitTime = serverTime + 250L
            ),
            host = HOST,
            requestTime = systemTime,
            requestTicks = 1_000L,
            responseTicks = 1_200L
        )

        val result = assertIs<SntpClient.Outcome.Success>(outcome).result
        assertEquals(-9_912L, result.offsetMillis, "decalage ${result.offsetMillis}")
        assertEquals(75L, result.roundTripMillis)
        assertEquals(HOST, result.server)
        assertEquals(1_200L, result.elapsedRealtimeAtSync)
    }

    @Test
    fun `une horloge juste donne un decalage nul`() {
        val time = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(
                originateTime = time,
                receiveTime = time + 125L,
                transmitTime = time + 125L
            ),
            host = HOST,
            requestTime = time,
            requestTicks = 0L,
            responseTicks = 250L
        )

        val result = assertIs<SntpClient.Outcome.Success>(outcome).result
        assertEquals(0L, result.offsetMillis)
        assertEquals(250L, result.roundTripMillis)
    }

    @Test
    fun `un aller-retour incoherent est ramene a zero`() {
        val time = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(
                originateTime = time,
                receiveTime = time,
                transmitTime = time + 500L
            ),
            host = HOST,
            requestTime = time,
            requestTicks = 0L,
            responseTicks = 10L
        )

        assertEquals(0L, assertIs<SntpClient.Outcome.Success>(outcome).result.roundTripMillis)
    }

    @Test
    fun `un paquet en mode client est rejete`() {
        val time = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(time, time, time, mode = 3),
            host = HOST,
            requestTime = time,
            requestTicks = 0L,
            responseTicks = 10L
        )

        val failure = assertIs<SntpClient.Outcome.Failure>(outcome)
        assertEquals(SntpClient.FailureReason.INVALID_RESPONSE, failure.reason)
        assertEquals(HOST, failure.server)
    }

    @Test
    fun `un serveur non synchronise est rejete`() {
        val time = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(time, time, time, stratum = 0),
            host = HOST,
            requestTime = time,
            requestTicks = 0L,
            responseTicks = 10L
        )

        assertEquals(
            SntpClient.FailureReason.INVALID_RESPONSE,
            assertIs<SntpClient.Outcome.Failure>(outcome).reason
        )
    }

    @Test
    fun `un stratum reserve est rejete`() {
        val time = 1_700_000_000_000L

        val outcome = SntpClient.evaluate(
            buffer = serverResponse(time, time, time, stratum = 16),
            host = HOST,
            requestTime = time,
            requestTicks = 0L,
            responseTicks = 10L
        )

        assertEquals(
            SntpClient.FailureReason.INVALID_RESPONSE,
            assertIs<SntpClient.Outcome.Failure>(outcome).reason
        )
    }

    @Test
    fun `un paquet vide est rejete`() {
        val outcome = SntpClient.evaluate(
            buffer = ByteArray(48),
            host = HOST,
            requestTime = 1_700_000_000_000L,
            requestTicks = 0L,
            responseTicks = 10L
        )

        assertEquals(
            SntpClient.FailureReason.INVALID_RESPONSE,
            assertIs<SntpClient.Outcome.Failure>(outcome).reason
        )
    }
}
