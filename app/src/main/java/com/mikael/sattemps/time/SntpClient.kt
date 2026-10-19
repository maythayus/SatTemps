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
    private const val NTP_MODE_SERVER = 4
    private const val NTP_VERSION = 3
    private const val MAX_STRATUM = 15

    data class Result(
        /** Decalage a ajouter a System.currentTimeMillis() pour obtenir le temps NTP. */
        val offsetMillis: Long,
        /** Temps aller-retour reseau, borne superieure de l'incertitude. */
        val roundTripMillis: Long,
        val server: String,
        val elapsedRealtimeAtSync: Long
    )

    /** Nature de l'echec, traduite dans l'interface. */
    enum class FailureReason { UNREACHABLE, INVALID_RESPONSE }

    /** Resultat d'une interrogation : mesure exploitable ou motif d'echec. */
    sealed interface Outcome {
        data class Success(val result: Result) : Outcome
        data class Failure(
            val server: String,
            val reason: FailureReason,
            /** Detail technique (message d'exception), utile au diagnostic. */
            val detail: String? = null
        ) : Outcome
    }

    /**
     * Interroge [host] en conservant le motif d'echec, afin de pouvoir l'afficher a
     * l'utilisateur (serveur injoignable, reponse invalide).
     */
    fun query(host: String, timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): Outcome {
        var socket: DatagramSocket? = null
        return try {
            val address = InetAddress.getByName(host)
            socket = DatagramSocket().apply { soTimeout = timeoutMillis }
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            NtpTimestamp.write(buffer, TRANSMIT_OFFSET, requestTime)

            socket.send(DatagramPacket(buffer, buffer.size, address, NTP_PORT))
            socket.receive(DatagramPacket(buffer, buffer.size))

            evaluate(buffer, host, requestTime, requestTicks, SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            Outcome.Failure(
                server = host,
                reason = FailureReason.UNREACHABLE,
                detail = e.message ?: e.javaClass.simpleName
            )
        } finally {
            socket?.close()
        }
    }

    /**
     * Valide la reponse NTP et en deduit le decalage d'horloge.
     *
     * Les temps monotones sont fournis par l'appelant, ce qui rend le calcul
     * independant d'Android et donc testable : [requestTicks] et [responseTicks]
     * encadrent l'echange reseau, [requestTime] est l'heure systeme au depart.
     */
    internal fun evaluate(
        buffer: ByteArray,
        host: String,
        requestTime: Long,
        requestTicks: Long,
        responseTicks: Long
    ): Outcome {
        val mode = buffer[0].toInt() and 0x7
        val stratum = buffer[1].toInt() and 0xFF
        val originateTime = NtpTimestamp.read(buffer, ORIGINATE_OFFSET)
        val receiveTime = NtpTimestamp.read(buffer, RECEIVE_OFFSET)
        val transmitTime = NtpTimestamp.read(buffer, TRANSMIT_OFFSET)

        if (mode != NTP_MODE_SERVER || stratum == 0 || stratum > MAX_STRATUM || transmitTime <= 0L) {
            return Outcome.Failure(server = host, reason = FailureReason.INVALID_RESPONSE)
        }

        val responseTime = requestTime + (responseTicks - requestTicks)
        val roundTrip = (responseTicks - requestTicks) - (transmitTime - receiveTime)
        val offset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2

        return Outcome.Success(
            Result(
                offsetMillis = offset,
                roundTripMillis = roundTrip.coerceAtLeast(0),
                server = host,
                elapsedRealtimeAtSync = responseTicks
            )
        )
    }

    internal const val DEFAULT_TIMEOUT_MILLIS = 4000
    internal const val ORIGINATE_OFFSET = 24
    internal const val RECEIVE_OFFSET = 32
    internal const val TRANSMIT_OFFSET = 40
}
