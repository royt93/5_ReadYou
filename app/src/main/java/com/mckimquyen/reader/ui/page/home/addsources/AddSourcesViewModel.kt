package com.mckimquyen.reader.ui.page.home.addsources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.RssSource
import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.repository.RssSourceRepository
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddSourcesViewModel @Inject constructor(
    private val rssSourceRepository: RssSourceRepository,
    private val addedRssSourceDao: AddedRssSourceDao,
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val accountService: AccountSv
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddSourcesUiState())
    val uiState: StateFlow<AddSourcesUiState> = _uiState.asStateFlow()

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
                    addedSources = addedSourceUrls,
                    error = null
                )
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
    val addedSources: List<String> = emptyList(),
    val error: String? = null
)