package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApkDownloadState
import com.example.data.model.AppUpdateInfo
import com.example.data.model.DepositRequest
import com.example.data.model.ReferralRecord
import com.example.data.model.UserProfile
import com.example.data.model.VipLevel
import com.example.data.model.WithdrawalRequest
import com.example.ui.components.AccountVerificationNoticeCard
import com.example.ui.components.BalanceHeroCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.StatMiniCard
import com.example.ui.components.VerifiedAccountBadge
import com.example.ui.theme.AmberPending

@Composable
fun HomeDashboardScreen(
    profile: UserProfile,
    todayDateKey: String,
    isAdmin: Boolean,
    isDarkTheme: Boolean = true,
    vipLevel: VipLevel = VipLevel.BRONZE,
    unreadAlertsCount: Int = 0,
    supportUnreadCount: Int = 0,
    onlineAgentsCount: Int = 0,
    isSupportStaff: Boolean = false,
    showVerifiedBanner: Boolean = false,
    onDismissVerifiedBanner: () -> Unit = {},
    deposits: List<DepositRequest>,
    withdrawals: List<WithdrawalRequest>,
    referrals: List<ReferralRecord>,
    appUpdateInfo: AppUpdateInfo = AppUpdateInfo(),
    apkDownloadState: ApkDownloadState = ApkDownloadState.Idle,
    onDownloadApk: (String) -> Unit = {},
    onOpenDownloads: (String) -> Unit = {},
    onNavigateTasks: () -> Unit,
    onNavigateDeposit: () -> Unit,
    onNavigateWithdraw: () -> Unit,
    onNavigateReferrals: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateProfile: () -> Unit,
    onOpenVip: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenLiveSupport: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenSupport: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val todayEarnings = if (profile.todayTaskDate == todayDateKey) profile.todayTaskEarnings else 0.0
    val pendingRequestsCount = deposits.count { it.status.equals("pending", ignoreCase = true) } +
        withdrawals.count { it.status.equals("pending", ignoreCase = true) }
    val approvedCommissionTotal = referrals
        .filter { it.commissionStatus == "approved" }
        .sumOf { it.commissionAmount }
        .coerceAtLeast(profile.totalReferralCommission)
    val totalReferralsCount = maxOf(referrals.size, profile.totalReferrals.toInt())

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Welcome, @${profile.username}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerifiedAccountBadge(isVerified = profile.isVerified, isDarkTheme = isDarkTheme)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val verifiedSuffix = if (profile.isVerified && profile.verifiedAt != null) {
                            " • Verified ${FintechFormatters.formatShortDate(profile.verifiedAt)}"
                        } else {
                            ""
                        }
                        Text(
                            text = "User ID: ${profile.uid.take(12).uppercase()}$verifiedSuffix",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = onOpenVip,
                            label = { Text(vipLevel.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "VIP tier ${vipLevel.displayName}",
                                    tint = AmberPending,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("home_vip_chip")
                        )
                        IconButton(
                            onClick = onOpenAlerts,
                            modifier = Modifier.testTag("home_alerts_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadAlertsCount > 0) {
                                        Badge {
                                            Text(if (unreadAlertsCount > 99) "99+" else "$unreadAlertsCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notification Center"
                                )
                            }
                        }
                        if (isAdmin) {
                            AssistChip(
                                onClick = onOpenAdmin,
                                label = { Text("Admin") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Open Admin Console",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("home_admin_chip")
                            )
                        } else if (isSupportStaff) {
                            AssistChip(
                                onClick = onOpenSupportConsole,
                                label = { Text("Desk") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SupportAgent,
                                        contentDescription = "Open Support Agent Console",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("home_support_console_chip")
                            )
                        }
                    }
                }
            }

            if (showVerifiedBanner && profile.isVerified) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_verified_notification")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Account verified",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Account Verified!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily tasks, referral sharing, and simulated demo withdrawals are now unlocked.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = onDismissVerifiedBanner) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            if (profile.accountStatus == "suspended") {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "This account is currently suspended by an administrator. Contact support for review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            if (!profile.isVerified) {
                item {
                    AccountVerificationNoticeCard(
                        firstDepositStatus = profile.firstDepositStatus,
                        isDarkTheme = isDarkTheme,
                        onActionClick = onNavigateDeposit
                    )
                }
            }

            item {
                BalanceHeroCard(
                    totalBalance = profile.combinedBalance,
                    realBalance = profile.effectiveDepositBalance,
                    rewardBalance = profile.effectiveRewardBalance,
                    walletId = profile.walletId,
                    isVerified = profile.isVerified
                )
            }

            if (appUpdateInfo.isUpdateAvailable && appUpdateInfo.downloadUrl.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_update_banner")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = "New update available",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Update Available • v${appUpdateInfo.latestVersionName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val metaLine = listOfNotNull(
                                            FintechFormatters.formatBytes(appUpdateInfo.apkFileSizeBytes).takeIf { it.isNotBlank() },
                                            FintechFormatters.formatIsoDate(appUpdateInfo.publishedAt).takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        if (metaLine.isNotBlank()) {
                                            Text(
                                                text = metaLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = appUpdateInfo.releaseNotes.ifBlank {
                                                "New signed APK ready (installed: v${appUpdateInfo.currentVersionName})."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                when (apkDownloadState) {
                                    is ApkDownloadState.Completed -> {
                                        Button(
                                            onClick = { onOpenDownloads(appUpdateInfo.downloadUrl) },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("home_install_apk_button")
                                        ) {
                                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Install")
                                        }
                                    }
                                    is ApkDownloadState.Downloading -> {
                                        OutlinedButton(
                                            onClick = { onOpenDownloads(appUpdateInfo.downloadUrl) },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("${apkDownloadState.progressPercent}%")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = { onDownloadApk(appUpdateInfo.downloadUrl) },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("home_download_apk_button")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                            if (apkDownloadState is ApkDownloadState.Downloading) {
                                LinearProgressIndicator(
                                    progress = { (apkDownloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            } else if (apkDownloadState is ApkDownloadState.Error) {
                                Text(
                                    text = apkDownloadState.message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard(
                            title = "Today's Tasks",
                            value = FintechFormatters.formatPkr(todayEarnings),
                            icon = Icons.Default.Today,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniCard(
                            title = "Completed Tasks",
                            value = "${profile.completedTasksCount}",
                            icon = Icons.Default.CheckCircle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard(
                            title = "Total Referrals",
                            value = "$totalReferralsCount",
                            icon = Icons.Default.Group,
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniCard(
                            title = "Ref Commission",
                            value = FintechFormatters.formatPkr(approvedCommissionTotal),
                            icon = Icons.Default.CardGiftcard,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    StatMiniCard(
                        title = "Pending Deposit & Withdrawal Requests",
                        value = "$pendingRequestsCount pending",
                        icon = Icons.Default.HourglassTop,
                        accentColor = AmberPending,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                val liveSupportSub = when {
                    supportUnreadCount > 0 -> "$supportUnreadCount unread replies"
                    onlineAgentsCount > 0 -> "$onlineAgentsCount online • Real-time chat"
                    else -> "24/7 ticket & chat desk"
                }
                val shortcuts = listOf(
                    ShortcutItem("Daily Tasks", "Simulated PKR rewards", Icons.Default.TaskAlt, "shortcut_daily_tasks", onNavigateTasks),
                    ShortcutItem("Deposit", "Min PKR 1,000 demo", Icons.Default.ArrowDownward, "shortcut_deposit", onNavigateDeposit),
                    ShortcutItem("Withdrawal", "Min PKR 3,500 demo", Icons.Default.ArrowUpward, "shortcut_withdrawal", onNavigateWithdraw),
                    ShortcutItem("Referral Program", "PKR 100 demo/invite", Icons.Default.CardGiftcard, "shortcut_referrals", onNavigateReferrals),
                    ShortcutItem("VIP Membership", "${vipLevel.displayName} tier & perks", Icons.Default.WorkspacePremium, "shortcut_vip", onOpenVip),
                    ShortcutItem("Leaderboard", "Top 100 & prizes", Icons.Default.EmojiEvents, "shortcut_leaderboard", onOpenLeaderboard),
                    ShortcutItem(
                        "Notification Center",
                        if (unreadAlertsCount > 0) "$unreadAlertsCount unread alerts" else "Broadcasts & alerts",
                        Icons.Default.Notifications,
                        "shortcut_alerts",
                        onOpenAlerts
                    ),
                    ShortcutItem("Account Security", "2FA, Biometrics & devices", Icons.Default.Security, "shortcut_security", onOpenSecurity),
                    ShortcutItem("Live Support", liveSupportSub, Icons.Default.SupportAgent, "shortcut_live_support", onOpenLiveSupport),
                    ShortcutItem("Transaction History", "Full simulated ledger", Icons.Default.History, "shortcut_history", onNavigateHistory),
                    ShortcutItem("My Profile", "Account & verification", Icons.Default.Person, "shortcut_profile", onNavigateProfile),
                    ShortcutItem("WhatsApp Channel", "Official community feed", Icons.Default.Forum, "shortcut_support", onOpenSupport)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shortcuts.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { item ->
                                ShortcutCard(
                                    item = item,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "All wallet balances, deposits, task rewards, withdrawals, and PKR 100 referral commissions in Binace Smart Earn are simulated demo records with no real-money obligations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class ShortcutItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun ShortcutCard(
    item: ShortcutItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag(item.tag)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
