package com.example.tt.network

import com.example.tt.douyin.DouyinResolver
import com.example.tt.network.dtk.DtkConfig
import com.example.tt.network.dtk.DtkException
import com.example.tt.network.dtk.DtkResolver
import kotlinx.coroutines.CancellationException

/**
 * Cửa vào duy nhất để phân tích link: tự động chọn provider theo nền tảng.
 *
 * Nếu người dùng đã cấu hình DTK (màn Nguồn tải video) thì DTK là provider ưu tiên
 * cho cả 2 nền tảng. Chỉ fallback về nguồn cũ khi lỗi mang tính hạ tầng;
 * lỗi về nội dung (private/xoá/sai key) được giữ nguyên để báo đúng cho người dùng.
 */
object VideoResolver {

    /**
     * @param source link hoặc text chia sẻ (tiếng Trung cũng được)
     * @throws IllegalArgumentException nếu không phải TikTok/Douyin
     */
    suspend fun resolve(source: String): VideoInfo {
        val url = UrlParser.extractUrl(source) ?: UrlParser.normalize(source)
        val platform = UrlParser.platformOf(url)
            ?: throw IllegalArgumentException("Chỉ hỗ trợ link TikTok hoặc Douyin")

        if (DtkConfig.isEnabled()) {
            try {
                // Gửi nguyên text cho /parse: DTK hiểu cả caption chia sẻ + link rút gọn.
                return DtkResolver.resolve(source, platform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DtkException) {
                if (!e.retryable) throw e
            } catch (e: Exception) {
                // Lỗi không dự kiến ở provider mới → vẫn để nguồn cũ xử lý.
            }
        }

        return when (platform) {
            VideoPlatform.DOUYIN -> DouyinResolver.resolve(url)
            VideoPlatform.TIKTOK -> ApiClient.fetchVideoInfo(url)
        }
    }
}
