package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article

@Composable
internal fun WebFeedPreviewContent(
    siteTitle: String,
    articles: List<Article>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    saving: Boolean = false,
) {
    Column(Modifier.padding(12.dp)) {
        Text(siteTitle)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.web_feed_preview_notice))
        LazyColumn {
            items(articles.take(PREVIEW_ARTICLE_LIMIT)) { article ->
                Text(article.title, Modifier.padding(vertical = 4.dp))
            }
        }
        TextButton(onClick = onConfirm, enabled = !saving) { Text(stringResource(R.string.web_feed_add)) }
        TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.cancel)) }
    }
}

private const val PREVIEW_ARTICLE_LIMIT = 5
