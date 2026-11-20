package com.example.tt.subtitle

import android.content.Context
import android.net.Uri
import com.example.tt.data.DownloadRecord
import com.example.tt.ui.MediaUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Pipeline Phase B+C: video -> PCM -> Moonshine -> ML Kit -> file .srt.
 * Moi buoc kiem tra ensureActive() de nguoi dung co the huy giua chung.
 */
class SubtitleProcessor(private val context: Context) {

    data class Progress(val message: String, val percent: Int)

    val repository = SubtitleRepository(context)

    suspend fun process(
        record: DownloadRecord,
        sourceLanguage: String,
        onProgress: (Progress) -> Unit
    ): String {
        // onProgress co the duoc goi tu thread IO (vd. callback tai model),
        // nen luon day ve main thread truoc khi cap nhat UI.
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val report: (Progress) -> Unit = { progress -> mainHandler.post { onProgress(progress) } }

        report(Progress("Đang mở file video…", 5))
        val uri: Uri = withContext(Dispatchers.IO) {
            MediaUri.resolve(context, record.filePath)
        } ?: throw IllegalStateException(
            "Không tìm thấy file video. Hãy tải lại video trước khi tạo phụ đề."
        )
        coroutineContext.ensureActive()

        report(Progress("Đang tách âm thanh…", 15))
        val pcm = withContext(Dispatchers.IO) { AudioExtractor.extract(context, uri) }
        coroutineContext.ensureActive()
        if (pcm.samples.isEmpty()) throw IllegalStateException("Video không có âm thanh để nhận dạng")

        report(Progress("Đang chuẩn bị model nhận dạng…", 30))
        val lines = withContext(Dispatchers.IO) {
            AsrEngine(context).transcribe(pcm.samples, pcm.sampleRate, sourceLanguage) { fraction ->
                report(
                    Progress(
                        "Đang tải model nhận dạng… ${(fraction * 100).toInt()}%",
                        (30 + fraction * 25).toInt().coerceAtMost(55)
                    )
                )
            }
        }
        coroutineContext.ensureActive()
        if (lines.isEmpty()) throw IllegalStateException("Không nhận dạng được giọng nói trong video")

        report(Progress("Đang dịch sang tiếng Việt…", 70))
        val translations = withContext(Dispatchers.IO) { Translator.translateAll(lines.map { it.text }, sourceLanguage) }
        coroutineContext.ensureActive()

        report(Progress("Đang tạo file phụ đề…", 90))
        val cues = CueBuilder.build(lines, translations)
        val path = withContext(Dispatchers.IO) {
            repository.save(record.id, SrtWriter.write(cues))
            repository.saveSources(record.id, cues.map { it.source })
            repository.pathFor(record.id)
        }
        coroutineContext.ensureActive()

        report(Progress("Hoàn tất", 100))
        return path
    }
}
