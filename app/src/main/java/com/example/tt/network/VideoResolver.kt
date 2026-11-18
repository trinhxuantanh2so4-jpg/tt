package com.example.tt.network

import com.example.tt.douyin.DouyinResolver

/**
 * Cua vao duy nhat de phan tich link: tu dong chon provider theo nen tang.
 */
object VideoResolver {

    /**
     * @param source link hoac text chia se (tieng Trung cung duoc)
     * @throws IllegalArgumentException neu khong phai TikTok/Douyin
     */
    suspend fun resolve(source: String): VideoInfo {
        val url = UrlParser.extractUrl(source) ?: UrlParser.normalize(source)
        return when (UrlParser.platformOf(url)) {
            VideoPlatform.DOUYIN -> DouyinResolver.resolve(url)
            VideoPlatform.TIKTOK -> ApiClient.fetchVideoInfo(url)
            null -> throw IllegalArgumentException("Chỉ hỗ trợ link TikTok hoặc Douyin")
        }
    }
}
