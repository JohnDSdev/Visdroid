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
}
