package com.example.tt.subtitle

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.tt.data.DownloadRecord
import com.example.tt.ui.MediaUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Pipeline thuyet minh (Phase D):
 *   SRT/cue -> TTS tung cau -> dat vao timeline + tron am goc -> WAV -> MP4 (Media3).
 *
 * Chi chay khi nguoi dung bam "Bat dau xu ly"; moi buoc deu kiem tra huy.
 */
class NarrationProcessor(private val context: Context) {

    data class Progress(val message: String, val percent: Int)

    data class Result(val dubPath: String, val wavPath: String)

    suspend fun process(
        record: DownloadRecord,
        cues: List<SubtitleCue>,
        voice: TtsEngine.VoiceOption,
        keepBackground: Boolean,
        onProgress: (Progress) -> Unit
    ): Result {
        require(cues.isNotEmpty()) { "Chưa có câu phụ đề để thuyết minh" }

        val mainHandler = Handler(Looper.getMainLooper())
        val report: (Progress) -> Unit = { progress -> mainHandler.post { onProgress(progress) } }

        report(Progress("Đang mở video…", 2))
        val uri: Uri = withContext(Dispatchers.IO) {
            MediaUri.resolve(context, record.filePath)
        } ?: throw IllegalStateException("Không mở được file video để thuyết minh")

        val dir = File(context.filesDir, "narration").apply { mkdirs() }
        val wavFile = File(dir, "${record.id}.wav")
        val outFile = File(dir, "${record.id}.mp4")

        val tts = SystemTtsEngine(context)
        try {
            report(Progress("Đang kiểm tra giọng đọc tiếng Việt…", 4))
            if (!tts.ensureReady() || !tts.isVietnameseAvailable()) {
                throw TtsUnavailableException()
            }
            coroutineContext.ensureActive()

            val durationMs = withContext(Dispatchers.IO) { readDurationMs(uri) }
                .takeIf { it > 0L }
                ?: cues.last().endMs.coerceAtLeast(1_000L)

            report(Progress("Đang đọc âm thanh gốc…", 8))
            val background = if (keepBackground) {
                withContext(Dispatchers.IO) {
                    // Video khong co audio -> van cho phep thuyet minh khong nen.
                    runCatching { AudioExtractor.extract(context, uri) }.getOrNull()
                }
            } else {
                null
            }
            coroutineContext.ensureActive()

            val speech = cues.filter { it.text.isNotBlank() }
            if (speech.isEmpty()) throw IllegalStateException("Không có câu phụ đề nào để đọc")

            val clips = mutableListOf<NarrationTimeline.Clip>()
            for ((index, cue) in speech.withIndex()) {
                coroutineContext.ensureActive()
                report(
                    Progress(
                        "Đang đọc câu ${index + 1}/${speech.size}…",
                        10 + (index * 58 / speech.size)
                    )
                )
                val clip = tts.synthesize(cue.text, voice)
                if (clip != null && clip.samples.isNotEmpty()) {
                    clips.add(
                        NarrationTimeline.Clip(clip.samples, clip.sampleRate, cue.startMs)
                    )
                }
            }
            if (clips.isEmpty()) {
                throw IllegalStateException("Không đọc được câu nào để thuyết minh")
            }
            coroutineContext.ensureActive()

            report(Progress("Đang trộn âm thanh…", 72))
            val mixed = withContext(Dispatchers.Default) {
                NarrationTimeline.render(
                    clips = clips,
                    background = background?.samples,
                    backgroundSampleRate = background?.sampleRate ?: 16_000,
                    backgroundGain = if (keepBackground) {
                        NarrationTimeline.BACKGROUND_GAIN_DEFAULT
                    } else {
                        0f
                    },
                    durationMs = durationMs,
                    outputSampleRate = OUTPUT_SAMPLE_RATE
                )
            }
            coroutineContext.ensureActive()

            report(Progress("Đang ghi track thuyết minh…", 80))
            withContext(Dispatchers.IO) {
                WavIo.write(wavFile, mixed, OUTPUT_SAMPLE_RATE)
            }
            coroutineContext.ensureActive()

            report(Progress("Đang ghép video…", 84))
            NarrationMuxer(context).mux(uri, wavFile, outFile) { fraction ->
                val pct = 84 + (fraction.coerceIn(0f, 1f) * 15f).toInt()
                report(Progress("Đang ghép video… $pct%", pct))
            }

            report(Progress("Xong ✓", 100))
            return Result(outFile.absolutePath, wavFile.absolutePath)
        } finally {
            tts.shutdown()
        }
    }

    private fun readDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE = 24_000
    }
}
