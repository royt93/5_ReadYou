package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContrarianCueLexiconTest {

    @Test
    fun finds_englishCues() {
        assertEquals("warns", ContrarianCueLexicon.find("Official warns of high inflation"))
        assertEquals("warn", ContrarianCueLexicon.find("Scientists warn of coral loss"))
        assertEquals("critics", ContrarianCueLexicon.find("Critics slam new tax policy"))
        assertEquals("disputes", ContrarianCueLexicon.find("Company disputes union claim"))
        assertEquals("risks", ContrarianCueLexicon.find("Experts highlight risks in AI race"))
        assertEquals("backlash", ContrarianCueLexicon.find("Outcry follows public backlash"))
    }

    @Test
    fun finds_vietnameseCues() {
        assertEquals("cảnh báo", ContrarianCueLexicon.find("Chuyên gia cảnh báo nguy cơ lũ quét"))
        assertEquals("chỉ trích", ContrarianCueLexicon.find("Dư luận chỉ trích quyết định mới"))
        assertEquals("tuy nhiên", ContrarianCueLexicon.find("Kinh tế tăng trưởng, tuy nhiên còn nhiều thách thức"))
        assertEquals("tranh cãi", ContrarianCueLexicon.find("Dự luật gây tranh cãi gay gắt"))
        assertEquals("bác bỏ", ContrarianCueLexicon.find("Bộ Ngoại giao bác bỏ thông tin sai lệch"))
    }

    @Test
    fun rejects_substringHits() {
        assertNull(ContrarianCueLexicon.find("A brisk jog in the park")) // not "risk"
        assertNull(ContrarianCueLexicon.find("Fresh butter on hot toast")) // not "but"
        assertNull(ContrarianCueLexicon.find("Alerts issued after the storm")) // no cue as a whole word
    }

    @Test
    fun returnsNull_whenNoCuePresent() {
        assertNull(ContrarianCueLexicon.find("Sunny weather expected this weekend"))
        assertNull(ContrarianCueLexicon.find("Tech company announces new quarterly results"))
        assertNull(ContrarianCueLexicon.find(""))
    }
}
