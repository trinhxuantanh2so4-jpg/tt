package com.example.tt.subtitle

import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Doc/ghi WAV PCM 16-bit. Dung cho:
 *  - file TTS he thong tra ve (thuong la WAV mono)
 *  - track thuyet minh da tron truoc khi dua vao Media3 Transformer
 *
 * Khong phu thuoc Android -> test duoc tren JVM.
 */
object WavIo {

    data class Pcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int
    )

    /** Ghi mono 16-bit. Gia tri float ngoai [-1, 1] bi cat bien. */
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val dataBytes = samples.size * 2
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLe(36 + dataBytes)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)          // PCM chunk size
            out.writeShortLe(1)         // PCM format
            out.writeShortLe(1)         // mono
            out.writeIntLe(sampleRate)
            out.writeIntLe(sampleRate * 2) // byte rate
            out.writeShortLe(2)         // block align
            out.writeShortLe(16)        // bits per sample
            out.writeBytes("data")
            out.writeIntLe(dataBytes)
            for (sample in samples) {
                val v = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
                out.writeShortLe(v)
            }
        }
    }

    /** Doc WAV PCM 16-bit, tu dong downmix ve mono. */
    fun read(file: File): Pcm {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF") { "Không phải file WAV" }
            require(String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { "Không phải file WAVE" }

            var sampleRate = 0
            var channels = 1
            var bits = 16
            var dataOffset = -1L
            var dataSize = 0

            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4)
                raf.readFully(id)
                val size = raf.readIntLe()
                val name = String(id, Charsets.US_ASCII)
                when (name) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        channels = (fmt[2].toInt() and 0xFF) or ((fmt[3].toInt() and 0xFF) shl 8)
                        sampleRate = (fmt[4].toInt() and 0xFF) or
                            ((fmt[5].toInt() and 0xFF) shl 8) or
                            ((fmt[6].toInt() and 0xFF) shl 16) or
                            ((fmt[7].toInt() and 0xFF) shl 24)
                        bits = (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                        raf.seek(raf.filePointer + size)
                    }
                    else -> raf.seek(raf.filePointer + size + (size and 1))
                }
            }

            require(sampleRate > 0) { "WAV thiếu chunk fmt" }
            require(dataOffset >= 0) { "WAV thiếu chunk data" }
            require(bits == 16) { "Chỉ hỗ trợ WAV PCM 16-bit (gặp $bits-bit)" }

            val frameBytes = 2 * channels
            val frames = (dataSize / frameBytes).coerceAtLeast(0)
            val out = FloatArray(frames)
            raf.seek(dataOffset)
            val buf = ByteArray(frameBytes)
            for (i in 0 until frames) {
                raf.readFully(buf)
                var sum = 0f
                for (ch in 0 until channels) {
                    val lo = buf[ch * 2].toInt() and 0xFF
                    val hi = buf[ch * 2 + 1].toInt()
                    val short = ((hi shl 8) or lo).toShort()
                    sum += short / 32768f
                }
                out[i] = sum / channels
            }
            return Pcm(out, sampleRate, channels)
        }
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun RandomAccessFile.readIntLe(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
