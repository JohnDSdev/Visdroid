package com.johndsdev.visdroid

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

object BarRenderer {
    fun draw(canvas: Canvas, levels: FloatArray, settings: VisSettings, density: Float, paint: Paint) {
        if (levels.isEmpty()) return
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val verticalEdge = settings.side == BarSide.RIGHT || settings.side == BarSide.LEFT
        val edgeSpan = if (verticalEdge) h else w
        val outwardSpan = if (verticalEdge) w else h
        val slot = edgeSpan / levels.size
        val desiredThickness = settings.barThicknessDp * density
        val gap = settings.gapDp * density
        val thickness = min(desiredThickness, (slot - gap).coerceAtLeast(1f))
        val maxLength = outwardSpan * settings.maxLengthFraction
        val silentBaseline = (4f * density / maxLength).coerceIn(0.008f, 0.03f)

        paint.color = settings.color
        paint.alpha = (255f * settings.opacity).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true

        for (i in levels.indices) {
            val level = levels[i].coerceIn(0f, 1f)
            val length = maxLength * maxOf(level, silentBaseline)
            val center = when (settings.side) {
                BarSide.RIGHT, BarSide.LEFT -> h - slot * (i + .5f)
                BarSide.BOTTOM, BarSide.TOP -> slot * (i + .5f)
            }
            val half = thickness / 2f
            val rect = when (settings.side) {
                BarSide.RIGHT -> RectF(w - length, center - half, w, center + half)
                BarSide.LEFT -> RectF(0f, center - half, length, center + half)
                BarSide.BOTTOM -> RectF(center - half, h - length, center + half, h)
                BarSide.TOP -> RectF(center - half, 0f, center + half, length)
            }
            if (settings.roundedBars) {
                val radius = thickness / 2f
                canvas.drawRoundRect(rect, radius, radius, paint)
            } else {
                canvas.drawRect(rect, paint)
            }
        }
    }
}
