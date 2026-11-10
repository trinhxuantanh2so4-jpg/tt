package com.example.tt.data

/**
 * Model 1 dong trong bang "downloads" (SQLite).
 */
data class DownloadRecord(
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceUrl: String,      // link TikTok goc
    val downloadUrl: String,    // link video khong logo
    val quality: String,        // "HD" | "NO_WATERMARK"
    val filePath: String,      // duong dan file da luu
    val fileSize: Long,         // bytes
    val favorite: Int = 0,      // 0/1
    val createdAt: Long = System.currentTimeMillis()
)
