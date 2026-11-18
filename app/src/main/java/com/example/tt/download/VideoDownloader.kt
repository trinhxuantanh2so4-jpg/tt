package com.example.tt.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Tai video bang OkHttp, luu vao may:
 *  - Android 10+ : MediaStore (Download/TikTok) - khong can quyen
 *  - Android 8-9 : app dir (khong can quyen)
 *  - Hoac ghi vao Uri do nguoi dung chon qua trinh chon tep (SAF - ACTION_CREATE_DOCUMENT)
 */
object VideoDownloader {

    private val client = OkHttpClient()

    /**
     * Tai ve vi tri mac dinh (Download/TikTok hoac thu muc app).
     *
     * @param onProgress (da tai, tong) - cap nhat % cho UI
     * @return duong dan file da luu
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        fetch(url) { input, total ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: ghi qua MediaStore vao Download/TikTok
                val name = safeName(fileName)
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikTok")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv
                ) ?: throw IllegalStateException("Khong tao duoc file")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    copyWithProgress(input, out, total, onProgress)
                } ?: throw IllegalStateException("Khong mo duoc file")
                "Download/TikTok/$name"
            } else {
                // Android 8-9: luu vao thu muc rieng cua app (an toan, khong can quyen)
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "TikTok"
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
        onProgress: (Long, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        fetch(url) { input, total ->
            val out = context.contentResolver.openOutputStream(target)
                ?: throw IllegalStateException("Khong mo duoc file da chon")
            out.use { copyWithProgress(input, out, total, onProgress) }
            target.toString()
        }
    }

    /** Goi API va tra InputStream cua video cho ham ghi dich. */
    private fun fetch(
        url: String,
        sink: (InputStream, Long) -> String
    ): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Loi tai: HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Khong co du lieu")
            val total = body.contentLength()
            return sink(body.byteStream(), total)
        }
    }

    private fun safeName(fileName: String): String =
        fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun copyWithProgress(
        input: InputStream,
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
