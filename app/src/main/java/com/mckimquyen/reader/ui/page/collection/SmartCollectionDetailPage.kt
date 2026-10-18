package com.mckimquyen.reader.ui.page.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

@Composable
fun SmartCollectionDetailPage(
    navController: NavHostController,
    collectionId: String,
    viewModel: SmartCollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collectionsWithCount.collectAsState()
    val collection = collections.firstOrNull { it.collection.id == collectionId }?.collection
    val articles by produceState(initialValue = emptyList(), collectionId) {
        viewModel.observeArticles(collectionId).collect { value = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { navController.popBackStack() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection?.name ?: stringResource(R.string.smart_collections_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                collection?.query?.let { query ->
                    Text(query, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (articles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.smart_collection_empty_articles),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(articles, key = { it.article.id }) { article ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable {
                            navController.navigate("${com.mckimquyen.reader.ui.page.common.RouteName.READING}/${article.article.id}") {
                                launchSingleTop = true
                            }
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(article.article.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.size(4.dp))
                        Text(article.feed.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
