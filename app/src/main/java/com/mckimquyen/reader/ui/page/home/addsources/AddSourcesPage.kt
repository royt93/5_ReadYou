package com.mckimquyen.reader.ui.page.home.addsources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.common.RouteName

@Preview
@Composable
fun AddSourcesPage(
    navController: NavHostController
) {
    CountriesList(navController = navController)
}

@Composable
fun AddSourcesMainPage(navController: NavHostController) {
    CountriesList(navController = navController)
}

@Composable
fun CountriesList(
    navController: NavHostController,
    viewModel: AddSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadBundles() }
    val countries = listOf(
        CountryItem("🇺🇸", stringResource(R.string.english_sources), "en"),
        CountryItem("🇻🇳", stringResource(R.string.vietnamese_sources), "vi"),
        CountryItem("🇯🇵", stringResource(R.string.japanese_sources), "ja"),
        CountryItem("🇰🇷", stringResource(R.string.korean_sources), "ko"),
        CountryItem("🇨🇳", stringResource(R.string.chinese_sources), "zh"),
        CountryItem("🇮🇩", stringResource(R.string.indonesian_sources), "id"),
        CountryItem("🇹🇭", stringResource(R.string.thai_sources), "th"),
        CountryItem("🇫🇷", stringResource(R.string.french_sources), "fr"),
        CountryItem("🇩🇪", stringResource(R.string.german_sources), "de"),
        CountryItem("🇪🇸", stringResource(R.string.spanish_sources), "es"),
        CountryItem("🇧🇷", stringResource(R.string.portuguese_sources), "pt"),
        CountryItem("🇸🇦", stringResource(R.string.arabic_sources), "ar")
    )
    AddSourcesList(
        state = state,
        countries = countries,
        language = Locale.getDefault().language,
        onSubscribe = viewModel::subscribeBundle,
        onDismissResult = viewModel::dismissBundleResult,
        onCountryClick = { country ->
            navController.navigate("${RouteName.ADD_SOURCES_DETAIL}/${country.code}") {
                launchSingleTop = true
            }
        },
    )
}

/**
 * The list itself, free of Hilt/navigation so it can be tested. The bundles load a moment after the page opens; the
 * first item is therefore keyed and, when it appears, the list is scrolled back to the top. Without this the first
 * visible item stays the first country card (the bundles item was empty, so Lazy skipped it) and the bundles end up
 * above the viewport where nobody sees them.
 */
@Composable
fun AddSourcesList(
    state: AddSourcesUiState,
    countries: List<CountryItem>,
    language: String,
    onSubscribe: (com.mckimquyen.reader.domain.curated.CuratedBundle) -> Unit,
    onDismissResult: () -> Unit,
    onCountryClick: (CountryItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val hasBundles = state.bundles.isNotEmpty()
    LaunchedEffect(hasBundles) {
        if (hasBundles) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
    ) {
        item(key = BUNDLES_ITEM_KEY) {
            CuratedBundlesSection(
                state = state,
                language = language,
                onSubscribe = onSubscribe,
                onDismissResult = onDismissResult,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        items(countries, key = { it.code }) { country ->
            CountryCard(country = country, onClick = { onCountryClick(country) })
        }
        item(key = BOTTOM_SPACER_KEY) {
            Spacer(modifier = Modifier.height(128.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private const val BUNDLES_ITEM_KEY = "curated_bundles"
private const val BOTTOM_SPACER_KEY = "bottom_spacer"

@Composable
private fun CountryCard(
    country: CountryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Box icon tương tự RSS item
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = country.flag,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Content column tương tự RSS item
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = country.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Country",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.explore_rss_sources),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action icon tương tự RSS item
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Navigate",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

data class CountryItem(
    val flag: String,
    val name: String,
    val code: String
)