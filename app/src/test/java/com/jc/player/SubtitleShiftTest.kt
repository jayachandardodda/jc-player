package com.jc.player

import com.jc.player.player.Subtitles
import com.jc.player.ui.library.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleShiftTest {

    @Test fun srtShiftsForwardAndBack() {
        val srt = "1\n00:00:01,500 --> 00:00:03,000\nHello\n"
        assertEquals("1\n00:00:02,000 --> 00:00:03,500\nHello\n", Subtitles.shiftSrtVtt(srt, 500))
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\nHello\n", Subtitles.shiftSrtVtt(srt, -500))
    }

    @Test fun srtNeverGoesNegative() {
        val srt = "00:00:00,200 --> 00:00:01,000"
        assertEquals("00:00:00,000 --> 00:00:00,000", Subtitles.shiftSrtVtt(srt, -2000))
    }

    @Test fun srtCarriesAcrossMinutesAndHours() {
        assertEquals("01:00:00,100 --> 01:00:01,000", Subtitles.shiftSrtVtt("00:59:59,600 --> 01:00:00,500", 500))
    }

    @Test fun textLinesWithTimesAreUntouched() {
        val srt = "00:00:01,000 --> 00:00:02,000\nMeet at 10:30:00,000 sharp"
        assertEquals("00:00:02,000 --> 00:00:03,000\nMeet at 10:30:00,000 sharp", Subtitles.shiftSrtVtt(srt, 1000))
    }

    @Test fun vttShortTimestamps() {
        val vtt = "WEBVTT\n\n01:02.500 --> 01:04.000 line:90%\nHi"
        assertEquals("WEBVTT\n\n00:01:03.500 --> 00:01:05.000 line:90%\nHi", Subtitles.shiftSrtVtt(vtt, 1000))
    }

    @Test fun assDialogueOnly() {
        val ass = "[Events]\nStyle: Default,Arial,20\nDialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,Hi"
        assertEquals(
            "[Events]\nStyle: Default,Arial,20\nDialogue: 0,0:00:02.00,0:00:03.50,Default,,0,0,0,,Hi",
            Subtitles.shiftAss(ass, 500),
        )
    }

    @Test fun externalIndexParsing() {
        assertEquals(0, Subtitles.externalIndex("ext:0"))
        assertEquals(3, Subtitles.externalIndex("1:ext:3"))
        assertEquals(null, Subtitles.externalIndex("2"))
        assertEquals(null, Subtitles.externalIndex(null))
    }

    @Test fun naturalSort() {
        val names = listOf("Episode 10.mkv", "Episode 2.mkv", "episode 1.mkv", "Episode 02b.mkv")
        assertEquals(listOf("episode 1.mkv", "Episode 2.mkv", "Episode 02b.mkv", "Episode 10.mkv"), names.sortedWith(NaturalOrder))
    }
}
