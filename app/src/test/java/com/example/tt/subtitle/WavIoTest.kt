package com.example.tt.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

class WavIoTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `ghi wav mono 16-bit dung kich thuoc header`() {
        val file = folder.newFile("out.wav")
        WavIo.write(file, FloatArray(100) { 0f }, 24_000)
        // 44 byte header + 100 sample * 2 byte
        assertEquals(44L + 100 * 2, file.length())
    }

    @Test
    fun `doc lai dung sample da ghi`() {
        val file = folder.newFile("roundtrip.wav")
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f)
        WavIo.write(file, samples, 16_000)
        val pcm = WavIo.read(file)
        assertEquals(16_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(samples.size, pcm.samples.size)
        for (i in samples.indices) {
            assertTrue(
                "sample $i lệch quá nhiều: ${pcm.samples[i]}",
                abs(pcm.samples[i] - samples[i]) < 0.0001f
            )
        }
    }

    @Test
    fun `cat bien gia tri ngoai khoang -1 den 1`() {
        val file = folder.newFile("clip.wav")
        WavIo.write(file, floatArrayOf(2f, -2f), 8_000)
        val pcm = WavIo.read(file)
        assertTrue(pcm.samples[0] <= 1f)
        assertTrue(pcm.samples[1] >= -1f)
    }
}
