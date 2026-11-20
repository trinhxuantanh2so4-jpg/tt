package com.example.tt.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSourceCodecTest {

    @Test
    fun roundTripKeepsUnicodeNewlinesAndEmptyLines() {
        val sources = listOf(
            "你好，世界",
            "第一行\n第二行",
            "",
            "Has dấu tiếng Việt + emoji 🎬"
        )

        val decoded = SubtitleSourceCodec.decode(SubtitleSourceCodec.encode(sources))

        assertEquals(sources, decoded)
    }

    @Test
    fun decodeIgnoresBrokenLines() {
        val decoded = SubtitleSourceCodec.decode("!!!khong-phai-base64\n${SubtitleSourceCodec.encode(listOf("ok"))}")

        assertEquals(listOf("ok"), decoded)
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        assertEquals(emptyList<String>(), SubtitleSourceCodec.decode(""))
    }
}
