package com.mckimquyen.reader.ui.page.collection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartCollectionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartCollectionDao: SmartCollectionDao,
) : ViewModel() {

    val collectionsWithCount: StateFlow<List<SmartCollectionWithCount>> =
        smartCollectionDao.observeCollectionsWithCount(context.currentAccountId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun createCollection(name: String, query: String, onComplete: () -> Unit = {}) {
        val cleanName = name.trim()
        val cleanQuery = query.trim()
        if (cleanName.isBlank() || cleanQuery.isBlank()) return
        viewModelScope.launch {
            smartCollectionDao.upsertCollection(
                SmartCollection(
                    accountId = context.currentAccountId,
                    name = cleanName,
                    query = cleanQuery,
                )
            )
            onComplete()
        }
    }

    fun updateCollection(id: String, name: String, query: String, onComplete: () -> Unit = {}) {
        val cleanName = name.trim()
        val cleanQuery = query.trim()
        if (cleanName.isBlank() || cleanQuery.isBlank()) return
        viewModelScope.launch {
            smartCollectionDao.updateCollection(id = id, name = cleanName, query = cleanQuery)
            onComplete()
        }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            smartCollectionDao.deleteCollection(id)
        }
    }

    fun observeArticles(collectionId: String): Flow<List<ArticleWithFeed>> =
        smartCollectionDao.observeArticlesInCollection(collectionId)
}
