package com.example.tt.network.dtk

import com.example.tt.network.VideoInfo
import com.example.tt.network.VideoPlatform
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Provider DTK: submit link → (nếu 202) poll task → map sang VideoInfo.
 * Gọi từ VideoResolver khi người dùng đã cấu hình máy chủ trong màn Nguồn tải video.
 */
object DtkResolver {

    private const val WAIT_SECONDS = 20
    private const val POLL_INTERVAL_MS = 800L
    private const val POLL_DEADLINE_MS = 90_000L
    private val gson = Gson()

    suspend fun resolve(source: String, expectedPlatform: VideoPlatform?): VideoInfo =
        withContext(Dispatchers.IO) {
            val config = DtkConfig.load()
            if (!config.enabled) {
                throw DtkException("NOT_CONFIGURED", "Chưa cấu hình máy chủ API", retryable = false)
            }
            val api = DtkApiFactory.get(config)
            val content = submitAndCollect(api, source, resubmitted = false)
            DtkMapper.toVideoInfo(content, source, expectedPlatform)
        }

    /** Gửi `/parse?wait=`, xử lý cả 200 (Content) lẫn 202 (task_id). */
    private suspend fun submitAndCollect(
        api: DtkApi,
        source: String,
        resubmitted: Boolean
    ): DtkContent {
        var last: DtkException? = null
        for (attempt in 0..1) {
            coroutineContext.ensureActive()
            try {
                val response = api.parse(WAIT_SECONDS, DtkParseRequest(source))
                val body = response.body()
                    ?: throw DtkErrors.fromHttp(
                        response.code(),
                        response.errorBody()?.string(),
                        "Phản hồi rỗng từ máy chủ API"
                    )
                if (!body.success) throw DtkErrors.from(body.error)

                val data = body.data
                val taskId = stringField(data, "task_id")
                return if (taskId != null) {
                    pollTask(api, taskId, source, resubmitted)
                } else {
                    parseContent(data)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DtkException) {
                if (!e.retryable || attempt == 1) throw e
                last = e
                delay(e.retryAfterSeconds?.times(1000L) ?: 1000L * (attempt + 1))
            } catch (e: IOException) {
                last = DtkException("NETWORK", "Không kết nối được máy chủ API", retryable = true)
                if (attempt == 1) throw last
                delay(1000L)
            } catch (e: Exception) {
                last = DtkException(
                    "BAD_RESPONSE",
                    "Máy chủ API trả dữ liệu không đúng định dạng",
                    retryable = true
                )
                if (attempt == 1) throw last
                delay(1000L)
            }
        }
        throw last ?: DtkException("NETWORK", "Không kết nối được máy chủ API", retryable = true)
    }

    /** Task failed vẫn là HTTP 200 + success:true — phải đọc `data.state`. */
    private suspend fun pollTask(
        api: DtkApi,
        taskId: String,
        source: String,
        resubmitted: Boolean
    ): DtkContent {
        val deadline = System.currentTimeMillis() + POLL_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActive()
            val envelope = try {
                api.task(taskId)
            } catch (e: IOException) {
                throw DtkException("NETWORK", "Không kết nối được máy chủ API", retryable = true)
            }
            if (!envelope.success) {
                val error = DtkErrors.from(envelope.error)
                if (error.code == "TASK_NOT_FOUND" && !resubmitted) {
                    return submitAndCollect(api, source, resubmitted = true)
                }
                throw error
            }

            val task = gson.fromJson(envelope.data, DtkTask::class.java)
                ?: throw DtkException("BAD_RESPONSE", "Máy chủ API trả tác vụ rỗng", retryable = true)
            when (task.state) {
                "done" -> return gson.fromJson(task.data, DtkContent::class.java)
                    ?: throw DtkException("BAD_RESPONSE", "Máy chủ API trả video rỗng", retryable = true)
                "failed" -> {
                    val error = DtkErrors.from(task.error)
                    if (error.code == "TASK_NOT_FOUND" && !resubmitted) {
                        return submitAndCollect(api, source, resubmitted = true)
                    }
                    throw error
                }
                "queued", "running" -> delay(POLL_INTERVAL_MS)
                else -> throw DtkException(
                    "TASK_STATE",
                    "Trạng thái tác vụ không hợp lệ: ${task.state}",
                    retryable = true
                )
            }
        }
        throw DtkException(
            "TIMEOUT",
            "Máy chủ API phản hồi quá chậm, vui lòng thử lại",
            retryable = true
        )
    }

    private fun parseContent(data: JsonElement?): DtkContent =
        gson.fromJson(data, DtkContent::class.java)
            ?: throw DtkException("BAD_RESPONSE", "Máy chủ API trả video rỗng", retryable = true)

    private fun stringField(element: JsonElement?, name: String): String? =
        element?.takeIf { it.isJsonObject }
            ?.asJsonObject?.get(name)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
}
