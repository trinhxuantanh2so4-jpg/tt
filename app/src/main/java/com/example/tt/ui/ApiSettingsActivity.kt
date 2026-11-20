package com.example.tt.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tt.R
import com.example.tt.databinding.ActivityApiSettingsBinding
import com.example.tt.network.dtk.DtkApiFactory
import com.example.tt.network.dtk.DtkAuthInfo
import com.example.tt.network.dtk.DtkConfig
import com.example.tt.network.dtk.DtkErrors
import com.example.tt.network.dtk.DtkException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn cấu hình nguồn tải video: nhập Base URL + API key của DTK lưu ngay trên máy.
 * Không đưa key vào APK/CI. Để trống → ứng dụng dùng nguồn cũ (tikwm / iesdouyin).
 */
class ApiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiSettingsBinding
    private var testJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav(binding.bottomNav, R.id.nav_none)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val current = DtkConfig.load()
        binding.etBaseUrl.setText(current.baseUrl)
        binding.etApiKey.setText(current.apiKey)
        if (current.enabled) {
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.api_settings_current, current.baseUrl)
        }

        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnClear.setOnClickListener { clear() }
    }

    private fun readInput(): DtkConfig.Config? {
        val base = binding.etBaseUrl.text?.toString()?.trim().orEmpty()
        val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
        when {
            base.isBlank() || key.isBlank() -> {
                binding.tilBaseUrl.error = getString(R.string.api_settings_missing)
                binding.tilApiKey.error = getString(R.string.api_settings_missing)
                return null
            }
            !DtkConfig.isHttpsUrl(base) -> {
                binding.tilBaseUrl.error = getString(R.string.api_settings_https_only)
                return null
            }
        }
        binding.tilBaseUrl.error = null
        binding.tilApiKey.error = null
        return DtkConfig.Config(DtkConfig.normalizeBaseUrl(base), key)
    }

    private fun save() {
        val config = readInput() ?: return
        DtkConfig.save(config.baseUrl, config.apiKey)
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.api_settings_current, config.baseUrl)
        Toast.makeText(this, R.string.api_settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun clear() {
        DtkConfig.clear()
        binding.etBaseUrl.setText("")
        binding.etApiKey.setText("")
        binding.tilBaseUrl.error = null
        binding.tilApiKey.error = null
        binding.tvStatus.visibility = View.GONE
        Toast.makeText(this, R.string.api_settings_cleared, Toast.LENGTH_SHORT).show()
    }

    /** Gọi GET /api/v1/auth/me bằng key đang nhập (không cần lưu trước). */
    private fun testConnection() {
        val config = readInput() ?: return
        testJob?.cancel()
        binding.btnTest.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.api_settings_testing)

        testJob = lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    DtkApiFactory.get(config).authMe()
                }
                if (!response.success) throw DtkErrors.from(response.error)

                val info = Gson().fromJson(response.data, DtkAuthInfo::class.java)
                val scopes = info?.user?.scopes.orEmpty()
                binding.tvStatus.text = getString(
                    R.string.api_settings_ok,
                    info?.user?.username ?: "—",
                    scopes.joinToString(", ").ifBlank { "—" },
                    info?.rate_limit_per_min?.toString() ?: "—"
                )
                binding.tilApiKey.error = if (
                    !scopes.contains("douyin:read") || !scopes.contains("tiktok:read")
                ) {
                    getString(R.string.api_settings_scope_warning)
                } else {
                    null
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: DtkException) {
                binding.tvStatus.text = getString(R.string.api_settings_fail, e.messageVi)
            } catch (e: Exception) {
                binding.tvStatus.text = getString(
                    R.string.api_settings_fail,
                    e.message?.takeIf { it.isNotBlank() } ?: "—"
                )
            } finally {
                binding.btnTest.isEnabled = true
            }
        }
    }
}
