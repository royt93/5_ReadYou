package com.mckimquyen.reader.ui.page.home.addsources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.RssSource
import kotlinx.coroutines.launch

// Data class để quản lý trạng thái item RSS
data class RssSourceItemState(
    val source: RssSource,
    val isAdded: Boolean,
    val isLoading: Boolean, // Trạng thái đang loading khi add
    val isEnabled: Boolean
)

// Trang chi tiết thêm nguồn RSS theo quốc gia - Refactored để fix scroll position
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceDetailPage(
    navController: NavHostController,
    countryCode: String, // Mã quốc gia (vi, en, ...)
    viewModel: AddSourcesViewModel = hiltViewModel(),
) {
    // Trạng thái UI và các biến điều khiển
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState() // Trạng thái UI từ ViewModel
    val searchQuery by viewModel.searchQuery.collectAsState() // Search query từ ViewModel
    val scope = rememberCoroutineScope() // Scope cho coroutine
    val snackbarHostState = remember { SnackbarHostState() } // Trạng thái snackbar
    var showConfirmDialog by remember { mutableStateOf(false) } // Hiển thị dialog xác nhận
    var selectedSource by remember { mutableStateOf<RssSource?>(null) } // Nguồn RSS được chọn
    var loadingSourceUrl by remember { mutableStateOf<String?>(null) } // URL đang loading

    // Tải danh sách nguồn RSS theo quốc gia khi component được khởi tạo
    LaunchedEffect(countryCode) {
        viewModel.loadSourcesByCountry(countryCode)
    }

    // Xác định tên hiển thị cho quốc gia
    val countryName = when (countryCode) {
        "en" -> stringResource(R.string.english_sources)
        "vi" -> stringResource(R.string.vietnamese_sources)
        "ja" -> stringResource(R.string.japanese_sources)
        "ko" -> stringResource(R.string.korean_sources)
        "zh" -> stringResource(R.string.chinese_sources)
        "id" -> stringResource(R.string.indonesian_sources)
        "th" -> stringResource(R.string.thai_sources)
        "fr" -> stringResource(R.string.french_sources)
        "de" -> stringResource(R.string.german_sources)
        "es" -> stringResource(R.string.spanish_sources)
        "pt" -> stringResource(R.string.portuguese_sources)
        "ar" -> stringResource(R.string.arabic_sources)
        else -> stringResource(R.string.feeds)
    }

    // Xác định icon cờ quốc gia
    val countryFlag = when (countryCode) {
        "en" -> "🇺🇸"
        "vi" -> "🇻🇳"
        "ja" -> "🇯🇵"
        "ko" -> "🇰🇷"
        "zh" -> "🇨🇳"
        "id" -> "🇮🇩"
        "th" -> "🇹🇭"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        "pt" -> "🇧🇷"
        "ar" -> "🇸🇦"
        else -> "🌍"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = countryFlag,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = countryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                RssSearchField(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onClearClick = { viewModel.clearSearch() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                when {
                    uiState.loading -> {
                        // Hiển thị loading đơn giản
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    uiState.error != null -> {
                        ErrorContent(
                            error = uiState.error ?: "Unknown error",
                            onRetry = { viewModel.loadSourcesByCountry(countryCode) }
                        )
                    }

                    else -> {
                        if (uiState.filteredSources.isEmpty()) {
                            EmptyStateContent(searchQuery = searchQuery)
                        } else {
                            SourcesList(
                                sources = uiState.filteredSources,
                                addedSources = uiState.addedSources,
                                loadingSourceUrl = loadingSourceUrl,
                                onAddSource = { source ->
                                    // Chỉ cho phép add nếu không có item nào đang loading
                                    if (loadingSourceUrl == null) {
                                        selectedSource = source
                                        showConfirmDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Hiển thị dialog xác nhận thêm nguồn RSS
        if (showConfirmDialog && selectedSource != null) {
            AddSourceConfirmationDialog(
                source = selectedSource!!,
                onConfirm = {
                    // Đóng dialog và bắt đầu loading
                    showConfirmDialog = false
                    val sourceToAdd = selectedSource!!
                    loadingSourceUrl = sourceToAdd.link
                    selectedSource = null

                    scope.launch {
                        try {
                            // Gọi API thêm nguồn RSS
                            val success = viewModel.addRssSource(sourceToAdd)
                            if (success) {
                                snackbarHostState.showSnackbar(context.getString(R.string.rss_feed_added_successfully))
                                // UI sẽ tự cập nhật qua StateFlow, không cần reload
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.add_rss_source_failed))
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(context.getString(R.string.add_rss_source_error, e.message ?: ""))
                        } finally {
                            // Tắt trạng thái loading
                            loadingSourceUrl = null
                        }
                    }
                },
                onDismiss = {
                    showConfirmDialog = false
                    selectedSource = null
                }
            )
        }
    }
}

@Composable
private fun EmptyStateContent(searchQuery: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 56.dp)
    ) {
        Icon(
            imageVector = if (searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.Error,
            contentDescription = stringResource(R.string.empty),
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) stringResource(R.string.no_results_found) else stringResource(R.string.no_rss_sources_available),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) {
                stringResource(R.string.try_different_keywords)
            } else {
                stringResource(R.string.no_sources_for_country)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_rss_sources),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearClick) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            },
            singleLine = true
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = stringResource(R.string.oops_something_wrong),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.oops_something_wrong),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = onRetry,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.try_again),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SourcesList(
    sources: List<RssSource>,
    addedSources: List<String>,
    loadingSourceUrl: String?,
    onAddSource: (RssSource) -> Unit,
) {
    // Đơn giản chỉ cần LazyListState bình thường - không cần phức tạp
    val listState = rememberLazyListState()

    // Tạo list item states
    val itemStates = sources.map { source ->
        RssSourceItemState(
            source = source,
            isAdded = addedSources.contains(source.link),
            isLoading = loadingSourceUrl == source.link,
            isEnabled = !addedSources.contains(source.link) && loadingSourceUrl == null
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
    ) {
        items(
            items = itemStates,
            key = { itemState -> itemState.source.link }
        ) { itemState ->
            SourceItem(
                source = itemState.source,
                isAdded = itemState.isAdded,
                isLoading = itemState.isLoading,
                enabled = itemState.isEnabled,
                onAddClick = { onAddSource(itemState.source) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(128.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

// Component hiển thị từng item nguồn RSS
@Composable
private fun SourceItem(
    source: RssSource, // Thông tin nguồn RSS
    isAdded: Boolean, // Đã được thêm hay chưa
    isLoading: Boolean, // Đang loading hay không
    enabled: Boolean = true, // Có cho phép tương tác không
    onAddClick: () -> Unit, // Callback khi nhấn thêm
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(enabled = enabled) { onAddClick() },
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = "RSS Source",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = source.name,
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
                            imageVector = Icons.Rounded.Link,
                            contentDescription = "URL",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = source.link,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Icon thay đổi theo trạng thái
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                isAdded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                isLoading -> MaterialTheme.colorScheme.surfaceVariant
                                enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable(enabled = enabled) { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            // Icon loading
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        isAdded -> {
                            // Icon check
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Already Added",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            // Icon add - màu xám khi disabled
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Source",
                                modifier = Modifier.size(20.dp),
                                tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog xác nhận thêm nguồn RSS
@Composable
private fun AddSourceConfirmationDialog(
    source: RssSource, // Thông tin nguồn RSS cần thêm
    onConfirm: () -> Unit, // Callback khi người dùng xác nhận
    onDismiss: () -> Unit, // Callback khi đóng dialog
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_rss_source),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_source_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = source.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                Text(stringResource(R.string.add_source))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}