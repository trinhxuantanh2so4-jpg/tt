package com.example.tt.subtitle

/**
 * Chuyen cac dong nhan dang cua Moonshine (startTime/duration tinh bang GIANY)
 * thanh danh sach [SubtitleCue] (tinh bang MILI-GIAY).
 *
 * - Bo qua dong rong / khong co text.
 * - Dam bao moi cue co do dai toi thieu [minDurationMs] de hien thi duoc.
 * - Dam bao cac cue khong chen nhau (end cua cai truoc <= start cai sau).
 */
object CueBuilder {

    private const val MIN_DURATION_MS = 600L

    /**
     * @param lines  danh sach (startTime giay, duration giay, text)
     * @param translations ban dich tuong ung theo index; null/blank se giu text goc
     */
    fun build(
        lines: List<RawLine>,
        translations: List<String?> = emptyList()
    ): List<SubtitleCue> {
        val result = mutableListOf<SubtitleCue>()
        var previousEnd = 0L

        for ((i, line) in lines.withIndex()) {
            val clean = line.text.trim()
            if (clean.isEmpty()) continue

            var start = (line.startSec * 1000f).toLong().coerceAtLeast(0L)
            var end = ((line.startSec + line.durationSec) * 1000f).toLong()

            // Neu engine tra time roi rac, sua lai theo thu tu tang dan
            if (start < previousEnd) start = previousEnd
            if (end - start < MIN_DURATION_MS) end = start + MIN_DURATION_MS
            previousEnd = end

            val translated = translations.getOrNull(i)?.trim()
            val display = if (translated.isNullOrEmpty()) clean else translated

            result.add(
                SubtitleCue(
                    index = result.size + 1,
                    startMs = start,
                    endMs = end,
                    source = clean,
                    text = display
                )
            )
        }
        return result
    }

    /** Dong nhan dang thuan JVM (khong phu thuoc vao lop cua Moonshine). */
    data class RawLine(
        val startSec: Float,
        val durationSec: Float,
        val text: String
    )
}
