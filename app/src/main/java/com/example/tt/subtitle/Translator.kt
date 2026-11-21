package com.example.tt.subtitle

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Dich bang ML Kit on-device. That bay loi thi bao ro thay vi am thim giu cau goc. */
object Translator {

    class TranslationUnavailableException(message: String, cause: Throwable? = null) :
        IllegalStateException(message, cause)

    suspend fun translateAll(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String = TranslateLanguage.VIETNAMESE
    ): List<String?> {
        if (texts.isEmpty() || sourceLanguage == targetLanguage) return texts

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        return try {
            try {
                translator.downloadModelIfNeeded().await()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                throw TranslationUnavailableException(
                    "Không tải được model dịch tiếng Việt. Hãy kiểm tra Google Play services " +
                        "và kết nối mạng, rồi thử lại.",
                    t
                )
            }

            val result = texts.map { text ->
                runCatching { translator.translate(text).await() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
            if (result.all { it == null }) {
                throw TranslationUnavailableException(
                    "Không dịch được câu nào sang tiếng Việt. Hãy kiểm tra Google Play services rồi thử lại."
                )
            }
            result
        } finally {
            runCatching { translator.close() }
        }
    }

    /** Ma nguon mac dinh theo nen tang (van cho nguoi dung doi tay). */
    fun defaultSourceLanguage(platformId: String): String = when (platformId) {
        "douyin" -> TranslateLanguage.CHINESE // "zh"
        else -> TranslateLanguage.ENGLISH     // "en"
    }

    val supportedSources: List<Pair<String, String>>
        get() = listOf(
            TranslateLanguage.CHINESE to "Tiếng Trung",
            TranslateLanguage.ENGLISH to "Tiếng Anh",
            TranslateLanguage.VIETNAMESE to "Tiếng Việt"
        )
}
