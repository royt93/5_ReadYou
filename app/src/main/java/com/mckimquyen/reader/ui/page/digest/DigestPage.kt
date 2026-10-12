package com.mckimquyen.reader.ui.page.digest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent

@Composable
fun DigestPage(
    state: DigestUiState,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
    onSelectDigest: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.daily_digest_title), style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(12.dp))
        when (state) {
            DigestUiState.Loading -> CircularProgressIndicator()
            DigestUiState.Empty -> {
                Text(stringResource(R.string.daily_digest_empty))
                Button(onClick = onGenerate) { Text(stringResource(R.string.daily_digest_generate)) }
            }
            DigestUiState.Error -> {
                Text(stringResource(R.string.daily_digest_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.daily_digest_generate)) }
            }
            is DigestUiState.Success -> {
                Text(stringResource(R.string.daily_digest_history), style = MaterialTheme.typography.subtitle1)
                LazyColumn(modifier = Modifier.height(96.dp)) {
                    items(state.history, key = { it.id }) { digest ->
                        Text(
                            digest.headline,
                            modifier = Modifier.fillMaxWidth().clickable { onSelectDigest(digest.id) }.padding(vertical = 6.dp),
                            color = if (digest.id == state.digest.id) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                        )
                    }
                }
                Text(state.digest.headline, style = MaterialTheme.typography.h6)
                val content = state.content
                LazyColumn {
                    content.sections.forEach { section ->
                        item(key = "section-${section.title}") {
                            Text(section.title, style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 12.dp))
                        }
                        items(section.items) { item ->
                            Column {
                                Text(item.text, modifier = Modifier.padding(vertical = 6.dp))
                                item.sourceIds.forEach { id ->
                                    val source = state.sources.firstOrNull { it.id == id } ?: return@forEach
                                    Text(
                                        source.title,
                                        modifier = Modifier.fillMaxWidth().clickable { onOpenSource(source.id) }.padding(vertical = 4.dp),
                                        color = MaterialTheme.colors.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Button(onClick = onGenerate) { Text(stringResource(R.string.daily_digest_generate)) }
            }
        }
    }
}

data class DigestSourceUi(val id: String, val title: String, val link: String)

sealed interface DigestUiState {
    data object Loading : DigestUiState
    data object Empty : DigestUiState
    data object Error : DigestUiState
    data class Success(
        val digest: DailyDigest,
        val content: DailyDigestContent,
        val sources: List<DigestSourceUi>,
        val history: List<DailyDigest> = listOf(digest),
    ) : DigestUiState
}
