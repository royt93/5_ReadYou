package com.mckimquyen.reader.ui.page.setting.feedhealth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedHealthItem(
    val feed: Feed,
    val record: FeedHealthRecord? = null,
    val isRetrying: Boolean = false,
)

data class FeedHealthUiState(
    val items: List<FeedHealthItem> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class FeedHealthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rssService: RssSv,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val feedHealthDao: FeedHealthDao,
) : ViewModel() {

    private val retryingFeedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<FeedHealthUiState> = combine(
        groupDao.queryAllGroupWithFeedAsFlow(context.currentAccountId),
        feedHealthDao.observeAll(),
        retryingFeedIds
    ) { groupsWithFeeds, healthRecords, retryingIds ->
        val recordMap = healthRecords.associateBy { it.feedId }
        val allFeeds = groupsWithFeeds.flatMap { it.feeds }

        val items = allFeeds.map { feed ->
            FeedHealthItem(
                feed = feed,
                record = recordMap[feed.id],
                isRetrying = retryingIds.contains(feed.id),
            )
        }.sortedWith(
            compareByDescending<FeedHealthItem> { it.record?.isFailing == true }
                .thenBy { it.feed.name.lowercase() }
        )
        FeedHealthUiState(items = items, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FeedHealthUiState(isLoading = true)
    )

    fun retryFeed(feedId: String) {
        if (retryingFeedIds.value.contains(feedId)) return
        retryingFeedIds.value = retryingFeedIds.value + feedId
        viewModelScope.launch {
            try {
                val feed = feedDao.queryById(feedId)
                if (feed != null) {
                    rssService.get().retryFeedSync(feed)
                }
            } finally {
                retryingFeedIds.value = retryingFeedIds.value - feedId
            }
        }
    }
}
