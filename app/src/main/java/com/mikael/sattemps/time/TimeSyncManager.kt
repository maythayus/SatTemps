package com.mikael.sattemps.time

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Origine du temps affiche, du plus precis au moins precis. */
enum class TimeSource { SYSTEME, NTP, GNSS, GNSS_ATOMIQUE }

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
    /** Decalage issu de l'horloge brute du recepteur GNSS (`GnssClock`). */
    val gnssClockOffsetMillis: Long? = null,
    val gnssClockUncertaintyNanos: Long? = null,
    val gpsMinusUtcSeconds: Int? = null,
    val lastSyncElapsedRealtime: Long? = null,
    /** Synchronisation NTP en cours : permet d'afficher un etat de chargement. */
    val syncing: Boolean = false,
    /** Dernier echec de synchronisation NTP, `null` si tout va bien. */
    val ntpFailure: SntpClient.Outcome.Failure? = null
) {
    /** Instant de reference courant, en millisecondes UTC. */
    fun nowMillis(): Long = System.currentTimeMillis() + offsetMillis

    val ageSeconds: Long?
        get() = lastSyncElapsedRealtime?.let { (SystemClock.elapsedRealtime() - it) / 1000 }
}

/**
 * Choisit la meilleure source de temps disponible et l'incertitude associee.
 *
 * Fonction pure, sans dependance Android, afin d'etre couverte par des tests unitaires.
 */
internal fun resolveTimeSource(state: TimeSyncState): TimeSyncState = when {
    state.gnssClockOffsetMillis != null -> state.copy(
        source = TimeSource.GNSS_ATOMIQUE,
        offsetMillis = state.gnssClockOffsetMillis,
        uncertaintyMillis = clockUncertaintyMillis(state.gnssClockUncertaintyNanos)
    )

    state.gnssOffsetMillis != null -> state.copy(
        source = TimeSource.GNSS,
        offsetMillis = state.gnssOffsetMillis,
        uncertaintyMillis = GNSS_UNCERTAINTY_MILLIS
    )

    state.ntpOffsetMillis != null -> state.copy(
        source = TimeSource.NTP,
        offsetMillis = state.ntpOffsetMillis,
        uncertaintyMillis = ((state.ntpRoundTripMillis ?: 0L) / 2).coerceAtLeast(1L)
    )

    else -> state.copy(
        source = TimeSource.SYSTEME,
        offsetMillis = 0L,
        uncertaintyMillis = SYSTEM_UNCERTAINTY_MILLIS
    )
}

/** L'horloge systeme n'est lue qu'a la milliseconde : l'incertitude affichee ne descend pas dessous. */
private fun clockUncertaintyMillis(uncertaintyNanos: Long?): Long =
    (((uncertaintyNanos ?: 0L) + 999_999L) / 1_000_000L).coerceAtLeast(1L)

private const val GNSS_UNCERTAINTY_MILLIS = 50L
private const val SYSTEM_UNCERTAINTY_MILLIS = 1000L

/** 5 min si tout va bien, puis 15 s doublees a chaque echec consecutif. */
internal fun retryDelayMillis(consecutiveFailures: Int): Long =
    if (consecutiveFailures <= 0) {
        BASE_INTERVAL_MILLIS
    } else {
        15_000L shl (consecutiveFailures - 1)
    }

private const val BASE_INTERVAL_MILLIS = 5 * 60 * 1000L
internal const val MAX_BACKOFF_STEPS = 4

/**
 * Fournit un temps de reference precis : l'horloge brute du recepteur GNSS, puis le
 * temps du fix satellite, sinon un serveur NTP, sinon l'horloge du telephone.
 *
 * En cas d'echec reseau, les tentatives sont espacees progressivement pour ne pas
 * vider la batterie hors couverture.
 */
class TimeSyncManager(private val scope: CoroutineScope) {

    private val servers = listOf("time.google.com", "pool.ntp.org", "time.cloudflare.com")

    private val _state = MutableStateFlow(TimeSyncState())
    val state: StateFlow<TimeSyncState> = _state.asStateFlow()

    init {
        scope.launch {
            var failures = 0
            while (isActive) {
                val ok = syncNtp()
                failures = if (ok) 0 else (failures + 1).coerceAtMost(MAX_BACKOFF_STEPS)
                delay(retryDelayMillis(failures))
            }
        }
    }

    /** Decalage mesure a partir d'un fix GNSS (temps satellite - horloge systeme). */
    fun onGnssTimeOffset(offsetMillis: Long) {
        _state.value = resolveTimeSource(
            _state.value.copy(
                gnssOffsetMillis = offsetMillis,
                lastSyncElapsedRealtime = SystemClock.elapsedRealtime()
            )
        )
    }

    /** Le fix GNSS est perdu ou trop ancien : on retombe sur NTP ou l'horloge systeme. */
    fun onGnssTimeLost() {
        if (_state.value.gnssOffsetMillis == null) return
        _state.value = resolveTimeSource(_state.value.copy(gnssOffsetMillis = null))
    }

    /** Decalage mesure par l'horloge brute du recepteur GNSS, la reference la plus precise. */
    fun onGnssClockOffset(offsetMillis: Long, uncertaintyNanos: Long, gpsMinusUtcSeconds: Int) {
        _state.value = resolveTimeSource(
            _state.value.copy(
                gnssClockOffsetMillis = offsetMillis,
                gnssClockUncertaintyNanos = uncertaintyNanos,
                gpsMinusUtcSeconds = gpsMinusUtcSeconds,
                lastSyncElapsedRealtime = SystemClock.elapsedRealtime()
            )
        )
    }

    /** Plus de mesures brutes recentes : on retombe sur le fix GNSS, NTP ou l'horloge systeme. */
    fun onGnssClockLost() {
        if (_state.value.gnssClockOffsetMillis == null) return
        _state.value = resolveTimeSource(
            _state.value.copy(gnssClockOffsetMillis = null, gnssClockUncertaintyNanos = null)
        )
    }

    fun forceNtpSync() {
        scope.launch { syncNtp() }
    }

    /** Interroge les serveurs NTP tour a tour ; retourne `true` des qu'une reponse est valide. */
    private suspend fun syncNtp(): Boolean {
        if (_state.value.syncing) return false
        _state.value = _state.value.copy(syncing = true)
        val outcomes = withContext(Dispatchers.IO) {
            val results = mutableListOf<SntpClient.Outcome>()
            for (server in servers) {
                val outcome = SntpClient.query(server)
                results += outcome
                if (outcome is SntpClient.Outcome.Success) break
            }
            results
        }
        val success = outcomes.filterIsInstance<SntpClient.Outcome.Success>().firstOrNull()

        _state.value = if (success != null) {
            val result = success.result
            resolveTimeSource(
                _state.value.copy(
                    ntpOffsetMillis = result.offsetMillis,
                    ntpRoundTripMillis = result.roundTripMillis,
                    ntpServer = result.server,
                    lastSyncElapsedRealtime = result.elapsedRealtimeAtSync,
                    syncing = false,
                    ntpFailure = null
                )
            )
        } else {
            _state.value.copy(
                syncing = false,
                ntpFailure = outcomes.filterIsInstance<SntpClient.Outcome.Failure>().lastOrNull()
            )
        }
        return success != null
    }

}
