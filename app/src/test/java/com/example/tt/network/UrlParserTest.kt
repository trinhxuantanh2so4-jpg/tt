package com.example.tt.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlParserTest {

    @Test
    fun `nhan dien nen tang`() {
        assertEquals(
            VideoPlatform.TIKTOK,
            UrlParser.platformOf("https://www.tiktok.com/@user/video/7312345678901234567")
        )
        assertEquals(
            VideoPlatform.TIKTOK,
            UrlParser.platformOf("https://vm.tiktok.com/ZMabc123/")
        )
        assertEquals(
            VideoPlatform.DOUYIN,
            UrlParser.platformOf("https://v.douyin.com/iRNBho6u/")
        )
        assertEquals(
            VideoPlatform.DOUYIN,
            UrlParser.platformOf("https://www.iesdouyin.com/share/video/7345678901234567890/")
        )
        assertNull(UrlParser.platformOf("https://www.youtube.com/watch?v=123"))
    }

    @Test
    fun `tach link tu text chia se tieng Trung`() {
        val shared = "7.95 复制打开抖音，看看【小明】的作品 https://v.douyin.com/iRNBho6u/ 复制此链接"
        assertEquals("https://v.douyin.com/iRNBho6u/", UrlParser.extractUrl(shared))
    }

    @Test
    fun `tach link tiktok co query`() {
        val shared = "Xem video này https://www.tiktok.com/@user/video/7312345678901234567?is_from_webapp=1"
        assertEquals(
            "https://www.tiktok.com/@user/video/7312345678901234567?is_from_webapp=1",
            UrlParser.extractUrl(shared)
        )
    }

    @Test
    fun `bo dau cau dinh sau link`() {
        assertEquals(
            "https://v.douyin.com/iRNBho6u/",
            UrlParser.extractUrl("Link đây: https://v.douyin.com/iRNBho6u/。")
        )
    }

    @Test
    fun `khong ho tro link khac`() {
        assertNull(UrlParser.extractUrl("https://www.youtube.com/watch?v=123"))
        assertNull(UrlParser.extractUrl("không có link nào"))
    }

    @Test
    fun `doc lai platform tu chuoi id`() {
        assertEquals(VideoPlatform.DOUYIN, VideoPlatform.fromId("douyin"))
        assertEquals(VideoPlatform.TIKTOK, VideoPlatform.fromId("TIKTOK"))
        assertEquals(VideoPlatform.TIKTOK, VideoPlatform.fromId(null))
        assertEquals(VideoPlatform.TIKTOK, VideoPlatform.fromId("abc"))
    }
}
