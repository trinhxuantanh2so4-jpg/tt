package com.example.tt.network.dtk

import com.example.tt.data.DownloadRecord
import com.example.tt.network.VideoInfo
import com.example.tt.network.VideoPlatform

/** Chuyển `Content` của DTK sang `VideoInfo` của app. Pure Kotlin/JVM. */
object DtkMapper {

    fun toVideoInfo(
        content: DtkContent,
        sourceUrl: String,
        expectedPlatform: VideoPlatform? = null
    ): VideoInfo {
        val platform = when (content.platform?.trim()?.lowercase()) {
            "douyin" -> VideoPlatform.DOUYIN
            "tiktok" -> VideoPlatform.TIKTOK
            else -> expectedPlatform ?: throw DtkException(
                "PLATFORM_MISMATCH",
                "Máy chủ API không xác định được nền tảng của link",
                retryable = false
            )
        }
        if (expectedPlatform != null && platform != expectedPlatform) {
            throw DtkException("PLATFORM_MISMATCH", "Link không đúng nền tảng đang chọn", retryable = false)
        }

        val kind = content.kind?.trim()?.lowercase().orEmpty()
        if (kind.contains("album") || kind.contains("image")) {
            throw DtkException(
                "UNSUPPORTED_CONTENT",
                "Link này là album ảnh, chưa hỗ trợ tải",
                retryable = false
            )
        }

        val streams = mutableListOf<DtkVideoStream>()
        content.media?.video?.let { streams.add(it) }
        content.media?.streams?.let { streams.addAll(it) }

        // CHỈ lấy stream sạch logo. Không fallback về stream có watermark.
        val clean = streams.firstOrNull { it.watermark != true && !it.url.isNullOrBlank() }
            ?: throw DtkException(
                "MEDIA_WITHHELD",
                "Video riêng tư hoặc đã bị xoá",
                retryable = false
            )
        val downloadUrl = clean.url!!
        val alternates = streams
            .flatMap { listOfNotNull(it.url) + it.urls.orEmpty() }
            .filter { it.isNotBlank() && it != downloadUrl && it.startsWith("http", ignoreCase = true) }
            .distinct()
        DtkStreamFallbacks.remember(downloadUrl, alternates)

        val defaultTitle = if (platform == VideoPlatform.DOUYIN) "Video Douyin" else "Video TikTok"
        val title = content.title?.takeIf { it.isNotBlank() }
            ?: content.description?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: defaultTitle

        val record = DownloadRecord(
            videoId = content.content_id?.takeIf { it.isNotBlank() } ?: sourceUrl,
            title = title,
            author = content.author?.nickname?.takeIf { it.isNotBlank() }
                ?: content.author?.unique_id?.takeIf { it.isNotBlank() }
                ?: "Không rõ",
            coverUrl = content.media?.covers?.firstNotNullOfOrNull { it.url?.takeIf { u -> u.isNotBlank() } }
                ?: "",
            sourceUrl = sourceUrl.ifBlank { content.web_url.orEmpty() },
            downloadUrl = downloadUrl,
            quality = if ((clean.height ?: 0) >= 1080) "HD" else "NO_WATERMARK",
            fileSize = clean.size_bytes ?: 0L,
            platform = platform
        )
        return VideoInfo(record, content.duration_ms?.takeIf { it > 0 }?.div(1000L))
    }
}
