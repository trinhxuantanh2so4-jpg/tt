package com.example.tt.subtitle

/** Tao noi dung file .srt (UTF-8) tu danh sach cue. */
object SrtWriter {

    fun write(cues: List<SubtitleCue>): String =
        if (cues.isEmpty()) ""
        else cues.joinToString(separator = "\n") { it.srtBlock }.trimEnd('\n') + "\n"

    /**
     * Doc lai file .srt da nguoi dung chinh sua (moi dong text co the co nhieu dong).
     * Chi can thay doi phan text la duoc; thoi gian giu nguyen.
     */
    fun parse(content: String): List<SubtitleCue> {
        val blocks = content.trim().split(Regex("\\R\\s*\\R"))
        val result = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue
            val arrow = lines.getOrNull(1) ?: continue
            val parts = arrow.split("-->")
            if (parts.size != 2) continue
            val start = parseTimestamp(parts[0].trim()) ?: continue
            val end = parseTimestamp(parts[1].trim()) ?: continue
            val text = lines.drop(2).joinToString("\n").trim()
            if (text.isEmpty()) continue
            result.add(
                SubtitleCue(
                    index = lines[0].trim().toIntOrNull() ?: (result.size + 1),
                    startMs = start,
                    endMs = end,
                    text = text
                )
            )
        }
        return result
    }

    private fun parseTimestamp(value: String): Long? {
        // Dinh dang: HH:MM:SS,mmm (chap nhan ca '.' thay cho ',')
        val match = Regex("(\\d+):(\\d{2}):(\\d{2})[.,](\\d{3})").matchEntire(value)
            ?: return null
        val (h, m, s, ms) = match.destructured
        return h.toLong() * 3_600_000 + m.toLong() * 60_000 + s.toLong() * 1_000 + ms.toLong()
    }
}
