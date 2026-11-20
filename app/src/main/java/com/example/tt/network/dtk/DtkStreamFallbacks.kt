package com.example.tt.network.dtk

import java.util.concurrent.ConcurrentHashMap

/**
 * URL CDN của DTK đã ký và có thể hết hạn trong vài giờ. Nếu link chính trả 403,
 * VideoDownloader sẽ thử các mirrorDTK trả trong `video.urls` / `streams[].urls`.
 */
object DtkStreamFallbacks {

    private val mirrors = ConcurrentHashMap<String, List<String>>()

    fun remember(primary: String, alternates: List<String>) {
        if (primary.isBlank()) return
        if (alternates.isEmpty()) mirrors.remove(primary) else mirrors[primary] = alternates
    }

    fun alternatesFor(url: String): List<String> = mirrors[url] ?: emptyList()

    fun clear() = mirrors.clear()
}
