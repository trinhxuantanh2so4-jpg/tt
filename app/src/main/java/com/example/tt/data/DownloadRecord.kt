package com.example.tt.data

import com.example.tt.network.VideoPlatform

/**
 * Model 1 dòng trong bảng "downloads" (SQLite).
 */
data class DownloadRecord(
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceUrl: String,      // link gốc (TikTok hoặc Douyin)
    val downloadUrl: String,    // link video không logo
    val quality: String,        // "HD" | "NO_WATERMARK"
    val filePath: String = "", // đường dẫn file đã lưu
    val fileSize: Long,         // bytes
    val favorite: Int = 0,      // 0/1
    val createdAt: Long = System.currentTimeMillis(),
    // Phase A: chỉ dùng trong bộ nhớ để hiện chip + đặt tên file.
    // Cột này sẽ được lưu vào SQLite ở migration v2 (Phase B).
    val platform: VideoPlatform = VideoPlatform.TIKTOK
)
