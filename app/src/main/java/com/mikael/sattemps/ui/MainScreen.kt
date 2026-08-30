package com.mikael.sattemps.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikael.sattemps.MainViewModel
import com.mikael.sattemps.geo.DishPointing
import com.mikael.sattemps.gnss.GnssState
import com.mikael.sattemps.network.MobileState
import com.mikael.sattemps.network.ScanState
import com.mikael.sattemps.sensors.CompassState
import com.mikael.sattemps.time.TimeSyncState
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

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
    val titles = listOf("Heure", "Ciel GPS", "Parabole", "Reseau")

    Scaffold(
        topBar = { TopAppBar(title = { Text("SatTemps — heure et orientation") }) }
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Heure exacte") {
            Text(
                text = zoned.format(TIME_FORMAT),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = zoned.format(DATE_FORMAT).replaceFirstChar { it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("UTC", utc.format(TIME_FORMAT))
            InfoRow("Fuseau", ZoneId.systemDefault().id)
            InfoRow("Source", time.source.name)
            InfoRow("Ecart horloge du telephone", "${time.offsetMillis} ms")
            InfoRow("Incertitude", "± ${time.uncertaintyMillis} ms")
            time.ntpServer?.let { InfoRow("Serveur NTP", "$it (aller-retour ${time.ntpRoundTripMillis} ms)") }
            time.gnssOffsetMillis?.let { InfoRow("Ecart mesure par GPS", "$it ms") }
            time.ageSeconds?.let { InfoRow("Derniere synchro", "il y a $it s") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSync) { Text("Resynchroniser maintenant") }
        }

        SectionCard("Position GPS") {
            val location = gnss.location
            if (location == null) {
                Text("Recherche de la position... sortez a ciel ouvert.")
            } else {
                InfoRow("Latitude", String.format(Locale.FRENCH, "%.6f°", location.latitude))
                InfoRow("Longitude", String.format(Locale.FRENCH, "%.6f°", location.longitude))
                InfoRow("Altitude", String.format(Locale.FRENCH, "%.0f m", location.altitude))
                InfoRow("Precision", String.format(Locale.FRENCH, "%.1f m", location.accuracy))
                InfoRow("Satellites utilises", "${gnss.satellitesUsed} / ${gnss.satellites.size}")
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
            targetLabel = "Meilleur ciel"
        )

        SectionCard("Meilleure orientation GPS") {
            InfoRow("Cap du telephone", formatAzimuth(compass.trueHeadingDeg.toDouble()))
            if (bestSky != null) {
                InfoRow("Meilleure direction (ciel degage)", formatAzimuth(bestSky.toDouble()))
                InfoRow("Rotation a effectuer", formatTurn(compass.trueHeadingDeg, bestSky))
            } else {
                Text("Aucun satellite recu pour le moment.")
            }
            best?.let {
                InfoRow(
                    "Meilleur satellite",
                    "${it.constellation} #${it.svid} · ${it.cn0DbHz.roundToInt()} dB-Hz · " +
                        "az ${it.azimuthDeg.roundToInt()}° el ${it.elevationDeg.roundToInt()}°"
                )
            }
            InfoRow("Declinaison magnetique", String.format(Locale.FRENCH, "%.1f°", compass.declinationDeg))
        }

        SectionCard("Satellites recus (${gnss.satellites.size})") {
            gnss.satellites.take(20).forEach { satellite ->
                InfoRow(
                    "${satellite.constellation} #${satellite.svid}${if (satellite.usedInFix) " ✓" else ""}",
                    "az ${satellite.azimuthDeg.roundToInt()}° · el ${satellite.elevationDeg.roundToInt()}° · " +
                        "${satellite.cn0DbHz.roundToInt()} dB-Hz"
                )
            }
            if (gnss.satellites.isEmpty()) Text("En attente du recepteur GNSS...")
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
            SectionCard("Parabole satellite") { Text("Position GPS requise pour calculer la visee.") }
            return@Column
        }

        SkyView(
            satellites = gnss.satellites,
            headingDeg = compass.trueHeadingDeg,
            targetAzimuthDeg = best?.azimuthDeg?.toFloat(),
            targetLabel = best?.satellite?.name
        )

        SectionCard("Meilleure visee geostationnaire") {
            if (best == null) {
                Text("Aucun satellite geostationnaire visible depuis cette position.")
            } else {
                InfoRow("Satellite", best.satellite.name)
                InfoRow("Azimut (nord vrai)", formatAzimuth(best.azimuthDeg))
                InfoRow("Elevation", String.format(Locale.FRENCH, "%.1f°", best.elevationDeg))
                InfoRow("Inclinaison LNB", String.format(Locale.FRENCH, "%.1f°", best.lnbSkewDeg))
                InfoRow("Distance", String.format(Locale.FRENCH, "%.0f km", best.rangeKm))
                InfoRow("Rotation a effectuer", formatTurn(compass.trueHeadingDeg, best.azimuthDeg.toFloat()))
            }
        }

        SectionCard("Tous les satellites") {
            dishes.forEach { pointing ->
                InfoRow(
                    pointing.satellite.name,
                    if (pointing.visible) {
                        String.format(
                            Locale.FRENCH, "az %.1f° · el %.1f° · LNB %.1f°",
                            pointing.azimuthDeg, pointing.elevationDeg, pointing.lnbSkewDeg
                        )
                    } else {
                        "sous l'horizon"
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Reseau mobile") {
                mobile.error?.let { Text(it) }
                InfoRow("Operateur", mobile.operator.ifEmpty { "-" })
                InfoRow("Technologie", mobile.networkType)
                InfoRow("5G NR", if (mobile.nrConnected) "connectee" else "non connectee")
                val serving = mobile.serving
                if (serving != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    InfoRow("Cellule servante", serving.technology)
                    InfoRow("Puissance", serving.dbm?.let { "$it dBm" } ?: "-")
                    serving.quality?.let { InfoRow("Qualite (RSRQ)", "$it dB") }
                    serving.sinr?.let { InfoRow("SINR", "$it dB") }
                    serving.bands?.let { InfoRow("Bandes", it) }
                    InfoRow("Identite", serving.identity)
                }
            }
        }

        item {
            SectionCard("Orientation de l'antenne relais") {
                Text(
                    "Lancez la mesure puis tournez lentement sur vous meme (360°) en gardant " +
                        "le telephone devant vous : l'application retient la direction ou le signal est le plus fort."
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Cap actuel", formatAzimuth(compass.trueHeadingDeg.toDouble()))
                InfoRow("Couverture de la mesure", "${scan.coveragePercent} %")
                LinearProgressIndicator(
                    progress = { scan.coveragePercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
                scan.bestSector?.let { sector ->
                    InfoRow("Meilleure direction", formatAzimuth(sector.azimuthDeg.toDouble()))
                    InfoRow(
                        "Signal dans ce secteur",
                        String.format(Locale.FRENCH, "%.1f dBm (max %d dBm)", sector.meanDbm, sector.bestDbm)
                    )
                    InfoRow("Rotation a effectuer", formatTurn(compass.trueHeadingDeg, sector.azimuthDeg))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onToggleScan) {
                    Text(if (scan.running) "Arreter la mesure" else "Demarrer la mesure 360°")
                }
            }
        }

        if (scan.sectors.isNotEmpty()) {
            item {
                SectionCard("Signal par secteur") {
                    scan.sectors.sortedByDescending { it.meanDbm }.forEach { sector ->
                        InfoRow(
                            formatAzimuth(sector.azimuthDeg.toDouble()),
                            String.format(
                                Locale.FRENCH, "%.1f dBm · %d mesures", sector.meanDbm, sector.sampleCount
                            )
                        )
                    }
                }
            }
        }

        items(mobile.cells) { cell ->
            SectionCard(cell.technology + if (cell.registered) " (servante)" else " (voisine)") {
                InfoRow("Puissance", cell.dbm?.let { "$it dBm" } ?: "-")
                cell.quality?.let { InfoRow("Qualite", "$it dB") }
                cell.sinr?.let { InfoRow("SINR", "$it dB") }
                cell.bands?.let { InfoRow("Bandes", it) }
                InfoRow("Identite", cell.identity)
            }
        }
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

private fun formatAzimuth(azimuthDeg: Double): String {
    val normalized = ((azimuthDeg % 360) + 360) % 360
    val cardinals = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    val cardinal = cardinals[((normalized + 11.25) / 22.5).toInt() % 16]
    return String.format(Locale.FRENCH, "%.1f° (%s)", normalized, cardinal)
}

private fun formatTurn(currentDeg: Float, targetDeg: Float): String {
    var delta = ((targetDeg - currentDeg + 540f) % 360f) - 180f
    if (abs(delta) < 1f) return "aligne"
    val direction = if (delta > 0) "vers la droite" else "vers la gauche"
    delta = abs(delta)
    return String.format(Locale.FRENCH, "%.0f° %s", delta, direction)
}
