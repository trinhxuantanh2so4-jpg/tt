package com.example.tt

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tt.databinding.ActivityTrendingBinding
import com.example.tt.network.ApiClient
import com.example.tt.network.TikTokVideo
import com.example.tt.ui.TrendingAdapter
import com.example.tt.ui.setupBottomNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tab "Thinh hanh": grid 2 cot, keo de lam moi, bam vao video -> mo tab Tai video
 * voi thong tin da dien san de nguoi dung bam Tai.
 */
class TrendingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrendingBinding
    private lateinit var adapter: TrendingAdapter
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav(binding.bottomNav, R.id.nav_trending)

        adapter = TrendingAdapter { openInMain(it) }
        binding.rvTrending.layoutManager = GridLayoutManager(this, 2)
        binding.rvTrending.adapter = adapter

        binding.swipeRefresh.setColorSchemeColors(
            getColor(R.color.accent),
            getColor(R.color.tt_red)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.surface_variant))
        binding.swipeRefresh.setOnRefreshListener { load(showFullLoading = false) }
        binding.btnRetry.setOnClickListener { load(showFullLoading = true) }

        load(showFullLoading = true)
    }

    private fun load(showFullLoading: Boolean) {
        loadJob?.cancel()
        binding.layoutError.visibility = View.GONE
        if (showFullLoading) {
            binding.layoutEmpty.visibility = View.GONE
            binding.progressTrending.visibility = View.VISIBLE
        }

        loadJob = lifecycleScope.launch {
            try {
                val feed = ApiClient.api.getTrending(12)
                val videos = feed.data.orEmpty()
                adapter.submitList(videos)
                binding.layoutEmpty.visibility =
                    if (videos.isEmpty()) View.VISIBLE else View.GONE
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (adapter.itemCount == 0) {
                    binding.layoutError.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                }
                toast(getString(R.string.toast_trending_error))
            } finally {
                binding.progressTrending.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /** Mo tab Tai video voi video da chon san */
    private fun openInMain(video: TikTokVideo) {
        val playUrl = video.hdPlayUrl ?: video.playUrl ?: run {
            toast(getString(R.string.toast_no_video))
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VIDEO_ID, video.id ?: "")
            putExtra(MainActivity.EXTRA_TITLE, video.title ?: getString(R.string.app_name))
            putExtra(MainActivity.EXTRA_AUTHOR, video.author?.nickname ?: "TikTok")
            putExtra(MainActivity.EXTRA_COVER, video.cover ?: "")
            putExtra(MainActivity.EXTRA_SOURCE_URL, "https://www.tiktok.com/video/${video.id}")
            putExtra(MainActivity.EXTRA_DOWNLOAD_URL, playUrl)
            putExtra(
                MainActivity.EXTRA_QUALITY,
                if (video.hdPlayUrl != null) "HD" else "NO_WATERMARK"
            )
            putExtra(MainActivity.EXTRA_SIZE, video.hdSize ?: video.size ?: 0L)
            putExtra(MainActivity.EXTRA_DURATION, video.duration ?: 0L)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
