package com.example.tt.subtitle

import kotlin.math.abs
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
    // Khi có lời thuyết minh thì hạ âm gốc xuống còn 25% của 20% (~5%) để
    // giọng đọc không bị lời gốc chồng lên, nhưng vẫn nghe thấy nhạc nền.
    private const val DUCK_FACTOR = 0.25f
    private const val DUCK_PAD_MS = 60L

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
        // Mat na duck: 1f = nghe am goc binh thuong, DUCK_FACTOR = dang co tieng doc.
        val duck = FloatArray(total) { 1f }

        // 1) Danh dau vung co tieng doc de duck am goc (truoc khi tron).
        for (clip in clips) {
            if (clip.samples.isEmpty() || clip.sampleRate <= 0) continue
            val offset = (clip.startMs.coerceAtLeast(0) * outputSampleRate / 1000L).toInt()
            if (offset >= total) continue
            val count = min(
                ((clip.samples.size.toLong() * outputSampleRate) / clip.sampleRate).toInt(),
                total - offset
            )
            val pad = (DUCK_PAD_MS * outputSampleRate / 1000L).toInt()
            val from = (offset - pad).coerceAtLeast(0)
            val to = (offset + count + pad).coerceAtMost(total)
            for (i in from until to) duck[i] = DUCK_FACTOR
        }

        // 2) Am goc lam nen (duck trong vung co lời).
        if (background != null && background.isNotEmpty() && backgroundGain > 0f) {
            val last = background.lastIndex
            for (i in 0 until total) {
                val src = i.toDouble() * backgroundSampleRate / outputSampleRate
                val i0 = src.toInt()
                if (i0 > last) break
                val i1 = min(i0 + 1, last)
                val frac = (src - i0).toFloat()
                val bg = (background[i0] * (1f - frac) + background[i1] * frac)
                out[i] += bg * backgroundGain * duck[i]
            }
        }

        // 3) Tung cau thuyet minh dat dung moc startMs
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

        // 4) Chuan hoa dinh <= 0.9 de khong bi vo tieng
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

    /**
     * Bỏ đoạn im lặng đầu/cuối mà engine TTS thường chèn vào file WAV.
     * Chính đoạn im lặng này làm câu nói bị trễ khỏi mốc startMs của phụ đề.
     * Giữ lại [padMs] ở hai đầu để không bị cụt hơi.
     *
     * Hàm thuần Kotlin, test được trên JVM.
     */
    fun trimSilence(
        clip: Clip,
        threshold: Float = 0.02f,
        padMs: Long = 20L
    ): Clip {
        if (clip.samples.isEmpty()) return clip
        var first = 0
        while (first < clip.samples.size && abs(clip.samples[first]) < threshold) first++
        var last = clip.samples.lastIndex
        while (last >= first && abs(clip.samples[last]) < threshold) last--
        if (last < first) return clip.copy(samples = FloatArray(0))

        val pad = (padMs * clip.sampleRate / 1_000L).toInt()
        val from = (first - pad).coerceAtLeast(0)
        val to = (last + pad).coerceAtMost(clip.samples.lastIndex)
        return clip.copy(samples = clip.samples.copyOfRange(from, to + 1))
    }

    /**
     * Đưa clip TTS vào đúng cửa sổ của câu phụ đề:
     *  1) cắt im lặng đầu/cuối (nguyên nhân chính gây trễ),
     *  2) nếu vẫn dài hơn cửa sổ thì cắt đuôi + fade-out ngắn.
     *
     * Không đổi sampleRate: đổi sampleRate làm giọng bị cao/chói ("rè") — lỗi
     * đã gặp ở bản trước. Việc chỉnh tốc độ được làm ở TTS (speechRate) ở bước
     * synthesize, ở đây chỉ đảm bảo không chồng sang câu kế tiếp.
     */
    fun fitToWindow(clip: Clip, windowMs: Long): Clip {
        if (clip.samples.isEmpty() || clip.sampleRate <= 0 || windowMs <= 0) return clip
        val trimmed = trimSilence(clip)
        if (trimmed.samples.isEmpty()) return trimmed

        val naturalMs = trimmed.samples.size.toLong() * 1_000L / trimmed.sampleRate
        val usableMs = windowMs.coerceAtLeast(300L)
        if (naturalMs <= usableMs) return trimmed

        val keep = (usableMs * trimmed.sampleRate / 1_000L).toInt()
            .coerceIn(1, trimmed.samples.size)
        val kept = trimmed.samples.copyOf(keep)
        val fade = (25L * trimmed.sampleRate / 1_000L).toInt().coerceIn(1, keep)
        for (i in keep - fade until keep) {
            kept[i] *= (keep - i).toFloat() / fade.toFloat()
        }
        return trimmed.copy(samples = kept)
    }

    /** Uoc luong so giay can de doc het cac cau (chi de hien thi tien do). */
    fun estimateSeconds(texts: List<String>, charsPerSecond: Float = 14f): Int =
        (texts.sumOf { it.length } / charsPerSecond).roundToInt()
}
