package com.example.tt.network.dtk

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM: ánh xạ mã lỗi v5 → retryable + message tiếng Việt. */
class DtkErrorTest {

    private val gson = Gson()

    @Test
    fun `loi ha tang duoc phep fallback`() {
        val codes = listOf(
            "RATE_LIMITED", "QUEUE_FULL", "IDENTITY_POOL_EXHAUSTED", "ENDPOINT_CIRCUIT_OPEN",
            "UPSTREAM_RISK_CONTROL", "UPSTREAM_UNREACHABLE", "SIGNING_FAILED", "INTERNAL",
            "NETWORK", "TIMEOUT", "BAD_RESPONSE", "TASK_STATE", "TASK_NOT_FOUND"
        )
        codes.forEach { code ->
            val e = DtkErrors.from(DtkError(code = code, message = "raw upstream message"))
            assertEquals(code, e.code)
            assertTrue("$code phải retryable", e.retryable)
            assertTrue("$code phải có message VI", e.messageVi.isNotBlank())
            assertFalse("Không được lộ message thô", e.messageVi.contains("raw upstream"))
        }
    }

    @Test
    fun `loi noi dung va key khong fallback`() {
        val codes = listOf(
            "INVALID_URL", "UNSUPPORTED_CONTENT", "NOT_FOUND", "CONTENT_PRIVATE",
            "MEDIA_WITHHELD", "IDENTITY_LOGIN_REQUIRED", "INVALID_PARAM",
            "UNAUTHENTICATED", "FORBIDDEN_SCOPE"
        )
        codes.forEach { code ->
            val e = DtkErrors.from(DtkError(code = code, message = "raw"))
            assertEquals(code, e.code)
            assertFalse("$code không được retryable", e.retryable)
            assertTrue(e.messageVi.isNotBlank())
        }
    }

    @Test
    fun `retry_after nhan ca so va chuoi`() {
        assertEquals(12L, DtkErrors.retryAfterSeconds(DtkError(retry_after = gson.toJsonTree(12))) ?: -1L)
        assertEquals(7L, DtkErrors.retryAfterSeconds(DtkError(retry_after = gson.toJsonTree("7"))) ?: -1L)
        assertEquals(0L, DtkErrors.retryAfterSeconds(DtkError(retry_after = gson.toJsonTree(-3))) ?: -1L)
        assertEquals(null, DtkErrors.retryAfterSeconds(DtkError(retry_after = gson.toJsonTree("abc"))))
    }

    @Test
    fun `http 401 403 429 500 duoc map dung`() {
        val cases = mapOf(
            401 to "UNAUTHENTICATED",
            403 to "FORBIDDEN_SCOPE",
            404 to "NOT_FOUND",
            429 to "RATE_LIMITED",
            503 to "INTERNAL"
        )
        cases.forEach { (http, code) ->
            val e = DtkErrors.fromHttp(http, """{"success":false,"error":{"code":"$code"}}""")
            assertEquals(code, e.code)
            assertTrue(e.messageVi.isNotBlank())
        }
    }

    @Test
    fun `body loi co code rieng thi uu tien code do`() {
        val e = DtkErrors.fromHttp(
            500,
            """{"success":false,"error":{"code":"QUEUE_FULL","retry_after":5}}"""
        )
        assertEquals("QUEUE_FULL", e.code)
        assertEquals(5L, e.retryAfterSeconds)
        assertTrue(e.retryable)
    }

    @Test
    fun `task failed van success true nhung error van duoc doc`() {
        val json = """
            {
              "success": true,
              "data": {
                "task_id": "abc",
                "state": "failed",
                "error": {"code": "CONTENT_PRIVATE", "message": "private"}
              }
            }
        """.trimIndent()
        val root = JsonParser.parseString(json).asJsonObject
        val task = gson.fromJson(root.getAsJsonObject("data"), DtkTask::class.java)

        assertEquals("failed", task?.state)
        assertNotNull(task?.error)
        val e = DtkErrors.from(task?.error)
        assertEquals("CONTENT_PRIVATE", e.code)
        assertFalse(e.retryable)
    }

    @Test
    fun `task done co payload data data`() {
        val json = """
            {"success":true,"data":{"task_id":"abc","state":"done","data":{"content_id":"7345","platform":"douyin"}}}
        """.trimIndent()
        val root = JsonParser.parseString(json).asJsonObject
        val task = gson.fromJson(root.getAsJsonObject("data"), DtkTask::class.java)
        val content = task?.data?.let { gson.fromJson(it, DtkContent::class.java) }

        assertEquals("done", task?.state)
        assertEquals("7345", content?.content_id)
        assertEquals("douyin", content?.platform)
    }
}
