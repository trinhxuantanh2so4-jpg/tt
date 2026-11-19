package com.example.tt

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.data.UserRepository
import com.example.tt.databinding.PartDownloadFormBinding
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phan dung chung cua 2 tab tai video (TikTok / Douyin):
 *  - nhan link chia se hoac dan tu clipboard
 *  - phan tich link -> card video
 *  - chon noi luu (SAF) hoac luu mac dinh Download/<nen tang>
 *  - tai co tien do / toc do / huy giua chung
 *  - ghi lich su SQLite + tang bo dem Firestore
 *
 * Activity con chi quyet dinh `platformFilter`, `navItemId`, chuoi hien thi va
 * hook sau khi tai xong (`onDownloadCompleted`).
 */
abstract class BaseDownloadActivity : AppCompatActivity() {

    protected lateinit var form: PartDownloadFormBinding
    protected val db: DatabaseHelper by lazy { DatabaseHelper(this) }

    // Video dang xem sau khi phan tich link
    protected var current: DownloadRecord? = null
    protected var currentDuration: Long? = null

    // Video cho san sang tai sau khi nguoi dung chon file/thu muc luu
    private var pendingRecord: DownloadRecord? = null

    private var downloadJob: Job? = null
    private var lastBytes = 0L
    private var lastTime = 0L
    private var lastClipboardSuggestion: String? = null
    protected var lastHandledUrl: String? = null

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** Nen tang duy nhat ma tab nay chap nhan. */
    protected abstract val platformFilter: VideoPlatform

    /** Item bottom nav tuong ung voi tab nay. */
    protected abstract val navItemId: Int

    /** Goi y trong o nhap link (khac nhau giua TikTok / Douyin). */
    protected open val hintRes: Int get() = R.string.hint_url

    /** Mo ta trang thai rong cua tab. */
    protected open val emptyDescRes: Int get() = R.string.empty_desc

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

    /**
     * Goi sau khi Activity con da `setContentView`.
     * @return false neu da chuyen sang man dang nhap (Activity nen ket thuc).
     */
    protected fun setupDownloadForm(
        formBinding: PartDownloadFormBinding,
        nav: BottomNavigationView
    ): Boolean {
        val auth = AuthRepository(UserRepository(db))
        // Phai dang nhap moi dung app
        if (auth.currentUser() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }

        form = formBinding

        form.tvEmptyDesc.setText(emptyDescRes)
        form.tilUrl.hint = getString(hintRes)

        setupBottomNav(nav, navItemId)

        // Dan link nhanh bang icon trong o nhap
        form.tilUrl.setEndIconOnClickListener { pasteFromClipboard(announce = true) }

        form.btnAnalyze.setOnClickListener { analyze() }
        form.btnRetry.setOnClickListener { analyze() }
        form.btnDownload.setOnClickListener { onDownloadClick() }
        form.btnCancel.setOnClickListener { downloadJob?.cancel() }

        // Dong "Luu tai" bam de bat/tat che do chon noi luu
        form.rowSaveTo.setOnClickListener { form.swChooseLocation.toggle() }

        form.swChooseLocation.isChecked = prefs.getBoolean(KEY_CHOOSE_LOCATION, true)
        form.swChooseLocation.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_CHOOSE_LOCATION, checked).apply()
            updateSavePath()
        }
        updateSavePath()

        handleIntent(intent)
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::form.isInitialized) return
        // Xu ly lai intent (neu chua xu ly) - vd: app bi huy roi mo lai tu link chia se
        handleIntent(intent)
        // Neu o nhap con trong va clipboard co link -> goi y dan
        if (form.etUrl.text.isNullOrEmpty()) suggestFromClipboard()
    }

    // ---------------- NHAN LINK TU CHIA SE / TU TAB THINH HANH ----------------
    protected open fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = UrlParser.extractUrl(shared) ?: return
            // Khong xu ly lai cung 1 link (onResume / onNewIntent co the goi nhieu lan)
            if (url == lastHandledUrl) return
            lastHandledUrl = url
            intent.removeExtra(Intent.EXTRA_TEXT)
            bindingUrl(url)
            analyze()
            return
        }

        // Duoc mo tu tab "Thinh hanh" hoac tu tab con lai
        val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
        if (!downloadUrl.isNullOrEmpty()) {
            if (downloadUrl == lastHandledUrl) return
            lastHandledUrl = downloadUrl
            intent.removeExtra(EXTRA_DOWNLOAD_URL)
            val rec = DownloadRecord(
                videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: System.currentTimeMillis().toString(),
                title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name),
                author = intent.getStringExtra(EXTRA_AUTHOR) ?: getString(R.string.app_name),
                coverUrl = intent.getStringExtra(EXTRA_COVER) ?: "",
                sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL) ?: "",
                downloadUrl = downloadUrl,
                quality = intent.getStringExtra(EXTRA_QUALITY) ?: "NO_WATERMARK",
                fileSize = intent.getLongExtra(EXTRA_SIZE, 0L),
                platform = VideoPlatform.fromId(intent.getStringExtra(EXTRA_PLATFORM))
            )
            if (rec.platform != platformFilter) {
                onWrongPlatform(rec.platform, rec.sourceUrl)
                return
            }
            current = rec
            currentDuration = intent.getLongExtra(EXTRA_DURATION, 0L).takeIf { it > 0 }
            showVideoCard(rec, currentDuration)
            return
        }

        // Tab nay duoc mo tu tab kia voi 1 link cu the (tab Douyin chan link TikTok)
        val sharedUrl = intent.getStringExtra(EXTRA_SHARED_URL)
        if (!sharedUrl.isNullOrEmpty()) {
            intent.removeExtra(EXTRA_SHARED_URL)
            bindingUrl(sharedUrl)
            analyze()
        }
    }

    private fun bindingUrl(url: String) {
        form.etUrl.setText(url)
        form.etUrl.setSelection(url.length)
    }

    // ---------------- GOI API: PHAN TICH LINK ----------------
    protected fun analyze() {
        val raw = form.etUrl.text.toString().trim()
        // Nguoi dung co the dan ca text chia se (Douyin/Trung Quoc) -> tach link truoc
        val url = UrlParser.extractUrl(raw) ?: raw
        val platform = UrlParser.platformOf(url)
        if (platform == null) {
            toast(getString(R.string.toast_need_link))
            return
        }
        if (platform != platformFilter) {
            if (onWrongPlatform(platform, url)) return
            toast(
                getString(
                    R.string.toast_wrong_platform,
                    platformName(platform),
                    platformName(platformFilter)
                )
            )
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            try {
                val info = VideoResolver.resolve(url)
                current = info.record
                currentDuration = info.duration
                form.progressDownload.progress = 0
                form.tvPercent.text = ""
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

    /**
     * Nguoi dung dan link cua nen tang con lai.
     * @return true neu Activity con da xu ly (vd: chuyen tab) va khong can bao loi.
     */
    protected open fun onWrongPlatform(platform: VideoPlatform, url: String): Boolean = false

    // ---------------- HIEN THI CARD VIDEO ----------------
    private fun showVideoCard(rec: DownloadRecord, duration: Long?) {
        form.layoutEmpty.visibility = View.GONE
        form.layoutError.visibility = View.GONE
        form.cardVideo.visibility = View.VISIBLE

        form.tvTitle.text = rec.title
        form.tvAuthor.text = rec.author
        form.tvSize.text = formatBytes(rec.fileSize)
        val quality = if (rec.quality == "HD") {
            getString(R.string.quality_hd)
        } else {
            getString(R.string.quality_no_wm)
        }
        form.tvQualityChip.text = getString(
            R.string.chip_platform_quality,
            platformName(rec.platform),
            quality
        )

        val durationText = formatDuration(duration)
        form.tvDuration.text = durationText
        form.tvDuration.visibility = if (durationText.isEmpty()) View.GONE else View.VISIBLE

        if (rec.coverUrl.isNotEmpty()) {
            form.imgCover.load(rec.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_media_placeholder)
                error(R.drawable.bg_media_placeholder)
            }
        } else {
            form.imgCover.setImageResource(R.drawable.bg_media_placeholder)
        }

        onVideoCardShown(rec, duration)
    }

    /** Hook cho tab Douyin: hien khoi vietsub / thuyet minh. */
    protected open fun onVideoCardShown(rec: DownloadRecord, duration: Long?) = Unit

    private fun showError() {
        form.cardVideo.visibility = View.GONE
        form.layoutEmpty.visibility = View.GONE
        form.layoutError.visibility = View.VISIBLE
    }

    /** Ten hien thi cua nen tang (tieng Viet). */
    protected fun platformName(platform: VideoPlatform): String = when (platform) {
        VideoPlatform.DOUYIN -> getString(R.string.platform_douyin)
        VideoPlatform.TIKTOK -> getString(R.string.platform_tiktok)
    }

    /** Thu muc mac dinh trong Download cho tung nen tang. */
    private fun folderName(platform: VideoPlatform): String = when (platform) {
        VideoPlatform.DOUYIN -> "Douyin"
        VideoPlatform.TIKTOK -> "TikTok"
    }

    /** Tien to ten file: douyin_..., tiktok_... */
    protected fun fileBaseName(rec: DownloadRecord): String =
        "${rec.platform.id}_${rec.videoId}".replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun showLoading(on: Boolean) {
        form.layoutLoading.visibility = if (on) View.VISIBLE else View.GONE
        form.btnAnalyze.isEnabled = !on
        form.btnRetry.isEnabled = !on
        if (on) {
            form.cardVideo.visibility = View.GONE
            form.layoutEmpty.visibility = View.GONE
            form.layoutError.visibility = View.GONE
        }
    }

    // ---------------- TAI VIDEO ----------------
    private fun onDownloadClick() {
        val rec = current ?: run {
            toast(getString(R.string.toast_need_analyze))
            return
        }
        if (form.swChooseLocation.isChecked) {
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
        form.progressDownload.progress = 0
        form.tvPercent.text = "0%"
        form.btnDownload.isEnabled = false
        form.btnAnalyze.isEnabled = false
        form.btnCancel.visibility = View.VISIBLE

        downloadJob = lifecycleScope.launch {
            val folder = folderName(rec.platform)
            try {
                val path = if (target != null) {
                    VideoDownloader.downloadToUri(
                        context = this@BaseDownloadActivity,
                        url = rec.downloadUrl,
                        target = target
                    ) { done, total -> onProgress(done, total) }
                } else {
                    VideoDownloader.download(
                        context = this@BaseDownloadActivity,
                        url = rec.downloadUrl,
                        fileName = "${fileBaseName(rec)}.mp4",
                        folder = folder
                    ) { done, total -> onProgress(done, total) }
                }

                // KY THUAT 1: luu lich su vao SQLite (IO thread)
                val saved = rec.copy(filePath = path)
                val newId = withContext(Dispatchers.IO) { db.insertDownload(saved) }
                val record = saved.copy(id = if (newId > 0) newId else saved.id)

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

                form.tvPercent.text = getString(R.string.done_percent)
                toast(
                    if (target != null) getString(R.string.toast_saved_custom)
                    else getString(R.string.toast_saved_default, folder)
                )
                current = record
                onDownloadCompleted(record)
            } catch (ce: CancellationException) {
                // File vua tao (neu co) -> xoa de khong con file rong
                if (target != null) runCatching { contentResolver.delete(target, null, null) }
                form.tvPercent.text = ""
                form.progressDownload.progress = 0
                toast(getString(R.string.toast_download_canceled))
            } catch (e: Exception) {
                if (target != null) runCatching { contentResolver.delete(target, null, null) }
                form.tvPercent.text = getString(R.string.failed_percent)
                toast(getString(R.string.toast_download_error, e.message ?: ""))
            } finally {
                form.btnCancel.visibility = View.GONE
                form.btnDownload.isEnabled = true
                form.btnAnalyze.isEnabled = true
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
                form.progressDownload.progress = pct
                val speedText = formatSpeed(speed)
                form.tvPercent.text =
                    if (speedText.isEmpty()) "$pct%" else "$pct% · $speedText"
            } else {
                form.tvPercent.text = formatBytes(done)
            }
        }
    }

    /** Hook sau khi video da tai xong va co id trong SQLite. */
    protected open fun onDownloadCompleted(record: DownloadRecord) = Unit

    private fun updateSavePath() {
        form.tvSavePath.text = if (form.swChooseLocation.isChecked) {
            getString(R.string.save_each_time)
        } else {
            getString(R.string.save_default_path)
        }
    }

    // ---------------- MO MAN HINH PHU DE (Phase B+C) ----------------
    protected fun openSubtitle(rec: DownloadRecord) {
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
        bindingUrl(url)
        if (announce) toast(getString(R.string.toast_paste_done))
    }

    private fun suggestFromClipboard() {
        val text = readClipboard() ?: return
        val url = UrlParser.extractUrl(text) ?: return
        // Chi goi y link phu hop voi tab dang mo
        if (UrlParser.platformOf(url) != platformFilter) return
        if (url == lastClipboardSuggestion) return
        lastClipboardSuggestion = url
        bindingUrl(url)
        toast(getString(R.string.toast_paste_done))
    }

    protected fun toast(message: String) =
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
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_SHARED_URL = "extra_shared_url"

        private const val PREFS = "tt_prefs"
        private const val KEY_CHOOSE_LOCATION = "choose_location"
    }
}
