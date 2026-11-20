package com.example.tt.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtWriterTest {

    @Test
    fun `dinh dang timestamp SRT`() {
        assertEquals("00:00:00,000", SubtitleCue.formatTimestamp(0))
        assertEquals("00:00:01,250", SubtitleCue.formatTimestamp(1250))
        assertEquals("00:01:05,007", SubtitleCue.formatTimestamp(65_007))
        assertEquals("01:02:03,004", SubtitleCue.formatTimestamp(3_723_004))
    }

    @Test
    fun `ghi va doc lai file srt`() {
        val cues = listOf(
            SubtitleCue(1, 0, 1500, "你好", "Xin chào"),
            SubtitleCue(2, 2000, 3500, "世界", "Thế giới")
        )
        val text = SrtWriter.write(cues)
        assertTrue(text.contains("00:00:00,000 --> 00:00:01,500"))
        assertTrue(text.contains("Xin chào"))
        assertTrue(text.contains("Thế giới"))

        val parsed = SrtWriter.parse(text)
        assertEquals(2, parsed.size)
        assertEquals("Xin chào", parsed[0].text)
        assertEquals(2000L, parsed[1].startMs)
        assertEquals(3500L, parsed[1].endMs)
    }

    @Test
    fun `doc srt da nguoi dung sua text`() {
        val raw = """
            1
            00:00:00,000 --> 00:00:02,000
            Dòng đã sửa

            2
            00:00:02,500 --> 00:00:04,000
            Dòng thứ hai
            vẫn là 2 dòng
        """.trimIndent()
        val cues = SrtWriter.parse(raw)
        assertEquals(2, cues.size)
        assertEquals("Dòng đã sửa", cues[0].text)
        assertEquals("Dòng thứ hai\nvẫn là 2 dòng", cues[1].text)
    }

    @Test
    fun `file rong thi khong co cue`() {
        assertTrue(SrtWriter.write(emptyList()).isEmpty())
        assertTrue(SrtWriter.parse("  \n ").isEmpty())
    }

    @Test
    fun `sua text xong ghi va doc lai van giu nguyen so cue va noi dung`() {
        // Mo phong luong: nguoi dung sua cau 2 roi bam "Luu file .srt", thoat app, mo lai.
        val edited = listOf(
            SubtitleCue(1, 0, 1200, "gốc 1", "Đã sửa câu 1"),
            SubtitleCue(2, 1500, 3000, "gốc 2", "Đã sửa\nhai dòng"),
            SubtitleCue(3, 3200, 5000, "gốc 3", "Giữ nguyên")
        )

        val content = SrtWriter.write(edited)
        val parsed = SrtWriter.parse(content)

        assertEquals(edited.size, parsed.size)
        assertEquals(edited.map { it.text }, parsed.map { it.text })
        assertEquals(edited.map { it.index }, parsed.map { it.index })
        assertEquals(edited.map { it.startMs }, parsed.map { it.startMs })
    }
}
