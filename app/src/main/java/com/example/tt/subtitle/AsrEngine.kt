package com.example.tt.subtitle

import ai.moonshine.voice.JNI
import ai.moonshine.voice.MicTranscriber
import ai.moonshine.voice.ProgressCallback
import ai.moonshine.voice.TranscriberOption
import android.content.Context

/**
 * Nhan dang tieng noi bang Moonshine, chay hoan toan offline tren may sau khi
 * model duoc tai lan dau. Khong mo microphone - ta truc tiep truyen PCM da trich
 * tu file video vao transcriber.
 */
class AsrEngine(private val context: Context) {

    /**
     * @param language ma nguon: "zh" (Douyin) hoac "en" (TikTok)
     * @param onModelProgress tien do tai model (0..1) - co the chay lai nhieu lan
     */
    fun transcribe(
        pcm: FloatArray,
        sampleRate: Int,
        language: String,
        onModelProgress: (Float) -> Unit = {}
    ): List<CueBuilder.RawLine> {
        val transcriber = MicTranscriber(context.applicationContext)
            .language(language)
            .modelArch(JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING)
            .onProgress(ProgressCallback { fraction, _ -> onModelProgress(fraction) })

        // Tieng Trung can them token/giay de khong bi cat cau ngan.
        if (language == "zh" || language == "cmn") {
            transcriber.options(TranscriberOption("max_tokens_per_second", "13.0"))
        }

        return try {
            transcriber.load()
            val transcript = transcriber.transcribeWithoutStreaming(pcm, sampleRate)
            (transcript.lines.orEmpty()).mapNotNull { line ->
                val text = line.text.orEmpty().trim()
                if (text.isEmpty()) null else CueBuilder.RawLine(
                    startSec = line.startTime,
                    durationSec = line.duration,
                    text = text
                )
            }
        } finally {
            runCatching { transcriber.close() }
        }
    }
}
