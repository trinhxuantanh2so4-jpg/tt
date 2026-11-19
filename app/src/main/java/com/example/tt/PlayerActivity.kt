package com.example.tt

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.appbar.MaterialToolbar

/**
 * Player xem video + phu de (Phase B+C).
 * - video duoc truyen qua content Uri (content://...) de co the dat cho AppProvider quyen doc.
 * - phu de SRT duoc gan qua MediaItem.SubtitleConfiguration.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private var subtitleUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerView = findViewById(R.id.playerView)
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoUriString = getIntent().getStringExtra(EXTRA_VIDEO_URI)
        val videoUri = if (videoUriString != null) {
            Uri.parse(videoUriString)
        } else {
            val path = com.example.tt.data.DatabaseHelper(this)
                .getDownload(getIntent().getLongExtra(EXTRA_RECORD_ID, 0L))?.filePath ?: ""
            Uri.parse(path)
        }
        subtitleUri = getIntent().getParcelableExtra(EXTRA_SUBTITLE_URI)
        val srtUri: Uri? = subtitleUri

        val builder = MediaItem.Builder().setUri(videoUri)
        if (srtUri != null) {
            val subtitle = MediaItem.SubtitleConfiguration.Builder(srtUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("vi")
                .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                .build()
            builder.setSubtitleConfigurations(listOf(subtitle))
        }
        val mediaItem = builder.build()

        player.setMediaItem(mediaItem, true)
        player.prepare()
        player.play()

        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener { onBack() }
    }

    private fun onBack() {
        runCatching { player.stop() }
        runCatching { player.release() }
        playerView.player = null
        finish()
    }

    override fun onDestroy() {
        runCatching { player.stop() }
        runCatching { player.release() }
        playerView.player = null
        super.onDestroy()
    }
}
