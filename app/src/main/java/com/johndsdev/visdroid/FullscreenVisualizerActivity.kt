package com.johndsdev.visdroid

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicReference

class FullscreenVisualizerActivity : Activity() {
    private lateinit var visualizerView: FullscreenVisualizerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        visualizerView = FullscreenVisualizerView(this)
        setContentView(visualizerView)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        visualizerView.start()
    }

    override fun onPause() {
        visualizerView.stop()
        super.onPause()
    }

    override fun onDestroy() {
        visualizerView.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private class FullscreenVisualizerView(context: Context) : View(context), Choreographer.FrameCallback {
        private val choreographer = Choreographer.getInstance()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var settings = fullscreenSettings()
        private var background: Bitmap? = BackgroundStore.load(context)
        private val legacyLevels = AtomicReference(FloatArray(settings.barCount))
        private var analyzer: AudioAnalyzer? = null
        private var running = false
        private var lastDrawNanos = 0L

        fun start() {
            settings = fullscreenSettings()
            if (legacyLevels.get().size != settings.barCount) {
                legacyLevels.set(FloatArray(settings.barCount))
            }
            if (background == null || background?.isRecycled == true) {
                background = BackgroundStore.load(context)
            }
            running = true
            lastDrawNanos = 0L
            updateAnalyzerState()
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }

        fun stop() {
            running = false
            lastDrawNanos = 0L
            choreographer.removeFrameCallback(this)
            stopAnalyzer()
        }

        fun release() {
            stop()
            background?.recycle()
            background = null
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            updateAnalyzerState()
            // On 60 Hz displays this draws every vsync; on 120 Hz displays it draws every other
            // vsync, matching the wallpaper's 60 fps target without doing redundant work.
            if (lastDrawNanos == 0L || frameTimeNanos - lastDrawNanos >= 15_000_000L) {
                invalidate()
                lastDrawNanos = frameTimeNanos
            }
            choreographer.postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawBackground(canvas)
            BarRenderer.draw(
                canvas,
                currentLevels(),
                settings,
                resources.displayMetrics.density,
                paint
            )
        }

        private fun fullscreenSettings(): VisSettings {
            // This is deliberately not persisted. Fullscreen mode temporarily lets bars use 95%
            // of the available dimension, then the wallpaper goes back to the user's saved value.
            return SettingsStore.load(context).copy(maxLengthFraction = .95f)
        }

        private fun updateAnalyzerState() {
            if (PlaybackSpectrumBus.active.get()) {
                stopAnalyzer()
            } else if (analyzer == null) {
                analyzer = AudioAnalyzer(
                    context = context,
                    settingsProvider = { settings },
                    onLevels = { legacyLevels.set(it) }
                ).also { candidate ->
                    if (!candidate.start()) analyzer = null
                }
            }
        }

        private fun stopAnalyzer() {
            analyzer?.stop()
            analyzer = null
        }

        private fun currentLevels(): FloatArray {
            if (PlaybackSpectrumBus.active.get()) {
                val captured = PlaybackSpectrumBus.levels.get()
                if (captured.isNotEmpty()) {
                    if (captured.size == settings.barCount) return captured
                    val resized = FloatArray(settings.barCount)
                    for (i in resized.indices) {
                        val sourceIndex = ((i.toFloat() / resized.size) * captured.size)
                            .toInt().coerceIn(0, captured.lastIndex)
                        resized[i] = captured[sourceIndex]
                    }
                    return resized
                }
            }
            return legacyLevels.get()
        }

        private fun drawBackground(canvas: Canvas) {
            val bmp = background
            if (bmp == null || bmp.isRecycled) {
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(Color.rgb(11, 13, 16), Color.rgb(25, 29, 36)),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            } else {
                val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                val drawWidth = bmp.width * scale
                val drawHeight = bmp.height * scale
                val left = (width - drawWidth) / 2f
                val top = (height - drawHeight) / 2f
                paint.alpha = 255
                canvas.drawBitmap(
                    bmp,
                    null,
                    RectF(left, top, left + drawWidth, top + drawHeight),
                    paint
                )
            }

            if (settings.backgroundDim > 0f) {
                paint.shader = null
                paint.color = Color.BLACK
                paint.alpha = (255f * settings.backgroundDim).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.alpha = 255
            }
        }
    }
}
