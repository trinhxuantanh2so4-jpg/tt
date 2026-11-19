package com.example.tt

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.data.UserRepository
import com.example.tt.databinding.ActivityMainBinding
import com.example.tt.download.VideoDownloader
import com.example.tt.firebase.AuthRepository
import com.example.tt.firebase.ProfileRepository
import com.example.tt.network.UrlParser
import com.example.tt.network.VideoPlatform
import com.example.tt.network.VideoResolver
import com.example.tt.ui.formatBytes
import com.example.tt.ui.formatDuration
import com.example.tt.ui.formatSpeed
import com.example.tt.ui.setupBottomNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Man chinh (tab "Tai video"):
 *  1. Dan / chia se link TikTok hoac Douyin -> VideoResolver chon dung provider
 *     (TikTok: tikwm.com, Douyin: trang share iesdouyin) de lay link KHONG LOGO
 *  2. Chon noi luu (trinh chon tep he thong) hoac luu mac dinh Download/TikTok|Douyin
 *  3. Tai video co tien do + toc do + huy giua chung
 *  4. Luu lich su SQLite + tang bo dem Firestore
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DatabaseHelper

    // Video dang xem sau khi phan tich link
    private var current: DownloadRecord? = null
    private var currentDuration: Long? = null

    // Video cho san sang tai sau khi nguoi dung chon file/thu muc luu
    private var pendingRecord: DownloadRecord? = null

    private var downloadJob: Job? = null
    private var lastBytes = 0L
    private var lastTime = 0L
    private var lastClipboardSuggestion: String? = null
    private var lastHandledUrl: String? = null

    private val prefs by lazy { getSharedPreferences("tt_prefs", MODE_PRIVATE) }

    /** Trinh chon tep cua he thong: nguoi dung tu dat ten file + thu muc luu. */
    private val saveFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            val rec = pendingRecord ?: current
            pendingRecord = null
            if (uri == null) {
                toast(getString(R.string.toast_choose_canceled))
                return@registerForActivityResult
            }
            if (rec == null) {
                toast(getString(R.string.toast_no_video))
                return@registerForActivityResult
            }
            startDownload(rec, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper(this)
        val auth = AuthRepository(UserRepository(db))

        // Phai dang nhap moi dung app
        if (auth.currentUser() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupBottomNav(binding.bottomNav, R.id.nav_download)

        // Dan link nhanh bang icon trong o nhap
        binding.tilUrl.setEndIconOnClickListener { pasteFromClipboard(announce = true) }

        binding.btnAnalyze.setOnClickListener { analyze() }
        binding.btnRetry.setOnClickListener { analyze() }
        binding.btnDownload.setOnClickListener { onDownloadClick() }
        binding.btnCancel.setOnClickListener { downloadJob?.cancel() }
        binding.btnSubtitle.setOnClickListener { openSubtitle() }

        // Dong "Luu tai" bam de bat/tat che do chon noi luu
        binding.rowSaveTo.setOnClickListener { binding.swChooseLocation.toggle() }

        binding.swChooseLocation.isChecked = prefs.getBoolean(KEY_CHOOSE_LOCATION, true)
        binding.swChooseLocation.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_CHOOSE_LOCATION, checked).apply()
            updateSavePath()
        }
        updateSavePath()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        // Xu ly lai intent (neu chua xu ly) - vd: app bi huy roi mo lai tu link chia se
        handleIntent(intent)
        // Neu o nhap con trong va clipboard co link TikTok -> goi y dan
        if (binding.etUrl.text.isNullOrEmpty()) suggestFromClipboard()
    }

    // ---------------- NHAN LINK TU CHIA SE / TU TAB THINH HANH ----------------
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = UrlParser.extractUrl(shared) ?: return
            // Khong xu ly lai cung 1 link (onResume / onNewIntent co the goi nhieu lan)
            if (url == lastHandledUrl) return
            lastHandledUrl = url
            intent.removeExtra(Intent.EXTRA_TEXT)
            binding.etUrl.setText(url)
            binding.etUrl.setSelection(url.length)
            analyze()
            return
        }

        // Duoc mo tu tab "Thinh hanh"
        val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
        if (!downloadUrl.isNullOrEmpty()) {
            if (downloadUrl == lastHandledUrl) return
            lastHandledUrl = downloadUrl
            intent.removeExtra(EXTRA_DOWNLOAD_URL)
            val rec = DownloadRecord(
                videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: System.currentTimeMillis().toString(),
                title = intent.getStringExtra(EXTRA_TITLE) ?: "Video TikTok",
                author = intent.getStringExtra(EXTRA_AUTHOR) ?: getString(R.string.app_name),
                coverUrl = intent.getStringExtra(EXTRA_COVER) ?: "",
                sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL) ?: "",
                downloadUrl = downloadUrl,
                quality = intent.getStringExtra(EXTRA_QUALITY) ?: "NO_WATERMARK",
                fileSize = intent.getLongExtra(EXTRA_SIZE, 0L)
            )
            current = rec
            currentDuration = intent.getLongExtra(EXTRA_DURATION, 0L).takeIf { it > 0 }
            showVideoCard(rec, currentDuration)
        }
    }

    // ---------------- GOI API: PHAN TICH LINK ----------------
    private fun analyze() {
        val raw = binding.etUrl.text.toString().trim()
        // Nguoi dung co the dan ca text chia se (Douyin/Trung Quoc) -> tach link truoc
        val url = UrlParser.extractUrl(raw) ?: raw
        if (UrlParser.platformOf(url) == null) {
            toast(getString(R.string.toast_need_link))
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            try {
                val info = VideoResolver.resolve(url)
                current = info.record
                currentDuration = info.duration
                binding.progressDownload.progress = 0
                binding.tvPercent.text = ""
                showVideoCard(info.record, info.duration)
            } catch (e: Exception) {
                current = null
                currentDuration = null
                showError()
                toast(e.message ?: getString(R.string.toast_analyze_error))
            } finally {
                showLoading(false)
            }
        }
    }

    // ---------------- HIEN THI CARD VIDEO ----------------
    private fun showVideoCard(rec: DownloadRecord, duration: Long?) {
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.cardVideo.visibility = View.VISIBLE
        binding.btnSubtitle.visibility = View.VISIBLE

        binding.tvTitle.text = rec.title
        binding.tvAuthor.text = rec.author
        binding.tvSize.text = formatBytes(rec.fileSize)
        val quality = if (rec.quality == "HD") {
            getString(R.string.quality_hd)
        } else {
            getString(R.string.quality_no_wm)
        }
        binding.tvQualityChip.text = getString(
            R.string.chip_platform_quality,
            platformName(rec.platform),
            quality
        )

        val durationText = formatDuration(duration)
        binding.tvDuration.text = durationText
        binding.tvDuration.visibility = if (durationText.isEmpty()) View.GONE else View.VISIBLE

        if (rec.coverUrl.isNotEmpty()) {
            binding.imgCover.load(rec.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_media_placeholder)
                error(R.drawable.bg_media_placeholder)
            }
        } else {
            binding.imgCover.setImageResource(R.drawable.bg_media_placeholder)
        }
    }

    private fun showError() {
        binding.cardVideo.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
    }

    /** Ten hien thi cua nen tang (tieng Viet). */
    private fun platformName(platform: VideoPlatform): String = when (platform) {
        VideoPlatform.DOUYIN -> getString(R.string.platform_douyin)
        VideoPlatform.TIKTOK -> getString(R.string.platform_tiktok)
    }

    /** Thu muc mac dinh trong Download cho tung nen tang. */
    private fun folderName(platform: VideoPlatform): String = when (platform) {
        VideoPlatform.DOUYIN -> "Douyin"
        VideoPlatform.TIKTOK -> "TikTok"
    }

    /** Tien to ten file: douyin_..., tiktok_... */
    private fun fileBaseName(rec: DownloadRecord): String =
        "${rec.platform.id}_${rec.videoId}".replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun showLoading(on: Boolean) {
        binding.layoutLoading.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnAnalyze.isEnabled = !on
        binding.btnRetry.isEnabled = !on
        if (on) {
            binding.cardVideo.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
            binding.layoutError.visibility = View.GONE
        }
    }

    // ---------------- TAI VIDEO ----------------
    private fun onDownloadClick() {
        val rec = current ?: run {
            toast(getString(R.string.toast_need_analyze))
            return
        }
        if (binding.swChooseLocation.isChecked) {
            pendingRecord = rec
            saveFileLauncher.launch("${fileBaseName(rec)}.mp4")
        } else {
            startDownload(rec, null)
        }
    }

    private fun startDownload(rec: DownloadRecord, target: Uri?) {
        if (downloadJob?.isActive == true) return

        lastBytes = 0L
        lastTime = SystemClock.elapsedRealtime()
        binding.progressDownload.progress = 0
        binding.tvPercent.text = "0%"
        binding.btnDownload.isEnabled = false
        binding.btnAnalyze.isEnabled = false
        binding.btnCancel.visibility = View.VISIBLE

        downloadJob = lifecycleScope.launch {
            val folder = folderName(rec.platform)
            try {
                val path = if (target != null) {
                    VideoDownloader.downloadToUri(
                        context = this@MainActivity,
                        url = rec.downloadUrl,
                        target = target
                    ) { done, total -> onProgress(done, total) }
                } else {
                    VideoDownloader.download(
                        context = this@MainActivity,
                        url = rec.downloadUrl,
                        fileName = "${fileBaseName(rec)}.mp4",
                        folder = folder
                    ) { done, total -> onProgress(done, total) }
                }

                // KY THUAT 1: luu lich su vao SQLite (IO thread)
                withContext(Dispatchers.IO) { db.insertDownload(rec.copy(filePath = path)) }

                // Giu quyen doc file nguoi dung chon de tab Lich su mo lai duoc video
                if (target != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            target,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }

                // KY THUAT 3: goi backend Firestore tang bo dem
                val uid = withContext(Dispatchers.IO) { UserRepository(db).currentSessionUid() }
                if (uid != null && !uid.startsWith("offline-")) {
                    runCatching { ProfileRepository().incrementDownloadCount(uid) }
                }

                binding.tvPercent.text = getString(R.string.done_percent)
                toast(
                    if (target != null) getString(R.string.toast_saved_custom)
                    else getString(R.string.toast_saved_default, folder)
                )
            } catch (ce: CancellationException) {
                // File vua tao (neu co) -> xoa de khong con file rong
                if (target != null) runCatching { contentResolver.delete(target, null, null) }
                binding.tvPercent.text = ""
                binding.progressDownload.progress = 0
                toast(getString(R.string.toast_download_canceled))
            } catch (e: Exception) {
                if (target != null) runCatching { contentResolver.delete(target, null, null) }
                binding.tvPercent.text = getString(R.string.failed_percent)
                toast(getString(R.string.toast_download_error, e.message ?: ""))
            } finally {
                binding.btnCancel.visibility = View.GONE
                binding.btnDownload.isEnabled = true
                binding.btnAnalyze.isEnabled = true
                downloadJob = null
            }
        }
    }

    private fun onProgress(done: Long, total: Long) {
        val now = SystemClock.elapsedRealtime()
        val deltaTime = now - lastTime
        val speed = if (deltaTime > 0) (done - lastBytes) * 1000f / deltaTime else 0f
        lastBytes = done
        lastTime = now

        runOnUiThread {
            if (total > 0) {
                val pct = (done * 100 / total).toInt()
                binding.progressDownload.progress = pct
                val speedText = formatSpeed(speed)
                binding.tvPercent.text =
                    if (speedText.isEmpty()) "$pct%" else "$pct% · $speedText"
            } else {
                binding.tvPercent.text = formatBytes(done)
            }
        }
    }

    private fun updateSavePath() {
        binding.tvSavePath.text = if (binding.swChooseLocation.isChecked) {
            getString(R.string.save_each_time)
        } else {
            getString(R.string.save_default_path)
        }
    }

    // ---------------- MO MAN HINH PHU DE (Phase B+C) ----------------
    private fun openSubtitle() {
        val rec = current ?: run {
            toast(getString(R.string.toast_need_analyze))
            return
        }
        if (rec.id <= 0L) {
            toast(getString(R.string.toast_need_analyze))
            return
        }
        startActivity(
            SubtitleActivity.createIntent(this, rec.id, rec.platform.id)
        )
    }

    // ---------------- CLIPBOARD ----------------
    private fun readClipboard(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun pasteFromClipboard(announce: Boolean) {
        val text = readClipboard()
        val url = text?.let { UrlParser.extractUrl(it) }
        if (url == null) {
            toast(getString(R.string.toast_paste_empty))
            return
        }
        binding.etUrl.setText(url)
        binding.etUrl.setSelection(url.length)
        if (announce) toast(getString(R.string.toast_paste_done))
    }

    private fun suggestFromClipboard() {
        val text = readClipboard() ?: return
        val url = UrlParser.extractUrl(text) ?: return
        if (url == lastClipboardSuggestion) return
        lastClipboardSuggestion = url
        binding.etUrl.setText(url)
        binding.etUrl.setSelection(url.length)
        toast(getString(R.string.toast_paste_done))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_COVER = "extra_cover"
        const val EXTRA_SOURCE_URL = "extra_source_url"
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_SIZE = "extra_size"
        const val EXTRA_DURATION = "extra_duration"

        private const val KEY_CHOOSE_LOCATION = "choose_location"
    }
}
