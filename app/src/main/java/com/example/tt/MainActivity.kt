package com.example.tt

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.tt.databinding.ActivityMainBinding
import com.example.tt.network.VideoPlatform

/**
 * Tab **TikTok**: chi tai video TikTok bang API (tikwm.com), khong co tuy chon
 * phu de / thuyet minh. Link Douyin dan vao day se duoc chuyen sang tab Douyin.
 */
class MainActivity : BaseDownloadActivity() {

    private lateinit var binding: ActivityMainBinding

    override val platformFilter: VideoPlatform = VideoPlatform.TIKTOK
    override val navItemId: Int = R.id.nav_download
    override val hintRes: Int = R.string.hint_url_tiktok
    override val emptyDescRes: Int = R.string.empty_desc_tiktok

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!setupDownloadForm(binding.form, binding.bottomNav)) return

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_trending) {
                startActivity(Intent(this, TrendingActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    override fun onWrongPlatform(platform: VideoPlatform, url: String): Boolean {
        if (platform == VideoPlatform.DOUYIN) {
            toast(getString(R.string.toast_switch_douyin))
            startActivity(
                Intent(this, DouyinActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(BaseDownloadActivity.EXTRA_SHARED_URL, url)
            )
            return true
        }
        return false
    }
}
