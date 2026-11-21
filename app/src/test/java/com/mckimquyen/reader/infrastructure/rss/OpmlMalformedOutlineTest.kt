package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/** An OPML file is untrusted input: one broken outline must not abort the whole import, and the good ones must still be read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpmlMalformedOutlineTest {
    private val defaultGroup = Group(id = "1\$default", name = "Default", accountId = 1)

    private fun parse(body: String): List<GroupWithFeed> = runBlocking {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><opml version="1.0"><head><title>t</title></head><body>$body</body></opml>"""
        OPMLDataSource(ApplicationProvider.getApplicationContext<Context>(), Dispatchers.Unconfined)
            .parseFileInputStream(ByteArrayInputStream(xml.toByteArray()), defaultGroup)
    }

    private fun feedNames(parsed: List<GroupWithFeed>) = parsed.flatMap { it.feeds }.map { it.name }

    @Test
    fun aFeedWithNeitherTitleNorText_isSkipped_andTheOthersAreRead() {
        val parsed = parse("""<outline xmlUrl="https://bad.example/feed"/><outline text="Good" xmlUrl="https://good.example/feed"/>""")
        assertEquals(listOf("Good"), feedNames(parsed))
    }

    @Test
    fun aGroupWithNeitherTitleNorText_isSkipped_andTheOthersAreRead() {
        val parsed = parse("""<outline><outline text="Hidden" xmlUrl="https://h.example/f"/></outline><outline text="Good" xmlUrl="https://good.example/feed"/>""")
        assertEquals(listOf("Good"), parsed.first().feeds.map { it.name })
    }

    @Test
    fun anEmptyGroupWithNeitherTitleNorText_isSkipped_andTheOthersAreRead() {
        val parsed = parse("""<outline/><outline text="Good" xmlUrl="https://good.example/feed"/>""")
        assertEquals("only the default group, no group without a name", 1, parsed.size)
        assertEquals(listOf("Good"), feedNames(parsed))
    }

    @Test
    fun anEmptyNamedGroup_isStillCreated() {
        val parsed = parse("""<outline text="Empty"/>""")
        assertEquals(listOf("Default", "Empty"), parsed.map { it.group.name })
    }

    @Test
    fun aFeedInsideAGroupWithoutXmlUrl_isSkipped_andItsSiblingsAreRead() {
        val parsed = parse("""<outline text="News"><outline text="NoUrl"/><outline text="Ok" xmlUrl="https://ok.example/f"/></outline>""")
        assertEquals(listOf("Ok"), parsed.flatMap { it.feeds }.map { it.name })
    }

    @Test
    fun aFeedInsideAGroupWithoutAName_isSkipped_andItsSiblingsAreRead() {
        val parsed = parse("""<outline text="News"><outline xmlUrl="https://nameless.example/f"/><outline text="Ok" xmlUrl="https://ok.example/f"/></outline>""")
        assertEquals(listOf("Ok"), parsed.flatMap { it.feeds }.map { it.name })
    }

    @Test
    fun aWellFormedFile_isReadAsBefore() {
        val parsed = parse("""<outline text="News"><outline text="A" xmlUrl="https://a.example/f"/></outline><outline title="B" xmlUrl="https://b.example/f"/>""")
        assertEquals(setOf("A", "B"), feedNames(parsed).toSet())
    }
}
