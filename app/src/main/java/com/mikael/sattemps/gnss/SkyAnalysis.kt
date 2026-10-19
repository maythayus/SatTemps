package com.mikael.sattemps.gnss

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analyse geometrique de la constellation recue, independante des API Android afin
 * d'etre couverte par des tests unitaires.
 */
internal object SkyAnalysis {

    /** Un satellite sous cette elevation est trop bas pour orienter une antenne. */
    const val MIN_USEFUL_ELEVATION_DEG = 5f

    /** Satellite offrant le meilleur signal parmi ceux suffisamment hauts. */
    fun bestSatellite(satellites: List<Satellite>): Satellite? =
        satellites.filter { it.elevationDeg > MIN_USEFUL_ELEVATION_DEG }.maxByOrNull { it.cn0DbHz }

    /**
     * Direction ou le ciel est le mieux degage : moyenne vectorielle des azimuts des
     * satellites recus, ponderee par la qualite du signal et l'elevation (un satellite
     * au zenith ne designe aucune direction utile).
     *
     * Retourne `null` si aucun satellite n'est exploitable ou si les directions
     * s'annulent exactement.
     */
    fun bestSkyAzimuthDeg(satellites: List<Satellite>): Float? {
        val visible = satellites.filter {
            it.cn0DbHz > 0f && it.elevationDeg > MIN_USEFUL_ELEVATION_DEG
        }
        if (visible.isEmpty()) return null

        var x = 0.0
        var y = 0.0
        visible.forEach { satellite ->
            val weight = satellite.cn0DbHz.toDouble() *
                cos(Math.toRadians(satellite.elevationDeg.toDouble()))
            val rad = Math.toRadians(satellite.azimuthDeg.toDouble())
            x += weight * cos(rad)
            y += weight * sin(rad)
        }
        if (x == 0.0 && y == 0.0) return null
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /** Elevation moyenne des satellites effectivement recus. */
    fun meanElevationDeg(satellites: List<Satellite>): Float? =
        satellites.filter { it.cn0DbHz > 0f }
            .map { it.elevationDeg }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
}
