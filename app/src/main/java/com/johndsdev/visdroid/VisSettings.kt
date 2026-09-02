package com.johndsdev.visdroid

import android.content.Context
import android.graphics.Color

enum class BarSide { RIGHT, LEFT, BOTTOM, TOP }

data class VisSettings(
    val side: BarSide = BarSide.RIGHT,
    val barCount: Int = 36,
    val barThicknessDp: Float = 7f,
    val gapDp: Float = 4f,
    val maxLengthFraction: Float = 0.42f,
    val sensitivity: Float = 1.35f,
    val decay: Float = 0.82f,
    val color: Int = Color.WHITE,
    val opacity: Float = 0.92f,
    val roundedBars: Boolean = true,
    val backgroundDim: Float = 0.08f
)

object SettingsStore {
    private const val PREFS = "visdroid_settings"

    fun load(context: Context): VisSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val side = runCatching {
            BarSide.valueOf(p.getString("side", BarSide.RIGHT.name) ?: BarSide.RIGHT.name)
        }.getOrDefault(BarSide.RIGHT)
        return VisSettings(
            side = side,
            barCount = p.getInt("barCount", 36).coerceIn(8, 88),
            barThicknessDp = p.getFloat("barThicknessDp", 7f).coerceIn(2f, 24f),
            gapDp = p.getFloat("gapDp", 4f).coerceIn(0f, 16f),
            maxLengthFraction = p.getFloat("maxLengthFraction", .42f).coerceIn(.15f, 1f),
            sensitivity = p.getFloat("sensitivity", 1.35f).coerceIn(.25f, 4f),
            decay = p.getFloat("decay", .82f).coerceIn(.30f, .97f),
            color = p.getInt("color", Color.WHITE),
            opacity = p.getFloat("opacity", .92f).coerceIn(.20f, 1f),
            roundedBars = p.getBoolean("roundedBars", true),
            backgroundDim = p.getFloat("backgroundDim", .08f).coerceIn(0f, .75f)
        )
    }

    fun save(context: Context, s: VisSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("side", s.side.name)
            .putInt("barCount", s.barCount)
            .putFloat("barThicknessDp", s.barThicknessDp)
            .putFloat("gapDp", s.gapDp)
            .putFloat("maxLengthFraction", s.maxLengthFraction)
            .putFloat("sensitivity", s.sensitivity)
            .putFloat("decay", s.decay)
            .putInt("color", s.color)
            .putFloat("opacity", s.opacity)
            .putBoolean("roundedBars", s.roundedBars)
            .putFloat("backgroundDim", s.backgroundDim)
            .apply()
    }
}
