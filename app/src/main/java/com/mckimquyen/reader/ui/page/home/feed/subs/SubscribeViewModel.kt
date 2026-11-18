package com.mckimquyen.reader.ui.page.home.feed.subs

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.SocialFeedUrlResolver
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.formatUrl
import com.mckimquyen.reader.ui.ext.spacerDollar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SubscribeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opmlService: OpmlSv,
    val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val androidStringsHelper: AndroidStringsHelper,
    private val webFeedScraper: WebFeedScraper,
    private val newsletterUrlResolver: com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver,
    private val nostrRelayClient: com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient,
    private val blueskyApiClient: com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient,
) : ViewModel() {

    private val _subscribeUiState = MutableStateFlow(SubscribeUiState())
    private var webPreviewFeed: Feed? = null
    private var webPreviewArticles: List<Article> = emptyList()
    val subscribeUiState: StateFlow<SubscribeUiState> = _subscribeUiState.asStateFlow()
    private var searchJob: Job? = null
    private var confirmingWebFeed = false

    fun init() {
        _subscribeUiState.update {
            it.copy(
                title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),
            )
        }
    }

    fun reset() {
        searchJob?.cancel()
        searchJob = null
        webPreviewFeed = null
        webPreviewArticles = emptyList()
        _subscribeUiState.update {
            SubscribeUiState().copy(title = androidStringsHelper.getString(R.string.subscribe))
        }
    }

    fun importFromInputStream(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                opmlService.saveToDatabase(inputStream)
                rssService.get().doSync()
            } catch (e: Exception) {
                Log.e("FeedsViewModel", "importFromInputStream: ", e)
            }
        }
    }

    fun selectedGroup(groupId: String) {
        _subscribeUiState.update { it.copy(selectedGroupId = groupId) }
    }

    fun addNewGroup() {
        if (_subscribeUiState.value.newGroupContent.isNotBlank()) {
            viewModelScope.launch {
                selectedGroup(rssService.get().addGroup(_subscribeUiState.value.newGroupContent))
                hideNewGroupDialog()
                _subscribeUiState.update { it.copy(newGroupContent = "") }
            }
        }
    }

    fun changeParseFullContentPreset() {
        _subscribeUiState.update {
            it.copy(parseFullContentPreset = !_subscribeUiState.value.parseFullContentPreset)
        }
    }

    fun changeAllowNotificationPreset() {
        _subscribeUiState.update {
            it.copy(allowNotificationPreset = !_subscribeUiState.value.allowNotificationPreset)
        }
    }

    fun search() {
        searchJob?.cancel()
        viewModelScope.launch {
            try {
                _subscribeUiState.update {
                    it.copy(
                        errorMessage = "",
                    )
                }
                // Must run before formatUrl(): it would turn "@bsky.app" into "https://@bsky.app".
                val typed = _subscribeUiState.value.linkContent
                if (com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.isSecretKey(typed)) {
                    _subscribeUiState.update {
                        it.copy(
                            linkContent = "",
                            errorMessage = androidStringsHelper.getString(R.string.web_feed_invalid_url),
                            lockLinkInput = false,
                            title = androidStringsHelper.getString(R.string.subscribe),
                        )
                    }
                    return@launch
                }
                if (context.currentAccountType == AccountType.Local.id) {
                    val authorHex = com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.authorHexFrom(typed)
                    if (authorHex != null) {
                        val npub = requireNotNull(com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.hexToNpub(authorHex))
                        val feedUrl = com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.feedUrl(npub)
                        if (rssService.get().isFeedExist(feedUrl)) {
                            _subscribeUiState.update {
                                it.copy(
                                    title = androidStringsHelper.getString(R.string.subscribe),
                                    errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                    lockLinkInput = false,
                                )
                            }
                            return@launch
                        }
                        // Same indicator as the RSS flow, so the user sees progress and cannot start a second search on the box.
                        _subscribeUiState.update {
                            it.copy(title = androidStringsHelper.getString(R.string.searching), lockLinkInput = true)
                        }
                        val fetched = runCatching { nostrRelayClient.fetch(authorHex) }
                            // A cancelled search (dialog closed, newer search) must stop here, not be reported as a failure.
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                            .getOrNull()
                        val articles = fetched
                            ?.let {
                                com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.toArticles(
                                    events = it.notes,
                                    feedId = context.currentAccountId.spacerDollar(UUID.randomUUID().toString()),
                                    accountId = context.currentAccountId,
                                    nowMillis = System.currentTimeMillis(),
                                )
                            }
                            .orEmpty()
                        if (articles.isEmpty()) {
                            _subscribeUiState.update {
                                it.copy(
                                    linkContent = feedUrl,
                                    title = androidStringsHelper.getString(R.string.subscribe),
                                    errorMessage = androidStringsHelper.getString(R.string.web_feed_fetch_failed),
                                    lockLinkInput = false,
                                )
                            }
                            return@launch
                        }
                        val feed = com.mckimquyen.reader.domain.model.feed.Feed(
                            id = articles.first().feedId,
                            name = com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.feedName(npub, fetched?.profileName),
                            url = feedUrl,
                            sourceUrl = feedUrl,
                            groupId = "",
                            accountId = context.currentAccountId,
                            sourceType = com.mckimquyen.reader.domain.model.feed.Feed.NOSTR_SOURCE_TYPE,
                        )
                        _subscribeUiState.update {
                            it.copy(
                                linkContent = feedUrl,
                                feed = feed,
                                articles = articles,
                                title = feed.name,
                                errorMessage = "",
                                lockLinkInput = true,
                                webPreview = false,
                                isSearchPage = false,
                            )
                        }
                        return@launch
                    }
                }
                val socialUrl = SocialFeedUrlResolver.resolve(typed)
                val blueskyActor = if (context.currentAccountType == AccountType.Local.id) {
                    com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.actorFromRssUrl(socialUrl)
                } else {
                    null
                }
                if (blueskyActor != null) {
                    val feedUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(blueskyActor)
                    val oldRssUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.rssUrl(blueskyActor)
                    // One account can already be subscribed in its old RSS form or its API form; either blocks a second source.
                    if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                lockLinkInput = false,
                            )
                        }
                        return@launch
                    }
                    _subscribeUiState.update {
                        it.copy(title = androidStringsHelper.getString(R.string.searching), lockLinkInput = true)
                    }
                    val outcome = runCatching { blueskyApiClient.fetch(blueskyActor) }
                        // A cancelled search (dialog closed, newer search) must stop here, not be reported as a failure.
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrNull()
                    // Not an exhaustive `when`: anything that is neither Found nor NotFound simply falls back to the RSS flow below.
                    if (outcome == com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.NotFound) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.web_feed_fetch_failed),
                                lockLinkInput = false,
                            )
                        }
                        return@launch
                    }
                    if (outcome is com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.Found) {
                        val found = outcome.result
                        // The API named the account's other name: a source stored under it (as API or as the old RSS form) is the same account.
                        val otherNames = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
                            .otherNameAddresses(blueskyActor, found.did, found.handle)
                        if (otherNames.any { rssService.get().isFeedExist(it) } ||
                            rssService.get().isFeedSourceExist(
                                com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did),
                            )
                        ) {
                            _subscribeUiState.update {
                                it.copy(
                                    title = androidStringsHelper.getString(R.string.subscribe),
                                    errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                    lockLinkInput = false,
                                )
                            }
                            return@launch
                        }
                        val feedId = context.currentAccountId.spacerDollar(UUID.randomUUID().toString())
                        val articles = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.toArticles(
                            profileDid = found.did,
                            items = found.items,
                            feedId = feedId,
                            accountId = context.currentAccountId,
                            nowMillis = System.currentTimeMillis(),
                        )
                        if (articles.isNotEmpty()) {
                            val feed = Feed(
                                id = feedId,
                                name = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedName(blueskyActor, found.displayName),
                                url = feedUrl,
                                // The DID outlives a handle change; url stays the typed name.
                                sourceUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did),
                                groupId = "",
                                accountId = context.currentAccountId,
                                sourceType = Feed.BLUESKY_SOURCE_TYPE,
                                icon = found.avatarUrl,
                            )
                            _subscribeUiState.update {
                                it.copy(
                                    linkContent = feedUrl,
                                    feed = feed,
                                    articles = articles,
                                    title = feed.name,
                                    errorMessage = "",
                                    lockLinkInput = true,
                                    webPreview = false,
                                    isSearchPage = false,
                                )
                            }
                            return@launch
                        }
                    }
                    // API failed, threw, or had nothing usable: the box was never rewritten, so the old RSS flow runs on what was typed.
                }
                (socialUrl ?: typed.formatUrl()).let { str ->
                    if (str != _subscribeUiState.value.linkContent) {
                        _subscribeUiState.update {
                            it.copy(
                                linkContent = str
                            )
                        }
                    }
                }
                _subscribeUiState.update {
                    it.copy(
                        title = androidStringsHelper.getString(R.string.searching),
                        lockLinkInput = true,
                    )
                }
                if (rssService.get().isFeedExist(_subscribeUiState.value.linkContent)) {
                    _subscribeUiState.update {
                        it.copy(
                            title = androidStringsHelper.getString(R.string.subscribe),
                            errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                            lockLinkInput = false,
                        )
                    }
                    return@launch
                }
                var link = _subscribeUiState.value.linkContent
                var feedWithArticle = runCatching { rssHelper.searchFeed(link) }.getOrNull()
                if (feedWithArticle == null && socialUrl != null) {
                    // The guessed RSS address is unreadable: run the old flow on what the user typed.
                    link = typed.formatUrl()
                    _subscribeUiState.update { it.copy(linkContent = link) }
                    feedWithArticle = runCatching { rssHelper.searchFeed(link) }.getOrNull()
                }
                if (feedWithArticle == null) {
                    val resolvedUrl = runCatching { newsletterUrlResolver.resolveToRssUrl(link) }.getOrNull()
                    if (!resolvedUrl.isNullOrBlank() && resolvedUrl != link) {
                        feedWithArticle = runCatching { rssHelper.searchFeed(resolvedUrl) }.getOrNull()
                    }
                }

                if (feedWithArticle != null) {
                    webPreviewFeed = null
                    webPreviewArticles = emptyList()
                    _subscribeUiState.update {
                        it.copy(
                            feed = feedWithArticle.feed,
                            articles = feedWithArticle.articles,
                            webPreview = false,
                        )
                    }
                    switchPage(false)
                } else {
                    if (context.currentAccountType != AccountType.Local.id) {
                        throw IllegalStateException("Failed to parse RSS feed from $link")
                    }
                    when (val result = webFeedScraper.discover(link)) {
                        is WebFeedDiscoveryResult.Preview -> {
                            val accountId = context.currentAccountId
                            val groupId = _subscribeUiState.value.selectedGroupId.ifBlank {
                                _subscribeUiState.value.groups.first().firstOrNull()?.id.orEmpty()
                            }
                            if (groupId.isBlank()) throw IllegalStateException("No feed group available")
                            webPreviewFeed = Feed(
                                id = accountId.spacerDollar(UUID.randomUUID().toString()),
                                name = result.siteTitle,
                                url = link,
                                groupId = groupId,
                                accountId = accountId,
                                sourceType = Feed.WEB_SOURCE_TYPE,
                                sourceUrl = link,
                            )
                            webPreviewArticles = result.articles.map { candidate ->
                                val title = candidate.title
                                Article(
                                    id = accountId.spacerDollar(UUID.randomUUID().toString()),
                                    date = candidate.publishedAt ?: Date(),
                                    title = title,
                                    rawDescription = "",
                                    shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                                    link = candidate.url.toString(),
                                    feedId = requireNotNull(webPreviewFeed).id,
                                    accountId = accountId,
                                )
                            }
                            _subscribeUiState.update { state ->
                                state.copy(
                                    title = result.siteTitle,
                                    feed = requireNotNull(webPreviewFeed),
                                    articles = webPreviewArticles,
                                    webPreview = true,
                                    lockLinkInput = true,
                                    errorMessage = "",
                                )
                            }
                            switchPage(false)
                        }

                        else -> {
                            // Diagnostic: result type only, never the URL/query/body.
                            Log.w("RLog", "web feed discovery failed: $result")
                            throw IllegalArgumentException(webFeedDiscoveryError(result))
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Closing the dialog or starting a newer search cancels this one: not a failure, and nothing may be written.
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                webPreviewFeed = null
                webPreviewArticles = emptyList()
                _subscribeUiState.update {
                    it.copy(
                        title = androidStringsHelper.getString(R.string.subscribe),
                        errorMessage = e.message ?: androidStringsHelper.getString(R.string.unknown),
                        lockLinkInput = false,
                        webPreview = false,
                    )
                }
            }
        }.also {
            searchJob = it
        }
    }

    fun confirmWebFeed() {
        if (!_subscribeUiState.value.webPreview || confirmingWebFeed || _subscribeUiState.value.savingWebFeed) return
        val feed = webPreviewFeed ?: return
        val articles = webPreviewArticles
        if (context.currentAccountType != AccountType.Local.id) return
        confirmingWebFeed = true
        _subscribeUiState.update { it.copy(savingWebFeed = true, errorMessage = "") }
        viewModelScope.launch {
            try {
                rssService.get(AccountType.Local.id).subscribe(feed, articles)
                webPreviewFeed = null
                webPreviewArticles = emptyList()
                hideDrawer()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _subscribeUiState.update {
                    it.copy(errorMessage = e.message ?: androidStringsHelper.getString(R.string.unknown))
                }
            } finally {
                confirmingWebFeed = false
                _subscribeUiState.update { it.copy(savingWebFeed = false) }
            }
        }
    }

    fun cancelWebFeedPreview() {
        if (!_subscribeUiState.value.webPreview || confirmingWebFeed) return
        webPreviewFeed = null
        webPreviewArticles = emptyList()
        _subscribeUiState.update {
            it.copy(
                feed = null,
                articles = emptyList(),
                webPreview = false,
                lockLinkInput = false,
                isSearchPage = true,
                title = androidStringsHelper.getString(R.string.subscribe),
            )
        }
    }

    private fun webFeedDiscoveryError(result: WebFeedDiscoveryResult): String = when (result) {
        WebFeedDiscoveryResult.TooFewCandidates,
        WebFeedDiscoveryResult.Unsupported,
        WebFeedDiscoveryResult.Ambiguous -> androidStringsHelper.getString(R.string.web_feed_unsupported)
        WebFeedDiscoveryResult.InvalidUrl,
        WebFeedDiscoveryResult.UnsafeUrl -> androidStringsHelper.getString(R.string.web_feed_invalid_url)
        WebFeedDiscoveryResult.RedirectRejected,
        WebFeedDiscoveryResult.NonHtml,
        is WebFeedDiscoveryResult.HttpError,
        WebFeedDiscoveryResult.Timeout,
        WebFeedDiscoveryResult.ResponseTooLarge -> androidStringsHelper.getString(R.string.web_feed_fetch_failed)
        is WebFeedDiscoveryResult.Preview -> ""
    }

    fun subscribe() {
        if (_subscribeUiState.value.webPreview) {
            confirmWebFeed()
            return
        }
        val feed = _subscribeUiState.value.feed ?: return
        val articles = _subscribeUiState.value.articles
        viewModelScope.launch {
            rssService.get().subscribe(
                feed.copy(
                    groupId = _subscribeUiState.value.selectedGroupId,
                    isNotification = _subscribeUiState.value.allowNotificationPreset,
                    // A Nostr note has no web page to read in full (parseFullContent returns nothing for it).
                    isFullContent = _subscribeUiState.value.parseFullContentPreset &&
                        feed.sourceType != com.mckimquyen.reader.domain.model.feed.Feed.NOSTR_SOURCE_TYPE &&
                        feed.sourceType != com.mckimquyen.reader.domain.model.feed.Feed.BLUESKY_SOURCE_TYPE,
                ), articles
            )
            hideDrawer()
        }
    }

    fun inputLink(content: String) {
        _subscribeUiState.update {
            it.copy(
                linkContent = content,
                errorMessage = "",
            )
        }
    }

    fun inputNewGroup(content: String) {
        _subscribeUiState.update { it.copy(newGroupContent = content) }
    }

    fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = true) }
    }

    fun hideDrawer() {
        _subscribeUiState.update { it.copy(visible = false) }
    }

    fun showNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = true) }
    }

    fun hideNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = false) }
    }

    fun switchPage(isSearchPage: Boolean) {
        _subscribeUiState.update { it.copy(isSearchPage = isSearchPage) }
    }

    fun showRenameDialog() {
        _subscribeUiState.update {
            it.copy(
                renameDialogVisible = true,
                newName = _subscribeUiState.value.feed?.name ?: "",
            )
        }
    }

    fun hideRenameDialog() {
        _subscribeUiState.update {
            it.copy(
                renameDialogVisible = false,
                newName = "",
            )
        }
    }

    fun inputNewName(content: String) {
        _subscribeUiState.update { it.copy(newName = content) }
    }

    fun renameFeed() {
        _subscribeUiState.value.feed?.let {
            _subscribeUiState.update {
                it.copy(
                    feed = it.feed?.copy(name = _subscribeUiState.value.newName),
                )
            }
        }
    }
}

@Keep
data class SubscribeUiState(
    val visible: Boolean = false,
    val title: String = "",
    val errorMessage: String = "",
    val linkContent: String = "",
    val lockLinkInput: Boolean = false,
    val feed: Feed? = null,
    val articles: List<Article> = emptyList(),
    val webPreview: Boolean = false,
    val savingWebFeed: Boolean = false,
    val allowNotificationPreset: Boolean = false,
    val parseFullContentPreset: Boolean = false,
    val selectedGroupId: String = "",
    val newGroupDialogVisible: Boolean = false,
    val newGroupContent: String = "",
    val groups: Flow<List<Group>> = emptyFlow(),
    val isSearchPage: Boolean = true,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)
