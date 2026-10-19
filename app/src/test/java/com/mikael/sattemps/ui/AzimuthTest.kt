package com.mikael.sattemps.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AzimuthTest {

    @Test
    fun `la normalisation ramene tous les azimuts dans un tour`() {
        assertEquals(0.0, Azimuth.normalize(360.0), 0.001)
        assertEquals(350.0, Azimuth.normalize(-10.0), 0.001)
        assertEquals(45.0, Azimuth.normalize(765.0), 0.001)
    }

    @Test
    fun `chaque azimut tombe dans le bon secteur cardinal`() {
        assertEquals(0, Azimuth.cardinalIndex(0.0))
        assertEquals(0, Azimuth.cardinalIndex(-5.0))
        assertEquals(1, Azimuth.cardinalIndex(22.5))
        assertEquals(4, Azimuth.cardinalIndex(90.0))
        assertEquals(8, Azimuth.cardinalIndex(180.0))
        assertEquals(12, Azimuth.cardinalIndex(270.0))
        assertEquals(15, Azimuth.cardinalIndex(345.0))
    }

    @Test
    fun `l'indice cardinal reste dans les bornes du tableau`() {
        (-720..720).forEach { degrees ->
            val index = Azimuth.cardinalIndex(degrees.toDouble())
            assertTrue(index in 0 until Azimuth.CARDINAL_COUNT, "azimut $degrees -> $index")
        }
    }

    @Test
    fun `la rotation la plus courte peut passer par le nord`() {
        assertEquals(20f, Azimuth.turnDelta(currentDeg = 350f, targetDeg = 10f), 0.01f)
        assertEquals(-20f, Azimuth.turnDelta(currentDeg = 10f, targetDeg = 350f), 0.01f)
        assertEquals(90f, Azimuth.turnDelta(currentDeg = 0f, targetDeg = 90f), 0.01f)
        assertEquals(-90f, Azimuth.turnDelta(currentDeg = 90f, targetDeg = 0f), 0.01f)
    }

    @Test
    fun `l'alignement tolere moins d'un demi degre`() {
        assertTrue(Azimuth.isAligned(currentDeg = 120f, targetDeg = 120.2f))
        assertFalse(Azimuth.isAligned(currentDeg = 120f, targetDeg = 122f))
        assertTrue(Azimuth.isAligned(currentDeg = 359.9f, targetDeg = 0f))
    }
}
