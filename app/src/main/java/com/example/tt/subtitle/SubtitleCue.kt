package com.example.tt.subtitle

/**
 * Mot dong phu de (1 cue SRT).
 *
 * @param index  so thu tu bat dau tu 1 (SRT yeu cau)
 * @param startMs thoi diem bat dau, tinh bang mili-giay
 * @param endMs  thoi diem ket thuc, tinh bang mili-giay (phai lon hon startMs)
 * @param source cau goc (ngon ngu nguon, de nguoi dung co the so sanh/sua)
 * @param text   cau hien thi (ban dich, hoac cau goc neu khong dich duoc)
 */
data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val source: String = "",
    val text: String = ""
) {
    /** Ghi ra file .srt de nguoi dung chinh sua tu do. */
    val srtBlock: String
        get() = "$index\n${formatTimestamp(startMs)} --> ${formatTimestamp(endMs)}\n$text\n"

    companion object {
        /** Dang SRT: HH:MM:SS,mmm */
        fun formatTimestamp(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val hours = safe / 3_600_000
            val minutes = (safe % 3_600_000) / 60_000
            val seconds = (safe % 60_000) / 1_000
            val millis = safe % 1_000
            return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
        }
    }
}
