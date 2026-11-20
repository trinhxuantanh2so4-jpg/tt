package com.example.tt.network.dtk

import android.content.Context
import java.net.URL

/**
 * Cấu hình DTK lưu ngay trên máy (SharedPreferences riêng).
 * Không nhét key vào APK/release: release v1.0.0 là APK công khai.
 * Mặc định rỗng → DTK tắt, app dùng nguồn cũ (tikwm / iesdouyin).
 */
object DtkConfig {

    private const val PREFS = "dtk_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"

    @Volatile
    private var appContext: Context? = null

    data class Config(val baseUrl: String, val apiKey: String) {
        val enabled: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
        val signature: String get() = "$baseUrl\n$apiKey"
    }

    /** Gọi một lần từ TTApp.onCreate(). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun load(): Config {
        val p = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?: return Config("", "")
        return Config(
            baseUrl = p.getString(KEY_BASE_URL, "")?.trim().orEmpty(),
            apiKey = p.getString(KEY_API_KEY, "")?.trim().orEmpty()
        )
    }

    fun isEnabled(): Boolean = load().enabled

    fun save(baseUrl: String, apiKey: String) {
        val p = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        p.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
        DtkApiFactory.reset()
    }

    fun clear() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.apply()
        DtkApiFactory.reset()
    }

    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/') + "/"

    /** Chỉ nhận HTTPS (self-host cần reverse proxy TLS). Message lỗi nằm ở strings.xml. */
    fun isHttpsUrl(raw: String): Boolean {
        val value = raw.trim()
        if (value.isBlank() || !value.startsWith("https://", ignoreCase = true)) return false
        return !runCatching { URL(value).host }.getOrNull().isNullOrBlank()
    }
}
