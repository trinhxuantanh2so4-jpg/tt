package com.example.tt.ui

import android.text.format.DateUtils
import java.util.Locale
import kotlin.math.roundToInt

/** Dinh dang dung luong: "850 KB", "12.4 MB", "1.2 GB" */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%d KB", kb.roundToInt().coerceAtLeast(1))
    }
}

/** Toc do tai: "1.8 MB/s" */
fun formatSpeed(bytesPerSecond: Float): String {
    if (bytesPerSecond <= 0f) return ""
    val kb = bytesPerSecond / 1024f
    return if (kb >= 1024f) {
        String.format(Locale.getDefault(), "%.1f MB/s", kb / 1024f)
    } else {
        String.format(Locale.getDefault(), "%d KB/s", kb.roundToInt())
    }
}

/** Thoi luong video: 7 -> "0:07", 75 -> "1:15" */
fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}

/** Thoi gian tuong doi: "2 giờ trước" */
fun relativeTime(timestamp: Long): CharSequence =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )

/** Kiem tra nhanh mot chuoi co phai link TikTok khong */
fun isTikTokUrl(text: String): Boolean =
    text.contains("tiktok.com", ignoreCase = true) ||
        text.contains("vm.tiktok.com", ignoreCase = true)

/** Tach link TikTok dau tien trong mot doan text (vd: text chia se tu app TikTok) */
fun extractTikTokUrl(text: String): String? {
    val regex = Regex("""https?://[^\s]*tiktok\.com[^\s]*""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.value
}
