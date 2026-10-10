/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import fr.sygix.jauge.model.Provider

object Palette {
    const val WARN = 0xFFF2A93B.toInt()
    const val CRITICAL = 0xFFE5484D.toInt()

    fun forUsage(provider: Provider, usedPercent: Double): Int = when {
        usedPercent >= 90 -> CRITICAL
        usedPercent >= 75 -> WARN
        else -> provider.accent
    }

    fun soft(color: Int, provider: Provider): Int = if (color == provider.accent) provider.accentSoft else ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.45f)

    fun track(color: Int): Int = ColorUtils.setAlphaComponent(color, 52)
}

data class RingSpec(val fraction: Float, val color: Int, val softColor: Int)

object GaugeBitmap {
    fun render(sizePx: Int, rings: List<RingSpec>, strokePx: Float, gapPx: Float): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = strokePx
        }
        rings.forEachIndexed { index, ring ->
            val inset = strokePx / 2f + index * (strokePx + gapPx)
            if (sizePx - 2 * inset <= strokePx) return@forEachIndexed
            val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
            paint.shader = null
            paint.color = Palette.track(ring.color)
            canvas.drawArc(rect, 0f, 360f, false, paint)
            val fraction = ring.fraction.coerceIn(0f, 1f)
            if (fraction <= 0.004f) return@forEachIndexed
            paint.color = ring.color
            if (fraction < 0.97f) {
                paint.shader = SweepGradient(center, center, intArrayOf(ring.softColor, ring.color, ring.color), floatArrayOf(0f, fraction, 1f))
                    .apply { setLocalMatrix(Matrix().apply { setRotate(-90f, center, center) }) }
            }
            canvas.drawArc(rect, -90f, 360f * fraction, false, paint)
        }
        return bitmap
    }
}
