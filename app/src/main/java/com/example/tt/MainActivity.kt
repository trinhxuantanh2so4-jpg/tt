package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.tt.data.DatabaseHelper
import com.example.tt.data.DownloadRecord
import com.example.tt.data.UserRepository
import com.example.tt.databinding.ActivityMainBinding
import com.example.tt.download.VideoDownloader
import com.example.tt.firebase.AuthRepository
import com.example.tt.firebase.ProfileRepository
import com.example.tt.network.ApiClient
import com.example.tt.ui.TrendingAdapter
import kotlinx.coroutines.launch

/**
 * Man chinh:
 *  1. Dan link TikTok -> goi API tikwm.com lay link KHONG LOGO (Ky thuat 2)
 *  2. Tai video ve may + luu lich su vao SQLite (Ky thuat 1)
 *  3. Sau khi tai xong goi Firestore tang bo dem (Ky thuat 3)
 *  4. Hien thi danh sach video thinh hanh tu API (Ky thuat 2)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DatabaseHelper
    private lateinit var trendingAdapter: TrendingAdapter

    // Video dang xem sau khi phan tich link
    private var current: DownloadRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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

        binding.btnAnalyze.setOnClickListener { analyze() }
        binding.btnDownload.setOnClickListener { downloadCurrent() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Danh sach video thinh hanh (goi API #2)
        trendingAdapter = TrendingAdapter { pickTrending(it) }
        binding.rvTrending.layoutManager = LinearLayoutManager(this)
        binding.rvTrending.adapter = trendingAdapter
        loadTrending()
    }

    // ---------------- GOI API: PHAN TICH LINK TIKTOK ----------------
    private fun analyze() {
        val url = binding.etUrl.text.toString().trim()
        if (!url.contains("tiktok.com", true)) {
            toast("Vui lòng dán link từ tiktok.com"); return
        }
        setLoading(true, "Đang phân tích link...")
        lifecycleScope.launch {
            try {
                val rec = ApiClient.fetchVideoInfo(url)
                current = rec
                showVideoCard(rec)
            } catch (e: Exception) {
                toast(e.message ?: "Không phân tích được link")
            }
            setLoading(false)
        }
    }

    // ---------------- GOI API: VIDEO THINH HANH ----------------
    private fun loadTrending() {
        binding.progressTrending.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val feed = ApiClient.api.getTrending(10)
                val videos = feed.data ?: emptyList()
                trendingAdapter.submit(videos)
            } catch (_: Exception) {
                toast("Không tải được danh sách thịnh hành")
            }
            binding.progressTrending.visibility = View.GONE
        }
    }

    // Chon 1 video thinh hanh -> hien thi luon card de tai
    private fun pickTrending(v: com.example.tt.network.TikTokVideo) {
        val noWm = v.hdPlayUrl ?: v.playUrl ?: return
        current = DownloadRecord(
            videoId = v.id ?: System.currentTimeMillis().toString(),
            title = v.title ?: "Video TikTok",
            author = v.author?.nickname ?: "Không rõ",
            coverUrl = v.cover ?: "",
            sourceUrl = v.id ?: "",
            downloadUrl = noWm,
            quality = if (v.hdPlayUrl != null) "HD" else "NO_WATERMARK",
            fileSize = v.hdSize ?: v.size ?: 0
        )
        showVideoCard(current!!)
    }

    // ---------------- HIEN THI KET QUA PHAN TICH ----------------
    private fun showVideoCard(rec: DownloadRecord) {
        binding.cardVideo.visibility = View.VISIBLE
        binding.tvTitle.text = rec.title
        binding.tvAuthor.text = "👤 ${rec.author}"
        binding.tvQuality.text = if (rec.quality == "HD") "HD · Không logo" else "Không logo"
        binding.tvSize.text = "💾 ${rec.fileSize / 1024 / 1024} MB"
        binding.tvDuration.text = "Nhấn tải để lưu vào máy"
        if (rec.coverUrl.isNotEmpty()) {
            binding.imgCover.load(rec.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_foreground)
            }
        }
    }

    // ---------------- TAI VIDEO + LUU SQLITE + FIREBASE ----------------
    private fun downloadCurrent() {
        val rec = current ?: run { toast("Phân tích link trước đã!"); return }
        setLoading(true, "Đang tải video...")
        binding.progressDownload.progress = 0

        lifecycleScope.launch {
            try {
                val path = VideoDownloader.download(
                    context = this@MainActivity,
                    url = rec.downloadUrl,
                    fileName = "tiktok_${rec.videoId}.mp4"
                ) { done, total ->
                    runOnUiThread {
                        if (total > 0) {
                            binding.progressDownload.progress = (done * 100 / total).toInt()
                            binding.tvPercent.text = "${done * 100 / total}%"
                        }
                    }
                }

                // KY THUAT 1: luu lich su vao SQLite
                db.insertDownload(rec.copy(filePath = path))

                // KY THUAT 3: goi backend Firestore tang bo dem
                val uid = UserRepository(db).currentSessionUid()
                if (uid != null && !uid.startsWith("offline-")) {
                    ProfileRepository().incrementDownloadCount(uid)
                }

                toast("✅ Đã lưu vào Download/TikTok")
                binding.tvPercent.text = "Hoàn tất ✓"
            } catch (e: Exception) {
                toast("Lỗi tải: ${e.message}")
            }
            setLoading(false)
        }
    }

    private fun setLoading(on: Boolean, msg: String = "") {
        binding.progress.visibility = if (on) View.VISIBLE else View.GONE
        if (msg.isNotEmpty()) binding.tvStatus.text = msg
        binding.btnAnalyze.isEnabled = !on
        binding.btnDownload.isEnabled = !on
    }

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
