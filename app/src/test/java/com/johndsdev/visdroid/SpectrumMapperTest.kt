package com.johndsdev.visdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumMapperTest {
    @Test
    fun silenceProducesSilence() {
        val result = SpectrumMapper.mapFft(ByteArray(1024), 48_000_000, 32, 1f, null, .8f)
        assertEquals(32, result.size)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun toneProducesVisibleBand() {
        val fft = ByteArray(1024)
        val bin = 9
        fft[bin * 2] = 120
        fft[bin * 2 + 1] = 20
        val result = SpectrumMapper.mapFft(fft, 48_000_000, 24, 2f, null, .8f)
        assertTrue(result.maxOrNull()!! > .15f)
    }

    @Test
    fun decayKeepsMotionSmooth() {
        val previous = FloatArray(12) { 1f }
        val result = SpectrumMapper.mapFft(ByteArray(1024), 48_000_000, 12, 1f, previous, .8f)
        assertTrue(result.all { it in .79f..81f })
    }
}
