package com.example.tt.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM cho parser Douyin (khong can Android / mang).
 * Chay trong CI: `gradle testDebugUnitTest`.
 */
class DouyinParserTest {

    /** HTML mau: JSON long nhieu cap, co ngoac nhon + dau nhay trong chuoi. */
    private val shareHtml = """
        <!DOCTYPE html><html><head><script>
        window._ROUTER_DATA = {"loaderData":{"video_(id)/page":{"videoInfoRes":{"item_list":[{
          "aweme_id":"7345678901234567890",
          "desc":"测试 \"引号\" 和 {括号}",
          "duration":15300,
          "author":{"nickname":"小明"},
          "video":{
            "play_addr":{"url_list":["http://v3-web.douyinvod.com/playwm/?video_id=v0300&ratio=720p"],"data_size":2048000},
            "cover":{"url_list":["https://p3.douyinpic.com/cover.jpg"]}
          }
        }]}}},"errors":null};</script></head></html>
    """.trimIndent()

    @Test
    fun `parse share html - lay duoc video khong logo`() {
        val media = DouyinParser.parseShareHtml(shareHtml)

        assertNotNull(media)
        val m = media!!
        assertEquals("7345678901234567890", m.awemeId)
        assertEquals("小明", m.author)
        assertEquals("https://p3.douyinpic.com/cover.jpg", m.coverUrl)
        // playwm -> play, http -> https
        assertEquals(
            "https://v3-web.douyinvod.com/play/?video_id=v0300&ratio=720p",
            m.playUrl
        )
        assertEquals(15300L, m.durationMs)
        assertEquals(2048000L, m.fileSize)
        assertEquals(0, m.imageCount)
        assertTrue(m.isUsable)
    }

    @Test
    fun `can bang ngoac - khong cat nham ngoac trong chuoi`() {
        val media = DouyinParser.parseShareHtml(shareHtml)
        assertEquals("测试 \"引号\" 和 {括号}", media?.title)
    }

    @Test
    fun `extract router data - tra null khi khong co marker`() {
        assertNull(DouyinParser.extractRouterData("<html><body>blocked</body></html>"))
    }

    @Test
    fun `album anh - khong co play url nhung nhan dien duoc`() {
        val html = """
            <script>
            window._ROUTER_DATA = {"loaderData":{"video_(id)/page":{"videoInfoRes":{"item_list":[{
              "aweme_id":"7111111111111111111",
              "desc":"album",
              "images":[{"url_list":["https://a"]},{"url_list":["https://b"]},{"url_list":["https://c"]}]
            }]}}}};
            </script>
        """.trimIndent()

        val media = DouyinParser.parseShareHtml(html)

        assertNotNull(media)
        val album = media!!
        assertEquals(3, album.imageCount)
        assertNull(album.playUrl)
        assertTrue("phai biet day la album de bao loi", album.isUsable)
    }

    @Test
    fun `iteminfo json - doc item_list`() {
        val json = """
            {"status_code":0,"item_list":[{
              "aweme_id":"7200000000000000000",
              "desc":"fallback api",
              "duration":8000,
              "author":{"nickname":"作者"},
              "video":{
                "play_addr":{"url_list":["https://v3.douyinvod.com/play/?video_id=x"]},
                "cover":{"url_list":["https://p3.douyinpic.com/c.jpg"]}
              }
            }]}
        """.trimIndent()

        val media = DouyinParser.parseItemListJson(json, fallbackId = "7200000000000000000")

        assertNotNull(media)
        val fromApi = media!!
        assertEquals("fallback api", fromApi.title)
        assertEquals("作者", fromApi.author)
        assertEquals(8000L, fromApi.durationMs)
        assertNotNull(fromApi.playUrl)
    }

    @Test
    fun `iteminfo json rong - tra null`() {
        assertNull(
            DouyinParser.parseItemListJson(
                """{"status_code":0,"item_list":[]}""",
                fallbackId = "7200000000000000000"
            )
        )
    }

    @Test
    fun `aweme detail json - doc aweme_detail`() {
        val json = """
            {"status_code":0,"aweme_detail":{
              "aweme_id":"7300000000000000000",
              "desc":"detail api",
              "video":{
                "play_addr":{"url_list":["https://v3.douyinvod.com/playwm/?video_id=d"]},
                "cover":{"url_list":["https://p3.douyinpic.com/d.jpg"]}
              }
            }}
        """.trimIndent()

        val media = DouyinParser.parseAwemeDetailJson(json, "7300000000000000000")

        assertNotNull(media)
        val detail = media!!
        assertEquals("detail api", detail.title)
        assertEquals(
            "https://v3.douyinvod.com/play/?video_id=d",
            detail.playUrl
        )
    }

    @Test
    fun `extract aweme id - moi dang link`() {
        assertEquals(
            "7345678901234567890",
            DouyinParser.extractAwemeId("https://www.douyin.com/video/7345678901234567890")
        )
        assertEquals(
            "7345678901234567890",
            DouyinParser.extractAwemeId(
                "https://www.iesdouyin.com/share/video/7345678901234567890/?region=CN"
            )
        )
        assertEquals(
            "7345678901234567890",
            DouyinParser.extractAwemeId(
                "https://www.douyin.com/discover?modal_id=7345678901234567890"
            )
        )
        assertNull(DouyinParser.extractAwemeId("https://v.douyin.com/iRNBho6u/"))
    }

    @Test
    fun `strip watermark - http sang https va playwm sang play`() {
        assertEquals(
            "https://v3.douyinvod.com/play/?video_id=1",
            DouyinParser.stripWatermark("http://v3.douyinvod.com/playwm/?video_id=1")
        )
        assertEquals(
            "https://v3.douyinvod.com/play/?video_id=2",
            DouyinParser.stripWatermark("https://v3.douyinvod.com/playwm/?video_id=2")
        )
    }
}
