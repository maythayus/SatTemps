package com.mikael.sattemps.network

data class SectorSample(
    /** Centre du secteur, en degres depuis le nord vrai. */
    val azimuthDeg: Float,
    val meanDbm: Float,
    val bestDbm: Int,
    val sampleCount: Int
)

data class ScanState(
    val running: Boolean = false,
    val sectors: List<SectorSample> = emptyList(),
    val coveragePercent: Int = 0
) {
    /** Secteur ou le signal mobile est le plus fort : direction de l'antenne relais. */
    val bestSector: SectorSample? get() = sectors.maxByOrNull { it.meanDbm }
}

/**
 * Mesure la puissance du signal mobile secteur par secteur pendant que l'utilisateur
 * tourne sur lui meme, afin d'identifier la direction du relais le mieux capte.
 */
class DirectionScanner(private val sectorCount: Int = 24) {

    private val sums = DoubleArray(sectorCount)
    private val counts = IntArray(sectorCount)
    private val best = IntArray(sectorCount) { Int.MIN_VALUE }
    private var running = false

    val sectorWidthDeg: Float get() = 360f / sectorCount

    fun start() {
        sums.fill(0.0)
        counts.fill(0)
        best.fill(Int.MIN_VALUE)
        running = true
    }

    fun stop() {
        running = false
    }

    fun addSample(headingDeg: Float, dbm: Int) {
        if (!running) return
        val index = (((headingDeg % 360f + 360f) % 360f) / sectorWidthDeg).toInt() % sectorCount
        sums[index] += dbm.toDouble()
        counts[index] += 1
        if (dbm > best[index]) best[index] = dbm
    }

    fun snapshot(): ScanState {
        val sectors = (0 until sectorCount).mapNotNull { i ->
            if (counts[i] == 0) null else SectorSample(
                azimuthDeg = i * sectorWidthDeg + sectorWidthDeg / 2f,
                meanDbm = (sums[i] / counts[i]).toFloat(),
                bestDbm = best[i],
                sampleCount = counts[i]
            )
        }
        val covered = counts.count { it > 0 }
        return ScanState(
            running = running,
            sectors = sectors,
            coveragePercent = covered * 100 / sectorCount
        )
    }
}
