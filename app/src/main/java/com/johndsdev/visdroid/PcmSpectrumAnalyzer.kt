package com.johndsdev.visdroid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Small radix-2 FFT used for AudioPlaybackCapture PCM. */
class PcmSpectrumAnalyzer(private val fftSize: Int = 2048) {
    init {
        require(fftSize > 1 && fftSize and (fftSize - 1) == 0) { "fftSize must be a power of two" }
    }

    private val window = FloatArray(fftSize) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))).toFloat()
    }
    private var previous: FloatArray? = null

    // CAVA-style automatic sensitivity. Loud material quickly turns gain down so a wall of
    // frequency bands does not clip at identical full height. Quieter material raises it slowly.
    private var autoGain = 1f

    fun analyze(
        pcm: ShortArray,
        sampleCount: Int,
        sampleRate: Int,
        settings: VisSettings
    ): FloatArray {
        if (sampleCount <= 0 || sampleRate <= 0 || settings.barCount <= 0) {
            return FloatArray(max(0, settings.barCount))
        }

        val usable = min(sampleCount, fftSize)
        var pcmPeak = 0
        for (i in 0 until usable) {
            pcmPeak = max(pcmPeak, abs(pcm[i].toInt()))
        }
        // AudioPlaybackCapture normally gives true/near digital zero when playback stops.
        // Reset smoothing here so silence means literally no bars instead of a long exponential tail.
        if (pcmPeak <= 8) {
            previous = null
            autoGain = 1f
            return FloatArray(settings.barCount)
        }

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        for (i in 0 until usable) {
            real[i] = (pcm[i] / 32768f) * window[i]
        }
        fft(real, imag)

        val nyquist = sampleRate / 2f
        val lowHz = 45f
        val highHz = min(16_000f, nyquist * .96f).coerceAtLeast(lowHz + 1f)
        val logLow = ln(lowHz)
        val logHigh = ln(highHz)
        val raw = FloatArray(settings.barCount)

        for (i in 0 until settings.barCount) {
            val f0 = exp(logLow + (logHigh - logLow) * (i.toFloat() / settings.barCount))
            val f1 = exp(logLow + (logHigh - logLow) * ((i + 1f) / settings.barCount))
            var k0 = ((f0 * fftSize) / sampleRate).toInt().coerceIn(1, fftSize / 2 - 1)
            val k1 = ((f1 * fftSize) / sampleRate).toInt().coerceIn(k0, fftSize / 2 - 1)
            if (k0 > k1) k0 = k1

            var energy = 0.0
            var count = 0
            for (k in k0..k1) {
                val re = real[k].toDouble()
                val im = imag[k].toDouble()
                energy += re * re + im * im
                count++
            }
            val rms = if (count == 0) 0f else sqrt((energy / count).toFloat()) / (fftSize * .5f)
            var value = ln(1f + rms * 90f) / ln(91f)
            value = ((value - .018f) / .982f).coerceAtLeast(0f)
            raw[i] = value
        }

        val rawPeak = raw.maxOrNull() ?: 0f
        if (rawPeak > 0.0001f) {
            val basePeak = rawPeak * settings.sensitivity
            // Aim the loudest band around 88% height. This preserves headroom and prevents the
            // characteristic flat ceiling where many loud bands all become exactly 1.0.
            val desiredGain = (.88f / basePeak).coerceIn(.30f, 3.0f)
            autoGain = if (desiredGain < autoGain) {
                // React quickly to loud sections / track changes.
                autoGain * .70f + desiredGain * .30f
            } else {
                // Recover gently on quiet sections so gain does not visibly pump every beat.
                autoGain * .985f + desiredGain * .015f
            }.coerceIn(.30f, 3.0f)
        }

        val out = FloatArray(settings.barCount)
        for (i in raw.indices) {
            val scaled = raw[i] * settings.sensitivity * autoGain
            // Soft knee above 78%. Unlike hard coerceIn(), this keeps loud bands distinguishable
            // while asymptotically approaching full height.
            val target = if (scaled <= .78f) {
                scaled.coerceAtLeast(0f)
            } else {
                (.78f + .22f * (1f - exp(-(scaled - .78f) / .22f))).coerceIn(0f, .998f)
            }

            val old = previous?.getOrNull(i) ?: 0f
            out[i] = if (target >= old) {
                old * .18f + target * .82f
            } else {
                old * settings.decay + target * (1f - settings.decay)
            }.coerceIn(0f, .998f)
        }
        previous = out
        return out
    }

    fun reset() {
        previous = null
        autoGain = 1f
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = (-2.0 * PI / len)
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wr - imag[i + k + half] * wi
                    val vI = real[i + k + half] * wi + imag[i + k + half] * wr
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val nextR = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
