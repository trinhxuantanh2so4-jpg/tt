package com.example.tt.subtitle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiServiceTest {

    private val fakeKey = "AIza" + "x".repeat(30)
    private val fakeStudioKey = "AQ." + "y".repeat(30)

    @Test
    fun `sanitize xoa key trong URL va trong message`() {
        val raw = "Loi goi https://api/x?key=$fakeKey voi api_key=$fakeStudioKey"
        val clean = GeminiService.sanitize(raw)

        assertFalse(clean.contains(fakeKey))
        assertFalse(clean.contains(fakeStudioKey))
        assertTrue(clean.contains("***"))
    }

    @Test
    fun `loi HTTP co ma va goi y bang tieng Viet`() {
        val body = """
            {"error":{"code":404,"message":"models/gemini-1.5-flash is not found","status":"NOT_FOUND"}}
        """.trimIndent()
        val message = GeminiService.describeHttpError(404, body)

        assertTrue(message.contains("HTTP 404"))
        assertTrue(message.contains("mã NOT_FOUND"))
        assertTrue(message.contains("model"))
        assertFalse(message.contains("key="))
    }

    @Test
    fun `message loi tu Google khong biet lo key`() {
        val body = """
            {"error":{"code":400,"message":"API key not valid with key=$fakeKey","status":"INVALID_ARGUMENT"}}
        """.trimIndent()
        val message = GeminiService.describeHttpError(400, body)

        assertTrue(message.startsWith("HTTP 400"))
        assertTrue(message.contains("mã INVALID_ARGUMENT"))
        assertFalse(message.contains(fakeKey))
    }

    @Test
    fun `body khong phai JSON van tra ve thong bao HTTP`() {
        val message = GeminiService.describeHttpError(503, "<html>bad gateway</html>")

        assertTrue(message.startsWith("HTTP 503"))
        assertTrue(message.contains("máy chủ Gemini"))
    }
}
