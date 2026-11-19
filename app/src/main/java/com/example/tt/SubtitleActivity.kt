package com.example.tt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ActivitySubtitleBinding
import com.example.tt.network.VideoPlatform
import com.example.tt.subtitle.CueBuilder
import com.example.tt.subtitle.SubtitleCue
import com.example.tt.subtitle.SubtitleProcessor
import com.example.tt.subtitle.SrtWriter
import com.example.tt.subtitle.SubtitleRepository
import com.example.tt.ui.MediaUri
import com.example.tt.ui.SubtitleAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Man hinh tao phu de (Phase B+C):
 *  - chay pipeline: audio -> Moonshine -> ML Kit -> SRT
 *  - danh sach cue + moi cue co the sua text
 *  - luu file .srt -> mo PlayerActivity xem video co phu de
 */
class SubtitleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_PLATFORM = "extra_platform"
        const val PREFS_KEY_CURRENT_SUBTITLE_RECORD = "current_subtitle_record_id"

        fun createIntent(context: Context, recordId: Long, platformId: String): Intent =
            Intent(context, SubtitleActivity::class.java).apply {
                putExtra(EXTRA_RECORD_ID, recordId)
                putExtra(EXTRA_PLATFORM, platformId)
            }
    }

    private lateinit var binding: ActivitySubtitleBinding
    private lateinit var db: DatabaseHelper

    private var recordId: Long = 0L
    private var record: DownloadRecord? = null

    private val repository = SubtitleRepository(this)
    private val processor = SubtitleProcessor(this)

    private val cues = mutableListOf<SubtitleCue>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubtitleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper(this)

        recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0L)

        if (recordId == 0L) {
            toast(getString(R.string.toast_subtitle_missing))
            finish()
            return
        }

        record = db.getDownload(recordId)
        if (record == null) {
            toast(getString(R.string.toast_subtitle_missing))
            finish()
            return
        }
        binding.spinnerSubtitleLanguage.setSelection(
            if (record?.platform == VideoPlatform.DOUYIN) 0 else 1
        )

        setupToolbar()
        setupCuesRecycler()
        setupLanguageListeners()
        binding.btnGenerateSubtitle.setOnClickListener { generate() }
        binding.btnSaveSrt.setOnClickListener { saveSrt() }
        binding.btnOpenPlayer.setOnClickListener { openPlayer() }

        refreshFromRecord()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private val subtitleAdapter = SubtitleAdapter { position, text ->
        if (position in cues.indices) {
            cues[position] = cues[position].copy(text = text)
        }
    }

    private fun setupCuesRecycler() {
        binding.rvCues.layoutManager = LinearLayoutManager(this)
        binding.rvCues.setItemViewCacheSize(0)
        binding.rvCues.adapter = subtitleAdapter
    }

    private fun setupLanguageListeners() {
        binding.spinnerSubtitleLanguage.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val code = when (binding.spinnerSubtitleLanguage.selectedItem?.toString()) {
                        "Tiếng Anh" -> "en"
                        "Tiếng Việt" -> "vi"
                        else -> "zh"
                    }
                    selectedSourceLanguage = code
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        )
    }

    private var selectedSourceLanguage: String = "zh"

    private fun refreshFromRecord() {
        val rec = runCatching { db.getDownload(recordId) }.getOrNull() ?: return
        record = rec
        binding.tvSubtitleVideoTitle.text = rec.title
        binding.tvSubtitleSource.text = if (rec.sourceUrl.isNotEmpty()) rec.sourceUrl else rec.downloadUrl

        val saved = runCatching { repository.read(recordId) }.getOrNull()
        cues.clear()
        if (!saved.isNullOrEmpty()) {
            cues.addAll(SrtWriter.parse(saved))
        }

        binding.rvCues.visibility = if (cues.isEmpty()) View.GONE else View.VISIBLE
        binding.cardCues.visibility = if (cues.isEmpty()) View.GONE else View.VISIBLE
        subtitleAdapter.submitList(cues.toList())
        binding.tvSubtitleStatus.text = saved?.let { "Đã có sẵn phụ đề" }
            ?: getString(R.string.subtitle_cues_empty)
        binding.pbSubtitle.visibility = View.GONE
        binding.btnGenerateSubtitle.isEnabled = true
    }

    private fun generate() {
        val source = selectedSourceLanguage
        lifecycleScope.launch {
            try {
                val path = processor.process(record = record!!, sourceLanguage = source) { progress ->
                    binding.pbSubtitle.visibility = View.VISIBLE
                    binding.pbSubtitle.progress = progress.percent
                    binding.tvSubtitleStatus.text = progress.message
                }
                toast(getString(R.string.toast_subtitle_created))
                refreshFromRecord()
            } catch (e: Exception) {
                binding.pbSubtitle.visibility = View.GONE
                binding.tvSubtitleStatus.text = getString(R.string.subtitle_error_empty)
                toast(getString(R.string.toast_subtitle_error, e.message ?: ""))
            }
        }
    }

    private fun saveSrt() {
        if (cues.isEmpty()) {
            toast(getString(R.string.toast_subtitle_missing))
            return
        }
        lifecycleScope.launch {
            val content = withContext(Dispatchers.Default) { SrtWriter.write(cues.toList()) }
            processor.repository.save(recordId, content)
            toast(getString(R.string.toast_subtitle_created))
            refreshFromRecord()
        }
    }

    private fun openPlayer() {
        val rec = record ?: return
        val uri = runCatching { MediaUri.resolve(this, rec.filePath) }.getOrNull() ?: return
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_RECORD_ID, recordId)
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, uri.toString())
            putExtra(PlayerActivity.EXTRA_SUBTITLE_URI, Uri.parse(processor.repository.pathFor(recordId)))
            putExtra(PlayerActivity.EXTRA_TITLE, rec.title)
        }
        startActivity(intent)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
