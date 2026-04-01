package com.mckimquyen.reader.ui.page.setting.color.flow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.general.Filter
import com.mckimquyen.reader.infrastructure.pref.FlowArticleListTonalElevationPref
import com.mckimquyen.reader.infrastructure.pref.FlowTopBarTonalElevationPref
import com.mckimquyen.reader.ui.component.FilterBar
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.ext.surfaceColorAtElevation
import com.mckimquyen.reader.ui.page.home.flow.ArticleItem
import com.mckimquyen.reader.ui.theme.palette.onDark
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPagePreview(
    articleListTonalElevation: FlowArticleListTonalElevationPref,
    filterBarStyle: Int,
    filterBarFilled: Boolean,
    filterBarPadding: Dp,
    filterBarTonalElevation: Dp,
) {
    var filter by remember { mutableStateOf(Filter.Unread) }

    Column(
        modifier = Modifier
            .animateContentSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    articleListTonalElevation.value.dp
                ) onDark MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        TopAppBar(
            title = {},
            navigationIcon = {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                ) {}
            },
            actions = {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = stringResource(R.string.mark_all_as_read),
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {}
                FeedbackIconButton(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {}
            }, colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color.Transparent,
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        ArticleItem(
            articleWithFeed = generateArticleWithFeedPreview(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilterBar(
            filter = filter,
            filterBarStyle = filterBarStyle,
            filterBarFilled = filterBarFilled,
            filterBarPadding = filterBarPadding,
            filterBarTonalElevation = filterBarTonalElevation,
        ) {
            filter = it
        }
    }
}

@Stable
@Composable
fun generateArticleWithFeedPreview(): ArticleWithFeed =
    ArticleWithFeed(
        Article(
            id = "",
            title = stringResource(R.string.preview_article_title),
            shortDescription = stringResource(R.string.preview_article_desc),
            rawDescription = stringResource(R.string.preview_article_desc),
            link = "",
            feedId = "",
            accountId = 0,
            date = Date(1654053729L),
            isStarred = true,
            img = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1yZWxhdGVkfDJ8fHxlbnwwfHx8fA%3D%3D&auto=format&fit=crop&w=800&q=60"
        ),
        feed = Feed(
            id = "",
            name = stringResource(R.string.preview_feed_name),
            icon = "",
            accountId = 0,
            groupId = "",
            url = "",
        ),
    )
