package com.example.tt.subtitle

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Dich bang ML Kit on-device. That bay loi thi giu nguyen cau goc. */
object Translator {

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
            translator.downloadModelIfNeeded().await()
            texts.map { text ->
                runCatching { translator.translate(text).await() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
        } finally {
            translator.close()
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
