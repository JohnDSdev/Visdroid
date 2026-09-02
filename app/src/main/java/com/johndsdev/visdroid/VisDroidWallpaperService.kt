package com.johndsdev.visdroid

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.concurrent.atomic.AtomicReference

class VisDroidWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VisEngine()

    private inner class VisEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val levels = AtomicReference(FloatArray(SettingsStore.load(this@VisDroidWallpaperService).barCount))
        private var settings = SettingsStore.load(this@VisDroidWallpaperService)
        private var background: Bitmap? = null
        private var visible = false
        private var analyzer: AudioAnalyzer? = null
        private val prefs = getSharedPreferences("visdroid_settings", MODE_PRIVATE)

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 16L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            background = BackgroundStore.load(this@VisDroidWallpaperService)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            stopAnalyzer()
            handler.removeCallbacks(drawRunnable)
            background?.recycle()
            background = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                reloadSettings()
                reloadBackground()
                startAnalyzer()
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
                stopAnalyzer()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(drawRunnable)
            stopAnalyzer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            reloadSettings()
        }

        private fun reloadSettings() {
            settings = SettingsStore.load(this@VisDroidWallpaperService)
            val current = levels.get()
            if (current.size != settings.barCount) levels.set(FloatArray(settings.barCount))
        }

        private fun reloadBackground() {
            background?.recycle()
            background = BackgroundStore.load(this@VisDroidWallpaperService)
        }

        private fun startAnalyzer() {
            if (analyzer != null) return
            analyzer = AudioAnalyzer(
                context = this@VisDroidWallpaperService,
                settingsProvider = { settings },
                onLevels = { levels.set(it) }
            ).also { it.start() }
        }

        private fun stopAnalyzer() {
            analyzer?.stop()
            analyzer = null
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                drawBackground(canvas)
                BarRenderer.draw(canvas, levels.get(), settings, resources.displayMetrics.density, paint)
            } catch (_: Throwable) {
                // A live wallpaper surface can disappear between callbacks. The next frame will recover.
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawBackground(canvas: Canvas) {
            val bmp = background
            if (bmp == null || bmp.isRecycled) {
                paint.shader = LinearGradient(
                    0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(),
                    intArrayOf(Color.rgb(11, 13, 16), Color.rgb(25, 29, 36)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                paint.shader = null
            } else {
                val scale = maxOf(canvas.width.toFloat() / bmp.width, canvas.height.toFloat() / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val left = (canvas.width - dw) / 2f
                val top = (canvas.height - dh) / 2f
                paint.alpha = 255
                canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
            }

            if (settings.backgroundDim > 0f) {
                paint.shader = null
                paint.color = Color.BLACK
                paint.alpha = (255 * settings.backgroundDim).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                paint.alpha = 255
            }
        }
    }
}
