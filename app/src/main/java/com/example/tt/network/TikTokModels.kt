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
    // endpoint /api/ tra "id", endpoint /api/feed/list tra "video_id" -> dung alternate
    @SerializedName(value = "id", alternate = ["video_id"]) val id: String?,
    val title: String?,
    val cover: String?,          // anh bia
    @SerializedName("play") val playUrl: String?,      // KHONG LOGO (SD)
    @SerializedName("hdplay") val hdPlayUrl: String?,   // KHONG LOGO (HD)
    @SerializedName("wmplay") val wmPlayUrl: String?,  // CO LOGO
    val size: Long?,             // dung luong (bytes)
    @SerializedName("hd_size") val hdSize: Long?,
    @SerializedName("wm_size") val wmSize: Long?,
    val duration: Long?,
    val music: String?,
    val author: TikTokAuthor?
)

data class TikTokAuthor(
    val id: String?,
    @SerializedName("unique_id") val uniqueId: String?,
    val nickname: String?,
    val avatar: String?
)

/**
 * Trending feed: https://tikwm.com/api/feed/list?count=10&region=VN
 * Luu y: "data" la MANG video truc tiep (khong boc trong object),
 * va bat buoc phai truyen tham so region (vd: VN).
 */
data class FeedResponse(
    val code: Int,
    val msg: String?,
    val data: List<TikTokVideo>?
)
