package com.mikael.sattemps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikael.sattemps.MainViewModel
import com.mikael.sattemps.R
import com.mikael.sattemps.geo.DishPointing
import com.mikael.sattemps.gnss.GnssIssue
import com.mikael.sattemps.gnss.GnssState
import com.mikael.sattemps.gnss.RawClockStatus
import com.mikael.sattemps.network.MobileIssue
import com.mikael.sattemps.network.MobileState
import com.mikael.sattemps.network.ScanState
import com.mikael.sattemps.sensors.CompassState
import com.mikael.sattemps.time.SntpClient
import com.mikael.sattemps.time.TimeSource
import com.mikael.sattemps.time.TimeSyncState
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val now by viewModel.nowMillis.collectAsStateWithLifecycle()
    val timeState by viewModel.timeState.collectAsStateWithLifecycle()
    val gnss by viewModel.gnssState.collectAsStateWithLifecycle()
    val compass by viewModel.compassState.collectAsStateWithLifecycle()
    val mobile by viewModel.mobileState.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val dishes by viewModel.dishPointings.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.tab_time),
        stringResource(R.string.tab_sky),
        stringResource(R.string.tab_dish),
        stringResource(R.string.tab_network)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_title)) }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> TimeTab(now, timeState, gnss, viewModel::forceTimeSync)
                1 -> SkyTab(gnss, compass)
                2 -> DishTab(dishes, compass, gnss)
                else -> NetworkTab(mobile, compass, scan, viewModel::toggleScan)
            }
        }
    }
}

@Composable
private fun TimeTab(
    nowMillis: Long,
    time: TimeSyncState,
    gnss: GnssState,
    onSync: () -> Unit
) {
    val zoned = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
    val utc = Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.UTC)
    val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(stringResource(R.string.section_exact_time)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontSize = clockFontSize(maxWidth.value)
                Text(
                    text = zoned.format(TIME_FORMAT),
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.2f,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = zoned.format(dateFormat).replaceFirstChar { it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(stringResource(R.string.label_utc), utc.format(TIME_FORMAT))
            InfoRow(stringResource(R.string.label_timezone), ZoneId.systemDefault().id)
            InfoRow(stringResource(R.string.label_source), timeSourceText(time.source))
            InfoRow(
                stringResource(R.string.label_clock_offset),
                stringResource(R.string.value_millis, time.offsetMillis)
            )
            InfoRow(
                stringResource(R.string.label_uncertainty),
                stringResource(R.string.value_uncertainty, time.uncertaintyMillis)
            )
            time.gnssClockUncertaintyNanos?.let {
                InfoRow(stringResource(R.string.label_gnss_clock_uncertainty), formatNanos(it))
            }
            time.gpsMinusUtcSeconds?.let {
                InfoRow(
                    stringResource(R.string.label_gps_minus_utc),
                    stringResource(R.string.value_seconds, it)
                )
            }
            InfoRow(stringResource(R.string.label_raw_clock), rawClockText(gnss.rawClock))
            gnss.rawSignalCount?.let {
                InfoRow(stringResource(R.string.label_raw_signals), it.toString())
            }
            time.ntpServer?.let { server ->
                InfoRow(
                    stringResource(R.string.label_ntp_server),
                    stringResource(
                        R.string.value_ntp_server, server, time.ntpRoundTripMillis ?: 0L
                    )
                )
            }
            time.gnssOffsetMillis?.let {
                InfoRow(
                    stringResource(R.string.label_gps_offset),
                    stringResource(R.string.value_millis, it)
                )
            }
            time.ageSeconds?.let {
                InfoRow(
                    stringResource(R.string.label_last_sync),
                    stringResource(R.string.value_seconds_ago, it)
                )
            }
            time.ntpFailure?.let { Text(stringResource(R.string.ntp_error, ntpFailureText(it))) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSync, enabled = !time.syncing) {
                Text(
                    stringResource(
                        if (time.syncing) R.string.action_syncing else R.string.action_resync
                    )
                )
            }
        }

        SectionCard(stringResource(R.string.section_gps_position)) {
            val location = gnss.location
            gnss.issue?.let { Text(gnssIssueText(it, gnss.fixAgeSeconds)) }
            if (location == null) {
                if (gnss.issue == null) Text(stringResource(R.string.gnss_searching))
            } else {
                InfoRow(stringResource(R.string.label_latitude), formatDegrees(location.latitude, 6))
                InfoRow(
                    stringResource(R.string.label_longitude),
                    formatDegrees(location.longitude, 6)
                )
                InfoRow(
                    stringResource(R.string.label_altitude),
                    String.format(Locale.getDefault(), "%.0f m", location.altitude)
                )
                InfoRow(
                    stringResource(R.string.label_accuracy),
                    String.format(Locale.getDefault(), "%.1f m", location.accuracy)
                )
                InfoRow(
                    stringResource(R.string.label_satellites_used),
                    stringResource(
                        R.string.value_satellite_ratio, gnss.satellitesUsed, gnss.satellites.size
                    )
                )
                gnss.fixAgeSeconds?.let {
                    InfoRow(
                        stringResource(R.string.label_fix_age),
                        stringResource(R.string.value_seconds, it)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkyTab(gnss: GnssState, compass: CompassState) {
    val best = gnss.bestSatellite
    val bestSky = gnss.bestSkyAzimuthDeg

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkyView(
            satellites = gnss.satellites,
            headingDeg = compass.trueHeadingDeg,
            targetAzimuthDeg = bestSky,
            targetLabel = stringResource(R.string.label_best_sky)
        )

        SectionCard(stringResource(R.string.section_best_gps_orientation)) {
            CompassWarning(compass)
            InfoRow(
                stringResource(R.string.label_phone_heading),
                formatAzimuth(compass.trueHeadingDeg.toDouble())
            )
            if (bestSky != null) {
                InfoRow(stringResource(R.string.label_best_sky), formatAzimuth(bestSky.toDouble()))
                InfoRow(
                    stringResource(R.string.label_turn),
                    formatTurn(compass.trueHeadingDeg, bestSky)
                )
            } else {
                Text(stringResource(R.string.gnss_no_satellite))
            }
            best?.let {
                InfoRow(
                    stringResource(R.string.label_best_satellite),
                    stringResource(
                        R.string.value_best_satellite,
                        it.constellation,
                        it.svid,
                        it.cn0DbHz.roundToInt(),
                        it.azimuthDeg.roundToInt(),
                        it.elevationDeg.roundToInt()
                    )
                )
            }
            InfoRow(
                stringResource(R.string.label_declination),
                formatDegrees(compass.declinationDeg.toDouble(), 1)
            )
        }

        SectionCard(stringResource(R.string.section_satellites, gnss.satellites.size)) {
            val usedMark = stringResource(R.string.value_used_in_fix)
            gnss.satellites.take(MAX_SATELLITE_ROWS).forEach { satellite ->
                InfoRow(
                    "${satellite.constellation} #${satellite.svid}" +
                        if (satellite.usedInFix) " $usedMark" else "",
                    stringResource(
                        R.string.value_satellite_row,
                        satellite.azimuthDeg.roundToInt(),
                        satellite.elevationDeg.roundToInt(),
                        satellite.cn0DbHz.roundToInt()
                    )
                )
            }
            if (gnss.satellites.isEmpty()) Text(stringResource(R.string.gnss_waiting_receiver))
        }
    }
}

@Composable
private fun DishTab(dishes: List<DishPointing>, compass: CompassState, gnss: GnssState) {
    val best = dishes.firstOrNull { it.visible }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (gnss.location == null) {
            SectionCard(stringResource(R.string.section_dish)) {
                Text(stringResource(R.string.dish_needs_position))
                gnss.issue?.let { Text(gnssIssueText(it, gnss.fixAgeSeconds)) }
            }
            return@Column
        }

        SkyView(
            satellites = gnss.satellites,
            headingDeg = compass.trueHeadingDeg,
            targetAzimuthDeg = best?.azimuthDeg?.toFloat(),
            targetLabel = best?.satellite?.name
        )

        SectionCard(stringResource(R.string.section_dish_best)) {
            if (best == null) {
                Text(stringResource(R.string.dish_none_visible))
            } else {
                CompassWarning(compass)
                InfoRow(stringResource(R.string.label_satellite), best.satellite.name)
                InfoRow(
                    stringResource(R.string.label_azimuth_true),
                    formatAzimuth(best.azimuthDeg)
                )
                InfoRow(
                    stringResource(R.string.label_elevation),
                    formatDegrees(best.elevationDeg, 1)
                )
                InfoRow(
                    stringResource(R.string.label_lnb_skew),
                    formatDegrees(best.lnbSkewDeg, 1)
                )
                InfoRow(
                    stringResource(R.string.label_distance),
                    String.format(Locale.getDefault(), "%.0f km", best.rangeKm)
                )
                InfoRow(
                    stringResource(R.string.label_turn),
                    formatTurn(compass.trueHeadingDeg, best.azimuthDeg.toFloat())
                )
            }
        }

        SectionCard(stringResource(R.string.section_dish_all)) {
            val belowHorizon = stringResource(R.string.value_below_horizon)
            dishes.forEach { pointing ->
                InfoRow(
                    pointing.satellite.name,
                    if (pointing.visible) {
                        stringResource(
                            R.string.value_dish_row,
                            formatDegrees(pointing.azimuthDeg, 1),
                            formatDegrees(pointing.elevationDeg, 1),
                            formatDegrees(pointing.lnbSkewDeg, 1)
                        )
                    } else {
                        belowHorizon
                    }
                )
            }
        }
    }
}

@Composable
private fun NetworkTab(
    mobile: MobileState,
    compass: CompassState,
    scan: ScanState,
    onToggleScan: () -> Unit
) {
    val dash = stringResource(R.string.value_dash)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(stringResource(R.string.section_mobile)) {
                mobile.issue?.let { Text(mobileIssueText(it, mobile.errorDetail)) }
                InfoRow(stringResource(R.string.label_operator), mobile.operator.ifEmpty { dash })
                InfoRow(
                    stringResource(R.string.label_technology),
                    mobile.networkType.ifEmpty { dash }
                )
                InfoRow(
                    stringResource(R.string.label_5g),
                    stringResource(
                        if (mobile.nrConnected) {
                            R.string.value_connected
                        } else {
                            R.string.value_not_connected
                        }
                    )
                )
                val serving = mobile.serving
                if (serving != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    InfoRow(stringResource(R.string.label_serving_cell), serving.technology)
                    InfoRow(
                        stringResource(R.string.label_power),
                        serving.dbm?.let { stringResource(R.string.value_dbm, it) } ?: dash
                    )
                    serving.quality?.let {
                        InfoRow(
                            stringResource(R.string.label_quality_rsrq),
                            stringResource(R.string.value_db, it)
                        )
                    }
                    serving.sinr?.let {
                        InfoRow(
                            stringResource(R.string.label_sinr),
                            stringResource(R.string.value_db, it)
                        )
                    }
                    serving.bands?.let { InfoRow(stringResource(R.string.label_bands), it) }
                    InfoRow(stringResource(R.string.label_identity), serving.identity)
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.section_antenna_direction)) {
                Text(stringResource(R.string.scan_help))
                CompassWarning(compass)
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    stringResource(R.string.label_current_heading),
                    formatAzimuth(compass.trueHeadingDeg.toDouble())
                )
                InfoRow(
                    stringResource(R.string.label_scan_coverage),
                    "${scan.coveragePercent} %"
                )
                LinearProgressIndicator(
                    progress = { scan.coveragePercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
                scan.bestSector?.let { sector ->
                    InfoRow(
                        stringResource(R.string.label_best_sky),
                        formatAzimuth(sector.azimuthDeg.toDouble())
                    )
                    InfoRow(
                        stringResource(R.string.label_sector_signal),
                        stringResource(
                            R.string.value_sector_mean,
                            formatDecimal(sector.meanDbm.toDouble(), 1),
                            sector.bestDbm
                        )
                    )
                    InfoRow(
                        stringResource(R.string.label_turn),
                        formatTurn(compass.trueHeadingDeg, sector.azimuthDeg)
                    )
                }
                if (scan.running && !scan.reliable) {
                    Text(
                        stringResource(
                            R.string.scan_need_coverage,
                            ScanState.MIN_RELIABLE_COVERAGE_PERCENT
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onToggleScan) {
                    Text(
                        stringResource(
                            if (scan.running) {
                                R.string.action_stop_scan
                            } else {
                                R.string.action_start_scan
                            }
                        )
                    )
                }
            }
        }

        if (scan.sectors.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.section_sector_signal)) {
                    scan.sectors.sortedByDescending { it.meanDbm }.forEach { sector ->
                        InfoRow(
                            formatAzimuth(sector.azimuthDeg.toDouble()),
                            stringResource(
                                R.string.value_sector_samples,
                                formatDecimal(sector.meanDbm.toDouble(), 1),
                                sector.sampleCount
                            )
                        )
                    }
                }
            }
        }

        items(mobile.cells) { cell ->
            val title = stringResource(
                if (cell.registered) R.string.cell_serving else R.string.cell_neighbour,
                cell.technology
            )
            SectionCard(title) {
                InfoRow(
                    stringResource(R.string.label_power),
                    cell.dbm?.let { stringResource(R.string.value_dbm, it) } ?: dash
                )
                cell.quality?.let {
                    InfoRow(
                        stringResource(R.string.label_quality),
                        stringResource(R.string.value_db, it)
                    )
                }
                cell.sinr?.let {
                    InfoRow(
                        stringResource(R.string.label_sinr),
                        stringResource(R.string.value_db, it)
                    )
                }
                cell.bands?.let { InfoRow(stringResource(R.string.label_bands), it) }
                InfoRow(stringResource(R.string.label_identity), cell.identity)
            }
        }
    }
}

/** Avertit lorsque la boussole est absente ou demande un etalonnage. */
@Composable
private fun CompassWarning(compass: CompassState) {
    when {
        !compass.available -> Text(stringResource(R.string.compass_unavailable))
        compass.needsCalibration -> Text(stringResource(R.string.compass_calibration))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Taille de l'heure pour que "HH:mm:ss.SSS" tienne sur une ligne, quelle que soit la
 * largeur de l'ecran ou la taille de police choisie dans les reglages.
 */
@Composable
private fun clockFontSize(availableWidthDp: Float): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    val fittingSp = availableWidthDp / (CLOCK_CHARACTERS * MONOSPACE_ADVANCE_EM) / fontScale
    return fittingSp.coerceAtMost(MAX_CLOCK_FONT_SP).sp
}

@Composable
private fun timeSourceText(source: TimeSource): String = when (source) {
    TimeSource.SYSTEME -> stringResource(R.string.source_system)
    TimeSource.NTP -> stringResource(R.string.source_ntp)
    TimeSource.GNSS -> stringResource(R.string.source_gnss)
    TimeSource.GNSS_ATOMIQUE -> stringResource(R.string.source_gnss_atomic)
}

@Composable
private fun rawClockText(status: RawClockStatus): String = when (status) {
    RawClockStatus.ACTIVE -> stringResource(R.string.raw_clock_active)
    RawClockStatus.WAITING -> stringResource(R.string.raw_clock_waiting)
    RawClockStatus.NO_GPS_TIME -> stringResource(R.string.raw_clock_no_gps_time)
    RawClockStatus.NO_ELAPSED_REALTIME -> stringResource(R.string.raw_clock_no_elapsed)
    RawClockStatus.NOT_SUPPORTED -> stringResource(R.string.raw_clock_not_supported)
    RawClockStatus.ANDROID_TOO_OLD -> stringResource(R.string.raw_clock_android_too_old)
}

@Composable
private fun formatNanos(nanos: Long): String = when {
    nanos < 1_000L -> stringResource(R.string.value_nanos, nanos)
    nanos < 1_000_000L -> stringResource(R.string.value_micros, formatDecimal(nanos / 1e3, 1))
    else -> stringResource(R.string.value_millis_decimal, formatDecimal(nanos / 1e6, 2))
}

@Composable
private fun gnssIssueText(issue: GnssIssue, fixAgeSeconds: Long?): String = when (issue) {
    GnssIssue.NO_SERVICE -> stringResource(R.string.gnss_no_service)
    GnssIssue.PERMISSION_DENIED -> stringResource(R.string.gnss_permission_denied)
    GnssIssue.RECEIVER_UNAVAILABLE -> stringResource(R.string.gnss_receiver_unavailable)
    GnssIssue.LOCATION_DISABLED -> stringResource(R.string.gnss_location_disabled)
    GnssIssue.SEARCHING -> stringResource(R.string.gnss_searching)
    GnssIssue.SIGNAL_LOST -> stringResource(R.string.gnss_signal_lost, fixAgeSeconds ?: 0L)
}

@Composable
private fun mobileIssueText(issue: MobileIssue, detail: String?): String = when (issue) {
    MobileIssue.NO_TELEPHONY -> stringResource(R.string.mobile_no_telephony)
    MobileIssue.PERMISSION_DENIED -> stringResource(R.string.mobile_permission_denied)
    MobileIssue.NO_CELL_INFO -> stringResource(R.string.mobile_no_cell_info)
    MobileIssue.READ_FAILED -> stringResource(
        R.string.mobile_read_failed,
        detail ?: stringResource(R.string.value_dash)
    )
}

@Composable
private fun ntpFailureText(failure: SntpClient.Outcome.Failure): String = when (failure.reason) {
    SntpClient.FailureReason.UNREACHABLE ->
        stringResource(R.string.ntp_unreachable, failure.server)

    SntpClient.FailureReason.INVALID_RESPONSE ->
        stringResource(R.string.ntp_invalid_response, failure.server)
}

@Composable
private fun formatAzimuth(azimuthDeg: Double): String {
    val cardinals = stringArrayResource(R.array.cardinal_points)
    val normalized = Azimuth.normalize(azimuthDeg)
    return stringResource(
        R.string.value_azimuth,
        formatDegrees(normalized, 1),
        cardinals[Azimuth.cardinalIndex(normalized)]
    )
}

@Composable
private fun formatTurn(currentDeg: Float, targetDeg: Float): String {
    if (Azimuth.isAligned(currentDeg, targetDeg)) return stringResource(R.string.value_aligned)
    val delta = Azimuth.turnDelta(currentDeg, targetDeg)
    return stringResource(
        if (delta > 0) R.string.value_turn_right else R.string.value_turn_left,
        abs(delta).roundToInt()
    )
}

private fun formatDegrees(value: Double, decimals: Int): String =
    formatDecimal(value, decimals) + "°"

private fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

private const val MAX_SATELLITE_ROWS = 20
private const val CLOCK_CHARACTERS = 12
/** Largeur d'un caractere monospace en gras, avec une marge de securite. */
private const val MONOSPACE_ADVANCE_EM = 0.66f
private const val MAX_CLOCK_FONT_SP = 56f
