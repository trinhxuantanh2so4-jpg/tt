package com.example.tt.data

/**
 * Model 1 dòng trong bảng "downloads" (SQLite).
 */
data class DownloadRecord(
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceUrl: String,      // link TikTok gốc
    val downloadUrl: String,    // link video không logo
    val quality: String,        // "HD" | "NO_WATERMARK"
    val filePath: String = "", // đường dẫn file đã lưu
    val fileSize: Long,         // bytes
    val favorite: Int = 0,      // 0/1
    val createdAt: Long = System.currentTimeMillis()
)
