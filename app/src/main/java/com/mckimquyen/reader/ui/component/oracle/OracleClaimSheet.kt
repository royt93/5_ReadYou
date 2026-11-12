package com.mckimquyen.reader.ui.component.oracle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import com.mckimquyen.reader.ui.page.oracle.OracleClaimCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val ORACLE_SHEET_TAG = "oracle_sheet"
const val ORACLE_QUESTION_TAG = "oracle_question"
const val ORACLE_EXPIRES_TAG = "oracle_expires"
const val ORACLE_CRITERIA_TAG = "oracle_criteria"
const val ORACLE_AI_LABEL_TAG = "oracle_ai_label"
const val ORACLE_CLOSE_TAG = "oracle_close"

private val MIN_TOUCH_TARGET = 48.dp
private const val SCRIM_ALPHA = 0.5f
private const val UTC = "UTC"

/** The claim card (ORACLE-01). Draws nothing for a null [card]; [footer] is where the betting part (ORACLE-02) goes. */
@Composable
fun OracleClaimSheet(
    card: OracleClaimCard?,
    onDismiss: () -> Unit,
    footer: @Composable (OracleClaimCard) -> Unit = {},
) {
    if (card == null) return
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
                Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Surface(
                // Swallows taps so they do not reach the scrim, WITHOUT clickable (clickable would merge the card's children).
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) { OracleClaimSheetContent(card, onDismiss, footer) }
        }
    }
}

/** The card body without the Dialog window, so a plain ComposeView (or a test) can compose it. */
@Composable
fun OracleClaimSheetContent(
    card: OracleClaimCard,
    onDismiss: () -> Unit,
    footer: @Composable (OracleClaimCard) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val date = remember(card.expiresAt, locale) { formatUtcDay(card.expiresAt, locale) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()).testTag(ORACLE_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.oracle_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_CLOSE_TAG)) {
                Text(stringResource(R.string.oracle_sheet_close))
            }
        }
        Text(
            card.question,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(ORACLE_QUESTION_TAG),
        )
        Text(
            stringResource(R.string.oracle_sheet_expires, date),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(ORACLE_EXPIRES_TAG),
        )
        Text(stringResource(R.string.oracle_sheet_criteria_heading), style = MaterialTheme.typography.labelLarge)
        Text(card.resolutionCriteria, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ORACLE_CRITERIA_TAG))
        Text(
            stringResource(R.string.oracle_sheet_ai_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ORACLE_AI_LABEL_TAG),
        )
        footer(card)
    }
}

/** The AI gives a calendar day, stored as the end of that UTC day, so it is shown as that UTC day on every device. */
private fun formatUtcDay(epochMillis: Long, locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = TimeZone.getTimeZone(UTC) }.format(Date(epochMillis))
