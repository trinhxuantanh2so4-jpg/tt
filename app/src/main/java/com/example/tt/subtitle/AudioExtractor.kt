package com.example.tt.subtitle

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/**
 * Giai ma track audio cua video thanh PCM float, mono, 16 kHz - dinh dang ma
 * Moonshine mong doi. Thoi gian (timeline) cua Moonshine bat dau tu 0 nen ta chen
 * doan im lang neu track audio bat dau tre hon video.
 *
 * Tiet kiem bo nho: moi chunk duoc downmix + resample ve 16k NGAY sau khi giai ma,
 * nen ta khong bao gio giu dung luc 3 ban PCM (src, mono, merged) cua toan video.
 * May co heap ~192MB de bi OOM khi video dai.
 */
object AudioExtractor {

    data class PcmAudio(val samples: FloatArray, val sampleRate: Int)

    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 20_000L

    private data class Chunk(val ptsUs: Long, val data: FloatArray)

    fun extract(context: Context, videoUri: Uri): PcmAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, videoUri, null)

            val audioTrack = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("Không tìm thấy track âm thanh trong video")

            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            extractor.selectTrack(audioTrack)
            codec = MediaCodec.createDecoderByType(mime)
            // Yeu cau PCM 16-bit de mo moi may; se doi sang float o day.
            // Giu nguyen format goc (csd-0/csd-1) de decoder AAC/Opus khoi loi.
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val chunks = ArrayList<Chunk>()
            // Tai su dung 1 buffer doc decoder thay vi cap phat ShortArray moi frame.
            var scratch = ShortArray(0)
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outIndex >= 0) {
                        val out = codec.getOutputBuffer(outIndex)!!
                        if (info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val count = info.size / 2
                            if (scratch.size < count) scratch = ShortArray(count)
                            out.asShortBuffer().get(scratch, 0, count)

                            val frames = count / channels
                            val mono = FloatArray(frames) { i ->
                                var sum = 0
                                var ch = 0
                                while (ch < channels) {
                                    sum += scratch[i * channels + ch].toInt()
                                    ch++
                                }
                                (sum / channels) / 32768f
                            }
                            val at16k =
                                if (sampleRate == TARGET_SAMPLE_RATE) mono
                                else resample(mono, sampleRate, TARGET_SAMPLE_RATE)
                            chunks.add(Chunk(info.presentationTimeUs, at16k))
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                }
            }

            if (chunks.isEmpty()) {
                throw IllegalStateException("Không giải mã được âm thanh từ video")
            }
            // chunks da la 16k mono; mergeChunks chi con them mot ban cuoi cung.
            val merged = mergeChunks(chunks, TARGET_SAMPLE_RATE)
            chunks.clear()
            return PcmAudio(merged, TARGET_SAMPLE_RATE)
        } finally {
            codec?.let { c -> runCatching { c.stop(); c.release() } }
            extractor.release()
        }
    }

    /** Chen doan im lang vao cac khoang trong de giu dung timeline. */
    private fun mergeChunks(chunks: List<Chunk>, sampleRate: Int): FloatArray {
        val firstPts = chunks.minOf { it.ptsUs }
        fun startOf(c: Chunk) =
            ((c.ptsUs - firstPts).coerceAtLeast(0) * sampleRate / 1_000_000).toInt()

        var total = 0
        for (c in chunks) total = max(total, startOf(c) + c.data.size)
        val out = FloatArray(total)
        for (c in chunks) c.data.copyInto(out, startOf(c))
        return out
    }

    /** Noi suy tuyen tinh de doi sample rate (48k/44.1k -> 16k). */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (input.isEmpty() || from == to) return input
        val ratio = to.toDouble() / from
        val out = FloatArray((input.size * ratio).toInt())
        for (i in out.indices) {
            val src = i / ratio
            val i0 = src.toInt()
            val i1 = min(i0 + 1, input.lastIndex)
            val frac = (src - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }
}
