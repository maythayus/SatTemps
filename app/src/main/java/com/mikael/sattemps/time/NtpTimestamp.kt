package com.mikael.sattemps.time

/**
 * Encodage des horodatages NTP (RFC 5905) : 64 bits, 32 bits de secondes depuis 1900
 * suivis de 32 bits de fraction de seconde.
 *
 * Isole du client reseau pour pouvoir etre verifie par des tests unitaires.
 */
internal object NtpTimestamp {

    /** Secondes entre l'epoque NTP (1900-01-01) et l'epoque Unix (1970-01-01). */
    const val OFFSET_1900_TO_1970 = 2208988800L

    private const val FRACTION_SCALE = 0x100000000L

    /** Lit un horodatage NTP et le convertit en millisecondes UTC depuis l'epoque Unix. */
    fun read(buffer: ByteArray, offset: Int): Long {
        val seconds = readUnsigned32(buffer, offset)
        val fraction = readUnsigned32(buffer, offset + 4)
        return (seconds - OFFSET_1900_TO_1970) * 1000L + (fraction * 1000L) / FRACTION_SCALE
    }

    /** Ecrit un instant Unix (millisecondes) au format NTP dans le tampon. */
    fun write(buffer: ByteArray, offset: Int, timeMillis: Long) {
        var seconds = timeMillis / 1000L + OFFSET_1900_TO_1970
        val milliseconds = timeMillis % 1000L
        for (i in 3 downTo 0) {
            buffer[offset + i] = (seconds and 0xFF).toByte()
            seconds = seconds shr 8
        }
        var fraction = milliseconds * FRACTION_SCALE / 1000L
        for (i in 3 downTo 0) {
            buffer[offset + 4 + i] = (fraction and 0xFF).toByte()
            fraction = fraction shr 8
        }
    }

    private fun readUnsigned32(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        return value
    }
}
