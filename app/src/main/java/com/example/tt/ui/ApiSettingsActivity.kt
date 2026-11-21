package com.example.tt.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
import com.example.tt.subtitle.GeminiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Màn cấu hình nguồn tải video: nhập Base URL + API key của DTK lưu ngay trên máy.
 * Cũng tích hợp cấu hình Gemini AI để tối ưu phụ đề.
 */
class ApiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiSettingsBinding
    private var testJob: Job? = null
    private var geminiJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Che API key khi chụp màn hình / ghi màn hình
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityApiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav(binding.bottomNav, R.id.nav_none)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // DTK Config
        val current = DtkConfig.load()
        binding.etBaseUrl.setText(current.baseUrl)
        binding.etApiKey.setText(current.apiKey)
        if (current.enabled) {
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.api_settings_current, current.baseUrl)
        }

        // Gemini Config
        binding.etGeminiKey.setText(GeminiConfig.loadKey())

        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnClear.setOnClickListener { clear() }

        binding.btnTestGemini.setOnClickListener { testGemini() }
    }

    private fun readInput(): DtkConfig.Config? {
        val base = binding.etBaseUrl.text?.toString()?.trim().orEmpty()
        val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
        
        // Neu ca hai deu trong, thi co the la dang muon xoa hoac chi save Gemini
        if (base.isBlank() && key.isBlank()) return null

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
        // Save DTK
        val dtkConfig = readInput()
        if (dtkConfig != null) {
            DtkConfig.save(dtkConfig.baseUrl, dtkConfig.apiKey)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.api_settings_current, dtkConfig.baseUrl)
        }

        // Save Gemini
        val geminiKey = binding.etGeminiKey.text?.toString()?.trim().orEmpty()
        GeminiConfig.saveKey(geminiKey)

        Toast.makeText(this, R.string.api_settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun clear() {
        DtkConfig.clear()
        GeminiConfig.clear()
        
        binding.etBaseUrl.setText("")
        binding.etApiKey.setText("")
        binding.etGeminiKey.setText("")
        
        binding.tilBaseUrl.error = null
        binding.tilApiKey.error = null
        binding.tvStatus.visibility = View.GONE
        binding.tvGeminiStatus.visibility = View.GONE
        
        Toast.makeText(this, R.string.api_settings_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun testGemini() {
        val key = binding.etGeminiKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) {
            binding.tilGeminiKey.error = getString(R.string.api_settings_missing)
            return
        }
        binding.tilGeminiKey.error = null
        
        geminiJob?.cancel()
        binding.btnTestGemini.isEnabled = false
        binding.tvGeminiStatus.visibility = View.VISIBLE
        binding.tvGeminiStatus.text = getString(R.string.gemini_status_testing)
        
        geminiJob = lifecycleScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    validateGeminiKey(key)
                }
                if (ok) {
                    binding.tvGeminiStatus.text = getString(R.string.gemini_status_ok)
                } else {
                    binding.tvGeminiStatus.text = getString(R.string.gemini_status_fail, "Key không hợp lệ hoặc hết hạn")
                }
            } catch (e: Exception) {
                binding.tvGeminiStatus.text = getString(R.string.gemini_status_fail, e.message ?: "Lỗi không xác định")
            } finally {
                binding.btnTestGemini.isEnabled = true
            }
        }
    }

    private suspend fun validateGeminiKey(key: String): Boolean {
        val client = OkHttpClient()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        
        val json = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", "Hi") })
                    })
                })
            })
        }
        
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        
        return client.newCall(request).execute().use { it.isSuccessful }
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
