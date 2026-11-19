package com.example.tt.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTimelineTest {

    @Test
    fun `dat clip dung moc startMs`() {
        val clip = NarrationTimeline.Clip(
            samples = FloatArray(100) { 0.5f },
            sampleRate = 1_000,
            startMs = 200
        )
        val out = NarrationTimeline.render(
            clips = listOf(clip),
            background = null,
            backgroundSampleRate = 1_000,
            backgroundGain = 0f,
            durationMs = 1_000,
            outputSampleRate = 1_000
        )
        assertEquals(1_000, out.size)
        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0.5f, out[200], 0.0001f)
        assertEquals(0.5f, out[299], 0.0001f)
        assertEquals(0f, out[300], 0.0001f)
    }

    @Test
    fun `giu am nen voi gain 20 phan tram`() {
        val out = NarrationTimeline.render(
            clips = emptyList(),
            background = FloatArray(500) { 1f },
            backgroundSampleRate = 1_000,
            backgroundGain = 0.2f,
            durationMs = 500,
            outputSampleRate = 1_000
        )
        assertEquals(500, out.size)
        assertEquals(0.2f, out[0], 0.0001f)
        assertEquals(0.2f, out[499], 0.0001f)
    }

    @Test
    fun `chuan hoa dinh khong vuot 0_9`() {
        val out = NarrationTimeline.render(
            clips = listOf(
                NarrationTimeline.Clip(FloatArray(100) { 1f }, 1_000, 0)
            ),
            background = FloatArray(100) { 1f },
            backgroundSampleRate = 1_000,
            backgroundGain = 1f,
            durationMs = 100,
            outputSampleRate = 1_000
        )
        val peak = out.maxOf { kotlin.math.abs(it) }
        assertTrue("peak = $peak", peak <= 0.9001f)
        assertTrue("peak = $peak", peak >= 0.899f)
    }
}
