package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppConstants
import com.example.data.model.AppSettings
import com.example.data.model.CommissionRecord
import com.example.data.model.ReferralRecord
import com.example.data.model.UserProfile
import com.example.ui.components.AccountVerificationNoticeCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.SimulatedBadge
import com.example.ui.components.StatMiniCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberPending
import com.example.ui.theme.EmeraldPrimary
import com.example.util.ReferralLinkHelper
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReferralsScreen(
    profile: UserProfile,
    settings: AppSettings,
    referrals: List<ReferralRecord>,
    commissions: List<CommissionRecord>,
    isDarkTheme: Boolean,
    onNavigateDeposit: () -> Unit = {}
) {
    val context = LocalContext.current
    val referralLink = ReferralLinkHelper.buildReferralLink(profile.referralCode)
    val shareMessage = ReferralLinkHelper.buildShareMessage(profile.referralCode)

    val totalRegistered = referrals.size
    val eligibleReferrals = referrals.count { it.firstDepositVerified }
    val pendingCommCount = commissions.count { it.status == "pending" } +
        referrals.count { !it.firstDepositVerified && it.status == "registered" }
    val approvedCommCount = commissions.count { it.status == "approved" }
    val totalCommEarned = commissions.filter { it.status == "approved" }.sumOf { it.amount }
        .coerceAtLeast(profile.totalReferralCommission)
    val rewardPerReferral = settings.referralCommission

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1A13)),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_referral_banner),
                            contentDescription = stringResource(R.string.cd_referral_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.35f)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xBB071610), Color(0xDD07140E))
                                    )
                                )
                        )
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SimulatedBadge(label = "SIMULATED RS. ${rewardPerReferral.toInt()} / REFERRAL")
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = EmeraldPrimary)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Simulated Demo Referral Rewards",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Share your invite link. When a friend signs up via binacesmartearn.online and their qualifying simulated demo deposit is verified, your demo wallet receives a simulated Rs. ${rewardPerReferral.toInt()} reward.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFBBD0C6)
                            )
                        }
                    }
                }
            }

            if (!profile.isVerified) {
                item {
                    AccountVerificationNoticeCard(
                        firstDepositStatus = profile.firstDepositStatus,
                        isDarkTheme = isDarkTheme,
                        title = "Referral Sharing Locked Until Verification",
                        message = "Complete your first PKR 1,000 simulated demo deposit verification in Wallet > Deposit to unlock referral link copying and sharing.",
                        actionLabel = "Go to Simulated Deposit",
                        onActionClick = onNavigateDeposit
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("My Unique Referral Code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.referralCode,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { copyToClipboard(context, "Referral Code", profile.referralCode) },
                                    enabled = profile.isVerified,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("copy_referral_code_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Code")
                                }
                            }
                        }

                        Text("My Official Referral Link", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = referralLink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("referral_link_text")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { copyToClipboard(context, "Referral Link", referralLink) },
                                        enabled = profile.isVerified,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("copy_referral_link_button")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Referral Link")
                                    }
                                    OutlinedButton(
                                        onClick = { openHttpsUrl(context, referralLink) },
                                        enabled = profile.isVerified,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_referral_link_button")
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Open link", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Text("Share With Friends", style = MaterialTheme.typography.labelLarge)
                        Button(
                            onClick = {
                                shareWithPackageOrWeb(
                                    context = context,
                                    packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
                                    message = shareMessage,
                                    webFallbackUrl = ReferralLinkHelper.whatsAppShareUrl(shareMessage)
                                )
                            },
                            enabled = profile.isVerified,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color(0xFF04140C)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_whatsapp_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (profile.isVerified) "Share on WhatsApp" else "Verify Account to Share",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    shareWithPackageOrWeb(
                                        context = context,
                                        packages = listOf("com.facebook.katana", "com.facebook.lite"),
                                        message = shareMessage,
                                        webFallbackUrl = ReferralLinkHelper.facebookShareUrl(referralLink, shareMessage)
                                    )
                                },
                                enabled = profile.isVerified,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("share_facebook_button")
                            ) {
                                Text("Facebook")
                            }
                            OutlinedButton(
                                onClick = {
                                    shareWithPackageOrWeb(
                                        context = context,
                                        packages = listOf("org.telegram.messenger", "org.thunderdog.challegram"),
                                        message = shareMessage,
                                        webFallbackUrl = ReferralLinkHelper.telegramShareUrl(referralLink, shareMessage)
                                    )
                                },
                                enabled = profile.isVerified,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("share_telegram_button")
                            ) {
                                Text("Telegram")
                            }
                            OutlinedButton(
                                onClick = { shareViaSms(context, shareMessage) },
                                enabled = profile.isVerified,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("share_sms_button")
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS")
                            }
                            OutlinedButton(
                                onClick = { shareGeneral(context, shareMessage) },
                                enabled = profile.isVerified,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("share_more_apps_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("More")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Official Website", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(AppConstants.OFFICIAL_WEBSITE_URL, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            OutlinedButton(
                                onClick = { openHttpsUrl(context, AppConstants.OFFICIAL_WEBSITE_URL) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("open_official_website_button")
                            ) {
                                Text("Visit")
                            }
                        }
                    }
                }
            }

            item {
                Text("Referral Dashboard", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard("Invited / Registered", "$totalRegistered", Icons.Default.Group, Modifier.weight(1f))
                        StatMiniCard("Eligible Referrals", "$eligibleReferrals", Icons.Default.Verified, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard("Pending Commissions", "$pendingCommCount", Icons.Default.HourglassTop, Modifier.weight(1f), AmberPending)
                        StatMiniCard("Approved Payouts", "$approvedCommCount", Icons.Default.CheckCircle, Modifier.weight(1f))
                    }
                    StatMiniCard(
                        title = "Total Commission Earned",
                        value = FintechFormatters.formatPkr(totalCommEarned),
                        icon = Icons.Default.CardGiftcard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Simulated Commission Rules (Rs. ${rewardPerReferral.toInt()} / Eligible Friend)", style = MaterialTheme.typography.titleMedium)
                        Text("• 1 eligible = Rs. ${rewardPerReferral.toInt()}  |  5 eligible = Rs. ${(rewardPerReferral * 5).toInt()}  |  10 eligible = Rs. ${(rewardPerReferral * 10).toInt()} (simulated)", style = MaterialTheme.typography.bodyMedium)
                        Text("• One simulated commission per referred friend after admin verifies their qualifying first simulated deposit (no real money or guaranteed earnings).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Self-referrals, duplicate referrals, pending deposits, and rejected deposits never earn commissions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text("Referral History", style = MaterialTheme.typography.titleLarge)
            }

            if (referrals.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Group,
                        title = "No Referrals Yet",
                        subtitle = "Share your referral link above. Friends who sign up with your code appear here automatically."
                    )
                }
            } else {
                items(referrals, key = { it.id }) { ref ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("@${ref.referredUsername}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Joined ${FintechFormatters.formatDate(ref.registrationDate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(status = ref.status, isDarkTheme = isDarkTheme)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "First Deposit: ${if (ref.firstDepositVerified) "Verified" else "Pending"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = FintechFormatters.formatPkr(if (ref.commissionAmount > 0) ref.commissionAmount else rewardPerReferral),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusPill(status = ref.commissionStatus, isDarkTheme = isDarkTheme)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun openHttpsUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
    }
}

private fun shareGeneral(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share Referral Link"))
}

private fun shareWithPackageOrWeb(
    context: Context,
    packages: List<String>,
    message: String,
    webFallbackUrl: String
) {
    for (pkg in packages) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage(pkg)
        }
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl)))
    } catch (_: Exception) {
        shareGeneral(context, message)
    }
}

private fun shareViaSms(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:")
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        shareGeneral(context, message)
    }
}
