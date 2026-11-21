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
    fun `fitToWindow cat duoi clip dai hon cua so ma khong doi sampleRate`() {
        // 1000 sample @ 1000Hz = 1000ms, cua so chi 500ms.
        // Khong duoc doi sampleRate (lam giong bi cao/re), chi cat duoi + fade.
        val clip = NarrationTimeline.Clip(FloatArray(1_000) { 0.5f }, 1_000, 0)
        val fitted = NarrationTimeline.fitToWindow(clip, 500L)

        assertEquals(1_000, fitted.sampleRate)
        assertTrue("so sample phai giam: ${fitted.samples.size}", fitted.samples.size <= 500)
        assertEquals(0L, fitted.startMs)
        assertTrue("cuoi phai fade: ${fitted.samples.last()}", fitted.samples.last() < fitted.samples[0])
    }

    @Test
    fun `fitToWindow giu nguyen clip ngan hon cua so`() {
        val clip = NarrationTimeline.Clip(FloatArray(100) { 0.5f }, 1_000, 250)
        val fitted = NarrationTimeline.fitToWindow(clip, 2_000L)
        assertEquals(clip.sampleRate, fitted.sampleRate)
        assertEquals(clip.startMs, fitted.startMs)
        assertTrue(fitted.samples.contentEquals(clip.samples))
    }

    @Test
    fun `trimSilence bo doan im lang dau cuoi`() {
        val samples = FloatArray(600) { 0f }
        for (i in 200..399) samples[i] = 0.8f
        val clip = NarrationTimeline.Clip(samples, 1_000, 120)

        val trimmed = NarrationTimeline.trimSilence(clip, padMs = 0L)
        assertEquals(200, trimmed.samples.size)
        assertEquals(0.8f, trimmed.samples.first(), 0.0001f)
        assertEquals(0.8f, trimmed.samples.last(), 0.0001f)
        assertEquals(120L, trimmed.startMs)
    }

    @Test
    fun `duck am goc khi co tieng doc`() {
        val clip = NarrationTimeline.Clip(FloatArray(1_000) { 0f }, 1_000, 0)
        val out = NarrationTimeline.render(
            clips = listOf(clip),
            background = FloatArray(2_000) { 1f },
            backgroundSampleRate = 1_000,
            backgroundGain = 0.2f,
            durationMs = 2_000,
            outputSampleRate = 1_000
        )
        // Ngoài vùng có lời: 20% nguyên vẹn. Trong vùng có lời: 20% x 25% = 5%.
        assertEquals(0.2f, out[1_500], 0.0001f)
        assertEquals(0.05f, out[500], 0.0001f)
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
