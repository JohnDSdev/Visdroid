package com.johndsdev.visdroid

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object SpectrumMapper {
    fun mapFft(
        fft: ByteArray,
        samplingRateMilliHz: Int,
        barCount: Int,
        sensitivity: Float,
        previous: FloatArray?,
        decay: Float
    ): FloatArray {
        if (fft.size < 4 || barCount <= 0) return FloatArray(max(0, barCount))

        val sampleRateHz = samplingRateMilliHz / 1000f
        val nyquist = sampleRateHz / 2f
        val lowHz = 45f
        val highHz = min(16_000f, nyquist * .96f).coerceAtLeast(lowHz + 1f)
        val captureSize = fft.size
        val maxBin = captureSize / 2 - 1
        val out = FloatArray(barCount)
        val logLow = ln(lowHz)
        val logHigh = ln(highHz)
        val normalizer = ln(182.0).toFloat()

        for (i in 0 until barCount) {
            val f0 = kotlin.math.exp(logLow + (logHigh - logLow) * (i.toFloat() / barCount))
            val f1 = kotlin.math.exp(logLow + (logHigh - logLow) * ((i + 1f) / barCount))
            var k0 = ((f0 * captureSize) / sampleRateHz).toInt().coerceIn(1, maxBin)
            val k1 = ((f1 * captureSize) / sampleRateHz).toInt().coerceIn(k0, maxBin)
            if (k0 > k1) k0 = k1

            var sum = 0.0
            var count = 0
            for (k in k0..k1) {
                val idx = 2 * k
                if (idx + 1 >= fft.size) break
                val re = fft[idx].toInt().toDouble()
                val im = fft[idx + 1].toInt().toDouble()
                sum += hypot(re, im)
                count++
            }

            val average = if (count == 0) 0f else (sum / count).toFloat()
            var target = (ln(1f + average) / normalizer) * sensitivity
            target = ((target - .055f) / .945f).coerceIn(0f, 1f)

            val old = if (previous != null && i < previous.size) previous[i] else 0f
            out[i] = if (target >= old) {
                old * .22f + target * .78f
            } else {
                old * decay + target * (1f - decay)
            }.coerceIn(0f, 1f)
        }
        return out
    }
}
