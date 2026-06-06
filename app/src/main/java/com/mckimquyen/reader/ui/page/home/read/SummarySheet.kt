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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

/**
 * Nội dung BottomSheet "Tóm tắt bằng AI", dùng làm sheetContent cho
 * [com.mckimquyen.reader.ui.component.base.BottomDrawer] (ModalBottomSheetLayout của material).
 *
 * Không có ô nhập API key: key Gemini do dev nhúng sẵn (BuildConfig.GEMINI_API_KEY) và nút ✨ chỉ
 * hiện khi key đã được cấu hình, nên người dùng phổ thông không cần biết tới API key.
 */
@Composable
fun SummarySheetContent(
    state: SummaryState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
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
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(16.dp))

        when (state) {
            SummaryState.Idle,
            SummaryState.Loading -> LoadingContent()

            is SummaryState.Success -> SuccessContent(text = state.text)

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
private fun SuccessContent(text: String) {
    // Không có nút "Tóm tắt lại" để tránh gọi lại API (tốn thêm 1 lượt). Đóng sheet bằng nút ✕.
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    )
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
