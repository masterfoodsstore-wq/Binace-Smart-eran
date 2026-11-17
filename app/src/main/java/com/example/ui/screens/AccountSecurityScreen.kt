package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.OtpChallengeState
import com.example.data.model.SecurityDeviceSession
import com.example.data.model.SecurityEvent
import com.example.data.model.SecurityProfile
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.StatusPill
import com.example.ui.theme.SupportPalette
import com.example.ui.theme.rememberSupportPalette
import com.example.util.SecurityCryptoHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSecurityScreen(
    profile: UserProfile,
    securityProfile: SecurityProfile?,
    currentDeviceId: String,
    devices: List<SecurityDeviceSession>,
    securityEvents: List<SecurityEvent>,
    otpState: OtpChallengeState,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    onRequestOtp: () -> Unit,
    onVerifyOtp: (challengeId: String, code: String) -> Unit,
    onResetOtpState: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onEnableTotp: (secret: String, code: String) -> Unit,
    onDisableTotp: () -> Unit,
    onSaveWithdrawDetails: (method: String, accountNumber: String, holderName: String) -> Unit,
    onTrustDevice: (SecurityDeviceSession) -> Unit,
    onRevokeDevice: (SecurityDeviceSession) -> Unit,
    onTriggerPasswordReset: (onGoogleSecurityRedirect: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val palette = rememberSupportPalette(isDarkTheme)

    var showOtpDialog by remember { mutableStateOf(false) }
    var showTotpDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val biometricOn = securityProfile?.biometricEnabled == true
    val totpOn = securityProfile?.totpEnabled == true
    val otpVerified = securityProfile?.otpVerified == true
    val withdrawVerified = securityProfile?.withdrawalDetailVerified == true
    val suspiciousDevicesCount = devices.count { it.isSuspicious && !it.isTrusted && it.status == "active" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SecurityHeroBanner(
                    palette = palette,
                    otpVerified = otpVerified,
                    totpEnabled = totpOn,
                    biometricEnabled = biometricOn,
                    withdrawVerified = withdrawVerified,
                    suspiciousCount = suspiciousDevicesCount
                )
            }

            if (suspiciousDevicesCount > 0 || securityProfile?.suspiciousAlertActive == true) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unrecognized Sign-In Alert", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Review active devices below to confirm your devices or revoke unknown sessions immediately.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Authentication Controls Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Account Protection & 2FA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.gold
                        )

                        // Biometric Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = palette.gold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Biometric App Guard", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                                    Text(
                                        text = "Require fingerprint/biometric verification on sensitive actions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = biometricOn,
                                onCheckedChange = { enable ->
                                    biometricError = null
                                    if (enable && activity != null) {
                                        SecurityCryptoHelper.launchBiometricPrompt(
                                            activity = activity,
                                            title = "Confirm Biometric Security",
                                            subtitle = "Verify your fingerprint to activate Biometric App Guard",
                                            onSuccess = { onToggleBiometric(true) },
                                            onError = { msg -> biometricError = msg }
                                        )
                                    } else {
                                        onToggleBiometric(false)
                                    }
                                },
                                modifier = Modifier.testTag("security_biometric_switch")
                            )
                        }
                        biometricError?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }

                        HorizontalDivider(color = palette.border)

                        // Optional TOTP 2FA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = palette.gold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Authenticator 2FA (Optional)", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                                    Text(
                                        text = if (totpOn) "Active (RFC 6238 6-digit TOTP)" else "Protect your account with an authenticator app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { if (totpOn) onDisableTotp() else showTotpDialog = true },
                                enabled = !isBusy,
                                modifier = Modifier.testTag("security_totp_button")
                            ) {
                                Text(if (totpOn) "Disable" else "Set Up")
                            }
                        }

                        HorizontalDivider(color = palette.border)

                        // Server-side OTP Challenge + Password Security Check
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onResetOtpState()
                                    showOtpDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.royalBlue, contentColor = palette.userBubbleText),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("security_otp_challenge_button")
                            ) {
                                Icon(Icons.Default.PhonelinkLock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (otpVerified) "Re-Verify OTP" else "Verify via Server OTP")
                            }
                            OutlinedButton(
                                onClick = {
                                    onTriggerPasswordReset {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://myaccount.google.com/signinoptions/password")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("security_password_reset_button")
                            ) {
                                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Password & Google Security")
                            }
                        }
                    }
                }
            }

            // Verified Withdrawal Details Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, if (withdrawVerified) palette.gold else palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = palette.gold)
                                Text("Verified Withdrawal Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            }
                            StatusPill(status = if (withdrawVerified) "approved" else "pending", isDarkTheme = isDarkTheme)
                        }
                        if (withdrawVerified && securityProfile != null) {
                            Text(
                                text = "${securityProfile.verifiedWithdrawMethod} • ${securityProfile.verifiedWithdrawHolderName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Account: ${securityProfile.verifiedWithdrawAccount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.gold
                            )
                        } else {
                            Text(
                                text = "Pre-verify your EasyPaisa, JazzCash, or Bank account before submitting withdrawals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { showWithdrawDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("security_verify_withdrawal_button")
                        ) {
                            Text(if (withdrawVerified) "Update Verified Payout Details" else "Verify Withdrawal Details")
                        }
                    }
                }
            }

            // Active Devices & Session Management
            item {
                Text(
                    text = "Devices & Active Sessions (${devices.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            if (devices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Security,
                        title = "No Devices Recorded",
                        subtitle = "Active device sessions appear here in real time."
                    )
                }
            } else {
                items(devices, key = { it.effectiveSessionId }) { session ->
                    val isCurrent = session.deviceId == currentDeviceId
                    val isRevoked = session.status == "revoked"
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(
                            1.dp,
                            when {
                                session.isSuspicious && !session.isTrusted -> MaterialTheme.colorScheme.error
                                isCurrent -> palette.gold
                                else -> palette.border
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.deviceName + if (isCurrent) " (This Device)" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusPill(
                                    status = when {
                                        isRevoked -> "suspended"
                                        session.isSuspicious && !session.isTrusted -> "pending"
                                        else -> "active"
                                    },
                                    isDarkTheme = isDarkTheme
                                )
                            }
                            Text(
                                text = "Android ${session.osVersion} • v${session.appVersion} • ${session.timezoneId ?: "UTC"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                            Text(
                                text = "Last seen: ${FintechFormatters.formatDate(session.lastActiveAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                            if (!session.suspiciousReason.isNullOrBlank() && !session.isTrusted) {
                                Text(
                                    text = session.suspiciousReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (!isRevoked) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!session.isTrusted || session.isSuspicious) {
                                        Button(
                                            onClick = { onTrustDevice(session) },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold)
                                        ) {
                                            Text("Trust Device")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onRevokeDevice(session) },
                                        enabled = !isBusy,
                                        modifier = Modifier.testTag("revoke_device_${session.deviceId}")
                                    ) {
                                        Text(if (isCurrent) "Sign Out This Session" else "Revoke Session")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Immutable Security History
            item {
                Text(
                    text = "Security Activity History (${securityEvents.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            if (securityEvents.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = "No Security Events Yet",
                        subtitle = "Logins, OTP checks, 2FA updates, and device revocations are logged immutably."
                    )
                }
            } else {
                items(securityEvents, key = { it.effectiveEventId }) { event ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (event.severity == "critical") MaterialTheme.colorScheme.error else palette.gold
                                )
                                Text(
                                    text = FintechFormatters.formatShortDate(event.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                            }
                            Text(event.details, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showOtpDialog) {
        OtpChallengeDialog(
            otpState = otpState,
            onDismiss = {
                showOtpDialog = false
                onResetOtpState()
            },
            onRequestOtp = onRequestOtp,
            onVerifyOtp = onVerifyOtp
        )
    }

    if (showTotpDialog) {
        TotpSetupDialog(
            userEmail = profile.email,
            isBusy = isBusy,
            onDismiss = { showTotpDialog = false },
            onConfirmSetup = { secret, code ->
                onEnableTotp(secret, code)
                showTotpDialog = false
            }
        )
    }

    if (showWithdrawDialog) {
        WithdrawalDetailsDialog(
            existing = securityProfile,
            isBusy = isBusy,
            onDismiss = { showWithdrawDialog = false },
            onSave = { method, acct, holder ->
                onSaveWithdrawDetails(method, acct, holder)
                showWithdrawDialog = false
            }
        )
    }
}

@Composable
private fun SecurityHeroBanner(
    palette: SupportPalette,
    otpVerified: Boolean,
    totpEnabled: Boolean,
    biometricEnabled: Boolean,
    withdrawVerified: Boolean,
    suspiciousCount: Int
) {
    val activeCount = listOf(otpVerified, totpEnabled, biometricEnabled, withdrawVerified).count { it }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(palette.headerStart, palette.headerEnd)))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.goldContainer,
                border = BorderStroke(1.dp, palette.gold),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = palette.gold, modifier = Modifier.size(28.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Account Security Shield",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.gold
                )
                Text(
                    text = "$activeCount / 4 security protections active" + if (suspiciousCount > 0) " • $suspiciousCount alert" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.userBubbleText.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
