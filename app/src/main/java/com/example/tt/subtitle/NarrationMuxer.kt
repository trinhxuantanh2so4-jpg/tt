package com.example.tt.subtitle

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ghep video goc (da tat am thanh) voi 1 track WAV thuyet minh thanh MP4 moi
 * bang Media3 Transformer. Khong dung ffmpeg.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NarrationMuxer(private val context: Context) {

    suspend fun mux(
        videoUri: Uri,
        narrationWav: File,
        output: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Main) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        // Sequence 1: video, bo am thanh goc (da tron san vao WAV neu can)
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setRemoveAudio(true)
            .build()
        // Sequence 2: track thuyet minh
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(narrationWav)))
            .build()

        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(videoItem).build(),
            EditedMediaItemSequence.Builder(audioItem).build()
        ).build()

        val progressHolder = ProgressHolder()
        coroutineScope {
            val transformer = Transformer.Builder(context).build()

            val monitor = launch {
                while (isActive) {
                    if (transformer.getProgress(progressHolder) ==
                        Transformer.PROGRESS_STATE_AVAILABLE
                    ) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    delay(300)
                }
            }

            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IllegalStateException(
                                        "Ghép thuyết minh lỗi: ${exportException.message}",
                                        exportException
                                    )
                                )
                            }
                        }
                    })
                    cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                    transformer.start(composition, output.absolutePath)
                }
            } finally {
                monitor.cancel()
            }
        }

        if (!output.exists() || output.length() == 0L) {
            throw IllegalStateException("Không tạo được file thuyết minh")
        }
        output
    }
}
