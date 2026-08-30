package com.mikael.sattemps.time

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Client NTP (SNTP) minimal : interroge un serveur de temps et calcule le decalage
 * entre l'horloge systeme et le temps de reference.
 */
object SntpClient {

    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val OFFSET_1900_TO_1970 = 2208988800L

    data class Result(
        /** Decalage a ajouter a System.currentTimeMillis() pour obtenir le temps NTP. */
        val offsetMillis: Long,
        /** Temps aller-retour reseau, borne superieure de l'incertitude. */
        val roundTripMillis: Long,
        val server: String,
        val elapsedRealtimeAtSync: Long
    )

    fun request(host: String, timeoutMillis: Int = 4000): Result? {
        var socket: DatagramSocket? = null
        return try {
            val address = InetAddress.getByName(host)
            socket = DatagramSocket().apply { soTimeout = timeoutMillis }
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            writeTimestamp(buffer, 40, requestTime)

            socket.send(DatagramPacket(buffer, buffer.size, address, NTP_PORT))
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val responseTicks = SystemClock.elapsedRealtime()
            val responseTime = requestTime + (responseTicks - requestTicks)

            val originateTime = readTimestamp(buffer, 24)
            val receiveTime = readTimestamp(buffer, 32)
            val transmitTime = readTimestamp(buffer, 40)

            val roundTrip = (responseTicks - requestTicks) - (transmitTime - receiveTime)
            val offset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2

            Result(
                offsetMillis = offset,
                roundTripMillis = roundTrip.coerceAtLeast(0),
                server = host,
                elapsedRealtimeAtSync = responseTicks
            )
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = readUnsigned32(buffer, offset)
        val fraction = readUnsigned32(buffer, offset + 4)
        return (seconds - OFFSET_1900_TO_1970) * 1000L + (fraction * 1000L) / 0x100000000L
    }

    private fun writeTimestamp(buffer: ByteArray, offset: Int, time: Long) {
        var seconds = time / 1000L + OFFSET_1900_TO_1970
        val milliseconds = time % 1000L
        for (i in 3 downTo 0) {
            buffer[offset + i] = (seconds and 0xFF).toByte()
            seconds = seconds shr 8
        }
        var fraction = milliseconds * 0x100000000L / 1000L
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
