package com.johndsdev.visdroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer

class AudioAnalyzer(
    private val context: Context,
    private val settingsProvider: () -> VisSettings,
    private val onLevels: (FloatArray) -> Unit
) {
    private var visualizer: Visualizer? = null
    private var previous: FloatArray? = null

    fun start(): Boolean {
        if (visualizer != null) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false

        return try {
            val v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = minOf(range[1], 1024)
            v.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null) return
                    val s = settingsProvider()
                    val mapped = SpectrumMapper.mapFft(
                        fft = fft,
                        samplingRateMilliHz = samplingRate,
                        barCount = s.barCount,
                        sensitivity = s.sensitivity,
                        previous = previous,
                        decay = s.decay
                    )
                    previous = mapped
                    onLevels(mapped)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
            true
        } catch (_: Throwable) {
            visualizer = null
            false
        }
    }

    fun stop() {
        val v = visualizer ?: return
        runCatching { v.enabled = false }
        runCatching { v.release() }
        visualizer = null
        previous = null
    }
}
