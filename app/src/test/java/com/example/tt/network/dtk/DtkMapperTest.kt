package com.example.tt.network.dtk

import com.example.tt.network.VideoPlatform
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Pure JVM: không import Android.Fixture bám schema Content v5 của DTK. */
class DtkMapperTest {

    private val gson = Gson()

    @Before
    fun setUp() = DtkStreamFallbacks.clear()

    @Test
    fun `map video Douyin co watermark thi chon stream sach`() {
        val content = parse(DOUYIN_VIDEO)
        val info = DtkMapper.toVideoInfo(content, SOURCE, VideoPlatform.DOUYIN)

        assertEquals("7345678901234567890", info.record.videoId)
        assertEquals("Một ngày ở Thượng Hải", info.record.title)
        assertEquals("Lien Hoa", info.record.author)
        assertEquals("https://cdn/cover.jpg", info.record.coverUrl)
        assertEquals(SOURCE, info.record.sourceUrl)
        assertEquals("https://cdn/video-clean.mp4", info.record.downloadUrl)
        assertEquals("HD", info.record.quality)
        assertEquals(2_411_724L, info.record.fileSize)
        assertEquals(31L, info.duration)
        assertEquals(VideoPlatform.DOUYIN, info.record.platform)

        assertTrue(
            DtkStreamFallbacks.alternatesFor(info.record.downloadUrl)
                .contains("https://mirror/video-clean.mp4")
        )
    }

    @Test
    fun `map video TikTok khong co title thi lay dong dau description`() {
        val info = DtkMapper.toVideoInfo(parse(TIKTOK_VIDEO), SOURCE, VideoPlatform.TIKTOK)

        assertEquals("7312345678901234567", info.record.videoId)
        assertEquals("Dòng đầu mô tả", info.record.title)
        assertEquals("user123", info.record.author)
        assertEquals("https://cdn/tiktok-clean.mp4", info.record.downloadUrl)
        assertEquals("NO_WATERMARK", info.record.quality)
        assertEquals(0L, info.record.fileSize)
        assertEquals(VideoPlatform.TIKTOK, info.record.platform)
    }

    @Test
    fun `album anh bi tu choi`() {
        assertDtkError("UNSUPPORTED_CONTENT") {
            DtkMapper.toVideoInfo(parse(ALBUM), SOURCE, null)
        }
    }

    @Test
    fun `khong co stream sach logo thi bao rieng tu`() {
        assertDtkError("MEDIA_WITHHELD") {
            DtkMapper.toVideoInfo(parse(WATERMARK_ONLY), SOURCE, null)
        }
    }

    @Test
    fun `platform khong khop tab dang chon`() {
        assertDtkError("PLATFORM_MISMATCH") {
            DtkMapper.toVideoInfo(parse(TIKTOK_VIDEO), SOURCE, VideoPlatform.DOUYIN)
        }
    }

    @Test
    fun `khong ro nen tangent van chap nhan`() {
        val info = DtkMapper.toVideoInfo(parse(TIKTOK_VIDEO), SOURCE, null)
        assertEquals(VideoPlatform.TIKTOK, info.record.platform)
    }

    private fun parse(json: String): DtkContent = gson.fromJson(json, DtkContent::class.java)

    private fun assertDtkError(code: String, block: () -> Unit) {
        try {
            block()
            fail("Expected DtkException with code $code")
        } catch (e: DtkException) {
            assertEquals(code, e.code)
            assertEquals(false, e.retryable)
            assertTrue(e.messageVi.isNotBlank())
        }
    }

    companion object {
        private const val SOURCE = "7.95 复制打开抖音，看看 https://v.douyin.com/abc/ 的作品"

        private val DOUYIN_VIDEO = """
            {
              "platform": "douyin",
              "content_id": "7345678901234567890",
              "kind": "video",
              "web_url": "https://www.douyin.com/video/7345678901234567890",
              "title": "Một ngày ở Thượng Hải",
              "description": "Một ngày ở Thượng Hải\nCảnh đẹp quá",
              "duration_ms": 31200,
              "author": {"nickname": "Lien Hoa", "unique_id": "lienhoa"},
              "media": {
                "covers": [{"url": "https://cdn/cover.jpg", "urls": ["https://cdn/cover2.jpg"]}],
                "video": {
                  "url": "https://cdn/video-wm.mp4", "width": 1080, "height": 1920,
                  "size_bytes": 2411724, "watermark": true
                },
                "streams": [
                  {
                    "url": "https://cdn/video-clean.mp4",
                    "urls": ["https://mirror/video-clean.mp4"],
                    "width": 1080, "height": 1920, "size_bytes": 2411724, "watermark": false
                  }
                ]
              }
            }
        """.trimIndent()

        private val TIKTOK_VIDEO = """
            {
              "platform": "tiktok",
              "content_id": "7312345678901234567",
              "kind": "video",
              "description": "Dòng đầu mô tả\nDòng hai",
              "duration_ms": 15000,
              "author": {"nickname": "", "unique_id": "user123"},
              "media": {
                "covers": [],
                "video": {
                  "url": "https://cdn/tiktok-clean.mp4", "height": 720, "watermark": false
                },
                "streams": []
              }
            }
        """.trimIndent()

        private val ALBUM = """
            {"platform":"douyin","kind":"image_album","content_id":"1","media":{"images":[]}}
        """.trimIndent()

        private val WATERMARK_ONLY = """
            {
              "platform":"douyin","kind":"video","content_id":"2",
              "media":{"video":{"url":"https://cdn/wm.mp4","watermark":true},"streams":[]}
            }
        """.trimIndent()
    }
}
