package com.mckimquyen.reader.ui.component.onthisday

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import java.time.ZoneId
import java.util.Date

const val ON_THIS_DAY_MAX_ROWS = 5
const val ON_THIS_DAY_CARD_TAG = "on_this_day_card"
const val ON_THIS_DAY_SEE_ALL_TAG = "on_this_day_see_all"
const val ON_THIS_DAY_ROW_TAG_PREFIX = "on_this_day_row_"

/** Số năm từ năm xuất bản (giờ local) đến [currentYear]; tối thiểu 1 vì truy vấn chỉ trả năm trước. */
internal fun yearsAgo(date: Date, currentYear: Int, zoneId: ZoneId = ZoneId.systemDefault()): Int =
    (currentYear - date.toInstant().atZone(zoneId).year).coerceAtLeast(1)

@Composable
internal fun OnThisDayRow(
    article: ArticleWithFeed,
    currentYear: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val years = yearsAgo(article.article.date, currentYear)
    val yearsText = LocalContext.current.resources.getQuantityString(R.plurals.on_this_day_years_ago, years, years)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ON_THIS_DAY_ROW_TAG_PREFIX + article.article.id)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = article.article.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = "${article.feed.name} · $yearsText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OnThisDayCard(
    articles: List<ArticleWithFeed>,
    currentYear: Int,
    onArticleClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(ON_THIS_DAY_CARD_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ON_THIS_DAY_SEE_ALL_TAG)
                    .clickable(onClick = onSeeAllClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.on_this_day_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.on_this_day_desc),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            articles.take(ON_THIS_DAY_MAX_ROWS).forEach { item ->
                OnThisDayRow(
                    article = item,
                    currentYear = currentYear,
                    onClick = { onArticleClick(item.article.id) },
                )
            }
        }
    }
}
