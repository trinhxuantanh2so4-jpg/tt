package com.example.tt.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * KY THUAT 2 - GOI API TREN MANG (Retrofit interface)
 * Dung API cong khai mien phi: https://tikwm.com
 */
interface TikTokApi {

    /** Phan tich link TikTok -> tra ve link video KHONG LOGO (hd neu co) */
    @GET("api/")
    suspend fun getVideo(
        @Query("url") url: String,
        @Query("hd") hd: Int = 1
    ): TikTokApiResponse

    /** Danh sach video trending TikTok */
    @GET("api/feed/list")
    suspend fun getTrending(
        @Query("count") count: Int = 10
    ): FeedResponse
}
