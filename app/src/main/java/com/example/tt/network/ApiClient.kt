package com.example.tt.network

import com.example.tt.data.DownloadRecord
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client.
 * Base URL: https://tikwm.com  (API mien phi, khong can key)
 */
object ApiClient {

    private const val BASE_URL = "https://tikwm.com/"

    val api: TikTokApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TikTokApi::class.java)
    }

    /**
     * Chuan hoa ket qua API -> DownloadRecord (chon link KHONG LOGO tot nhat).
     * @throws IllegalStateException neu link khong phai TikTok hoac API loi.
     */
    suspend fun fetchVideoInfo(url: String): DownloadRecord {
        if (!url.contains("tiktok.com", ignoreCase = true)) {
            throw IllegalStateException("Vui long dan link tu tiktok.com")
        }
        val res = api.getVideo(url, 1)
        if (res.code != 0 || res.data == null) {
            throw IllegalStateException(res.msg ?: "API loi: ${'$'}{res.code}")
        }
        val v = res.data
        val noWatermark = v.hdPlayUrl ?: v.playUrl
            ?: throw IllegalStateException("Khong tim thay video (link rieng tu?)")
        return DownloadRecord(
            videoId = v.id ?: url,
            title = v.title ?: "Video TikTok",
            author = v.author?.nickname ?: v.author?.uniqueId ?: "Khong ro",
            coverUrl = v.cover ?: "",
            sourceUrl = url,
            downloadUrl = noWatermark,
            quality = if (v.hdPlayUrl != null) "HD" else "NO_WATERMARK",
            fileSize = v.hdSize ?: v.size ?: 0
        )
    }
}
