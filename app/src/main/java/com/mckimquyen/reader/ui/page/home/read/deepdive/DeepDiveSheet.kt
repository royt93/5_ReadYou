package com.mckimquyen.reader.ui.page.home.read.deepdive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import com.mckimquyen.reader.ui.page.rpg.LocalOpenPrivacySettings

const val DEEP_DIVE_SHEET_TAG = "deep_dive_sheet"
const val DEEP_DIVE_CONSENT_TAG = "deep_dive_consent"
const val DEEP_DIVE_AGREE_TAG = "deep_dive_agree"
const val DEEP_DIVE_LATER_TAG = "deep_dive_later"
const val DEEP_DIVE_DECLINED_TAG = "deep_dive_declined"
const val DEEP_DIVE_OPEN_PRIVACY_TAG = "deep_dive_open_privacy"
const val DEEP_DIVE_LOADING_TAG = "deep_dive_loading"
const val DEEP_DIVE_FAILED_TAG = "deep_dive_failed"
const val DEEP_DIVE_RETRY_TAG = "deep_dive_retry"
const val DEEP_DIVE_REPORT_TAG = "deep_dive_report"
const val DEEP_DIVE_MATRIX_TAG = "deep_dive_matrix"
const val DEEP_DIVE_EXPORT_TAG = "deep_dive_export"

private val MIN_TOUCH_TARGET = 48.dp
private const val SHEET_HEIGHT_FRACTION = 0.85f
private const val SCRIM_ALPHA = 0.5f

/** The Deep Dive report sheet (FRONT-01). Draws nothing for [DeepDiveUiState.Idle]. */
@Composable
fun DeepDiveSheet(
    state: DeepDiveUiState,
    title: String,
    onDismiss: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onConsentLater: () -> Unit,
    onRetry: () -> Unit,
    onExportPdf: () -> Unit,
) {
    if (state == DeepDiveUiState.Idle) return
    SheetDialog(onDismiss) {
        DeepDiveSheetContent(state, title, onDismiss, onConsent, onConsentLater, onRetry, onExportPdf)
    }
}

/**
 * The sheet body without the Dialog window. Split out because a Dialog is a separate window, which a device test that
 * walks the Activity's semantics tree cannot see; the device test composes this body in a plain ComposeView.
 */
@Composable
fun DeepDiveSheetContent(
    state: DeepDiveUiState,
    title: String,
    onDismiss: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onConsentLater: () -> Unit,
    onRetry: () -> Unit,
    onExportPdf: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp).testTag(DEEP_DIVE_SHEET_TAG)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.deep_dive_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.deep_dive_close))
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            DeepDiveUiState.Idle -> Unit
            DeepDiveUiState.AwaitingConsent -> ConsentPart(onConsent, onConsentLater)
            DeepDiveUiState.ConsentDeclined -> DeclinedPart()
            DeepDiveUiState.Loading -> LoadingPart()
            is DeepDiveUiState.Failed -> FailedPart(state.reason, onRetry)
            is DeepDiveUiState.Ready -> ReportPart(state.report, title, onExportPdf)
        }
    }
}

@Composable
private fun ConsentPart(onConsent: (Boolean) -> Unit, onLater: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_CONSENT_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.deep_dive_consent_message), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { onConsent(true) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_AGREE_TAG),
        ) { Text(stringResource(R.string.deep_dive_consent_agree)) }
        OutlinedButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_LATER_TAG),
        ) { Text(stringResource(R.string.deep_dive_consent_later)) }
    }
}

@Composable
private fun DeclinedPart() {
    val openPrivacy = LocalOpenPrivacySettings.current
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_DECLINED_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.deep_dive_declined_message), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = openPrivacy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_OPEN_PRIVACY_TAG),
        ) { Text(stringResource(R.string.deep_dive_open_privacy)) }
    }
}

@Composable
private fun LoadingPart() {
    Column(
        Modifier.fillMaxSize().testTag(DEEP_DIVE_LOADING_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.deep_dive_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FailedPart(reason: AiDeepDiveFailure, onRetry: () -> Unit) {
    val message = when (reason) {
        AiDeepDiveFailure.NO_KEY -> R.string.deep_dive_no_key
        AiDeepDiveFailure.NOTHING_TO_ANALYZE -> R.string.deep_dive_nothing_to_analyze
        AiDeepDiveFailure.NETWORK, AiDeepDiveFailure.REJECTED -> R.string.deep_dive_failed
    }
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_FAILED_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        if (reason != AiDeepDiveFailure.NOTHING_TO_ANALYZE) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_RETRY_TAG),
            ) { Text(stringResource(R.string.deep_dive_retry)) }
        }
    }
}

@Composable
private fun ReportPart(report: DeepDiveReport, title: String, onExportPdf: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(DEEP_DIVE_REPORT_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.deep_dive_label), style = MaterialTheme.typography.labelMedium)
        Divider()
        Section(stringResource(R.string.deep_dive_section_context))
        report.context.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Section(stringResource(R.string.deep_dive_section_critical))
        report.perspectives.forEach {
            Text("• ${it.school}: ${it.view}", style = MaterialTheme.typography.bodyMedium)
        }
        Section(stringResource(R.string.deep_dive_section_impact))
        Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_MATRIX_TAG), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MatrixRow(
                stringResource(R.string.deep_dive_col_area),
                stringResource(R.string.deep_dive_col_short),
                stringResource(R.string.deep_dive_col_long),
                bold = true,
            )
            report.impacts.forEach { MatrixRow(it.area, it.shortTerm, it.longTerm, bold = false) }
        }
        Button(
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_EXPORT_TAG),
        ) { Text(stringResource(R.string.deep_dive_export_pdf)) }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MatrixRow(area: String, short: String, long: String, bold: Boolean) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(area, short, long).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = weight, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        DialogEdgeToEdge()
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                    // Swallows taps so they do not reach the scrim, WITHOUT clickable (clickable would merge every
                    // descendant into one semantics node and hide the sheet's children from tests and TalkBack).
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) { content() }
        }
    }
}
