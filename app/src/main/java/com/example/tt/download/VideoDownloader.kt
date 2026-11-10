package com.example.tt.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Tai video bang OkHttp, luu vao may:
 *  - Android 10+ : MediaStore (Download/TikTok) - khong can quyen
 *  - Android 8-9 : app dir (khong can quyen)
 */
object VideoDownloader {

    private val client = OkHttpClient()

    /**
     * @param onProgress (da tai, tong) - cap nhat % cho UI
     * @return duong dan file da luu
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Loi tai: HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Khong co du lieu")
            val total = body.contentLength()

            val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: ghi qua MediaStore vao Download/TikTok
                val name = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikTok")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
                ) ?: throw IllegalStateException("Khong tao duoc file")
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, out, total, onProgress)
                    }
                }
                "Download/TikTok/$name"
            } else {
                // Android 8-9: luu vao thu muc rieng cua app (an toan, khong can quyen)
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "TikTok"
                ).apply { mkdirs() }
                val file = File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, out, total, onProgress)
                    }
                }
                file.absolutePath
            }
            target
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val buf = ByteArray(16 * 1024)
        var done = 0L
        var lastPct = -1
        while (true) {
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
