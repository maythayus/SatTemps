package com.mikael.sattemps.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectionScannerTest {

    @Test
    fun `aucune mesure n'est retenue avant le demarrage`() {
        val scanner = DirectionScanner()

        scanner.addSample(headingDeg = 90f, dbm = -70)
        val state = scanner.snapshot()

        assertFalse(state.running)
        assertTrue(state.sectors.isEmpty())
        assertEquals(0, state.coveragePercent)
        assertNull(state.bestSector)
    }

    @Test
    fun `la mesure est affectee au secteur du cap`() {
        val scanner = DirectionScanner(sectorCount = 24)
        scanner.start()

        scanner.addSample(headingDeg = 100f, dbm = -80)
        val sector = assertNotNull(scanner.snapshot().sectors.singleOrNull())

        // 100° tombe dans le secteur 6 (90° a 105°), dont le centre est 97,5°.
        assertEquals(97.5f, sector.azimuthDeg, 0.01f)
        assertEquals(-80f, sector.meanDbm, 0.01f)
        assertEquals(-80, sector.bestDbm)
        assertEquals(1, sector.sampleCount)
    }

    @Test
    fun `un cap negatif ou superieur a un tour retombe dans le bon secteur`() {
        val scanner = DirectionScanner(sectorCount = 24)
        scanner.start()

        scanner.addSample(headingDeg = -350f, dbm = -70)
        scanner.addSample(headingDeg = 370f, dbm = -90)

        val sector = assertNotNull(scanner.snapshot().sectors.singleOrNull())
        assertEquals(7.5f, sector.azimuthDeg, 0.01f)
        assertEquals(2, sector.sampleCount)
    }

    @Test
    fun `le secteur retient la moyenne et le meilleur signal`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()

        scanner.addSample(headingDeg = 10f, dbm = -100)
        scanner.addSample(headingDeg = 20f, dbm = -80)

        val sector = assertNotNull(scanner.snapshot().sectors.singleOrNull())
        assertEquals(-90f, sector.meanDbm, 0.01f)
        assertEquals(-80, sector.bestDbm)
        assertEquals(2, sector.sampleCount)
    }

    @Test
    fun `le meilleur secteur est celui dont la moyenne est la plus forte`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()

        scanner.addSample(headingDeg = 10f, dbm = -100)
        scanner.addSample(headingDeg = 100f, dbm = -75)
        scanner.addSample(headingDeg = 190f, dbm = -95)

        val best = assertNotNull(scanner.snapshot().bestSector)
        assertEquals(135f, best.azimuthDeg, 0.01f)
    }

    @Test
    fun `la couverture progresse avec les secteurs visites`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()

        scanner.addSample(headingDeg = 0f, dbm = -80)
        assertEquals(25, scanner.snapshot().coveragePercent)

        scanner.addSample(headingDeg = 180f, dbm = -80)
        assertEquals(50, scanner.snapshot().coveragePercent)
    }

    @Test
    fun `la mesure n'est fiable qu'au dela du seuil de couverture`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()

        scanner.addSample(headingDeg = 0f, dbm = -80)
        scanner.addSample(headingDeg = 90f, dbm = -80)
        assertFalse(scanner.snapshot().reliable)

        scanner.addSample(headingDeg = 180f, dbm = -80)
        assertTrue(scanner.snapshot().reliable)
    }

    @Test
    fun `un decoupage vide est refuse`() {
        assertFailsWith<IllegalArgumentException> { DirectionScanner(sectorCount = 0) }
        assertFailsWith<IllegalArgumentException> { DirectionScanner(sectorCount = -1) }
    }

    @Test
    fun `un cap invalide est ignore`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()

        scanner.addSample(headingDeg = Float.NaN, dbm = -80)

        assertTrue(scanner.snapshot().sectors.isEmpty())
    }

    @Test
    fun `un nouveau demarrage efface les mesures precedentes`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()
        scanner.addSample(headingDeg = 0f, dbm = -80)
        scanner.stop()

        scanner.start()

        val state = scanner.snapshot()
        assertTrue(state.running)
        assertTrue(state.sectors.isEmpty())
        assertEquals(0, state.coveragePercent)
    }

    @Test
    fun `l'arret conserve les mesures deja acquises`() {
        val scanner = DirectionScanner(sectorCount = 4)
        scanner.start()
        scanner.addSample(headingDeg = 0f, dbm = -80)

        scanner.stop()

        val state = scanner.snapshot()
        assertFalse(state.running)
        assertEquals(1, state.sectors.size)
    }
}
