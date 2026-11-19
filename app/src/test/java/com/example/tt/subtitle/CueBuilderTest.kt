package com.example.tt.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class CueBuilderTest {

    @Test
    fun `chuyen giay sang mili giay va dan so thu tu`() {
        val lines = listOf(
            CueBuilder.RawLine(0f, 1.2f, "  Xin chào  "),
            CueBuilder.RawLine(1.5f, 2f, "Thế giới")
        )
        val cues = CueBuilder.build(lines)
        assertEquals(2, cues.size)
        assertEquals(1, cues[0].index)
        assertEquals(0L, cues[0].startMs)
        assertEquals(1200L, cues[0].endMs)
        assertEquals("Xin chào", cues[0].text)
        assertEquals(2, cues[1].index)
        assertEquals(1500L, cues[1].startMs)
    }

    @Test
    fun `bo qua dong rong va ap dung ban dich`() {
        val lines = listOf(
            CueBuilder.RawLine(0f, 1f, "   "),
            CueBuilder.RawLine(1f, 3f, "你好")
        )
        val cues = CueBuilder.build(lines, listOf(null, "Xin chào bạn"))
        assertEquals(1, cues.size)
        assertEquals("Xin chào bạn", cues[0].text)
        assertEquals("你好", cues[0].source)
    }

    @Test
    fun `dam bao do dai toi thieu va khong chen nhau`() {
        val lines = listOf(
            CueBuilder.RawLine(0f, 0.1f, "A"),
            CueBuilder.RawLine(0.2f, 0.1f, "B"),
            CueBuilder.RawLine(0.25f, 1f, "C")
        )
        val cues = CueBuilder.build(lines)
        assertEquals(3, cues.size)
        assertEquals(600L, cues[0].endMs - cues[0].startMs)
        assertEquals(cues[0].endMs, cues[1].startMs)
        assertEquals(cues[1].endMs, cues[2].startMs)
    }

    @Test
    fun `khong co dong nao thi tra danh sach rong`() {
        assertTrue(CueBuilder.build(emptyList()).isEmpty())
    }
}
