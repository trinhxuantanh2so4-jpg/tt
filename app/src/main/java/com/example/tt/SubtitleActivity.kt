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
import com.example.tt.subtitle.NarrationProcessor
import com.example.tt.subtitle.SubtitleCue
import com.example.tt.subtitle.SubtitleProcessor
import com.example.tt.subtitle.SrtWriter
import com.example.tt.subtitle.SubtitleRepository
import com.example.tt.subtitle.TtsEngine
import com.example.tt.ui.MediaUri
import com.example.tt.ui.SubtitleAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        const val EXTRA_WITH_NARRATION = "extra_with_narration"
        const val EXTRA_VOICE_LABEL = "extra_voice_label"
        const val EXTRA_KEEP_BACKGROUND = "extra_keep_background"
        const val EXTRA_AUTO_RUN = "extra_auto_run"
        const val PREFS_KEY_CURRENT_SUBTITLE_RECORD = "current_subtitle_record_id"

        fun createIntent(
            context: Context,
            recordId: Long,
            platformId: String,
            withNarration: Boolean = false,
            voiceLabel: String = "",
            keepBackground: Boolean = true,
            autoRun: Boolean = false
        ): Intent =
            Intent(context, SubtitleActivity::class.java).apply {
                putExtra(EXTRA_RECORD_ID, recordId)
                putExtra(EXTRA_PLATFORM, platformId)
                putExtra(EXTRA_WITH_NARRATION, withNarration)
                putExtra(EXTRA_VOICE_LABEL, voiceLabel)
                putExtra(EXTRA_KEEP_BACKGROUND, keepBackground)
                putExtra(EXTRA_AUTO_RUN, autoRun)
            }
    }

    private lateinit var binding: ActivitySubtitleBinding
    private lateinit var db: DatabaseHelper

    private var recordId: Long = 0L
    private var record: DownloadRecord? = null

    private val repository = SubtitleRepository(this)
    private val processor = SubtitleProcessor(this)
    private val narrationProcessor by lazy { NarrationProcessor(this) }

    private val cues = mutableListOf<SubtitleCue>()

    private var withNarration = false
    private var voiceLabel = TtsEngine.VoiceOption.DEFAULT.label
    private var keepBackground = true
    private var autoRun = false

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

        val loaded = db.getDownload(recordId)
        if (loaded == null) {
            toast(getString(R.string.toast_subtitle_missing))
            finish()
            return
        }
        record = loaded

        withNarration = intent.getBooleanExtra(EXTRA_WITH_NARRATION, false)
        keepBackground = intent.getBooleanExtra(EXTRA_KEEP_BACKGROUND, true)
        autoRun = intent.getBooleanExtra(EXTRA_AUTO_RUN, false)
        voiceLabel = intent.getStringExtra(EXTRA_VOICE_LABEL)
            ?.takeIf { it.isNotBlank() }
            ?: loaded.voice.takeIf { it.isNotBlank() }
            ?: TtsEngine.VoiceOption.DEFAULT.label

        // Dia chi ban ghi duoc luu tu lan truoc; neu chua co thi mac dinh theo nen tang.
        val initialLanguage = loaded.subtitleLang.takeIf { it.isNotBlank() }
            ?: if (loaded.platform == VideoPlatform.DOUYIN) "zh" else "en"
        selectedSourceLanguage = initialLanguage
        binding.spinnerSubtitleLanguage.setSelection(languageIndex(initialLanguage))

        setupToolbar()
        setupCuesRecycler()
        setupLanguageListeners()
        setupNarrationControls()
        binding.btnGenerateSubtitle.setOnClickListener { generate() }
        binding.btnSaveSrt.setOnClickListener { saveSrt() }
        binding.btnOpenPlayer.setOnClickListener { openPlayer() }
        binding.btnCreateNarration.setOnClickListener { startNarration() }
        binding.btnOpenDub.setOnClickListener { openDub() }

        refreshFromRecord()

        binding.tvSubtitleStatus.text = if (withNarration) {
            getString(R.string.narration_progress_check_tts)
        } else if (cues.isEmpty()) {
            getString(R.string.subtitle_cues_empty)
        } else {
            getString(R.string.subtitle_ready)
        }

        // Tab Douyin bam "Bat dau xu ly" -> chay luon, khong bat nguoi dung bam them.
        if (autoRun) {
            if (cues.isEmpty()) generate() else if (withNarration) startNarration()
        }
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

    private fun setupNarrationControls() {
        binding.spinnerVoice.setSelection(voiceIndex(voiceLabel))
        voiceLabel = binding.spinnerVoice.selectedItem?.toString()?.takeIf { it.isNotBlank() }
            ?: TtsEngine.VoiceOption.DEFAULT.label
        binding.spinnerVoice.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    voiceLabel = binding.spinnerVoice.selectedItem?.toString() ?: voiceLabel
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        binding.rgBackground.check(
            if (keepBackground) R.id.rbKeepBackground else R.id.rbNarrationOnly
        )
        binding.rgBackground.setOnCheckedChangeListener { _, checkedId ->
            keepBackground = checkedId != R.id.rbNarrationOnly
        }
    }

    private fun languageIndex(code: String): Int = when (code) {
        "en" -> 1
        "vi" -> 2
        else -> 0
    }

    private fun voiceIndex(label: String): Int {
        val labels = resources.getStringArray(R.array.voice_options)
        return labels.indexOfFirst { it.equals(label, ignoreCase = true) }.coerceAtLeast(0)
    }

    private var selectedSourceLanguage: String = "zh"

    private fun refreshFromRecord() {
        val rec = runCatching { db.getDownload(recordId) }.getOrNull() ?: return
        record = rec
        binding.tvSubtitleVideoTitle.text = rec.title
        binding.tvSubtitleSource.text = if (rec.sourceUrl.isNotEmpty()) rec.sourceUrl else rec.downloadUrl

        val saved = runCatching { repository.read(recordId) }.getOrNull()
        val sources = runCatching { repository.readSources(recordId) }.getOrDefault(emptyList())
        cues.clear()
        if (!saved.isNullOrEmpty()) {
            SrtWriter.parse(saved).forEachIndexed { index, cue ->
                cues.add(if (index < sources.size) cue.copy(source = sources[index]) else cue)
            }
        }

        binding.rvCues.visibility = if (cues.isEmpty()) View.GONE else View.VISIBLE
        binding.cardCues.visibility = if (cues.isEmpty()) View.GONE else View.VISIBLE
        subtitleAdapter.submitList(cues.toList())
        binding.tvSubtitleStatus.text = if (saved.isNullOrEmpty()) {
            getString(R.string.subtitle_cues_empty)
        } else {
            getString(R.string.subtitle_ready)
        }
        binding.pbSubtitle.visibility = View.GONE
        binding.pbSubtitle.isIndeterminate = true
        binding.btnGenerateSubtitle.isEnabled = true
        binding.btnCreateNarration.visibility =
            if (cues.isEmpty()) View.GONE else View.VISIBLE
        binding.btnOpenDub.visibility = if (rec.dubPath.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun generate() {
        val source = selectedSourceLanguage
        binding.btnCreateNarration.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val path = processor.process(record = record!!, sourceLanguage = source) { progress ->
                    binding.pbSubtitle.visibility = View.VISIBLE
                    binding.pbSubtitle.isIndeterminate = false
                    binding.pbSubtitle.progress = progress.percent
                    binding.tvSubtitleStatus.text = progress.message
                }
                withContext(Dispatchers.IO) {
                    db.updateSubtitle(recordId, path, "done", "vi", source)
                }
                toast(getString(R.string.toast_subtitle_created))
                refreshFromRecord()
                if (withNarration) startNarration()
            } catch (e: CancellationException) {
                binding.pbSubtitle.visibility = View.GONE
                throw e
            } catch (e: Exception) {
                binding.pbSubtitle.visibility = View.GONE
                binding.tvSubtitleStatus.text = getString(R.string.subtitle_error_empty)
                toast(getString(R.string.toast_subtitle_error, e.message ?: ""))
            } finally {
                binding.btnCreateNarration.visibility =
                    if (cues.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    /** Phase D: doc tung cau phu de -> track thuyet minh -> MP4 long tieng. */
    private fun startNarration() {
        val rec = record ?: return
        if (cues.isEmpty()) {
            toast(getString(R.string.subtitle_cues_empty))
            return
        }
        // Auto-run tu nut "Bat dau xu ly" khong can hoi lai; mo tu lich sử thi co xac nhan.
        if (rec.dubPath.isNotEmpty() && !autoRun) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_replace_narration_title)
                .setMessage(R.string.dialog_replace_narration_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_replace) { _, _ -> runNarration(cues.toList()) }
                .show()
        } else {
            runNarration(cues.toList())
        }
    }

    private fun runNarration(cueSnapshot: List<SubtitleCue>) {
        val rec = record ?: return
        val option = TtsEngine.VoiceOption.fromLabel(voiceLabel)
        binding.btnCreateNarration.isEnabled = false
        binding.btnGenerateSubtitle.isEnabled = false
        lifecycleScope.launch {
            try {
                binding.pbSubtitle.visibility = View.VISIBLE
                binding.pbSubtitle.isIndeterminate = false
                binding.pbSubtitle.progress = 0
                // Luu phu de da sua truoc de MP4 khong lech text so voi .srt.
                persistSubtitleNow(selectedSourceLanguage)
                val result = narrationProcessor.process(
                    record = rec,
                    cues = cueSnapshot,
                    voice = option,
                    keepBackground = keepBackground
                ) { progress ->
                    binding.pbSubtitle.progress = progress.percent
                    binding.tvSubtitleStatus.text = progress.message
                }
                withContext(Dispatchers.IO) {
                    db.updateDub(recordId, result.dubPath, option.label, "done")
                }
                toast(getString(R.string.narration_done))
                refreshFromRecord()
            } catch (e: CancellationException) {
                binding.pbSubtitle.visibility = View.GONE
                throw e
            } catch (e: Exception) {
                binding.pbSubtitle.visibility = View.GONE
                binding.tvSubtitleStatus.text = e.message ?: getString(R.string.subtitle_error_empty)
                toast(getString(R.string.toast_subtitle_error, e.message ?: ""))
            } finally {
                binding.btnCreateNarration.isEnabled = true
                binding.btnGenerateSubtitle.isEnabled = true
            }
        }
    }

    /** Ghi .srt + cau goc va cap nhat SQLite de lich sử biet ban xu ly co that. */
    private suspend fun persistSubtitleNow(sourceLanguage: String) {
        val content = withContext(Dispatchers.Default) { SrtWriter.write(cues.toList()) }
        val path = withContext(Dispatchers.IO) {
            processor.repository.save(recordId, content)
            processor.repository.saveSources(recordId, cues.map { it.source })
            processor.repository.pathFor(recordId)
        }
        withContext(Dispatchers.IO) {
            db.updateSubtitle(recordId, path, "done", "vi", sourceLanguage)
        }
    }

    private fun saveSrt() {
        if (cues.isEmpty()) {
            toast(getString(R.string.toast_subtitle_missing))
            return
        }
        lifecycleScope.launch {
            persistSubtitleNow(selectedSourceLanguage)
            toast(getString(R.string.toast_subtitle_created))
            refreshFromRecord()
        }
    }

    private fun openPlayer() {
        val rec = record ?: return
        val uri = runCatching { MediaUri.resolve(this, rec.filePath) }.getOrNull() ?: return
        val srtPath = processor.repository.pathFor(recordId)
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_RECORD_ID, recordId)
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, uri.toString())
            if (File(srtPath).exists()) {
                putExtra(PlayerActivity.EXTRA_SUBTITLE_URI, Uri.parse(srtPath))
            }
            putExtra(PlayerActivity.EXTRA_START_VARIANT, PlayerActivity.VARIANT_VIETSUB)
            putExtra(PlayerActivity.EXTRA_TITLE, rec.title)
        }
        startActivity(intent)
    }

    /** Mo ban MP4 da long tieng thuyet minh, van chong phu de neu co. */
    private fun openDub() {
        val rec = record ?: return
        if (rec.dubPath.isEmpty()) return
        val originalUri = runCatching { MediaUri.resolve(this, rec.filePath) }.getOrNull()
        val dubUri = runCatching { MediaUri.resolve(this, rec.dubPath) }.getOrNull()
        if (originalUri == null || dubUri == null) {
            toast(getString(R.string.toast_file_missing))
            return
        }
        val srtPath = processor.repository.pathFor(recordId)
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_RECORD_ID, recordId)
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, originalUri.toString())
            putExtra(PlayerActivity.EXTRA_DUB_URI, dubUri.toString())
            if (File(srtPath).exists()) {
                putExtra(PlayerActivity.EXTRA_SUBTITLE_URI, Uri.parse(srtPath))
            }
            putExtra(PlayerActivity.EXTRA_START_VARIANT, PlayerActivity.VARIANT_NARRATION)
            putExtra(PlayerActivity.EXTRA_TITLE, rec.title)
        }
        startActivity(intent)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
