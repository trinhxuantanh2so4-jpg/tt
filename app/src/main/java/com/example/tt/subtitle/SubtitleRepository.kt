package com.example.tt.subtitle

import android.content.Context
import java.io.File

/** Luu file .srt trong bo nho rieng cua app (filesDir/subtitles). */
class SubtitleRepository(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "subtitles").apply { mkdirs() }

    fun pathFor(recordId: Long): String = File(dir, "$recordId.srt").absolutePath

    fun save(recordId: Long, content: String): String =
        pathFor(recordId).also { File(it).writeText(content, Charsets.UTF_8) }

    fun read(recordId: Long): String? =
        File(pathFor(recordId)).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun delete(recordId: Long) {
        File(pathFor(recordId)).delete()
    }
}
