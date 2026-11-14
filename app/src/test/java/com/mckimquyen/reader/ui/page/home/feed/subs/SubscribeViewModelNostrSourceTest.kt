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

    @Test fun npubIsResolvedBeforeFormatUrl() {
        val nostr = search.substringAfter("npubToHex(")
        assertTrue(nostr.contains("nostrRelayClient.fetch("))
        assertTrue(search.indexOf("npubToHex(") < search.indexOf("formatUrl()"))
    }

    @Test fun nsecGuardIsBeforeEveryNetworkResolver() {
        val guard = search.indexOf("isSecretKey(")
        assertTrue(guard >= 0)
        listOf("nostrRelayClient.fetch(", "rssHelper.searchFeed(", "resolveToRssUrl(", "webFeedScraper.discover(")
            .forEach { call -> assertTrue("guard must be before $call", guard < search.indexOf(call)) }
    }

    @Test fun nostrFailureDoesNotFallThroughToWeb() {
        val branch = search.substringAfter("npubToHex(").substringBefore("formatUrl()")
        assertFalse(branch.contains("resolveToRssUrl("))
        assertFalse(branch.contains("webFeedScraper.discover("))
    }

    @Test fun newNostrCodeDoesNotLogKeysOrNotes() {
        val file = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt")
            .readLines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") }.joinToString("\n")
        val nostr = file.substringAfter("npubToHex(").substringBefore("formatUrl()")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, nostr.contains(it)) }
    }
}
