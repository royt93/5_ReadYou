package com.mckimquyen.reader.ui.page.home.feed.subs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import kotlinx.coroutines.Dispatchers

private const val FAKE_RELAY_TIMEOUT_MS = 10_000L

/**
 * Builds the real SubscribeViewModel from the app's real singletons (read from the running RApp, there is no Hilt test
 * runner here), but reads Nostr from the fake relay at [relayUrl]. The web/newsletter helpers are never used by the Nostr path.
 */
fun subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val app = context as RApp
        return SubscribeViewModel(
            context = context,
            opmlService = app.opmlService,
            rssService = app.rssSv,
            rssHelper = app.rssHelper,
            androidStringsHelper = app.androidStringsHelper,
            webFeedScraper = WebFeedScraper(WebFeedHttpClient(), WebFeedParser()),
            newsletterUrlResolver = NewsletterUrlResolver(),
            nostrRelayClient = NostrRelayClient(app.okHttpClient, Dispatchers.IO, listOf(relayUrl), false, FAKE_RELAY_TIMEOUT_MS),
        ) as T
    }
}
