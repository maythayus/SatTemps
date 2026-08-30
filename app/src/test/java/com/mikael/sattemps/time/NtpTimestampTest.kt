package com.mikael.sattemps.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NtpTimestampTest {

    @Test
    fun `l'ecriture puis la lecture restituent l'instant a la milliseconde`() {
        val buffer = ByteArray(48)
        val time = 1_724_930_123_456L

        NtpTimestamp.write(buffer, 40, time)

        // La fraction NTP est tronquee : au plus une milliseconde d'ecart.
        assertTrue(kotlin.math.abs(NtpTimestamp.read(buffer, 40) - time) <= 1L)
    }

    @Test
    fun `l'epoque Unix correspond au decalage NTP de 1900`() {
        val buffer = ByteArray(16)

        NtpTimestamp.write(buffer, 0, 0L)

        val seconds = (0 until 4).fold(0L) { acc, i ->
            (acc shl 8) or (buffer[i].toLong() and 0xFF)
        }
        assertEquals(NtpTimestamp.OFFSET_1900_TO_1970, seconds)
        assertEquals(0L, NtpTimestamp.read(buffer, 0))
    }

    @Test
    fun `la fraction de seconde est encodee sur les 32 bits de poids faible`() {
        val buffer = ByteArray(16)

        NtpTimestamp.write(buffer, 0, 1_000L + 500L)

        // 0,5 s correspond au bit de poids fort de la fraction.
        assertEquals(0x80.toByte(), buffer[4])
        assertEquals(1_500L, NtpTimestamp.read(buffer, 0))
    }

    @Test
    fun `un horodatage non ecrit est lu comme anterieur a l'epoque Unix`() {
        assertTrue(NtpTimestamp.read(ByteArray(48), 40) < 0L)
    }

    @Test
    fun `plusieurs horodatages coexistent dans le meme paquet`() {
        val buffer = ByteArray(48)

        NtpTimestamp.write(buffer, SntpClient.RECEIVE_OFFSET, 1_000L)
        NtpTimestamp.write(buffer, SntpClient.TRANSMIT_OFFSET, 2_000L)

        assertEquals(1_000L, NtpTimestamp.read(buffer, SntpClient.RECEIVE_OFFSET))
        assertEquals(2_000L, NtpTimestamp.read(buffer, SntpClient.TRANSMIT_OFFSET))
    }
}
