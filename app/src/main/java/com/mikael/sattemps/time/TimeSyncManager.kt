package com.mikael.sattemps.time

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TimeSource { SYSTEME, NTP, GNSS }

data class TimeSyncState(
    val source: TimeSource = TimeSource.SYSTEME,
    /** Decalage a ajouter a l'horloge systeme pour obtenir le temps de reference. */
    val offsetMillis: Long = 0L,
    /** Incertitude estimee de la reference en millisecondes. */
    val uncertaintyMillis: Long = 0L,
    val ntpOffsetMillis: Long? = null,
    val ntpRoundTripMillis: Long? = null,
    val ntpServer: String? = null,
    val gnssOffsetMillis: Long? = null,
    val lastSyncElapsedRealtime: Long? = null
) {
    /** Instant de reference courant, en millisecondes UTC. */
    fun nowMillis(): Long = System.currentTimeMillis() + offsetMillis

    val ageSeconds: Long?
        get() = lastSyncElapsedRealtime?.let { (SystemClock.elapsedRealtime() - it) / 1000 }
}

/**
 * Fournit un temps de reference precis : le temps GNSS (issu du fix satellite) est
 * prioritaire, sinon un serveur NTP, sinon l'horloge du telephone.
 */
class TimeSyncManager(private val scope: CoroutineScope) {

    private val servers = listOf("time.google.com", "pool.ntp.org", "time.cloudflare.com")

    private val _state = MutableStateFlow(TimeSyncState())
    val state: StateFlow<TimeSyncState> = _state.asStateFlow()

    init {
        scope.launch {
            while (true) {
                syncNtp()
                delay(5 * 60 * 1000L)
            }
        }
    }

    /** Decalage mesure a partir d'un fix GNSS (temps satellite - horloge systeme). */
    fun onGnssTimeOffset(offsetMillis: Long) {
        _state.value = recompute(
            _state.value.copy(
                gnssOffsetMillis = offsetMillis,
                lastSyncElapsedRealtime = SystemClock.elapsedRealtime()
            )
        )
    }

    fun forceNtpSync() {
        scope.launch { syncNtp() }
    }

    private suspend fun syncNtp() {
        val result = withContext(Dispatchers.IO) {
            servers.firstNotNullOfOrNull { SntpClient.request(it) }
        } ?: return
        _state.value = recompute(
            _state.value.copy(
                ntpOffsetMillis = result.offsetMillis,
                ntpRoundTripMillis = result.roundTripMillis,
                ntpServer = result.server,
                lastSyncElapsedRealtime = result.elapsedRealtimeAtSync
            )
        )
    }

    private fun recompute(state: TimeSyncState): TimeSyncState = when {
        state.gnssOffsetMillis != null -> state.copy(
            source = TimeSource.GNSS,
            offsetMillis = state.gnssOffsetMillis,
            uncertaintyMillis = 50L
        )

        state.ntpOffsetMillis != null -> state.copy(
            source = TimeSource.NTP,
            offsetMillis = state.ntpOffsetMillis,
            uncertaintyMillis = ((state.ntpRoundTripMillis ?: 0L) / 2).coerceAtLeast(1L)
        )

        else -> state.copy(source = TimeSource.SYSTEME, offsetMillis = 0L, uncertaintyMillis = 1000L)
    }
}
