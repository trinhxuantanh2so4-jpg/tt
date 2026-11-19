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
    // Phase B: lưu xuống SQLite để tab Lịch sử biết nguồn và phụ đề.
    val platform: VideoPlatform = VideoPlatform.TIKTOK,
    val language: String = "",       // mã nguồn đã dùng khi nhận dạng ("zh"/"en")
    val subtitlePath: String = "",   // đường dẫn file .srt (nếu đã tạo)
    val processStatus: String = ""   // "" | "done" | "error"
)
