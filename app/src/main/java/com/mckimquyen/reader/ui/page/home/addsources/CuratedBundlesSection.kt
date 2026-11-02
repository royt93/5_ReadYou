package com.mckimquyen.reader.ui.page.home.addsources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.curated.BundleSubscribeResult
import com.mckimquyen.reader.domain.curated.CuratedBundle

const val CURATED_BUNDLES_SECTION_TAG = "curated_bundles_section"
const val CURATED_BUNDLE_HEADER_TAG_PREFIX = "curated_bundle_header_"
const val CURATED_BUNDLE_VERIFIED_TAG_PREFIX = "curated_bundle_verified_"
const val CURATED_BUNDLE_SOURCE_TAG_PREFIX = "curated_bundle_source_"
const val CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX = "curated_bundle_subscribe_"
const val CURATED_BUNDLE_LOCAL_ONLY_TAG = "curated_bundle_local_only"
const val CURATED_BUNDLE_DISMISS_TAG = "curated_bundle_dismiss"

private val MIN_TOUCH_TARGET = 48.dp
private val CARD_GAP = 12.dp
private val INNER_GAP = 8.dp

/**
 * The "Curated bundles" part of Add Sources. Browsing is offline (static asset); expanding a bundle only reveals its
 * source names. The subscribe button exists only when [AddSourcesUiState.canSubscribeBundles] (Local account).
 */
@Composable
fun CuratedBundlesSection(
    state: AddSourcesUiState,
    language: String,
    onSubscribe: (CuratedBundle) -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.bundles.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().testTag(CURATED_BUNDLES_SECTION_TAG)) {
        Text(
            text = stringResource(R.string.curated_bundles_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(INNER_GAP))
        if (!state.canSubscribeBundles) {
            Text(
                text = stringResource(R.string.curated_bundle_account_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(CURATED_BUNDLE_LOCAL_ONLY_TAG),
            )
            Spacer(Modifier.height(INNER_GAP))
        }
        BundleOutcome(state = state, onDismiss = onDismissResult)
        state.bundles.forEach { bundle ->
            BundleCard(bundle = bundle, language = language, state = state, onSubscribe = onSubscribe)
            Spacer(Modifier.height(CARD_GAP))
        }
    }
}

@Composable
private fun BundleOutcome(state: AddSourcesUiState, onDismiss: () -> Unit) {
    val result = state.bundleResult
    val error = state.bundleError
    if (result == null && error == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = CARD_GAP),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (result != null) {
                Text(text = resultText(result), style = MaterialTheme.typography.bodyMedium)
                if (result.added == 0) {
                    Text(
                        text = stringResource(R.string.curated_bundle_no_sources_added),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (error != null) {
                Text(
                    text = stringResource(R.string.curated_bundle_error, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(CURATED_BUNDLE_DISMISS_TAG),
            ) { Text(stringResource(android.R.string.ok)) }
        }
    }
}

@Composable
private fun resultText(result: BundleSubscribeResult): String =
    stringResource(R.string.curated_bundle_result, result.added, result.skippedExisting, result.failed)

@Composable
private fun BundleCard(
    bundle: CuratedBundle,
    language: String,
    state: AddSourcesUiState,
    onSubscribe: (CuratedBundle) -> Unit,
) {
    var expanded by rememberSaveable(bundle.id) { mutableStateOf(false) }
    val busy = state.subscribingBundleId == bundle.id
    val anyBusy = state.subscribingBundleId != null
    val title = bundle.localizedTitle(language)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = title }
                    .testTag(CURATED_BUNDLE_HEADER_TAG_PREFIX + bundle.id),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(INNER_GAP), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.curated_bundle_source_count, bundle.sources.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.curated_bundle_verified_on, bundle.verifiedOn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(CURATED_BUNDLE_VERIFIED_TAG_PREFIX + bundle.id),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(INNER_GAP))
                bundle.sources.forEach { source ->
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp).testTag(CURATED_BUNDLE_SOURCE_TAG_PREFIX + bundle.id),
                    )
                }
            }
            if (busy) {
                val progress = state.bundleProgress
                Text(
                    text = if (progress != null) {
                        stringResource(R.string.curated_bundle_progress, progress.first, progress.second)
                    } else {
                        stringResource(R.string.curated_bundle_subscribing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.canSubscribeBundles) {
                Spacer(Modifier.height(INNER_GAP))
                Button(
                    onClick = { onSubscribe(bundle) },
                    enabled = !anyBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .testTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + bundle.id),
                ) { Text(stringResource(R.string.curated_bundle_subscribe_all)) }
            }
        }
    }
}
