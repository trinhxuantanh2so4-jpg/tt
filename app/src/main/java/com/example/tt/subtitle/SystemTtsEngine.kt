package com.example.tt.subtitle

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * TTS he thong (Google TTS / engine cai san). Synthesize ra file WAV roi doc PCM.
 * Moi thiet bi chi co 1 engine -> goi tuan tu tung cue.
 */
class SystemTtsEngine(context: Context) : TtsEngine {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var initStarted = false

    override suspend fun ensureReady(): Boolean = withContext(Dispatchers.Main) {
        val current = ready
        if (current != null) return@withContext current.await()

        initStarted = true
        val deferred = CompletableDeferred<Boolean>()
        ready = deferred
        tts = TextToSpeech(appContext) { status ->
            if (!deferred.isCompleted) deferred.complete(status == TextToSpeech.SUCCESS)
        }
        deferred.await()
    }

    override suspend fun isVietnameseAvailable(): Boolean = withContext(Dispatchers.Main) {
        if (!ensureReady()) return@withContext false
        val engine = tts ?: return@withContext false
        val locale = VIETNAMESE
        val result = engine.setLanguage(locale)
        if (result < TextToSpeech.LANG_AVAILABLE) return@withContext false
        // setLanguage thanh cong nhung co the chi co giong mang; van cho phep thu.
        pickVoice(TtsEngine.Gender.FEMALE) != null ||
            runCatching { engine.voices?.any { it.locale.language == "vi" } == true }.getOrDefault(false)
    }

    override suspend fun synthesize(text: String, option: TtsEngine.VoiceOption): TtsEngine.Clip? {
        if (!isVietnameseAvailable()) throw TtsUnavailableException()

        val engine = tts ?: return null
        val file = File(appContext.cacheDir, "tts_${UUID.randomUUID()}.wav")
        return try {
            withContext(Dispatchers.Main) {
                engine.setLanguage(VIETNAMESE)
                engine.setSpeechRate(option.speechRate.coerceIn(0.5f, 2.0f))
                pickVoice(option.gender)?.let { voice -> runCatching { engine.setVoice(voice) } }
            }
            synthesizeToFile(engine, text, file)
            val pcm = withContext(Dispatchers.IO) { WavIo.read(file) }
            TtsEngine.Clip(pcm.samples, pcm.sampleRate)
        } catch (e: TtsUnavailableException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            file.delete()
        }
    }

    private suspend fun synthesizeToFile(engine: TextToSpeech, text: String, file: File) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine(block = { cont ->
                val utteranceId = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("TTS không đọc được câu này")
                            )
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("TTS lỗi ($errorCode)")
                            )
                        }
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
                }
                val result = engine.synthesizeToFile(text, params, file, utteranceId)
                if (result != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("Không khởi động được TTS")
                    )
                }
                cont.invokeOnCancellation { runCatching { engine.stop() } }
            })
        }

    private fun pickVoice(gender: TtsEngine.Gender): Voice? {
        val voices = runCatching { tts?.voices }.getOrNull() ?: return null
        val vietnamese = voices.filter { it.locale.language == "vi" }
        if (vietnamese.isEmpty()) return null

        val keywords = when (gender) {
            TtsEngine.Gender.FEMALE -> listOf("female", "woman", "nu", "nữ", "f0", "f1", "sfg")
            TtsEngine.Gender.MALE -> listOf("male", "man", "nam", "m0", "m1", "svox")
        }
        // Uu tien giong chat luong cao (Google/Neural/Studio...). Giong can mang
        // thuong nghe tu nhien hon giong offline, nen van duoc phep chon neu co.
        val qualityHints = listOf("neural", "studio", "google", "natural", "premium", "hd")
        fun isQuality(voice: Voice): Boolean {
            val name = voice.name.lowercase(Locale.ROOT)
            return qualityHints.any { name.contains(it) }
        }
        fun matchesGender(voice: Voice): Boolean {
            val name = voice.name.lowercase(Locale.ROOT)
            return keywords.any { name.contains(it) }
        }

        return vietnamese.firstOrNull { isQuality(it) && matchesGender(it) }
            ?: vietnamese.firstOrNull { isQuality(it) }
            ?: vietnamese.firstOrNull { matchesGender(it) && !it.isNetworkConnectionRequired }
            ?: vietnamese.firstOrNull { !it.isNetworkConnectionRequired }
            ?: vietnamese.first()
    }

    override fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = null
        initStarted = false
    }

    companion object {
        private val VIETNAMESE = Locale("vi", "VN")
    }
}
