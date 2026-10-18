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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCollectionsPage(
    navController: NavHostController,
    viewModel: SmartCollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collectionsWithCount.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCollection by remember { mutableStateOf<SmartCollectionWithCount?>(null) }

    Scaffold(
        topBar = {
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
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.smart_collections_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.smart_collection_save_action))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.smart_collection_save_action))
            }
        },
    ) { innerPadding ->
        if (collections.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp).testTag(TEST_TAG_EMPTY),
                    text = stringResource(R.string.smart_collection_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(collections, key = { it.collection.id }) { collection ->
                    SmartCollectionCard(
                        collection = collection,
                        onClick = {
                            navController.navigate(
                                "${com.mckimquyen.reader.ui.page.common.RouteName.SMART_COLLECTIONS}/${collection.collection.id}"
                            )
                        },
                        onEdit = { editingCollection = collection },
                        onDelete = { viewModel.deleteCollection(collection.collection.id) },
                    )
                }
                item { Spacer(Modifier.size(16.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        SaveSmartCollectionDialog(
            initialQuery = "",
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onSave = { name, query ->
                viewModel.createCollection(name, query) {
                    showCreateDialog = false
                    android.widget.Toast.makeText(context, context.getString(R.string.smart_collection_saved), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    editingCollection?.let { item ->
        SaveSmartCollectionDialog(
            initialQuery = item.collection.query,
            initialName = item.collection.name,
            onDismiss = { editingCollection = null },
            onSave = { name, query ->
                viewModel.updateCollection(item.collection.id, name, query) { editingCollection = null }
            },
        )
    }
}

@Composable
private fun SmartCollectionCard(
    collection: SmartCollectionWithCount,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)
            .testTag("smart_collection_${collection.collection.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = collection.collection.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.smart_collection_edit)) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.smart_collection_delete)) },
                        onClick = { menuExpanded = false; confirmDelete = true },
                    )
                }
            }
            Text(
                text = collection.collection.query,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = stringResource(R.string.smart_collection_articles_count, collection.articleCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.smart_collection_delete)) },
            text = { Text(stringResource(R.string.smart_collection_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.smart_collection_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private const val TEST_TAG_EMPTY = "smart_collections_empty"
