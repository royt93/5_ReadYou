package com.mckimquyen.reader.ui.page.radar

import com.mckimquyen.reader.domain.model.radar.ArticleRadarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarFlagsTest {

    @Test
    fun removedWinsOverEdited() {
        assertEquals(RadarFlag.REMOVED, ArticleRadarState("a", editCount = 3, removedAt = 5).toFlag())
    }

    @Test
    fun editedWhenOnlyEditsRecorded() {
        assertEquals(RadarFlag.EDITED, ArticleRadarState("a", editCount = 1).toFlag())
    }

    @Test
    fun cleanStateHasNoFlag() {
        assertNull(ArticleRadarState("a", missedSyncs = 2).toFlag())
    }
}
