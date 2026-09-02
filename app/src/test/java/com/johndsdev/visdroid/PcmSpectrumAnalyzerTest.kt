package com.johndsdev.visdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PcmSpectrumAnalyzerTest {
    @Test
    fun silenceProducesSilence() {
        val analyzer = PcmSpectrumAnalyzer(2048)
        val out = analyzer.analyze(ShortArray(2048), 2048, 48_000, VisSettings(barCount = 36))
        assertEquals(36, out.size)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun oneKilohertzToneProducesStrongBand() {
        val sampleRate = 48_000
        val pcm = ShortArray(2048) { i ->
            (sin(2.0 * PI * 1000.0 * i / sampleRate) * 25_000.0).toInt().toShort()
        }
        val analyzer = PcmSpectrumAnalyzer(2048)
        val out = analyzer.analyze(pcm, pcm.size, sampleRate, VisSettings(barCount = 36, sensitivity = 1.2f))
        assertTrue(out.maxOrNull()!! > .35f)
        assertTrue(out.count { it > .08f } < 12)
    }

    @Test
    fun digitalSilenceClearsPreviousBars() {
        val sampleRate = 48_000
        val settings = VisSettings(barCount = 36, sensitivity = 1.2f, decay = .97f)
        val tone = ShortArray(2048) { i ->
            (sin(2.0 * PI * 1000.0 * i / sampleRate) * 25_000.0).toInt().toShort()
        }
        val analyzer = PcmSpectrumAnalyzer(2048)
        val active = analyzer.analyze(tone, tone.size, sampleRate, settings)
        assertTrue(active.maxOrNull()!! > .35f)

        val silent = analyzer.analyze(ShortArray(2048), 2048, sampleRate, settings)
        assertTrue(silent.all { it == 0f })
    }

    @Test
    fun adaptiveGainPreventsLoudAudioFromFlatTopping() {
        val sampleRate = 48_000
        val frequencies = doubleArrayOf(70.0, 120.0, 240.0, 480.0, 900.0, 1800.0, 3600.0, 7200.0)
        val loud = ShortArray(2048) { i ->
            var sample = 0.0
            for ((index, frequency) in frequencies.withIndex()) {
                sample += sin(2.0 * PI * frequency * i / sampleRate) * (9000.0 - index * 500.0)
            }
            sample.toInt().coerceIn(-32768, 32767).toShort()
        }

        val analyzer = PcmSpectrumAnalyzer(2048)
        val settings = VisSettings(barCount = 36, sensitivity = 4f, decay = .82f)
        var out = FloatArray(36)
        repeat(24) {
            out = analyzer.analyze(loud, loud.size, sampleRate, settings)
        }

        assertTrue(out.maxOrNull()!! < .96f)
        assertTrue(out.count { it > .93f } <= 2)
        assertTrue(out.distinctBy { (it * 100).toInt() }.size > 6)
    }
}
