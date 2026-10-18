package com.mckimquyen.reader.ui.page.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSmartCollectionDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSave: (name: String, query: String) -> Unit,
    initialName: String = initialQuery.trim().take(MAX_SUGGESTED_NAME_LENGTH),
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val isValid = name.isNotBlank() && query.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smart_collection_save_title)) },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag(TEST_TAG_NAME),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.smart_collection_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(TEST_TAG_QUERY),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.smart_collection_query)) },
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TEST_TAG_SAVE),
                enabled = isValid,
                onClick = { if (isValid) onSave(name.trim(), query.trim()) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

internal const val MAX_SUGGESTED_NAME_LENGTH = 40
internal const val TEST_TAG_NAME = "smart_collection_name"
internal const val TEST_TAG_QUERY = "smart_collection_query"
internal const val TEST_TAG_SAVE = "smart_collection_save"
