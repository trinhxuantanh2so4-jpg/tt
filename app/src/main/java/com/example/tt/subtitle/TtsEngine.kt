package com.example.tt.subtitle

/**
 * Giao dien doc van ban thanh tieng (TTS).
 * Mac dinh dung TTS he thong; sau nay co the cam Moonshine TTS ma khong doi pipeline.
 */
interface TtsEngine {

    data class VoiceOption(
        val gender: Gender,
        val speechRate: Float
    ) {
        val label: String
            get() = "${if (gender == Gender.FEMALE) "Nữ" else "Nam"} · ${speechRate}x"

        companion object {
            val DEFAULT = VoiceOption(Gender.FEMALE, 1.0f)

            /** Doi nhan tu Spinner ("Nữ · 1.0x"...) thanh tuy chon. */
            fun fromLabel(label: String): VoiceOption {
                val male = label.startsWith("Nam", ignoreCase = true)
                val rate = Regex("""([0-9]+(?:[.,][0-9]+)?)x""")
                    .find(label)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace(',', '.')
                    ?.toFloatOrNull()
                    ?: 1.0f
                return VoiceOption(if (male) Gender.MALE else Gender.FEMALE, rate)
            }
        }
    }

    enum class Gender { FEMALE, MALE }

    data class Clip(val samples: FloatArray, val sampleRate: Int)

    /** Khoi tao engine; false neu thiet bi khong co TTS. */
    suspend fun ensureReady(): Boolean

    /** Kiem tra giong tieng Viet offline co san khong. */
    suspend fun isVietnameseAvailable(): Boolean

    /** Doc 1 cau; null neu engine khong tao duoc file. */
    suspend fun synthesize(text: String, option: VoiceOption): Clip?

    fun shutdown()
}

/** Thiet bi khong co giong TTS tieng Viet. */
class TtsUnavailableException :
    IllegalStateException("Máy chưa có giọng đọc tiếng Việt. Hãy cài trong Cài đặt > Ngôn ngữ & nhập > Chuyển văn bản thành giọng nói.")
