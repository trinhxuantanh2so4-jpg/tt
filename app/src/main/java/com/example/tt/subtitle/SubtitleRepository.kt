package com.example.tt.subtitle

import android.content.Context
import java.io.File

/** Luu file .srt trong bo nho rieng cua app (filesDir/subtitles). */
class SubtitleRepository(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "subtitles").apply { mkdirs() }

    fun pathFor(recordId: Long): String = File(dir, "$recordId.srt").absolutePath

    private fun sourcePathFor(recordId: Long): String =
        File(dir, "$recordId.sources").absolutePath

    fun save(recordId: Long, content: String): String =
        pathFor(recordId).also { File(it).writeText(content, Charsets.UTF_8) }

    fun read(recordId: Long): String? =
        File(pathFor(recordId)).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    /** Luu cau goc rieng voi .srt de nguoi dung mo lai van thay ban goc de so sanh. */
    fun saveSources(recordId: Long, sources: List<String>): String =
        sourcePathFor(recordId).also {
            File(it).writeText(SubtitleSourceCodec.encode(sources), Charsets.UTF_8)
        }

    fun readSources(recordId: Long): List<String> =
        File(sourcePathFor(recordId)).takeIf { it.exists() }
            ?.let { file ->
                runCatching { SubtitleSourceCodec.decode(file.readText(Charsets.UTF_8)) }
                    .getOrDefault(emptyList())
            }
            ?: emptyList()

    fun delete(recordId: Long) {
        File(pathFor(recordId)).delete()
        File(sourcePathFor(recordId)).delete()
    }
}
