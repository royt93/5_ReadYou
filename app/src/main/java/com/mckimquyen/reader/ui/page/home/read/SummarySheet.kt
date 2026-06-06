package com.mckimquyen.reader.ui.page.home.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

private const val GEMINI_API_KEY_URL = "https://aistudio.google.com/app/apikey"

/**
 * Nội dung BottomSheet "Tóm tắt bằng AI", dùng làm sheetContent cho
 * [com.mckimquyen.reader.ui.component.base.BottomDrawer] (ModalBottomSheetLayout của material).
 *
 * Không tự dựng Dialog/Surface nữa: BottomDrawer đã cung cấp drag handle, shape, scrim và xử
 * lý system insets đúng — tránh lỗi edge-to-edge của Dialog tự chế trước đây.
 */
@Composable
fun SummarySheetContent(
    state: SummaryState,
    onRetry: () -> Unit,
    onSaveKey: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 8.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(16.dp))

        when (state) {
            SummaryState.Idle,
            SummaryState.Loading -> LoadingContent()

            SummaryState.NeedApiKey -> ApiKeyContent(onSaveKey = onSaveKey)

            is SummaryState.Success -> SuccessContent(text = state.text, onRetry = onRetry)

            is SummaryState.Error -> ErrorContent(
                message = if (state.arg != null) {
                    stringResource(state.messageRes, state.arg)
                } else {
                    stringResource(state.messageRes)
                },
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.summary_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessContent(text: String, onRetry: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    )
    Spacer(Modifier.size(16.dp))
    OutlinedButton(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.summary_retry))
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(16.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.summary_try_again))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyContent(onSaveKey: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var key by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.summary_apikey_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(12.dp))
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(R.string.summary_apikey_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(12.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                android.util.Log.d("roy93~AI", "SummarySheet: 'get key' clicked -> open $GEMINI_API_KEY_URL")
                uriHandler.openUri(GEMINI_API_KEY_URL)
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.summary_get_key))
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = {
                android.util.Log.d("roy93~AI", "SummarySheet: 'save & summarize' clicked (keyLen=${key.trim().length})")
                onSaveKey(key.trim())
            },
            enabled = key.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.summary_save_and_summarize))
        }
    }
}
