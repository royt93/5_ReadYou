package com.mckimquyen.reader.ui.page.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    private val viewModel: ShareTargetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.initFromShareText(text)
        } else {
            finish()
            return
        }

        setContent {
            val useDark = isSystemInDarkTheme()
            AppTheme(useDarkTheme = useDark) {
                ShareTargetScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun ShareTargetScreen(
    viewModel: ShareTargetViewModel,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val invalidUrlMessage = stringResource(R.string.share_target_invalid_url)
    val savedToastMessage = stringResource(R.string.share_target_saved_toast)
    val feedTitle = stringResource(R.string.read_later_feed_title)

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            Toast.makeText(context, invalidUrlMessage, Toast.LENGTH_SHORT).show()
            onFinish()
        }
    }

    val targetUrl = state.targetUrl
    if (targetUrl != null) {
        ShareTargetBottomSheet(
            url = targetUrl,
            isLoading = state.isLoading,
            onSave = {
                viewModel.saveArticle(
                    accountId = context.currentAccountId,
                    defaultTitle = feedTitle,
                    onSaved = {
                        Toast.makeText(context, savedToastMessage, Toast.LENGTH_SHORT).show()
                        onFinish()
                    }
                )
            },
            onCancel = onFinish
        )
    }
}
