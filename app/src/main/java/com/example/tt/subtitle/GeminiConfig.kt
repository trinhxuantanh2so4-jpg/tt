package com.example.tt.subtitle

import android.content.Context

/**
 * Cấu hình Gemini AI lưu trên máy.
 * Key lấy từ Google AI Studio (miễn phí).
 */
object GeminiConfig {

    private const val PREFS = "gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun loadKey(): String {
        return appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_API_KEY, "")?.trim().orEmpty()
    }

    fun isEnabled(): Boolean = loadKey().isNotBlank()

    fun saveKey(apiKey: String) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_API_KEY, apiKey.trim())
            ?.apply()
    }

    fun clear() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.apply()
    }
}
