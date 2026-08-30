package com.mikael.sattemps.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Satellite geostationnaire identifie par sa longitude orbitale (est positif). */
data class GeoSatellite(val name: String, val longitudeDeg: Double)

data class DishPointing(
    val satellite: GeoSatellite,
    /** Azimut de visee par rapport au nord geographique, en degres. */
    val azimuthDeg: Double,
    /** Elevation de la parabole au dessus de l'horizon, en degres. */
    val elevationDeg: Double,
    /** Inclinaison de la tete LNB (positif = sens horaire vu de derriere la parabole). */
    val lnbSkewDeg: Double,
    /** Distance oblique jusqu'au satellite, en kilometres. */
    val rangeKm: Double
) {
    val visible: Boolean get() = elevationDeg > 0.0
}

/**
 * Calcul de visee d'une parabole vers un satellite geostationnaire, a partir de la
 * position GPS de l'utilisateur.
 */
object GeoSatellites {

    private const val EARTH_RADIUS_KM = 6378.137
    private const val GEO_RADIUS_KM = 42164.0

    /** Quelques satellites courants en Europe / Afrique / Amerique. */
    val common = listOf(
        GeoSatellite("Astra 1 (19.2E)", 19.2),
        GeoSatellite("Hotbird 13 (13.0E)", 13.0),
        GeoSatellite("Astra 2 (28.2E)", 28.2),
        GeoSatellite("Eutelsat 5 West B (5.0W)", -5.0),
        GeoSatellite("Eutelsat 7 West A (7.0W)", -7.0),
        GeoSatellite("Nilesat 201 (7.0W)", -7.0),
        GeoSatellite("Astra 3 (23.5E)", 23.5),
        GeoSatellite("Turksat 4A (42.0E)", 42.0),
        GeoSatellite("Intelsat 20 (68.5E)", 68.5),
        GeoSatellite("SES 4 (22.0W)", -22.0),
        GeoSatellite("Galaxy 19 (97.0W)", -97.0),
        GeoSatellite("Hispasat 30W (30.0W)", -30.0)
    )

    fun pointing(satellite: GeoSatellite, latDeg: Double, lonDeg: Double, altitudeM: Double = 0.0): DishPointing {
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val satLon = Math.toRadians(satellite.longitudeDeg)
        val deltaLon = satLon - lon
        val observerRadius = EARTH_RADIUS_KM + altitudeM / 1000.0

        // Vecteur observateur -> satellite exprime dans le repere local (est, nord, zenith).
        val east = GEO_RADIUS_KM * sin(deltaLon)
        val north = -GEO_RADIUS_KM * sin(lat) * cos(deltaLon)
        val up = GEO_RADIUS_KM * cos(lat) * cos(deltaLon) - observerRadius

        val horizontal = hypot(east, north)
        val azimuth = (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
        val elevation = Math.toDegrees(atan2(up, horizontal))
        val range = sqrt(east * east + north * north + up * up)

        val skew = if (abs(lat) < 1e-6) {
            if (deltaLon >= 0) 90.0 else -90.0
        } else {
            Math.toDegrees(atan2(sin(deltaLon), tan(lat)))
        }

        return DishPointing(satellite, azimuth, elevation, skew, range)
    }

    /** Satellite geostationnaire le plus haut dans le ciel pour la position donnee. */
    fun best(latDeg: Double, lonDeg: Double, altitudeM: Double = 0.0): DishPointing? =
        common.map { pointing(it, latDeg, lonDeg, altitudeM) }
            .filter { it.visible }
            .maxByOrNull { it.elevationDeg }

    fun all(latDeg: Double, lonDeg: Double, altitudeM: Double = 0.0): List<DishPointing> =
        common.map { pointing(it, latDeg, lonDeg, altitudeM) }
            .sortedByDescending { it.elevationDeg }

    /** Azimut du point le plus haut de l'arc de Clarke : plein sud (ou nord) local. */
    fun clarkeArcApexAzimuth(latDeg: Double): Double = if (latDeg >= 0) 180.0 else 0.0
}
