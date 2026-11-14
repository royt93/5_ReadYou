package com.mckimquyen.reader.ui.page.home.feed.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubscribeViewModelSocialSourceTest {

    private val vm = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val search = vm.substringAfter("fun search()").substringBefore("fun ")

    @Test fun theResolverRunsBeforeFormatUrl() {
        assertTrue(search.contains("SocialFeedUrlResolver.resolve("))
        assertTrue(search.contains("formatUrl()"))
        assertTrue("the resolver must run first", search.indexOf("SocialFeedUrlResolver.resolve(") < search.indexOf("formatUrl()"))
    }

    @Test fun theResolverIsCalledExactlyOnce() {
        assertEquals(1, Regex("""SocialFeedUrlResolver\.resolve\(""").findAll(vm).count())
    }

    @Test fun theOldFlowIsStillThere() {
        assertTrue(search.contains("newsletterUrlResolver.resolveToRssUrl("))
        assertTrue(search.contains("webFeedScraper.discover("))
        assertTrue(search.contains("rssService.get().isFeedExist("))
    }

    @Test fun theFallbackBranchDoesNotLogWhatTheUserTyped() {
        val fallback = search.substringAfter("feedWithArticle == null && socialUrl != null")
            .substringBefore("if (feedWithArticle == null) {")
        assertTrue("fallback block not found", fallback.contains("typed.formatUrl()"))
        listOf("Log.", "println", "printStackTrace").forEach { assertTrue("the fallback must not log: $it", !fallback.contains(it)) }
    }

    @Test fun noNetworkOrAccountDetailGoesIntoTheNewCode() {
        val region = search.substringBefore("rssService.get().isFeedExist(")
        listOf("Log.", "println", "printStackTrace").forEach { assertTrue("the new branch must not log: $it", !region.contains(it)) }
    }
}
