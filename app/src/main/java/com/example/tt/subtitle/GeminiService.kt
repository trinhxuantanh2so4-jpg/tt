package com.example.tt.subtitle

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service ket noi Gemini AI de trau chuot phu de.
 * Dung API REST de tranh keo theo SDK nang.
 */
object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MODEL = "gemini-1.5-flash"

    /**
     * Gui danh sach phu de sang Gemini de toi uu cho thuyet minh.
     */
    suspend fun optimizeForNarration(cues: List<SubtitleCue>): List<SubtitleCue> {
        val apiKey = GeminiConfig.loadKey()
        if (apiKey.isBlank()) return cues

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

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3) // Thap de duy tri tinh on dinh
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return cues

            val respBody = response.body?.string() ?: return cues
            val respJson = JSONObject(respBody)
            val candidates = respJson.optJSONArray("candidates") ?: return cues
            if (candidates.length() == 0) return cues
            
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) return cues
            
            val resultText = parts.getJSONObject(0).getString("text").trim()
            val resultLines = resultText.lines().filter { it.isNotBlank() }
            
            if (resultLines.size != cues.size) {
                // Neu Gemini tra ve so dong khong khop, ta bo qua de tranh lech phu de
                return cues
            }

            cues.mapIndexed { index, cue ->
                cue.copy(text = resultLines[index])
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cues
        }
    }
}
