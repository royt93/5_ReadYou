package com.mckimquyen.reader.ui.page.home.addsources

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.domain.model.RssSource
import kotlinx.coroutines.launch

// Trang chi tiết thêm nguồn RSS theo quốc gia
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceDetailPage(
    navController: NavHostController,
    countryCode: String, // Mã quốc gia (vi, en, ...)
    viewModel: AddSourcesViewModel = hiltViewModel(),
) {
    // Trạng thái UI và các biến điều khiển
    val uiState by viewModel.uiState.collectAsState() // Trạng thái UI từ ViewModel
    val scope = rememberCoroutineScope() // Scope cho coroutine
    val snackbarHostState = remember { SnackbarHostState() } // Trạng thái snackbar
    var showConfirmDialog by remember { mutableStateOf(false) } // Hiển thị dialog xác nhận
    var selectedSource by remember { mutableStateOf<RssSource?>(null) } // Nguồn RSS được chọn
    var isAddingSource by remember { mutableStateOf(false) } // Trạng thái đang thêm nguồn RSS

    // Tải danh sách nguồn RSS theo quốc gia khi component được khởi tạo
    LaunchedEffect(countryCode) {
        viewModel.loadSourcesByCountry(countryCode)
    }

    // Xác định tên hiển thị cho quốc gia
    val countryName = when (countryCode) {
        "en" -> "English Sources" // Nguồn tiếng Anh
        "vi" -> "Vietnamese Sources" // Nguồn tiếng Việt
        else -> "RSS Sources" // Nguồn khác
    }

    // Xác định icon cờ quốc gia
    val countryFlag = when (countryCode) {
        "en" -> "🇺🇸" // Cờ Mỹ
        "vi" -> "🇻🇳" // Cờ Việt Nam
        else -> "🌍" // Icon thế giới
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
            when {
                uiState.loading -> {
                    LoadingContent()
                }

                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error ?: "Unknown error",
                        onRetry = { viewModel.loadSourcesByCountry(countryCode) }
                    )
                }

                else -> {
                    SourcesList(
                        sources = uiState.rssSources,
                        addedSources = uiState.addedSources,
                        isAddingSource = isAddingSource,
                        onAddSource = { source ->
                            // Chỉ hiển thị popup nếu không đang thêm nguồn RSS nào khác
                            if (!isAddingSource) {
                                selectedSource = source
                                showConfirmDialog = true
                            }
                        }
                    )
                }
            }
        }

        // Hiển thị dialog xác nhận thêm nguồn RSS
        if (showConfirmDialog && selectedSource != null) {
            AddSourceConfirmationDialog(
                source = selectedSource!!,
                onConfirm = {
                    // Đóng dialog ngay lập tức và bật trạng thái loading
                    showConfirmDialog = false
                    isAddingSource = true

                    scope.launch {
                        try {
                            val sourceToAdd = selectedSource!!
                            selectedSource = null

                            // Gọi API thêm nguồn RSS
                            val success = viewModel.addRssSource(sourceToAdd)
                            if (success) {
                                snackbarHostState.showSnackbar("RSS feed added successfully!")
                                // Tải lại danh sách để cập nhật UI
                                viewModel.loadSourcesByCountry(countryCode)
                            } else {
                                snackbarHostState.showSnackbar("Thêm nguồn RSS thất bại")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Lỗi khi thêm nguồn RSS: ${e.message}")
                        } finally {
                            // Tắt trạng thái loading
                            isAddingSource = false
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
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading RSS sources...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
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
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Oops! Something went wrong",
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
                    "Try Again",
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
    isAddingSource: Boolean,
    onAddSource: (RssSource) -> Unit,
) {
    // Sort sources: not added sources first, then added sources at the end
    val sortedSources = sources.sortedBy { source ->
        addedSources.contains(source.link)
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp)
    ) {
        item {
            Text(
                text = "Available RSS Sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Hiển thị loading indicator khi đang thêm nguồn RSS
        if (isAddingSource) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    // Loại bỏ border để không có viền dày
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Adding RSS source...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        items(sortedSources) { source ->
            val isAdded = addedSources.contains(source.link)
            SourceItem(
                source = source,
                isAdded = isAdded,
                enabled = !isAdded && !isAddingSource,
                onAddClick = { onAddSource(source) }
            )
            Spacer(modifier = Modifier.height(12.dp))
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
    enabled: Boolean = true, // Có cho phép tương tác không
    onAddClick: () -> Unit, // Callback khi nhấn thêm
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onAddClick() },
        shape = RoundedCornerShape(16.dp), // Sử dụng shape thay vì clip để giữ border
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        border = BorderStroke(
            width = 1.dp, // Tăng độ dày border để dễ thấy
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAdded -> MaterialTheme.colorScheme.surface
                enabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface
            }
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
                        fontWeight = if (isAdded) FontWeight.Medium else FontWeight.SemiBold,
                        color = when {
                            isAdded -> MaterialTheme.colorScheme.onPrimaryContainer
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
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
                            tint = when {
                                isAdded -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = source.link,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isAdded -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                isAdded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable(enabled = !isAdded) { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isAdded) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Already Added",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Source",
                            modifier = Modifier.size(20.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                text = "Add RSS Source",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Do you want to add this RSS source to your feeds?",
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
                Text("Add Source")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}