package com.example.tt.douyin

import com.example.tt.data.DownloadRecord
import com.example.tt.network.UrlParser
import com.example.tt.network.VideoInfo
import com.example.tt.network.VideoPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tai metadata video Douyin KHONG logo.
 *
 * Cach lam (khong can cookie / chu ky):
 *  1. GET link nguoi dung (ke ca `v.douyin.com/...`) voi UA mobile -> theo redirect lay aweme_id
 *  2. GET `https://www.iesdouyin.com/share/video/{id}` -> parse `window._ROUTER_DATA`
 *  3. `video.play_addr.url_list[0]`, doi `playwm` -> `play` => MP4 sach logo
 *  4. Du phong: API iteminfo cu -> API web aweme/detail
 *
 * Module tach rieng de de cap nhat khi Douyin doi HTML.
 */
object DouyinResolver {

    private const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Phan tich link Douyin -> [VideoInfo] san sang tai.
     *
     * @throws IllegalStateException voi thong bao tieng Viet hien thang cho nguoi dung
     */
    suspend fun resolve(source: String): VideoInfo = withContext(Dispatchers.IO) {
        val input = UrlParser.extractUrl(source) ?: UrlParser.normalize(source)
        if (UrlParser.platformOf(input) != VideoPlatform.DOUYIN) {
            throw IllegalStateException("Chỉ hỗ trợ link Douyin")
        }

        val first = try {
            fetch(input)
        } catch (e: IOException) {
            throw IllegalStateException("Không kết nối được Douyin: ${e.message ?: "lỗi mạng"}")
        }

        val awemeId = DouyinParser.extractAwemeId(first.finalUrl)
            ?: DouyinParser.extractAwemeId(input)

        // Link rut gon co the redirect thang toi trang share -> doc luon HTML vua tai
        val fromPage = first.body?.let { DouyinParser.parseShareHtml(it, awemeId ?: "") }

        val media = when {
            fromPage?.isUsable == true -> fromPage
            awemeId != null -> resolveById(awemeId) ?: fromPage
            else -> fromPage
        } ?: throw IllegalStateException("Không tìm thấy video trong link Douyin")

        if (media.imageCount > 0) {
            throw IllegalStateException("Link này là album ảnh Douyin, chưa hỗ trợ tải")
        }
        val playUrl = media.playUrl
            ?: throw IllegalStateException("Video Douyin riêng tư hoặc đã bị xoá")

        val record = DownloadRecord(
            videoId = media.awemeId.ifBlank { awemeId ?: input },
            title = media.title.ifBlank { "Video Douyin" },
            author = media.author.ifBlank { "Không rõ" },
            coverUrl = media.coverUrl,
            sourceUrl = input,
            downloadUrl = playUrl,
            quality = "NO_WATERMARK",
            fileSize = media.fileSize,
            platform = VideoPlatform.DOUYIN
        )
        VideoInfo(
            record = record,
            duration = media.durationMs.takeIf { it > 0 }?.div(1000L)
        )
    }

    /** Thu lan luot cac nguon du phong cho aweme_id da biet. */
    private fun resolveById(id: String, fallback: DouyinMedia? = null): DouyinMedia? {
        fetchOrNull("https://www.iesdouyin.com/share/video/$id/")
            ?.body?.let { DouyinParser.parseShareHtml(it, id) }
            ?.takeIf { it.isUsable }
            ?.let { return it }

        fetchOrNull("https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$id")
            ?.body?.let { DouyinParser.parseItemListJson(it, id) }
            ?.takeIf { it.isUsable }
            ?.let { return it }

        fetchOrNull(
            "https://www.douyin.com/aweme/v1/web/aweme/detail/" +
                "?aweme_id=$id&aid=1128&device_platform=webapp"
        )
            ?.body?.let { DouyinParser.parseAwemeDetailJson(it, id) }
            ?.takeIf { it.isUsable }
            ?.let { return it }

        return fallback
    }

    private class Page(val finalUrl: String, val body: String?)

    private fun fetch(url: String): Page {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .header("Referer", "https://www.douyin.com/")
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,vi;q=0.8,en;q=0.7")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Douyin trả về HTTP ${response.code}")
            }
            return Page(
                finalUrl = response.request.url.toString(),
                body = response.body?.string()
            )
        }
    }

    private fun fetchOrNull(url: String): Page? =
        try {
            fetch(url)
        } catch (e: Exception) {
            null
        }
}
