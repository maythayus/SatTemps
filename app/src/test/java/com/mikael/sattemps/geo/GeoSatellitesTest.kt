package com.mikael.sattemps.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PARIS_LAT = 48.8566
private const val PARIS_LON = 2.3522

class GeoSatellitesTest {

    @Test
    fun `visee d'Astra 19_2E depuis Paris pointe vers le sud-est`() {
        val pointing = GeoSatellites.pointing(GeoSatellite("Astra", 19.2), PARIS_LAT, PARIS_LON)

        assertTrue(pointing.azimuthDeg in 150.0..165.0, "azimut ${pointing.azimuthDeg}")
        assertTrue(pointing.elevationDeg in 28.0..35.0, "elevation ${pointing.elevationDeg}")
        assertTrue(pointing.rangeKm in 37_000.0..40_000.0, "distance ${pointing.rangeKm}")
        assertTrue(pointing.visible)
    }

    @Test
    fun `un satellite a l'oppose du globe est sous l'horizon`() {
        val pointing = GeoSatellites.pointing(GeoSatellite("Test", -170.0), PARIS_LAT, PARIS_LON)

        assertTrue(pointing.elevationDeg < 0.0, "elevation ${pointing.elevationDeg}")
        assertFalse(pointing.visible)
    }

    @Test
    fun `un satellite a la meme longitude est plein sud depuis l'hemisphere nord`() {
        val pointing = GeoSatellites.pointing(GeoSatellite("Test", PARIS_LON), PARIS_LAT, PARIS_LON)

        assertEquals(180.0, pointing.azimuthDeg, 0.001)
        assertEquals(0.0, pointing.lnbSkewDeg, 0.001)
    }

    @Test
    fun `l'inclinaison LNB change de signe de part et d'autre du meridien local`() {
        val east = GeoSatellites.pointing(GeoSatellite("Est", 30.0), PARIS_LAT, PARIS_LON)
        val west = GeoSatellites.pointing(GeoSatellite("Ouest", -30.0), PARIS_LAT, PARIS_LON)

        assertTrue(east.lnbSkewDeg > 0.0, "est ${east.lnbSkewDeg}")
        assertTrue(west.lnbSkewDeg < 0.0, "ouest ${west.lnbSkewDeg}")
    }

    @Test
    fun `depuis l'equateur le satellite de meme longitude est au zenith`() {
        val pointing = GeoSatellites.pointing(GeoSatellite("Test", 0.0), 0.0, 0.0)

        assertEquals(90.0, pointing.elevationDeg, 0.001)
    }

    @Test
    fun `best retourne le satellite visible le plus haut`() {
        val best = assertNotNull(GeoSatellites.best(PARIS_LAT, PARIS_LON))
        val all = GeoSatellites.all(PARIS_LAT, PARIS_LON)

        assertTrue(best.visible)
        assertEquals(all.first { it.visible }.satellite.name, best.satellite.name)
    }

    @Test
    fun `all trie les satellites par elevation decroissante`() {
        val elevations = GeoSatellites.all(PARIS_LAT, PARIS_LON).map { it.elevationDeg }

        assertEquals(GeoSatellites.common.size, elevations.size)
        assertEquals(elevations.sortedDescending(), elevations)
    }

    @Test
    fun `l'altitude reduit legerement la distance oblique`() {
        val sol = GeoSatellites.pointing(GeoSatellite("Astra", 19.2), PARIS_LAT, PARIS_LON, 0.0)
        val montagne =
            GeoSatellites.pointing(GeoSatellite("Astra", 19.2), PARIS_LAT, PARIS_LON, 3000.0)

        assertTrue(montagne.rangeKm < sol.rangeKm)
        assertEquals(sol.azimuthDeg, montagne.azimuthDeg, 0.001)
    }

    @Test
    fun `l'apex de l'arc de Clarke depend de l'hemisphere`() {
        assertEquals(180.0, GeoSatellites.clarkeArcApexAzimuth(PARIS_LAT))
        assertEquals(0.0, GeoSatellites.clarkeArcApexAzimuth(-33.9))
    }
}
