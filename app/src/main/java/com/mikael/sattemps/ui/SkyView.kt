package com.mikael.sattemps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.mikael.sattemps.R
import com.mikael.sattemps.gnss.Satellite
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vue du ciel : projection polaire des satellites GNSS (zenith au centre, horizon au
 * bord), orientee selon le cap du telephone, avec une aiguille vers la direction cible.
 *
 * @param headingDeg cap du telephone par rapport au nord vrai.
 * @param targetAzimuthDeg direction a viser, `null` si aucune cible connue.
 * @param targetLabel etiquette dessinee au bout de l'aiguille.
 */
@Composable
fun SkyView(
    satellites: List<Satellite>,
    headingDeg: Float,
    targetAzimuthDeg: Float?,
    targetLabel: String?,
    modifier: Modifier = Modifier
) {
    val cardinals = stringArrayResource(R.array.cardinal_points)
    val quadrants = listOf(cardinals[0], cardinals[4], cardinals[8], cardinals[12])

    Box(modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 24.dp.toPx()

            listOf(1f, 2f / 3f, 1f / 3f).forEach { factor ->
                drawCircle(
                    color = Color(0x553FA9F5),
                    radius = radius * factor,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Le ciel tourne a l'inverse du telephone pour rester cale sur le nord vrai.
            rotate(degrees = -headingDeg, pivot = center) {
                drawCardinals(center, radius, quadrants)

                satellites.forEach { satellite ->
                    if (satellite.elevationDeg < 0f) return@forEach
                    val r = radius * (1f - satellite.elevationDeg / 90f)
                    val rad = Math.toRadians(satellite.azimuthDeg.toDouble())
                    val position = Offset(
                        center.x + (r * sin(rad)).toFloat(),
                        center.y - (r * cos(rad)).toFloat()
                    )
                    val strength = (satellite.cn0DbHz / 45f).coerceIn(0f, 1f)
                    drawCircle(
                        color = signalColor(strength, satellite.usedInFix),
                        radius = 6.dp.toPx() + 4.dp.toPx() * strength,
                        center = position
                    )
                }

                targetAzimuthDeg?.let { azimuth ->
                    val rad = Math.toRadians(azimuth.toDouble())
                    val end = Offset(
                        center.x + (radius * sin(rad)).toFloat(),
                        center.y - (radius * cos(rad)).toFloat()
                    )
                    drawLine(
                        color = Color(0xFFFFB74D),
                        start = center,
                        end = end,
                        strokeWidth = 5f
                    )
                    drawCircle(Color(0xFFFFB74D), radius = 8f, center = end)
                    targetLabel?.let { drawTargetLabel(it, end) }
                }
            }

            drawTelephoneMarker(center, radius)
        }
    }
}

private fun DrawScope.drawCardinals(center: Offset, radius: Float, labels: List<String>) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    labels.forEachIndexed { index, label ->
        val rad = Math.toRadians(index * 90.0)
        val x = center.x + ((radius + 30f) * sin(rad)).toFloat()
        val y = center.y - ((radius + 30f) * cos(rad)).toFloat() + 12f
        drawContext.canvas.nativeCanvas.drawText(label, x, y, paint)
    }
}

private fun DrawScope.drawTargetLabel(label: String, position: Offset) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FFB74D")
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(label, position.x, position.y - 16f, paint)
}

/** Repere fixe indiquant le haut du telephone (direction dans laquelle il pointe). */
private fun DrawScope.drawTelephoneMarker(center: Offset, radius: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius - 6f)
        lineTo(center.x - 14f, center.y - radius + 22f)
        lineTo(center.x + 14f, center.y - radius + 22f)
        close()
    }
    drawPath(path, Color(0xFF4FC3F7))
}

private fun signalColor(strength: Float, usedInFix: Boolean): Color = when {
    !usedInFix && strength < 0.3f -> Color(0xFF78909C)
    strength > 0.75f -> Color(0xFF4CAF50)
    strength > 0.45f -> Color(0xFFCDDC39)
    else -> Color(0xFFFF7043)
}
