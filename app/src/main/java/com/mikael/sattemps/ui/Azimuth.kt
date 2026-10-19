package com.mikael.sattemps.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Conversions d'azimut utilisees par l'interface, sans dependance Android ni
 * ressource texte afin d'etre couvertes par des tests unitaires.
 */
internal object Azimuth {

    /** Nombre de secteurs cardinaux (N, NNE, NE, ...). */
    const val CARDINAL_COUNT = 16

    private const val CARDINAL_WIDTH_DEG = 360.0 / CARDINAL_COUNT

    /** Ramene un azimut quelconque dans l'intervalle [0, 360[. */
    fun normalize(azimuthDeg: Double): Double = ((azimuthDeg % 360.0) + 360.0) % 360.0

    /** Indice du secteur cardinal correspondant a l'azimut, de 0 (nord) a 15. */
    fun cardinalIndex(azimuthDeg: Double): Int =
        ((normalize(azimuthDeg) + CARDINAL_WIDTH_DEG / 2) / CARDINAL_WIDTH_DEG).toInt() % CARDINAL_COUNT

    /**
     * Rotation la plus courte pour passer du cap courant a la cible : positive vers la
     * droite, negative vers la gauche, dans l'intervalle ]-180, 180].
     */
    fun turnDelta(currentDeg: Float, targetDeg: Float): Float =
        ((targetDeg - currentDeg + 540f) % 360f) - 180f

    /** Vrai lorsque l'ecart avec la cible est inferieur au degre. */
    fun isAligned(currentDeg: Float, targetDeg: Float): Boolean =
        abs(turnDelta(currentDeg, targetDeg)).roundToInt() == 0
}
