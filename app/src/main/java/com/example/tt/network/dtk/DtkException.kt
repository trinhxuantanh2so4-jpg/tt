package com.example.tt.network.dtk

import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * Lỗi từ provider DTK. `retryable` quyết định có fallback về provider cũ hay không:
 * lỗi hạ tầng → có; lỗi về nội dung/key → không (xem docs/PLAN_API_DTK.md §6).
 */
class DtkException(
    val code: String,
    message: String,
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null
) : Exception(message) {
    val messageVi: String = message
}

object DtkErrors {

    private val RETRYABLE = setOf(
        "RATE_LIMITED",
        "QUEUE_FULL",
        "IDENTITY_POOL_EXHAUSTED",
        "ENDPOINT_CIRCUIT_OPEN",
        "UPSTREAM_RISK_CONTROL",
        "UPSTREAM_UNREACHABLE",
        "SIGNING_FAILED",
        "INTERNAL",
        "NETWORK",
        "TIMEOUT",
        "BAD_RESPONSE",
        "TASK_STATE",
        "TASK_NOT_FOUND"
    )

    /** Chuyển `error.code` thành lỗi đã dịch sẵn; KHÔNG branch theo error.message. */
    fun from(error: DtkError?): DtkException {
        val code = error?.code?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        return DtkException(
            code = code,
            message = messageFor(code, error?.message),
            retryable = code in RETRYABLE,
            retryAfterSeconds = retryAfterSeconds(error)
        )
    }

    /** Envelope báo lỗi kèm HTTP code (4xx/5xx hoặc body không parse được). */
    fun fromHttp(httpCode: Int, errorBody: String?, fallback: String? = null): DtkException {
        val parsed = runCatching {
            Gson().fromJson(errorBody, DtkEnvelope::class.java) as? DtkEnvelope<*>
        }.getOrNull()
        if (parsed?.error != null) return from(parsed.error)

        val code = when (httpCode) {
            401 -> "UNAUTHENTICATED"
            403 -> "FORBIDDEN_SCOPE"
            404 -> "NOT_FOUND"
            429 -> "RATE_LIMITED"
            else -> if (httpCode >= 500) "INTERNAL" else "HTTP_$httpCode"
        }
        return DtkException(
            code = code,
            message = messageFor(code, fallback),
            retryable = code in RETRYABLE,
            retryAfterSeconds = parsed?.error?.let { retryAfterSeconds(it) }
        )
    }

    /** `retry_after` có thể là số hoặc chuỗi; trả về giây, clamp >= 0. */
    fun retryAfterSeconds(error: DtkError?): Long? {
        val value: JsonElement = error?.retry_after ?: return null
        if (!value.isJsonPrimitive) return null
        val p = value.asJsonPrimitive
        val seconds = if (p.isNumber) {
            runCatching { p.asNumber.toLong() }.getOrNull()
        } else if (p.isString) {
            p.asString.trim().toLongOrNull()
        } else {
            null
        }
        return seconds?.coerceAtLeast(0L)
    }

    private fun messageFor(code: String, serverMessage: String?): String = when (code) {
        "RATE_LIMITED" -> "Máy chủ API đang giới hạn request, vui lòng thử lại sau"
        "QUEUE_FULL" -> "Máy chủ API đang quá tải, vui lòng thử lại sau"
        "IDENTITY_POOL_EXHAUSTED" -> "Máy chủ API hết phiên truy cập, vui lòng thử lại sau"
        "ENDPOINT_CIRCUIT_OPEN" -> "Máy chủ API đang tạm ngừng yêu cầu này"
        "UPSTREAM_RISK_CONTROL" -> "Nền tảng đang chặn truy cập, vui lòng thử lại sau"
        "UPSTREAM_UNREACHABLE" -> "Máy chủ API không kết nối được nền tảng"
        "SIGNING_FAILED" -> "Máy chủ API không ký được yêu cầu, vui lòng thử lại"
        "INTERNAL", "HTTP_500", "HTTP_502", "HTTP_503", "HTTP_504" -> "Máy chủ API gặp lỗi, vui lòng thử lại sau"
        "NETWORK" -> "Không kết nối được máy chủ API"
        "TIMEOUT" -> "Máy chủ API phản hồi quá chậm, vui lòng thử lại"

        "INVALID_URL" -> "Link không hợp lệ hoặc không được hỗ trợ"
        "UNSUPPORTED_CONTENT" -> "Nền tảng không cung cấp nội dung này"
        "NOT_FOUND" -> "Không tìm thấy video"
        "CONTENT_PRIVATE" -> "Video riêng tư, không thể tải"
        "MEDIA_WITHHELD" -> "Video không cho phép tải"
        "IDENTITY_LOGIN_REQUIRED" -> "Cần tài khoản đăng nhập trên máy chủ API để xem nội dung này"
        "INVALID_PARAM" -> "Tham số không hợp lệ"
        "PLATFORM_MISMATCH" -> "Link không đúng nền tảng đang chọn"

        "UNAUTHENTICATED" -> "Key API hết hạn hoặc không hợp lệ — kiểm tra trong màn Nguồn tải video"
        "FORBIDDEN_SCOPE" -> "Key API thiếu quyền douyin:read hoặc tiktok:read"

        "TASK_NOT_FOUND" -> "Kết quả phân tích đã hết hạn, vui lòng thử lại"
        "TASK_STATE" -> "Máy chủ API trả trạng thái tác vụ không hợp lệ"
        "BAD_RESPONSE" -> "Máy chủ API trả dữ liệu không đúng định dạng"

        else -> serverMessage?.takeIf { it.isNotBlank() }?.let { "Lỗi từ máy chủ API: $it" }
            ?: "Lỗi từ máy chủ API ($code)"
    }
}
