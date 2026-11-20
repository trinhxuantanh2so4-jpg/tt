package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.tt.data.DownloadRecord
import com.example.tt.databinding.ActivityDouyinBinding
import com.example.tt.network.VideoPlatform

/**
 * Tab **Douyin**: tai video Douyin khong logo, kem tuy chon:
 *  - phu de tieng Viet (ASR Moonshine -> dich ML Kit -> .srt)
 *  - thuyet minh (TTS) chay khi nguoi dung bam "Bat dau xu ly"
 */
class DouyinActivity : BaseDownloadActivity() {

    private lateinit var binding: ActivityDouyinBinding

    override val platformFilter: VideoPlatform = VideoPlatform.DOUYIN
    override val navItemId: Int = R.id.nav_douyin
    override val hintRes: Int = R.string.hint_url_douyin
    override val emptyDescRes: Int = R.string.empty_desc_douyin

    private val options get() = binding.form.subtitleOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDouyinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!setupDownloadForm(binding.form, binding.bottomNav)) return

        setupSubtitleOptions()

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_trending) {
                startActivity(Intent(this, TrendingActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updateStartButton()
    }

    private fun setupSubtitleOptions() {
        options.root.visibility = View.VISIBLE

        options.swVietsub.setOnCheckedChangeListener { _, checked ->
            if (!checked) options.swNarration.isChecked = false
            options.swNarration.isEnabled = checked
            updateStartButton()
        }

        options.swNarration.setOnCheckedChangeListener { _, checked ->
            options.tilVoice.visibility = if (checked) View.VISIBLE else View.GONE
            options.layoutBackground.visibility = if (checked) View.VISIBLE else View.GONE
        }

        options.btnStartProcessing.setOnClickListener {
            val rec = current ?: run {
                toast(getString(R.string.toast_need_analyze))
                return@setOnClickListener
            }
            if (rec.filePath.isEmpty()) {
                toast(getString(R.string.toast_need_download_first))
                return@setOnClickListener
            }
            // MVP: vietsub truoc, sau do (neu bat) moi thuyet minh bang TTS.
            openSubtitle(
                rec,
                withNarration = options.swNarration.isChecked,
                voiceLabel = options.spinnerVoice.selectedItem?.toString().orEmpty(),
                keepBackground = options.rbKeepBackground.isChecked,
                autoRun = true
            )
        }

        // Trang thai ban dau
        options.tilVoice.visibility = View.GONE
        options.layoutBackground.visibility = View.GONE
        updateStartButton()
    }

    override fun onVideoCardShown(rec: DownloadRecord, duration: Long?) {
        options.root.visibility = View.VISIBLE
        options.tvProcessEstimate.text = getString(R.string.process_estimate)
        updateStartButton()
    }

    override fun onDownloadCompleted(record: DownloadRecord) {
        // Khong tu mo man phu de: nguoi dung phai bam "Bat dau xu ly" de chay.
        updateStartButton()
        if (options.swVietsub.isChecked) {
            toast(getString(R.string.toast_download_ready_process))
        }
    }

    private fun updateStartButton() {
        val hasFile = current?.filePath?.isNotEmpty() == true
        options.btnStartProcessing.isEnabled = hasFile && options.swVietsub.isChecked
    }

    override fun onWrongPlatform(platform: VideoPlatform, url: String): Boolean {
        if (platform == VideoPlatform.TIKTOK) {
            toast(getString(R.string.toast_switch_tiktok))
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(BaseDownloadActivity.EXTRA_SHARED_URL, url)
            )
            return true
        }
        return false
    }
}
