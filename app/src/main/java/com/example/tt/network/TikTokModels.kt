package com.example.tt.network

import com.google.gson.annotations.SerializedName

/**
 * Response cua API https://tikwm.com/api/ (FREE, khong can key)
 * ?url=<link tiktok>&hd=1  -> thong tin video khong logo
 */

data class TikTokApiResponse(
    val code: Int,
    @SerializedName("msg") val msg: String?,
    val data: TikTokVideo?
)

data class TikTokVideo(
    val id: String?,
    val title: String?,
    val cover: String?,          // anh bia
    @SerializedName("play") val playUrl: String?,      // KHONG LOGO (SD)
    @SerializedName("hdplay") val hdPlayUrl: String?,   // KHONG LOGO (HD)
    @SerializedName("wmplay") val wmPlayUrl: String?,  // CO LOGO
    val size: Long?,             // dung luong (bytes)
    @SerializedName("hd_size") val hdSize: Long?,
    val duration: Long?,
    val music: String?,
    val author: TikTokAuthor?
)

data class TikTokAuthor(
    val id: String?,
    @SerializedName("unique_id") val uniqueId: String?,
    val nickname: String?
)

/** Trending feed: https://tikwm.com/api/feed/list?count=10 */
data class FeedResponse(
    val code: Int,
    val data: FeedData?
)

data class FeedData(
    val videos: List<TikTokVideo>?
)
