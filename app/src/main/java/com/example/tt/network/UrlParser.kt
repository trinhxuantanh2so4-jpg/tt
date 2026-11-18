package com.example.tt.network

import java.util.Locale

/**
 * Nhan dien va tach link TikTok / Douyin tu text nguoi dung dan hoac text chia se.
 *
 * Text chia se cua Douyin thuong co dang:
 * `7.95 复制打开抖音... https://v.douyin.com/iRNBho6u/ ...`
 * nen khong the chi `trim()` roi coi ca chuoi la URL.
 */
object UrlParser {

    /** Bat URL http(s), dung som o khoang trang / dau ngoac / dau nhay. */
    private val URL_REGEX = Regex(
        """https?://[^\s"'<>()\[\]{}【】（）]+""",
        RegexOption.IGNORE_CASE
    )

    /** Dau cau hay dinh sau URL khi copy tu chat / chia se. */
    private val TRAILING = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '，', '。', '！', '？', '、'
    )

    /** Nen tang cua chuoi (khong can la URL hop le) — null neu khong ho tro. */
    fun platformOf(text: String): VideoPlatform? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT)
        return when {
            // iesdouyin.com cung chua "douyin.com" nen chi can 1 phep kiem tra
            lower.contains("douyin.com") -> VideoPlatform.DOUYIN
            lower.contains("tiktok.com") -> VideoPlatform.TIKTOK
            else -> null
        }
    }

    /** Link TikTok/Douyin dau tien trong doan text (null neu khong co). */
    fun extractUrl(text: String): String? =
        URL_REGEX.findAll(text)
            .map { it.value.trimEnd(*TRAILING) }
            .firstOrNull { it.length > 12 && platformOf(it) != null }

    /** Chuan hoa: bo khoang trang 2 dau, giu nguyen query. */
    fun normalize(url: String): String = url.trim()
}
