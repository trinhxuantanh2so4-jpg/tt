package com.example.tt.subtitle

import java.util.Base64

/**
 * Luu cau goc cua moi cue vao file phu, khong lam thay doi dinh dang .srt
 * (ExoPlayer se doc dung phan text da dich). Dang: moi dong la mot cau duoc Base64 URL-safe.
 * Khong import Android -> dung duoc trong unit test JVM thuần.
 */
object SubtitleSourceCodec {

    // Base64 cua chuoi rong la "" -> bi dong trong bo qua khi doc. Danh dau rieng de khong truot index.
    private const val EMPTY_MARKER = "-"

    fun encode(sources: List<String>): String =
        sources.joinToString("\n") { source ->
            if (source.isEmpty()) EMPTY_MARKER
            else Base64.getUrlEncoder()
                .encodeToString(source.toByteArray(Charsets.UTF_8))
        }

    fun decode(content: String): List<String> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                if (line == EMPTY_MARKER) ""
                else runCatching {
                    String(Base64.getUrlDecoder().decode(line), Charsets.UTF_8)
                }.getOrNull()
            }
            .toList()
}
