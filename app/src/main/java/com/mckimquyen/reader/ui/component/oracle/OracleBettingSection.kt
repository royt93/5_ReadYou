package com.mckimquyen.reader.ui.component.oracle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import com.mckimquyen.reader.domain.oracle.OracleEvidencePolicy
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import com.mckimquyen.reader.ui.page.oracle.OracleEvidenceUiState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

const val ORACLE_BET_SECTION_TAG = "oracle_bet_section"
const val ORACLE_BET_BALANCE_TAG = "oracle_bet_balance"
const val ORACLE_BET_SIDE_YES_TAG = "oracle_bet_side_yes"
const val ORACLE_BET_SIDE_NO_TAG = "oracle_bet_side_no"
const val ORACLE_BET_STAKE_TAG_PREFIX = "oracle_bet_stake_"
const val ORACLE_BET_PLACE_TAG = "oracle_bet_place"
const val ORACLE_BET_OUT_OF_POINTS_TAG = "oracle_bet_out_of_points"
const val ORACLE_BET_OPEN_TAG = "oracle_bet_open"
const val ORACLE_BET_RESULT_YES_TAG = "oracle_bet_result_yes"
const val ORACLE_BET_RESULT_NO_TAG = "oracle_bet_result_no"
const val ORACLE_BET_RESULT_UNCLEAR_TAG = "oracle_bet_result_unclear"
const val ORACLE_BET_SETTLED_TAG = "oracle_bet_settled"
const val ORACLE_BET_CLOSED_TAG = "oracle_bet_closed"
const val ORACLE_BET_ERROR_TAG = "oracle_bet_error"
const val ORACLE_BET_NOTE_TAG = "oracle_bet_note"

const val ORACLE_EVIDENCE_SECTION_TAG = "oracle_evidence_section"
const val ORACLE_EVIDENCE_ITEM_TAG_PREFIX = "oracle_evidence_item_"
const val ORACLE_EVIDENCE_NONE_TAG = "oracle_evidence_none"
const val ORACLE_EVIDENCE_LOADING_TAG = "oracle_evidence_loading"
const val ORACLE_EVIDENCE_HINT_TAG = "oracle_evidence_hint"

private val MIN_TOUCH_TARGET = 48.dp

/** The betting part of the prediction card (ORACLE-02). Virtual points only; there is no control that leaves the app. */
@Composable
fun OracleBettingSection(
    state: OracleBetUiState,
    failed: Boolean,
    onPlaceBet: (OracleBetSide, Int) -> Unit,
    onSettle: (OracleOutcome) -> Unit,
    evidence: OracleEvidenceUiState = OracleEvidenceUiState.Idle,
    onOpenArticle: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(Modifier.fillMaxWidth().testTag(ORACLE_BET_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.oracle_bet_balance, number.format(state.balance)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(ORACLE_BET_BALANCE_TAG),
        )
        when (state.phase) {
            OracleBetPhase.CAN_BET -> PlaceBetControls(state.balance, number, onPlaceBet)
            OracleBetPhase.OUT_OF_POINTS -> Text(
                stringResource(R.string.oracle_bet_out_of_points),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ORACLE_BET_OUT_OF_POINTS_TAG),
            )
            OracleBetPhase.BET_OPEN -> state.bet?.let { bet ->
                Text(
                    stringResource(R.string.oracle_bet_open, number.format(bet.stake), sideName(bet.side)),
                    modifier = Modifier.testTag(ORACLE_BET_OPEN_TAG),
                )
                Text(stringResource(R.string.oracle_bet_open_hint), style = MaterialTheme.typography.bodySmall)
            }
            OracleBetPhase.AWAITING_RESULT -> state.bet?.let { bet ->
                Text(stringResource(R.string.oracle_bet_awaiting_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.oracle_bet_awaiting_hint, number.format(bet.stake), sideName(bet.side)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(ORACLE_BET_OPEN_TAG),
                )
                EvidenceSection(evidence, locale, onOpenArticle)
                ResultButtons(onSettle)
            }
            OracleBetPhase.SETTLED -> state.bet?.let { bet ->
                val res = when (bet.status) {
                    OracleBetStatus.WON -> R.string.oracle_bet_settled_won
                    OracleBetStatus.LOST -> R.string.oracle_bet_settled_lost
                    else -> R.string.oracle_bet_settled_refunded
                }
                Text(
                    stringResource(res, number.format(bet.stake)),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(ORACLE_BET_SETTLED_TAG),
                )
            }
            OracleBetPhase.CLOSED_NO_BET -> Text(
                stringResource(R.string.oracle_bet_closed_no_bet),
                modifier = Modifier.testTag(ORACLE_BET_CLOSED_TAG),
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.oracle_bet_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ORACLE_BET_ERROR_TAG),
            )
        }
        Text(
            stringResource(R.string.oracle_bet_virtual_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ORACLE_BET_NOTE_TAG),
        )
    }
}

@Composable
private fun sideName(side: OracleBetSide): String =
    stringResource(if (side == OracleBetSide.YES) R.string.oracle_bet_yes else R.string.oracle_bet_no)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceBetControls(balance: Long, number: NumberFormat, onPlaceBet: (OracleBetSide, Int) -> Unit) {
    var side by remember { mutableStateOf<OracleBetSide?>(null) }
    var stake by remember { mutableStateOf<Int?>(null) }
    Text(stringResource(R.string.oracle_bet_pick_side), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SideChip(OracleBetSide.YES, side == OracleBetSide.YES, ORACLE_BET_SIDE_YES_TAG) { side = OracleBetSide.YES }
        SideChip(OracleBetSide.NO, side == OracleBetSide.NO, ORACLE_BET_SIDE_NO_TAG) { side = OracleBetSide.NO }
    }
    Text(stringResource(R.string.oracle_bet_pick_stake), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OracleBettingPolicy.STAKES.forEach { value ->
            val label = stringResource(R.string.oracle_bet_stake_option, number.format(value))
            FilterChip(
                selected = stake == value,
                enabled = balance >= value,
                onClick = { stake = value },
                label = { Text(label) },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = label }
                    .testTag(ORACLE_BET_STAKE_TAG_PREFIX + value),
            )
        }
    }
    val chosenSide = side
    val chosenStake = stake
    val ready = chosenSide != null && chosenStake != null && balance >= chosenStake
    Button(
        onClick = { if (chosenSide != null && chosenStake != null) onPlaceBet(chosenSide, chosenStake) },
        enabled = ready,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_PLACE_TAG),
    ) {
        Text(
            if (chosenSide != null && chosenStake != null) {
                stringResource(R.string.oracle_bet_place_summary, number.format(chosenStake), sideName(chosenSide))
            } else {
                stringResource(R.string.oracle_bet_place)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideChip(side: OracleBetSide, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(sideName(side)) },
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(tag),
    )
}

@Composable
private fun ResultButtons(onSettle: (OracleOutcome) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onSettle(OracleOutcome.YES) },
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_YES_TAG),
        ) { Text(stringResource(R.string.oracle_bet_yes)) }
        OutlinedButton(
            onClick = { onSettle(OracleOutcome.NO) },
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_NO_TAG),
        ) { Text(stringResource(R.string.oracle_bet_no)) }
        OutlinedButton(
            onClick = { onSettle(OracleOutcome.UNCLEAR) },
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_UNCLEAR_TAG),
        ) { Text(stringResource(R.string.oracle_bet_outcome_unclear)) }
    }
}

/** Hints from the user's own saved news; they can be wrong, so the user still decides (ORACLE-03). */
@Composable
private fun EvidenceSection(evidence: OracleEvidenceUiState, locale: Locale, onOpenArticle: (String) -> Unit) {
    if (evidence == OracleEvidenceUiState.Idle) return
    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    Column(Modifier.fillMaxWidth().testTag(ORACLE_EVIDENCE_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.oracle_due_evidence_title), style = MaterialTheme.typography.titleSmall)
        when (evidence) {
            OracleEvidenceUiState.Loading -> CircularProgressIndicator(Modifier.size(20.dp).testTag(ORACLE_EVIDENCE_LOADING_TAG))
            is OracleEvidenceUiState.Ready -> if (evidence.items.isEmpty()) {
                Text(stringResource(R.string.oracle_due_evidence_none), modifier = Modifier.testTag(ORACLE_EVIDENCE_NONE_TAG))
            } else {
                evidence.items.take(OracleEvidencePolicy.MAX_EVIDENCE).forEach { item ->
                    val line = "${item.title}\n${item.feedName} · ${dateFormat.format(Date(item.dateMillis))}"
                    TextButton(
                        onClick = { onOpenArticle(item.articleId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .semantics { contentDescription = item.title }
                            .testTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + item.articleId),
                    ) { Text(line, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall) }
                }
            }
            OracleEvidenceUiState.Idle -> Unit
        }
        Text(
            stringResource(R.string.oracle_due_evidence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ORACLE_EVIDENCE_HINT_TAG),
        )
    }
}
