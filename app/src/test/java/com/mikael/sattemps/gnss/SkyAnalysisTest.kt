package com.mikael.sattemps.gnss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun satellite(
    azimuthDeg: Float,
    elevationDeg: Float,
    cn0DbHz: Float,
    usedInFix: Boolean = true,
    svid: Int = 1
) = Satellite(
    constellation = "GPS",
    svid = svid,
    azimuthDeg = azimuthDeg,
    elevationDeg = elevationDeg,
    cn0DbHz = cn0DbHz,
    usedInFix = usedInFix,
    hasEphemeris = true
)

class SkyAnalysisTest {

    @Test
    fun `aucun satellite ne donne aucune direction`() {
        assertNull(SkyAnalysis.bestSatellite(emptyList()))
        assertNull(SkyAnalysis.bestSkyAzimuthDeg(emptyList()))
        assertNull(SkyAnalysis.meanElevationDeg(emptyList()))
    }

    @Test
    fun `le meilleur satellite est le plus fort au dessus de l'elevation minimale`() {
        val best = SkyAnalysis.bestSatellite(
            listOf(
                satellite(azimuthDeg = 10f, elevationDeg = 2f, cn0DbHz = 48f, svid = 1),
                satellite(azimuthDeg = 90f, elevationDeg = 40f, cn0DbHz = 35f, svid = 2),
                satellite(azimuthDeg = 200f, elevationDeg = 20f, cn0DbHz = 30f, svid = 3)
            )
        )

        assertEquals(2, assertNotNull(best).svid)
    }

    @Test
    fun `les satellites trop bas sont ignores pour la direction`() {
        val satellites = listOf(satellite(azimuthDeg = 90f, elevationDeg = 1f, cn0DbHz = 45f))

        assertNull(SkyAnalysis.bestSatellite(satellites))
        assertNull(SkyAnalysis.bestSkyAzimuthDeg(satellites))
    }

    @Test
    fun `un satellite sans signal est ignore pour la direction`() {
        val satellites = listOf(satellite(azimuthDeg = 90f, elevationDeg = 30f, cn0DbHz = 0f))

        assertNull(SkyAnalysis.bestSkyAzimuthDeg(satellites))
    }

    @Test
    fun `la direction est la moyenne vectorielle ponderee des azimuts`() {
        val azimuth = SkyAnalysis.bestSkyAzimuthDeg(
            listOf(
                satellite(azimuthDeg = 80f, elevationDeg = 30f, cn0DbHz = 40f, svid = 1),
                satellite(azimuthDeg = 100f, elevationDeg = 30f, cn0DbHz = 40f, svid = 2)
            )
        )

        assertEquals(90f, assertNotNull(azimuth), 0.01f)
    }

    @Test
    fun `la moyenne d'azimuts autour du nord ne passe pas par le sud`() {
        val azimuth = SkyAnalysis.bestSkyAzimuthDeg(
            listOf(
                satellite(azimuthDeg = 350f, elevationDeg = 30f, cn0DbHz = 40f, svid = 1),
                satellite(azimuthDeg = 10f, elevationDeg = 30f, cn0DbHz = 40f, svid = 2)
            )
        )

        assertEquals(0f, assertNotNull(azimuth), 0.01f)
    }

    @Test
    fun `la direction reste dans un tour complet`() {
        val azimuth = SkyAnalysis.bestSkyAzimuthDeg(
            listOf(
                satellite(azimuthDeg = 300f, elevationDeg = 20f, cn0DbHz = 40f, svid = 1),
                satellite(azimuthDeg = 340f, elevationDeg = 20f, cn0DbHz = 40f, svid = 2)
            )
        )

        assertEquals(320f, assertNotNull(azimuth), 0.01f)
    }

    @Test
    fun `le signal le plus fort tire la direction vers lui`() {
        val azimuth = SkyAnalysis.bestSkyAzimuthDeg(
            listOf(
                satellite(azimuthDeg = 0f, elevationDeg = 30f, cn0DbHz = 45f, svid = 1),
                satellite(azimuthDeg = 90f, elevationDeg = 30f, cn0DbHz = 15f, svid = 2)
            )
        )

        val value = assertNotNull(azimuth)
        assertEquals(true, value in 0f..45f, "azimut $value")
    }

    @Test
    fun `l'elevation moyenne ne compte que les satellites recus`() {
        val mean = SkyAnalysis.meanElevationDeg(
            listOf(
                satellite(azimuthDeg = 0f, elevationDeg = 20f, cn0DbHz = 40f, svid = 1),
                satellite(azimuthDeg = 90f, elevationDeg = 40f, cn0DbHz = 30f, svid = 2),
                satellite(azimuthDeg = 180f, elevationDeg = 80f, cn0DbHz = 0f, svid = 3)
            )
        )

        assertEquals(30f, assertNotNull(mean), 0.01f)
    }

    @Test
    fun `l'etat GNSS expose les analyses du ciel`() {
        val state = GnssState(
            satellites = listOf(
                satellite(azimuthDeg = 90f, elevationDeg = 45f, cn0DbHz = 42f, svid = 7)
            )
        )

        assertEquals(7, assertNotNull(state.bestSatellite).svid)
        assertEquals(90f, assertNotNull(state.bestSkyAzimuthDeg), 0.01f)
    }
}
