package com.example.tt.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service ket noi Gemini AI de trau chuot phu de.
 * Dung API REST de tranh keo theo SDK nang.
 *
 * Luu y: gemini-1.5-flash da bi nguon (shutdown) tu 2025-09-29. Tu Gemini 3,
 * cac tham so sampling (temperature/top_p/top_k) khong con duoc ho tro.
 */
object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Model stable, re tien, phu hop voi viec bien tap van ban ngan.
    private const val MODEL = "gemini-3.5-flash-lite"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /** Ket qua kiem tra key, kem thong bao loi da duoc lam sach (khong lo key). */
    sealed interface GeminiResult<out T> {
        data class Success<T>(val value: T) : GeminiResult<T>
        data class Failure(val message: String) : GeminiResult<Nothing>
    }

    private class GeminiApiException(message: String) : Exception(message)

    /**
     * Goi API va tra ve body tho. Nem [GeminiApiException] voi thong bao da sanitize
     * de UI co the hien ly do that (khong phai "key khong hop le" chung chung).
     */
    private fun execute(prompt: String, apiKey: String): String {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            // Co tinh khong gui generationConfig: temperature da bi loai bo tu Gemini 3.
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            // Gui key qua header de khong xuat hien trong URL / log.
            .header("x-goog-api-key", apiKey)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw GeminiApiException("Không kết nối được tới Gemini: ${safeMessage(e)}")
        }

        response.use { resp ->
            val body = try {
                resp.body?.string().orEmpty()
            } catch (e: Exception) {
                throw GeminiApiException("Không đọc được phản hồi Gemini: ${safeMessage(e)}")
            }
            if (!resp.isSuccessful) {
                throw GeminiApiException(describeHttpError(resp.code, body))
            }
            return body
        }
    }

    /** Kiem tra key bang mot request nho nhat. */
    suspend fun validateKey(apiKey: String): GeminiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            execute("Hi", apiKey)
            GeminiResult.Success(Unit)
        } catch (e: GeminiApiException) {
            GeminiResult.Failure(e.message ?: "Lỗi không xác định")
        } catch (e: Exception) {
            GeminiResult.Failure(safeMessage(e))
        }
    }

    /**
     * Gui danh sach phu de sang Gemini de toi uu cho thuyet minh.
     * That bai thi giu nguyen phu de cu (khong lam gian doan pipeline).
     */
    suspend fun optimizeForNarration(cues: List<SubtitleCue>): List<SubtitleCue> =
        withContext(Dispatchers.IO) {
            val apiKey = GeminiConfig.loadKey()
            if (apiKey.isBlank() || cues.isEmpty()) return@withContext cues

            val prompt = """
                Bạn là một biên tập viên thuyết minh phim chuyên nghiệp.
                Dưới đây là danh sách phụ đề tiếng Việt (được dịch máy).
                Hãy biên tập lại nội dung để câu văn trôi chảy, tự nhiên, mang phong cách ngôn ngữ nói và dễ nghe khi đọc bằng giọng nhân tạo (TTS).

                Yêu cầu nghiêm ngặt:
                1. Giữ nguyên số lượng dòng và thứ tự.
                2. Mỗi dòng kết quả chỉ chứa phần văn bản đã biên tập, không thêm số thứ tự hay ghi chú.
                3. Nếu câu đã ổn, hãy giữ nguyên.
                4. Chỉ trả về các dòng văn bản, phân cách bằng dấu xuống dòng.

                Danh sách phụ đề:
                ${cues.joinToString("\n") { it.text }}
            """.trimIndent()

            try {
                val body = execute(prompt, apiKey)
                val respJson = JSONObject(body)
                val candidates = respJson.optJSONArray("candidates") ?: return@withContext cues
                if (candidates.length() == 0) return@withContext cues

                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext cues
                val parts = content.optJSONArray("parts") ?: return@withContext cues
                if (parts.length() == 0) return@withContext cues

                val resultText = parts.getJSONObject(0).optString("text").trim()
                val resultLines = resultText.lines().filter { it.isNotBlank() }

                if (resultLines.size != cues.size) {
                    // Neu Gemini tra ve so dong khong khop, bo qua de tranh lech phu de.
                    return@withContext cues
                }

                cues.mapIndexed { index, cue -> cue.copy(text = resultLines[index]) }
            } catch (e: GeminiApiException) {
                System.err.println("Gemini optimize failed: ${e.message}")
                cues
            } catch (e: Exception) {
                System.err.println("Gemini parse failed: ${safeMessage(e)}")
                cues
            }
        }

    /** Mo ta loi HTTP thanh tieng Viet, uu tien lay message tu body cua Google. */
    internal fun describeHttpError(code: Int, rawBody: String): String {
        var apiMessage = ""
        var apiStatus = ""
        try {
            val error = JSONObject(rawBody).optJSONObject("error")
            apiMessage = error?.optString("message").orEmpty()
            apiStatus = error?.optString("status").orEmpty()
        } catch (_: Exception) {
            // Body co the la HTML cua proxy -> bo qua.
        }

        val hint = when (code) {
            400 -> "yêu cầu không hợp lệ"
            401, 403 -> "key không hợp lệ, hết hạn hoặc chưa bật Gemini API"
            404 -> "model không tồn tại hoặc key chưa có quyền truy cập"
            429 -> "vượt hạn mức / hết quota miễn phí"
            in 500..599 -> "máy chủ Gemini đang lỗi"
            else -> "lỗi HTTP"
        }

        val parts = mutableListOf("HTTP $code ($hint)")
        if (apiStatus.isNotBlank()) parts += "mã $apiStatus"
        if (apiMessage.isNotBlank()) parts += sanitize(apiMessage)
        return parts.joinToString(" — ").take(300)
    }

    /** Xoa cac chuoi mang tinh key truoc khi hien len UI / ghi log. */
    internal fun sanitize(raw: String): String {
        var text = raw

        // ?key=... / &api_key=... trong URL
        text = Regex("(?i)([?&](?:key|api_key|apikey)=)[^&\\s\"']+")
            .replace(text) { "${it.groupValues[1]}***" }

        // "x-goog-api-key: ..." hoac "key = ..."
        text = Regex("(?i)((?:x-goog-api-key|api[_-]?key|key)\\s*[:=]\\s*)[^\\s,;\"'}&]+")
            .replace(text) { "${it.groupValues[1]}***" }

        // Dinh dang key cua Google (AIza..., AQ....)
        text = Regex("AIza[0-9A-Za-z_\\-]{10,}").replace(text, "***")
        text = Regex("AQ\\.[0-9A-Za-z_\\-]{8,}").replace(text, "***")

        return text.take(300)
    }

    private fun safeMessage(e: Exception): String =
        sanitize(e.message?.takeIf { it.isNotBlank() } ?: "Lỗi không xác định")
}
