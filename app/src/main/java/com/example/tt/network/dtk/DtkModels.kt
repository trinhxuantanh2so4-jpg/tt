package com.example.tt.network.dtk

import com.google.gson.JsonElement

/**
 * Contract JSON v5 của Douyin_TikTok_Download_API (DTK).
 * Mọi field có default để Gson tạo được instance ngay cả khi response thiếu field.
 * Không import Android — dùng được trong unit test JVM.
 */

data class DtkEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: DtkError? = null,
    val meta: DtkMeta? = null
)

data class DtkMeta(
    val request_id: String? = null,
    val cached: Boolean? = null,
    val duration_ms: Long? = null
)

/** `error` trong envelope và trong task bị failed. */
data class DtkError(
    val code: String? = null,
    val message: String? = null,
    val retry_after: JsonElement? = null,
    val details: JsonElement? = null
)

/** Kết quả ngay khi submit: HTTP 202 + task_id. */
data class DtkTaskRef(
    val task_id: String? = null,
    val state: String? = null
)

/** `body.data` khi poll `/api/v1/tasks/{id}`. `data` ở đây là Content. */
data class DtkTask(
    val task_id: String? = null,
    val state: String? = null,
    val endpoint: String? = null,
    val data: JsonElement? = null,
    val error: DtkError? = null
)

/** `/api/v1/auth/me` → `data.user`. */
data class DtkAuthUser(
    val id: String? = null,
    val username: String? = null,
    val role: String? = null,
    val scopes: List<String>? = null,
    val via: String? = null
)

data class DtkAuthInfo(
    val user: DtkAuthUser? = null,
    val rate_limit_per_min: Int? = null
)

/** Schema `Content` chuẩn của DTK (src/dtk/models/content.py). */
data class DtkContent(
    val platform: String? = null,
    val content_id: String? = null,
    val kind: String? = null,
    val web_url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val duration_ms: Long? = null,
    val author: DtkAuthor? = null,
    val media: DtkMedia? = null
)

data class DtkAuthor(
    val uid: String? = null,
    val sec_uid: String? = null,
    val unique_id: String? = null,
    val nickname: String? = null,
    val avatar: DtkImage? = null
)

data class DtkImage(
    val url: String? = null,
    val urls: List<String>? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class DtkMedia(
    val covers: List<DtkImage>? = null,
    val video: DtkVideoStream? = null,
    val streams: List<DtkVideoStream>? = null
)

data class DtkVideoStream(
    val url: String? = null,
    val urls: List<String>? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val size_bytes: Long? = null,
    val watermark: Boolean? = null
)
