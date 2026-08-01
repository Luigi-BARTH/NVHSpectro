package com.example.nvhspectro.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.nvhspectro.TelemetryData
import kotlin.math.max
import kotlin.math.min

enum class TelemetryMetric(val label: String, val unit: String) {
    SPEED("Vitesse", "km/h"),
    ACCELERATION("Accélération", "g"),
    ORDER("Ordre", "dBFS"),
    TTNR("TTNR", "dB")
}

@Composable
fun TelemetryGraph(
    history: List<TelemetryData>,
    metric: TelemetryMetric,
    timeWindowSec: Double,
    historySize: Int = 150,
    ttnrSpectrum: DoubleArray = DoubleArray(0),
    minFreq: Int = 0,
    maxFreq: Int = 10000,
    sampleRate: Int = 44100,
    isKinematicsEnabled: Boolean = false,
    selectedOrderName: String = "H18",
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val warningTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#FF9100")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val tickTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 20f
            isAntiAlias = true
        }
    }

    val axisPaint = remember {
        Paint().apply {
            color = android.graphics.Color.GRAY
            strokeWidth = 2.5f
            isAntiAlias = true
        }
    }

    val fineGridPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#334155")
            strokeWidth = 1.5f
            isAntiAlias = true
        }
    }

    val badgeBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#CC121824")
            isAntiAlias = true
        }
    }

    val badgeBorderPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
    }

    val badgeTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val marginLeft = 190f
        val marginRight = 30f
        val marginTop = 20f
        val marginBottom = 45f

        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom

        if (metric == TelemetryMetric.TTNR) {
            // =========================================================================
            // MODE SPECTRE 2D TTNR : GRILLE MULTI-REPÈRES HAUTE PRÉCISION ET LISIBILITÉ
            // ABSCISSE = FRÉQUENCE (Hz), ORDONNÉE = ÉMERGENCE (dB)
            // =========================================================================
            val maxTtnrDb = 20.0
            val totalBins = if (ttnrSpectrum.isNotEmpty()) ttnrSpectrum.size else 1024
            val nyquist = sampleRate / 2
            val minBin = ((minFreq * totalBins) / nyquist).coerceIn(0, totalBins - 1)
            val maxBin = ((maxFreq * totalBins) / nyquist).coerceIn(minBin + 1, totalBins)
            val displayedBins = (maxBin - minBin).coerceAtLeast(1)

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                // 1. Grille Horizontale d'Émergence (0 dB, 5 dB, 10 dB, 15 dB, 20 dB)
                val dbSteps = 4 // 0, 5, 10, 15, 20 dB
                for (step in 0..dbSteps) {
                    val dbVal = step * (maxTtnrDb / dbSteps)
                    val y = (marginTop + plotHeight) - (step.toFloat() / dbSteps) * plotHeight

                    // Ligne de grille fine
                    native.drawLine(marginLeft, y, marginLeft + plotWidth, y, fineGridPaint)

                    // Graduation Y
                    val label = if (step == dbSteps) "+20 dB" else if (step == 0) "0 dB" else "+${dbVal.toInt()} dB"
                    native.drawText(label, 15f, y + 8f, tickTextPaint)
                }

                // 2. Grille Verticale de Fréquence (5 paliers réguliers : 0 Hz, 25%, 50%, 75%, 100% maxFreq)
                val freqSteps = 4
                for (step in 0..freqSteps) {
                    val fraction = step.toFloat() / freqSteps
                    val x = marginLeft + fraction * plotWidth
                    val freqVal = minFreq + (fraction * (maxFreq - minFreq)).toInt()

                    // Ligne de grille verticale fine
                    native.drawLine(x, marginTop, x, marginTop + plotHeight, fineGridPaint)

                    // Graduation X
                    val label = "${freqVal} Hz"
                    val labelW = tickTextPaint.measureText(label)
                    var labelX = x - (labelW / 2f)
                    labelX = labelX.coerceIn(marginLeft, marginLeft + plotWidth - labelW)
                    native.drawText(label, labelX, marginTop + plotHeight + 35f, tickTextPaint)
                }

                // Encadrement des Axes principaux
                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)
            }

            // Tracé de la courbe du spectre d'émergence 2D AAA
            if (ttnrSpectrum.isNotEmpty() && displayedBins > 1) {
                val path = Path()
                var maxEmergence = 0.0
                var maxEmergenceBin = -1

                for (i in 0 until displayedBins) {
                    val bin = minBin + i
                    val valTtnr = if (bin in ttnrSpectrum.indices) ttnrSpectrum[bin] else 0.0
                    if (valTtnr > maxEmergence) {
                        maxEmergence = valTtnr
                        maxEmergenceBin = bin
                    }

                    val fractionX = i.toFloat() / max(1, displayedBins - 1)
                    val x = marginLeft + fractionX * plotWidth

                    val ttnrVal = valTtnr.coerceIn(0.0, maxTtnrDb)
                    val normY = (ttnrVal / maxTtnrDb).toFloat()
                    val y = (marginTop + plotHeight) - (normY * plotHeight)

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Ombrage dégradé translucide sous la courbe (AAA Grade)
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(marginLeft + plotWidth, marginTop + plotHeight)
                    lineTo(marginLeft, marginTop + plotHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x55D500F9), Color(0x00D500F9)),
                        startY = marginTop,
                        endY = marginTop + plotHeight
                    )
                )

                // Tracé de la ligne Néon Magenta
                drawPath(
                    path = path,
                    color = Color(0xFFD500F9), // Neon Magenta
                    style = Stroke(width = 3.5f)
                )

                // Curseur Automatique Ultra-Précis et Alignement Pixel-Perfect du Pic (>= 3.0 dB)
                if (maxEmergence >= 3.0 && maxEmergenceBin >= minBin) {
                    val peakFreq = ((maxEmergenceBin.toDouble() / totalBins) * nyquist).toInt()
                    val peakFractionX = (maxEmergenceBin - minBin).toFloat() / max(1, displayedBins - 1)
                    val peakX = marginLeft + peakFractionX * plotWidth
                    val peakNormY = (maxEmergence.coerceIn(0.0, maxTtnrDb) / maxTtnrDb).toFloat()
                    val peakY = (marginTop + plotHeight) - (peakNormY * plotHeight)

                    // Ligne pointillée Cyan pour le pic
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(peakX, marginTop),
                        end = Offset(peakX, marginTop + plotHeight),
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // Dot au sommet du pic
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 6f,
                        center = Offset(peakX, peakY)
                    )

                    // Badge numérique en haut
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val badgeText = "${peakFreq} Hz | +${String.format("%.1f", maxEmergence)} dB"
                        val textWidth = badgeTextPaint.measureText(badgeText)

                        var badgeLeft = peakX - (textWidth / 2f) - 16f
                        badgeLeft = badgeLeft.coerceIn(marginLeft, marginLeft + plotWidth - textWidth - 32f)

                        val badgeTop = marginTop + 10f
                        val badgeRight = badgeLeft + textWidth + 32f
                        val badgeBottom = badgeTop + 36f

                        val rect = android.graphics.RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
                        native.drawRoundRect(rect, 12f, 12f, badgeBgPaint)
                        native.drawRoundRect(rect, 12f, 12f, badgeBorderPaint)
                        native.drawText(badgeText, badgeLeft + 16f, badgeTop + 26f, badgeTextPaint)
                    }
                }
            }
        } else if (metric == TelemetryMetric.ORDER && (!isKinematicsEnabled || (history.isNotEmpty() && history.first().speedKmh <= 1.0f))) {
            // =========================================================================
            // MODE ORDRE (GMPe INACTIF OU VITESSE <= 1 km/h) : MESSAGE D'AVERTISSEMENT
            // =========================================================================
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)

                val msg = if (!isKinematicsEnabled) {
                    "⚠️ Disponible uniquement en mode étude GMPe activé"
                } else {
                    "⚠️ Suivi d'Ordre inactif (< 1 km/h)"
                }
                val textW = warningTextPaint.measureText(msg)
                val msgX = marginLeft + (plotWidth - textW) / 2f
                val msgY = marginTop + plotHeight / 2f
                native.drawText(msg, msgX, msgY, warningTextPaint)
            }
        } else {
            // =========================================================================
            // MODES TÉLÉMÉTRIE (Vitesse, Accélération, Ordre GMPe) : ABSCISSE = TEMPS (s)
            // =========================================================================
            val values = history.map { data ->
                when (metric) {
                    TelemetryMetric.SPEED -> data.speedKmh.toDouble()
                    TelemetryMetric.ACCELERATION -> data.accelerationG.toDouble()
                    TelemetryMetric.ORDER -> data.trackedOrderDbFS.coerceIn(-120.0, 0.0)
                    else -> 0.0
                }
            }

            val minVal = if (metric == TelemetryMetric.ORDER) -110.0 else (if (values.isNotEmpty()) values.minOrNull() ?: 0.0 else 0.0)
            val maxVal = if (metric == TelemetryMetric.ORDER) 0.0 else (if (values.isNotEmpty()) values.maxOrNull() ?: 1.0 else 1.0)
            val valRange = if (maxVal > minVal) maxVal - minVal else 1.0

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                // Grille de fond
                native.drawLine(marginLeft, marginTop, marginLeft, marginTop + plotHeight, axisPaint)
                native.drawLine(marginLeft, marginTop + plotHeight, marginLeft + plotWidth, marginTop + plotHeight, axisPaint)

                // Labels Y
                val maxStr = String.format("%.1f %s", maxVal, metric.unit)
                val minStr = String.format("%.1f %s", minVal, metric.unit)
                native.drawText(maxStr, 12f, marginTop + 22f, textPaint)
                native.drawText(minStr, 12f, marginTop + plotHeight - 4f, textPaint)
            }

            if (values.size > 1) {
                val pointCount = values.size
                val targetHistSize = max(historySize, pointCount)

                if (metric == TelemetryMetric.ORDER) {
                    // Tracé avec nuancier dynamique d'émergence TTNR (Blanc -> Orange -> Rouge)
                    for (i in 0 until pointCount - 1) {
                        val fractionX1 = i.toFloat() / max(1, targetHistSize - 1)
                        val x1 = marginLeft + (1f - fractionX1) * plotWidth
                        val normY1 = ((values[i] - minVal) / valRange).toFloat()
                        val y1 = (marginTop + plotHeight) - (normY1 * plotHeight)

                        val fractionX2 = (i + 1).toFloat() / max(1, targetHistSize - 1)
                        val x2 = marginLeft + (1f - fractionX2) * plotWidth
                        val normY2 = ((values[i + 1] - minVal) / valRange).toFloat()
                        val y2 = (marginTop + plotHeight) - (normY2 * plotHeight)

                        val emergenceDb = history[i].trackedOrderEmergenceDb
                        val segColor = when {
                            emergenceDb >= 20.0 -> Color(0xFFD50000) // 5. Rouge Pourpre Intense (Émergence extrême >= 20 dB)
                            emergenceDb >= 15.0 -> Color(0xFFFF1744) // 4. Rouge Vif (Émergence très forte 15 à 20 dB)
                            emergenceDb >= 10.0 -> Color(0xFFFF5722) // 3. Orange-Rouge Vermillon (Émergence forte 10 à 15 dB)
                            emergenceDb >= 5.0  -> Color(0xFFFF9800) // 2. Orange Fluo (Émergence franche 5 à 10 dB)
                            emergenceDb > 0.0   -> Color(0xFFFFB74D) // 1. Orange Clair / Ambre (Émergence débutante 0 à 5 dB)
                            else -> Color.White                      // Blanc (Aucune émergence <= 0 dB)
                        }

                        drawLine(
                            color = segColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 4f
                        )
                    }
                } else {
                    val path = Path()
                    for (i in 0 until pointCount) {
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
                        else -> Color.White
                    }

                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
    }
}
