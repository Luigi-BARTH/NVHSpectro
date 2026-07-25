package com.example.nvhspectro.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.nvhspectro.TelemetryData
import kotlin.math.max
import kotlin.math.min

enum class TelemetryMetric(val label: String, val unit: String) {
    SPEED("Vitesse", "km/h"),
    ACCELERATION("Accélération", "g"),
    ALTITUDE("Altitude", "m"),
    TTNR("TTNR", "dB")
}

@Composable
fun TelemetryGraph(
    history: List<TelemetryData>,
    metric: TelemetryMetric,
    timeWindowSec: Double,
    historySize: Int = 150,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val gridPaint = remember {
        Paint().apply {
            color = android.graphics.Color.DKGRAY
            strokeWidth = 2f
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val marginLeft = 150f
        val marginRight = 40f
        val marginTop = 20f
        val marginBottom = 40f

        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom

        // Extraction des valeurs de la métrique sélectionnée
        val values = history.map { data ->
            when (metric) {
                TelemetryMetric.SPEED -> data.speedKmh.toDouble()
                TelemetryMetric.ACCELERATION -> data.accelerationG.toDouble()
                TelemetryMetric.ALTITUDE -> data.altitude
                TelemetryMetric.TTNR -> data.ttnrDb.toDouble()
            }
        }

        val minVal = if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0
        val maxVal = if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0
        val valRange = if (maxVal > minVal) maxVal - minVal else 1.0

        // Dessiner la grille de fond
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Ligne du bas (Axe X) et de gauche (Axe Y)
            native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, gridPaint)
            native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, gridPaint)

            // Labels Min et Max sur l'axe Y
            val maxStr = String.format("%.1f %s", maxVal, metric.unit)
            val minStr = String.format("%.1f %s", minVal, metric.unit)
            native.drawText(maxStr, 10f, marginTop + 25f, textPaint)
            native.drawText(minStr, 10f, marginTop + plotHeight, textPaint)
        }

        // Trace de la courbe si assez de données
        if (values.size > 1) {
            val path = Path()
            val pointCount = values.size
            val targetHistSize = max(historySize, pointCount)

            for (i in 0 until pointCount) {
                // Synchronisation temporelle 1-to-1 exacte avec le colormap
                // i = 0 est le point le plus récent (à droite : marginLeft + plotWidth)
                val fractionX = i.toFloat() / max(1, targetHistSize - 1)
                val x = marginLeft + (1f - fractionX) * plotWidth

                val normY = ((values[i] - minVal) / valRange).toFloat()
                val y = (marginTop + plotHeight) - (normY * plotHeight)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            val strokeColor = when (metric) {
                TelemetryMetric.SPEED -> Color(0xFF00E676) // Vert fluo
                TelemetryMetric.ACCELERATION -> Color(0xFFFF9100) // Orange vif
                TelemetryMetric.ALTITUDE -> Color(0xFF00B0FF) // Bleu cyan
                TelemetryMetric.TTNR -> Color(0xFFD500F9) // Violet / Magenta néon
            }

            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 4f)
            )
        }
    }
}
