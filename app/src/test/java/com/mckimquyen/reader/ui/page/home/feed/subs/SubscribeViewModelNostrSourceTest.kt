package com.mckimquyen.reader.ui.page.home.feed.subs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubscribeViewModelNostrSourceTest {
    private val source = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt").readLines()
        .filterNot { it.trimStart().let { t -> t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ") } }
        .joinToString("\n")
    private val search = source.substringAfter("fun search()").substringBefore("fun confirmWebFeed()")

    // substringAfter() returns the WHOLE text when its anchor is missing, which would make the order checks below pass for nothing.
    @Test fun theNostrAnchorExists() {
        assertTrue("the Nostr branch must call authorHexFrom(", search.contains("authorHexFrom("))
        assertTrue("an nprofile must not be read through npubToHex only", !search.contains("npubToHex("))
        assertTrue(search.indexOf("authorHexFrom(") >= 0 && search.indexOf("formatUrl()") > search.indexOf("authorHexFrom("))
    }

    @Test fun npubIsResolvedBeforeFormatUrl() {
        val nostr = search.substringAfter("authorHexFrom(")
        assertTrue(nostr.contains("nostrRelayClient.fetch("))
        assertTrue(search.indexOf("authorHexFrom(") < search.indexOf("formatUrl()"))
    }

    @Test fun nsecGuardIsBeforeEveryNetworkResolver() {
        val guard = search.indexOf("isSecretKey(")
        assertTrue(guard >= 0)
        listOf("nostrRelayClient.fetch(", "rssHelper.searchFeed(", "resolveToRssUrl(", "webFeedScraper.discover(")
            .forEach { call -> assertTrue("guard must be before $call", guard < search.indexOf(call)) }
    }

    @Test fun nostrFailureDoesNotFallThroughToWeb() {
        val branch = search.substringAfter("authorHexFrom(").substringBefore("formatUrl()")
        assertFalse(branch.contains("resolveToRssUrl("))
        assertFalse(branch.contains("webFeedScraper.discover("))
    }

    @Test fun newNostrCodeDoesNotLogKeysOrNotes() {
        val file = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt")
            .readLines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") }.joinToString("\n")
        val nostr = file.substringAfter("authorHexFrom(").substringBefore("formatUrl()")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, nostr.contains(it)) }
    }
}
