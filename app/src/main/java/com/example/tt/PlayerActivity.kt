package com.example.tt

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.tt.data.DatabaseHelper
import com.example.tt.databinding.ActivityPlayerBinding
import com.example.tt.subtitle.SubtitleRepository
import com.example.tt.ui.MediaUri
import com.example.tt.ui.setupBottomNav
import java.io.File

/**
 * Player xem video + phu de, co the doi ban Goc / Co phu de / Thuyet minh ngay tren cung.
 * Video truyen qua content Uri de FileProvider cap quyen doc; SRT gan bang SubRip config.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"
        const val EXTRA_DUB_URI = "extra_dub_uri"
        const val EXTRA_START_VARIANT = "extra_start_variant"
        const val EXTRA_TITLE = "extra_title"

        const val VARIANT_ORIGINAL = "original"
        const val VARIANT_VIETSUB = "vietsub"
        const val VARIANT_NARRATION = "narration"
    }

    private data class Source(val label: String, val videoUri: Uri, val subtitleUri: Uri?)

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private val sources = mutableListOf<Source>()
    private var baseTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        baseTitle = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        binding.toolbar.title = baseTitle
        binding.toolbar.setNavigationOnClickListener { onBack() }
        setupBottomNav(binding.bottomNav, R.id.nav_none)

        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player
        buildSources()
        if (sources.isEmpty()) {
            Toast.makeText(this, R.string.toast_file_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.spinnerSource.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sources.map { it.label }
        )
        binding.spinnerSource.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) = play(position)

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        val startIndex = when (intent.getStringExtra(EXTRA_START_VARIANT)) {
            VARIANT_VIETSUB -> sources.indexOfFirst {
                it.label == getString(R.string.player_source_vietsub)
            }
            VARIANT_NARRATION -> sources.indexOfFirst {
                it.label == getString(R.string.player_source_narration)
            }
            else -> 0
        }
        binding.spinnerSource.setSelection(startIndex.coerceAtLeast(0))
    }

    private fun buildSources() {
        sources.clear()
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0L)
        val record = DatabaseHelper(this).getDownload(recordId)

        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: record?.filePath?.takeIf { it.isNotBlank() }
                ?.let { MediaUri.resolve(this, it) }
        // content Uri da duoc cap quyen; file path thi phai ton tai that.
        if (videoUri == null || !exists(videoUri)) return

        // Nhan dang String de tranh getParcelableExtra khong on dinh theo API;
        // fallback deu di qua MediaUri de ra content Uri (FileProvider) thay vi bare path.
        val subtitleUri = intent.getStringExtra(EXTRA_SUBTITLE_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: record?.subtitlePath?.takeIf { it.isNotBlank() }
                ?.let { MediaUri.resolve(this, it) }
            ?: SubtitleRepository(this).pathFor(recordId).takeIf { File(it).exists() }
                ?.let { MediaUri.resolve(this, it) }
        val srt = subtitleUri?.takeIf { exists(it) }

        sources.add(Source(getString(R.string.player_source_original), videoUri, null))
        if (srt != null) {
            sources.add(Source(getString(R.string.player_source_vietsub), videoUri, srt))
        }

        val dubUri = intent.getStringExtra(EXTRA_DUB_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: record?.dubPath?.takeIf { it.isNotBlank() }
                ?.let { MediaUri.resolve(this, it) }
        if (dubUri != null && exists(dubUri)) {
            // Ban thuyet minh van chong SRT neu co de nguoi dung doc theo.
            sources.add(Source(getString(R.string.player_source_narration), dubUri, srt))
        }
    }

    private fun exists(uri: Uri): Boolean =
        uri.scheme == "content" || runCatching { File(uri.path ?: "").exists() }.getOrDefault(false)

    private fun play(index: Int) {
        val source = sources.getOrNull(index) ?: return
        try {
            // Chọn rõ track text "vi" thay vì chỉ dựa vào cờ FORCED: trên một số
            // thiết bị DefaultTrackSelector không tự bật track nên phụ đề không hiện.
            player.trackSelectionParameters = TrackSelectionParameters.Builder(this)
                .setPreferredTextLanguage("vi")
                .setSelectUndeterminedTextLanguage(true)
                .build()

            val builder = MediaItem.Builder().setUri(source.videoUri)
            source.subtitleUri?.let { srt ->
                builder.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(srt)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("vi")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                            .build()
                    )
                )
            }
            player.setMediaItem(builder.build(), true)
            player.prepare()
            player.play()
            binding.toolbar.title = if (index == 0) baseTitle else "$baseTitle · ${source.label}"
        } catch (t: Throwable) {
            // Khong de loi media lam thoat app (T5).
            Toast.makeText(
                this,
                getString(R.string.toast_player_error, t.message ?: "không rõ"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun onBack() {
        releasePlayer()
        finish()
    }

    private fun releasePlayer() {
        runCatching { player.stop() }
        runCatching { player.release() }
        binding.playerView.player = null
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
