package com.mckimquyen.reader.ui.page.home.addsources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.curated.BundleSubscribeResult
import com.mckimquyen.reader.domain.curated.CuratedBundle
import com.mckimquyen.reader.domain.curated.CuratedBundleService
import com.mckimquyen.reader.domain.model.RssSource
import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.repository.RssSourceRepository
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddSourcesViewModel @Inject constructor(
    private val rssSourceRepository: RssSourceRepository,
    private val addedRssSourceDao: AddedRssSourceDao,
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val accountService: AccountSv,
    private val curatedBundleService: CuratedBundleService,
) : ViewModel() {

    /** UI language used to pick the bundle/group title; a property (not a constructor argument) so Hilt keeps one constructor. */
    internal var languageProvider: () -> String = { Locale.getDefault().language }

    private val _uiState = MutableStateFlow(AddSourcesUiState())
    val uiState: StateFlow<AddSourcesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Debounce search query với delay 300ms
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collect { query ->
                    filterSources(query)
                }
        }
    }

    fun loadBundles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingBundles = true, bundleError = null)
            try {
                val bundles = curatedBundleService.load()
                val canSubscribe = curatedBundleService.canSubscribe()
                _uiState.value = _uiState.value.copy(
                    loadingBundles = false,
                    bundles = bundles,
                    canSubscribeBundles = canSubscribe,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loadingBundles = false,
                    bundleError = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    /** Subscribes every usable source of [bundle] into a new group named after the bundle in the UI language. */
    fun subscribeBundle(bundle: CuratedBundle) {
        if (_uiState.value.subscribingBundleId != null) return
        _uiState.value = _uiState.value.copy(
            subscribingBundleId = bundle.id,
            bundleProgress = null,
            bundleResult = null,
            bundleResultId = null,
            bundleError = null,
        )
        viewModelScope.launch {
            try {
                val result = curatedBundleService.subscribe(bundle, bundle.localizedTitle(languageProvider())) { done, total ->
                    _uiState.value = _uiState.value.copy(bundleProgress = done to total)
                }
                _uiState.value = _uiState.value.copy(bundleResult = result, bundleResultId = bundle.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(bundleError = e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.value = _uiState.value.copy(subscribingBundleId = null, bundleProgress = null)
            }
        }
    }

    fun dismissBundleResult() {
        _uiState.value = _uiState.value.copy(bundleResult = null, bundleResultId = null, bundleError = null)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    private fun filterSources(query: String) {
        val currentState = _uiState.value
        if (query.isBlank()) {
            // Nếu query rỗng, hiển thị tất cả sources
            _uiState.value = currentState.copy(
                filteredSources = currentState.rssSources
            )
        } else {
            // Filter sources theo tên hoặc link
            val filtered = currentState.rssSources.filter { source ->
                source.name.contains(query, ignoreCase = true) ||
                source.link.contains(query, ignoreCase = true)
            }
            _uiState.value = currentState.copy(
                filteredSources = filtered
            )
        }
    }

    fun loadSourcesByCountry(countryCode: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true)
                val account = accountService.getCurrentAccount()
                val sources = rssSourceRepository.getRssSourcesByCountry(countryCode)
                val addedSources = addedRssSourceDao.getAddedSourcesByAccount(account.id ?: 0).first()
                val addedSourceUrls = addedSources.map { it.url }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    rssSources = sources,
                    filteredSources = sources,
                    addedSources = addedSourceUrls,
                    error = null
                )
                // Apply current search filter nếu có
                filterSources(_searchQuery.value)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    suspend fun addRssSource(source: RssSource): Boolean {
        return try {
            // Get current account and default group
            val account = accountService.getCurrentAccount()
            val groups = rssService.get().pullGroups().first()
            val defaultGroup = groups.firstOrNull() ?: return false

            // Create feed object
            val feed = Feed(
                id = UUID.randomUUID().toString(),
                name = source.name,
                url = source.link,
                groupId = defaultGroup.id,
                accountId = account.id ?: return false,
                isNotification = false,
                isFullContent = false
            )

            // First, try to fetch the RSS feed to validate it and get articles
            try {
                val feedWithArticle = rssHelper.searchFeed(source.link)
                val networkFeed = feedWithArticle.feed
                val articles = feedWithArticle.articles

                // Use the actual feed data from RSS
                val finalFeed = feed.copy(
                    name = networkFeed.name.ifBlank { source.name },
                    icon = networkFeed.icon
                )

                // Subscribe to the feed
                rssService.get().subscribe(finalFeed, articles)

                // Track this source as added
                val addedSource = AddedRssSource(
                    url = source.link,
                    name = source.name,
                    accountId = account.id ?: 0
                )
                addedRssSourceDao.insertAddedSource(addedSource)

                // Cập nhật UI state để reflect thay đổi
                val currentAddedSources = _uiState.value.addedSources.toMutableList()
                if (!currentAddedSources.contains(source.link)) {
                    currentAddedSources.add(source.link)
                    _uiState.value = _uiState.value.copy(addedSources = currentAddedSources)
                }

                true
            } catch (feedError: Exception) {
                // If RSS parsing fails, just add the feed with basic info
                rssService.get().subscribe(feed, emptyList())

                // Track this source as added
                val addedSource = AddedRssSource(
                    url = source.link,
                    name = source.name,
                    accountId = account.id ?: 0
                )
                addedRssSourceDao.insertAddedSource(addedSource)

                // Cập nhật UI state để reflect thay đổi
                val currentAddedSources = _uiState.value.addedSources.toMutableList()
                if (!currentAddedSources.contains(source.link)) {
                    currentAddedSources.add(source.link)
                    _uiState.value = _uiState.value.copy(addedSources = currentAddedSources)
                }

                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

data class AddSourcesUiState(
    val loading: Boolean = false,
    val rssSources: List<RssSource> = emptyList(),
    val filteredSources: List<RssSource> = emptyList(),
    val addedSources: List<String> = emptyList(),
    val error: String? = null,
    val bundles: List<CuratedBundle> = emptyList(),
    val loadingBundles: Boolean = false,
    val canSubscribeBundles: Boolean = false,
    val subscribingBundleId: String? = null,
    val bundleProgress: Pair<Int, Int>? = null,
    val bundleResult: BundleSubscribeResult? = null,
    val bundleResultId: String? = null,
    val bundleError: String? = null,
)