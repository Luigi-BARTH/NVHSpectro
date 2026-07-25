package com.example.nvhspectro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * Retourne un Int ARGB basé sur la colormap "Jet"
 */
fun getJetColorInt(v: Float): Int {
    var v = max(0f, min(1f, v))
    var r = 1f
    var g = 1f
    var b = 1f
    when {
        v < 0.125f -> { r = 0f; g = 0f; b = 0.5f + 4f * v }
        v < 0.375f -> { r = 0f; g = 4f * (v - 0.125f); b = 1f }
        v < 0.625f -> { r = 4f * (v - 0.375f); g = 1f; b = 1f - 4f * (v - 0.375f) }
        v < 0.875f -> { r = 1f; g = 1f - 4f * (v - 0.625f); b = 0f }
        else -> { r = 1f - 4f * (v - 0.875f); g = 0f; b = 0f }
    }
    return AndroidColor.argb(
        255,
        (r * 255).toInt(),
        (g * 255).toInt(),
        (b * 255).toInt()
    )
}

@Composable
fun SpectrogramCanvas(
    history: List<DoubleArray>,
    modifier: Modifier = Modifier,
    minDb: Double = -120.0,
    maxDb: Double = 0.0,
    maxFreq: Int = 10000,
    fftSize: Int = 2048,
    sampleRate: Int = 44100,
    historySize: Int = 150,
    displayMode: DisplayMode = DisplayMode.ABSOLUTE
) {
    if (history.isEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {}
        return
    }

    val totalBinCount = history.first().size
    val nyquistFreq = sampleRate / 2
    val displayedBinCount = minOf(totalBinCount, (maxFreq * totalBinCount) / nyquistFreq)
    val actualMaxFreq = (displayedBinCount * nyquistFreq) / totalBinCount

    val bitmapWidth = historySize
    val bitmapHeight = displayedBinCount

    var cursorYRatio by remember { mutableFloatStateOf(0.5f) }

    val bitmap by remember(bitmapWidth, bitmapHeight) {
        mutableStateOf(Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888))
    }
    val pixels by remember(bitmapWidth, bitmapHeight) {
        mutableStateOf(IntArray(bitmapWidth * bitmapHeight) { AndroidColor.BLACK })
    }

    val effectiveMin = if (displayMode == DisplayMode.TTNR) 0.0 else minDb
    val effectiveMax = if (displayMode == DisplayMode.TTNR) 20.0 else maxDb

    LaunchedEffect(history, effectiveMin, effectiveMax, displayMode) {
        if (history.isNotEmpty()) {
            val latestFrame = history.first()

            // 1. Décalage vers la gauche
            for (y in 0 until bitmapHeight) {
                System.arraycopy(pixels, y * bitmapWidth + 1, pixels, y * bitmapWidth, bitmapWidth - 1)

                val b = bitmapHeight - 1 - y
                val magnitude = if (b < latestFrame.size) latestFrame[b] else effectiveMin
                
                val normalized = ((magnitude - effectiveMin) / (effectiveMax - effectiveMin)).toFloat()
                val colorInt = getJetColorInt(normalized)
                
                pixels[y * bitmapWidth + (bitmapWidth - 1)] = colorInt
            }
            
            // 2. Mise à jour du Bitmap
            bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
        }
    }

    val imageBitmap = bitmap.asImageBitmap()
    
    val textPaint = remember {
        Paint().apply {
            color = AndroidColor.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val tickPaint = remember {
        Paint().apply {
            color = AndroidColor.WHITE
            strokeWidth = 3f
            isAntiAlias = true
        }
    }

    // Peinture discrète pour le curseur (ligne cyan pointillée)
    val cursorLinePaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#00E5FF") // Cyan vif discret
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            isAntiAlias = true
        }
    }

    val cursorBadgeBgPaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#E6002A36") // Cyan très sombre translucide
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val cursorBadgeTextPaint = remember {
        Paint().apply {
            color = AndroidColor.parseColor("#00E5FF")
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val marginTop = 60f
                    val marginBottom = 120f
                    val plotHeight = size.height - marginTop - marginBottom
                    if (plotHeight > 0) {
                        val touchY = change.position.y
                        val relativeY = (touchY - marginTop).coerceIn(0f, plotHeight)
                        cursorYRatio = relativeY / plotHeight
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val marginTop = 60f
                    val marginBottom = 120f
                    val plotHeight = size.height - marginTop - marginBottom
                    if (plotHeight > 0) {
                        val relativeY = (offset.y - marginTop).coerceIn(0f, plotHeight)
                        cursorYRatio = relativeY / plotHeight
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        val marginLeft = 150f
        val marginTop = 60f
        val marginBottom = 120f
        val marginRight = 40f
        
        val plotWidth = w - marginLeft - marginRight
        val plotHeight = h - marginTop - marginBottom

        // 1. Dessiner le spectrogramme
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmapWidth, bitmapHeight),
            dstOffset = IntOffset(marginLeft.toInt(), marginTop.toInt()),
            dstSize = IntSize(plotWidth.toInt(), plotHeight.toInt()),
            filterQuality = FilterQuality.None
        )

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            val plotBottom = marginTop + plotHeight
            val plotRight = marginLeft + plotWidth

            // --- AXE Y (Fréquences) ---
            native.drawLine(marginLeft, marginTop, marginLeft, plotBottom, tickPaint)
            val ySteps = 5
            for (i in 0..ySteps) {
                val f = actualMaxFreq - (i * actualMaxFreq / ySteps)
                val y = marginTop + i * (plotHeight / ySteps)
                
                native.drawLine(marginLeft - 15f, y, marginLeft, y, tickPaint)
                
                val textY = when (i) {
                    0 -> y + 25f
                    ySteps -> y - 5f
                    else -> y + 10f
                }
                native.drawText("${f} Hz", 10f, textY, textPaint)
            }

            // --- CURSEUR EN FRÉQUENCE DISCRET ---
            val cursorY = marginTop + cursorYRatio * plotHeight
            val selectedFreqHz = ((1f - cursorYRatio) * actualMaxFreq).toInt() // Entier sans décimale !

            // Ligne pointillée horizontale sur toute la largeur de l'image
            native.drawLine(marginLeft, cursorY, plotRight, cursorY, cursorLinePaint)

            // Badge de fréquence interactif attaché au curseur sur l'axe Y
            val freqStr = "$selectedFreqHz Hz"
            val badgeTextWidth = cursorBadgeTextPaint.measureText(freqStr)
            val badgePaddingHorizontal = 12f
            val badgeHeight = 38f

            val badgeLeft = marginLeft + 10f
            val badgeTop = (cursorY - badgeHeight / 2f).coerceIn(marginTop, plotBottom - badgeHeight)
            val badgeRight = badgeLeft + badgeTextWidth + (badgePaddingHorizontal * 2f)
            val badgeBottom = badgeTop + badgeHeight

            native.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, 8f, 8f, cursorBadgeBgPaint)
            native.drawText(freqStr, badgeLeft + badgePaddingHorizontal, badgeTop + 28f, cursorBadgeTextPaint)

            // --- AXE X (Temps en secondes) ---
            native.drawLine(marginLeft, plotBottom, plotRight, plotBottom, tickPaint)

            val hopSize = fftSize / 2.0
            val dt = hopSize / sampleRate
            val totalTimeSec = historySize * dt

            val xSteps = 5
            for (i in 0..xSteps) {
                val fraction = i.toFloat() / xSteps
                val x = marginLeft + fraction * plotWidth
                val tSec = -totalTimeSec * (1f - fraction)

                native.drawLine(x, plotBottom, x, plotBottom + 15f, tickPaint)

                val label = String.format("%.1fs", tSec)
                val labelWidth = textPaint.measureText(label)
                val textX = (x - labelWidth / 2f).coerceIn(marginLeft, plotRight - labelWidth)
                native.drawText(label, textX, plotBottom + 50f, textPaint)
            }

            // Titre Axe X
            val xTitle = "Temps (s)"
            val xTitleWidth = textPaint.measureText(xTitle)
            native.drawText(xTitle, marginLeft + (plotWidth - xTitleWidth) / 2f, h - 20f, textPaint)

            // --- LÉGENDE (Haut Droite) ---
            if (displayMode == DisplayMode.TTNR) {
                native.drawText("MAX: +20 dB TTNR", plotRight - 300f, marginTop + 35f, textPaint)
                native.drawText("MIN: 0 dB TTNR", plotRight - 300f, marginTop + 75f, textPaint)
            } else {
                native.drawText(String.format("MAX: %.0f dBFS", maxDb), plotRight - 260f, marginTop + 35f, textPaint)
                native.drawText(String.format("MIN: %.0f dBFS", minDb), plotRight - 260f, marginTop + 75f, textPaint)
            }
        }
    }
}
