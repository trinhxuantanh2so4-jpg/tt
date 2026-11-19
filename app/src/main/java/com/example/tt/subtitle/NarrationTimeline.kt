package com.example.tt.subtitle

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dat tung clip thuyet minh vao timeline video, tron voi am goc (neu giu nen),
 * chuan hoa dinh va tra ve PCM mono.
 *
 * Khong phu thuoc Android -> test duoc tren JVM.
 */
object NarrationTimeline {

    data class Clip(
        val samples: FloatArray,
        val sampleRate: Int,
        val startMs: Long
    )

    const val BACKGROUND_GAIN_DEFAULT = 0.2f
    private const val PEAK_LIMIT = 0.9f

    /**
     * @param durationMs do dai video (ms); track ra dung bang do dai nay
     * @param backgroundGain 0f = chi thuyet minh
     */
    fun render(
        clips: List<Clip>,
        background: FloatArray?,
        backgroundSampleRate: Int,
        backgroundGain: Float,
        durationMs: Long,
        outputSampleRate: Int = 24_000
    ): FloatArray {
        val total = ((durationMs.coerceAtLeast(0) * outputSampleRate) / 1000L).toInt()
        if (total <= 0) return FloatArray(0)

        val out = FloatArray(total)

        // 1) Am goc lam nen
        if (background != null && background.isNotEmpty() && backgroundGain > 0f) {
            val last = background.lastIndex
            for (i in 0 until total) {
                val src = i.toDouble() * backgroundSampleRate / outputSampleRate
                val i0 = src.toInt()
                if (i0 > last) break
                val i1 = min(i0 + 1, last)
                val frac = (src - i0).toFloat()
                out[i] += (background[i0] * (1f - frac) + background[i1] * frac) * backgroundGain
            }
        }

        // 2) Tung cau thuyet minh dat dung moc startMs
        for (clip in clips) {
            if (clip.samples.isEmpty() || clip.sampleRate <= 0) continue
            val offset = (clip.startMs.coerceAtLeast(0) * outputSampleRate / 1000L).toInt()
            if (offset >= total) continue
            val count = min(
                ((clip.samples.size.toLong() * outputSampleRate) / clip.sampleRate).toInt(),
                total - offset
            )
            val last = clip.samples.lastIndex
            for (i in 0 until count) {
                val src = i.toDouble() * clip.sampleRate / outputSampleRate
                val i0 = src.toInt().coerceAtMost(last)
                val i1 = min(i0 + 1, last)
                val frac = (src - i0).toFloat()
                val sample = clip.samples[i0] * (1f - frac) + clip.samples[i1] * frac
                out[offset + i] += sample
            }
        }

        // 3) Chuan hoa dinh <= 0.9 de khong bi vo tieng
        var peak = 0f
        for (sample in out) {
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
        }
        if (peak > PEAK_LIMIT) {
            val scale = PEAK_LIMIT / peak
            for (i in out.indices) out[i] *= scale
        }
        return out
    }

    /** Uoc luong so giay can de doc het cac cau (chi de hien thi tien do). */
    fun estimateSeconds(texts: List<String>, charsPerSecond: Float = 14f): Int =
        (texts.sumOf { it.length } / charsPerSecond).roundToInt()
}
