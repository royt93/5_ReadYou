package com.mckimquyen.reader.ui.page.setting.vip

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.ext.findActivity
import com.roy.sdkadbmob.AdManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VipGold = Color(0xFFFFD60A)

private fun formatDate(ms: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipManagementPage(navController: NavHostController) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val vipPrefs = remember { VipPrefs(context) }

    // refreshTrigger++ buộc đọc lại trạng thái VIP từ lib sau redeem/revoke.
    var refreshTrigger by remember { mutableStateOf(0) }
    val isActive = remember(refreshTrigger) { AdManager.isVipByKeyActive() }
    val expiryMs = remember(refreshTrigger) { AdManager.getVipByKeyExpiry() }
    val grantedAtMs = remember(refreshTrigger) { vipPrefs.getGrantedAtMs() }
    val userRedeemed = remember(refreshTrigger) { vipPrefs.userRedeemedAtLeastOnce() }

    // Clock tick mỗi giây khi VIP active (countdown + progress).
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isActive, expiryMs) {
        while (isActive && System.currentTimeMillis() < expiryMs) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
        nowMs = System.currentTimeMillis()
        // VIP vừa hết hạn khi đang mở màn → recompute để UI lật về Free, ẩn countdown.
        if (isActive && System.currentTimeMillis() >= expiryMs) refreshTrigger++
    }

    // Preload rewarded để nút "Watch ad" sẵn sàng.
    LaunchedEffect(Unit) { activity?.let { AdManager.loadRewarded(it) } }

    var inputKey by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<VipDialog?>(null) }
    var confettiPop by remember { mutableStateOf(false) }

    fun refresh() { refreshTrigger++ }

    fun celebrate() {
        confettiPop = true
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun onGranted(grantedNowMs: Long) {
        vipPrefs.saveGrantedAtMs(grantedNowMs)
        vipPrefs.markUserRedeemed()
        celebrate()
        refresh()
    }

    fun grantRewarded() {
        val ok = AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, VipKeys.REWARDED_DAYS)
        if (ok) onGranted(System.currentTimeMillis())
    }

    fun redeem() {
        val days = VipKeys.lookupDays(inputKey)
        if (days == null) {
            dialog = VipDialog.Failed(context.getString(R.string.vip_failed_invalid_key))
            return
        }
        val ok = AdManager.activateVipByKey(context, AdKeys.VIP_SECRET, days)
        if (ok) {
            inputKey = ""
            onGranted(System.currentTimeMillis())
            dialog = VipDialog.Success(days)
        } else {
            dialog = VipDialog.Failed(context.getString(R.string.vip_failed_invalid_key))
        }
    }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            ) { navController.popBackStack() }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // (1) Hero card — entry slide/fade in.
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(600)) + expandVertically(tween(600)),
                ) {
                    VipHeroCard(isActive = isActive, expiryMs = expiryMs, confettiPop = confettiPop)
                }

                if (isActive) {
                    // Grace entry (auto-trial) không được app lưu grantedAt → fallback expiry - 24h
                    // để activation date + progress bar không bị sai (1970 / ~100%).
                    val effectiveGrantedAt =
                        if (grantedAtMs > 0) grantedAtMs
                        else (expiryMs - 24L * 60L * 60L * 1000L).coerceAtLeast(0L)

                    // (2) Activation date + (3) Expiry date
                    VipInfoRow(stringResource(R.string.vip_activated_at, formatDate(effectiveGrantedAt)))
                    VipInfoRow(stringResource(R.string.vip_expires_at, formatDate(expiryMs)))

                    // (4) Progress bar (elapsed semantic)
                    val progress = VipMath.computeElapsedProgress(effectiveGrantedAt, expiryMs, nowMs)
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = VipGold,
                    )

                    // (5) Countdown
                    Text(
                        text = VipMath.formatCountdown((expiryMs - nowMs).coerceAtLeast(0L)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // (12) Active VIP card (single-entry)
                    VipActiveCard(
                        isGrace = !userRedeemed,
                        expiryMs = expiryMs,
                        nowMs = nowMs,
                        onRevoke = { dialog = VipDialog.ConfirmRevoke },
                    )
                }

                // (6) Nhập key VIP
                OutlinedTextField(
                    value = inputKey,
                    // KHÔNG uppercase/capitalize: VIP key là mixed-case (vd 9fA0q7eN!...),
                    // ép hoa sẽ khiến VipKeys.lookupDays không bao giờ khớp.
                    onValueChange = { inputKey = it },
                    label = { Text(stringResource(R.string.vip_redeem_label)) },
                    placeholder = { Text(stringResource(R.string.vip_redeem_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { redeem() },
                    enabled = inputKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.vip_redeem_button)) }

                // (7) Watch ad → 3 days VIP (pulse animation #1)
                val pulse = rememberInfiniteTransition(label = "pulse")
                val scale by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                    label = "pulseScale",
                )
                Button(
                    onClick = {
                        val act = activity ?: return@Button
                        AdManager.showRewarded(act) { earned ->
                            if (earned) {
                                // Chỉ grant khi user xem HẾT rewarded.
                                grantRewarded()
                            } else {
                                // Rewarded chưa sẵn / đóng sớm → vẫn tận dụng interstitial
                                // (doanh thu) nhưng KHÔNG grant; báo user thử lại sau khi ad đóng.
                                AdManager.showInterstitial(act) {
                                    dialog = VipDialog.Failed(
                                        context.getString(R.string.vip_rewarded_not_earned)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale),
                ) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vip_watch_ad_3d))
                }

                // (8–11) Buy VIP (disabled — chờ IAP)
                LockedButton(stringResource(R.string.vip_buy_30d_locked))
                LockedButton(stringResource(R.string.vip_buy_90d_locked))
                LockedButton(stringResource(R.string.vip_buy_1y_locked))
                LockedButton(stringResource(R.string.vip_buy_lifetime_locked))

                // (13) Restore purchase (disabled)
                LockedButton(stringResource(R.string.vip_restore_locked), icon = true)

                // Privacy Policy footer
                Text(
                    text = stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(AdKeys.PRIVACY_POLICY_URL))
                                )
                            }
                        },
                )
                Spacer(Modifier.height(24.dp))
            }
        },
    )

    // reset confetti pop sau 1 nhịp
    LaunchedEffect(confettiPop) {
        if (confettiPop) { kotlinx.coroutines.delay(900L); confettiPop = false }
    }

    when (val d = dialog) {
        is VipDialog.Success -> VipResultDialog(
            title = stringResource(R.string.vip_success_title),
            message = stringResource(R.string.vip_success_message, d.days),
            onDismiss = { dialog = null },
        )
        is VipDialog.Failed -> VipResultDialog(
            title = stringResource(R.string.vip_failed_title),
            message = d.message,
            onDismiss = { dialog = null },
        )
        VipDialog.ConfirmRevoke -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.vip_revoke_all_confirm_title)) },
            text = { Text(stringResource(R.string.vip_revoke_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    AdManager.clearVipByKey()
                    vipPrefs.clearGrantedAtMs()
                    dialog = null
                    refresh()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
        null -> Unit
    }
}

private sealed interface VipDialog {
    data class Success(val days: Int) : VipDialog
    data class Failed(val message: String) : VipDialog
    data object ConfirmRevoke : VipDialog
}

@Composable
private fun VipHeroCard(isActive: Boolean, expiryMs: Long, confettiPop: Boolean) {
    // (3) Crown shimmer (rotation) + (5) confetti pop scale
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val rotation by shimmer.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "crownRotation",
    )
    val popScale = if (confettiPop) 1.2f else 1f

    val brush = if (isActive) {
        Brush.horizontalGradient(listOf(Color(0xFFFFE066), VipGold))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF9E9E9E), Color(0xFF616161)))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier
                    .size(48.dp)
                    .rotate(rotation)
                    .scale(popScale),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(if (isActive) R.string.vip_active else R.string.vip_free_user),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.vip_until, formatDate(expiryMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun VipInfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VipActiveCard(isGrace: Boolean, expiryMs: Long, nowMs: Long, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val remainingMs = (expiryMs - nowMs).coerceAtLeast(0L)
            val days = remainingMs / (24L * 60L * 60L * 1000L)
            val hours = (remainingMs / (60L * 60L * 1000L)) % 24
            Text(
                text = if (isGrace) {
                    stringResource(R.string.vip_entry_first_install)
                } else {
                    stringResource(R.string.vip_entry_active)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.vip_remaining_short, days, hours),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.vip_expires_at, formatDate(expiryMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRevoke, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vip_revoke_all_button))
            }
        }
    }
}

@Composable
private fun LockedButton(text: String, icon: Boolean = false) {
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        if (icon) {
            Icon(Icons.Outlined.Restore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(Icons.Outlined.CardGiftcard, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun VipResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

