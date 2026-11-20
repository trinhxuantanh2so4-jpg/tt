package com.example.tt.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.tt.network.dtk.DtkStreamFallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Tai video bang OkHttp, luu vao may:
 *  - Android 10+ : MediaStore (Download/TikTok) - khong can quyen
 *  - Android 8-9 : app dir (khong can quyen)
 *  - Hoac ghi vao Uri do nguoi dung chon qua trinh chon tep (SAF - ACTION_CREATE_DOCUMENT)
 *
 * Ho tro HUY: vong lap copy kiem tra coroutine hien tai, huy Job -> dung tai ngay.
 */
object VideoDownloader {

    private val client = OkHttpClient()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Tai ve vi tri mac dinh (Download/<folder> hoac thu muc app).
     *
     * @param fileName ten file (vd: tiktok_123.mp4)
     * @param folder ten thu muc con trong Download ("TikTok" / "Douyin")
     * @param headers header bo sung (vd Referer theo nen tang); de trong tu suy theo host
     * @param onProgress (da tai, tong) - cap nhat % cho UI
     * @return duong dan file da luu
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        folder: String = "TikTok",
        headers: Map<String, String> = emptyMap(),
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val safeFolder = safeName(folder)
        fetch(url, headers) { input, total ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: ghi qua MediaStore vao Download/<folder>
                val name = safeName(fileName)
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + safeFolder
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
                ) ?: throw IllegalStateException("Khong tao duoc file")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    copyWithProgress(input, out, total, onProgress)
                } ?: throw IllegalStateException("Khong mo duoc file")
                "Download/$safeFolder/$name"
            } else {
                // Android 8-9: luu vao thu muc rieng cua app (an toan, khong can quyen)
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeFolder
                ).apply { mkdirs() }
                val file = File(dir, safeName(fileName))
                file.outputStream().use { out ->
                    copyWithProgress(input, out, total, onProgress)
                }
                file.absolutePath
            }
        }
    }

    /**
     * Tai video va ghi vao dung file/vi tri nguoi dung da chon (Uri tu
     * ActivityResultContracts.CreateDocument hoac OpenDocument).
     *
     * @return Uri (dang chuoi) cua file da luu
     */
    suspend fun downloadToUri(
        context: Context,
        url: String,
        target: Uri,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        fetch(url, headers) { input, total ->
            val out = context.contentResolver.openOutputStream(target)
                ?: throw IllegalStateException("Khong mo duoc file da chon")
            out.use { copyWithProgress(input, out, total, onProgress) }
            target.toString()
        }
    }

    /**
     * Goi API va tra InputStream cua video cho ham ghi dich.
     * URL CDN da ky cua DTK co the het han (403/404) -> thu lan luot cac mirror
     * da luu luc resolve. Cac loi khac (500, 302 den trang login...) van bao ngay.
     */
    private suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        sink: suspend (InputStream, Long) -> String
    ): String {
        val candidates = listOf(url) + DtkStreamFallbacks.alternatesFor(url)
        var lastCode = 0
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            val req = Request.Builder()
                .url(candidate)
                .apply { defaultHeaders(candidate, headers).forEach { header(it.key, it.value) } }
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 403 || resp.code == 404) {
                    lastCode = resp.code
                    return@use
                }
                if (!resp.isSuccessful) throw IllegalStateException("Loi tai: HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("Khong co du lieu")
                val total = body.contentLength()
                return sink(body.byteStream(), total)
            }
        }
        throw IllegalStateException("Khong tai duoc video (HTTP $lastCode)")
    }

    /** Referer/User-Agent mac dinh theo host CDN; header truyen vao co uu tien. */
    private fun defaultHeaders(url: String, extra: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val host = runCatching { java.net.URL(url).host.lowercase() }.getOrNull().orEmpty()
        val referer = when {
            host.contains("douyin") || host.contains("ixigua") || host.contains("snssdk") ||
                host.contains("amemv") || host.contains("byteimg") -> "https://www.douyin.com/"
            host.contains("tiktok") || host.contains("musical") -> "https://www.tiktok.com/"
            else -> null
        }
        if (referer != null && !extra.containsKey("Referer")) result["Referer"] = referer
        result["User-Agent"] = USER_AGENT
        result.putAll(extra)
        return result
    }

    private fun safeName(fileName: String): String =
        fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private suspend fun copyWithProgress(
        input: InputStream,
        out: java.io.OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val buf = ByteArray(16 * 1024)
        var done = 0L
        var lastPct = -1
        while (true) {
            // Huy tai: nguoi dung bam "Huy" -> job bi cancel -> dung vong lap
            coroutineContext.ensureActive()

            val r = input.read(buf)
            if (r == -1) break
            out.write(buf, 0, r)
            done += r
            if (total > 0) {
                val pct = (done * 100 / total).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(done, total)
                }
            } else {
                onProgress(done, 0)
            }
        }
        out.flush()
    }
}
