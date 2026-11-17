package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApkDownloadState
import com.example.data.model.AppConstants
import com.example.data.model.AppUpdateInfo
import com.example.data.model.SecurityProfile
import com.example.data.model.UserProfile
import com.example.data.model.VipLevel
import com.example.data.model.WalletAccount
import com.example.ui.components.FintechFormatters
import com.example.ui.components.StatusPill
import com.example.ui.components.VerifiedAccountBadge

@Composable
fun ProfileScreen(
    profile: UserProfile,
    wallet: WalletAccount? = null,
    vipLevel: VipLevel = VipLevel.BRONZE,
    securityProfile: SecurityProfile? = null,
    supportUnreadCount: Int = 0,
    onlineAgentsCount: Int = 0,
    isSupportStaff: Boolean = false,
    isAdmin: Boolean,
    isDarkTheme: Boolean,
    isActionBusy: Boolean,
    appUpdateInfo: AppUpdateInfo = AppUpdateInfo(),
    apkDownloadState: ApkDownloadState = ApkDownloadState.Idle,
    onToggleTheme: (Boolean) -> Unit,
    onUpdateProfile: (fullName: String, username: String, phone: String) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onDownloadApk: (String) -> Unit = {},
    onOpenDownloads: (String) -> Unit = {},
    onOpenAdmin: () -> Unit,
    onOpenVip: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenLiveSupport: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenWebsite: () -> Unit = {},
    onOpenSupport: () -> Unit,
    onLogout: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User avatar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@${profile.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(status = profile.accountStatus, isDarkTheme = isDarkTheme)
                                VerifiedAccountBadge(isVerified = profile.isVerified, isDarkTheme = isDarkTheme)
                            }
                        }
                    }
                }
            }

            if (isAdmin) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Verified Admin Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Manage users, tasks, deposits, withdrawals, and PKR 100 commissions.", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = onOpenAdmin,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("profile_open_admin_button")
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Admin")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Account Details", style = MaterialTheme.typography.titleLarge)
                        ProfileInfoRow("Full Name", profile.fullName)
                        ProfileInfoRow("Username", "@${profile.username}")
                        ProfileInfoRow("Email", profile.email)
                        ProfileInfoRow("Phone Number", profile.phone)
                        ProfileInfoRow("User ID", profile.uid)
                        ProfileInfoRow("Wallet ID", wallet?.walletId?.ifBlank { profile.walletId } ?: profile.walletId)
                        ProfileInfoRow("Wallet Balance", FintechFormatters.formatPkr(wallet?.balance ?: profile.combinedBalance))
                        ProfileInfoRow("Referral Code", profile.referralCode)
                        if (profile.referredBy.isNotBlank()) {
                            ProfileInfoRow("Referred By", profile.referredBy)
                        }
                        ProfileInfoRow("Registered On", FintechFormatters.formatDate(profile.createdAt))
                        ProfileInfoRow(
                            "Verification Status",
                            if (profile.isVerified) "VERIFIED" else profile.firstDepositStatus.uppercase()
                        )
                        if (profile.isVerified && profile.verifiedAt != null) {
                            ProfileInfoRow("Verified Date", FintechFormatters.formatDate(profile.verifiedAt))
                        }
                        ProfileInfoRow("VIP Tier", "${vipLevel.displayName} Level")
                        ProfileInfoRow(
                            "2FA & Biometric Lock",
                            buildString {
                                append(if (securityProfile?.totpEnabled == true) "2FA ON" else "2FA OFF")
                                append(" • ")
                                append(if (securityProfile?.biometricEnabled == true) "Biometric ON" else "Biometric OFF")
                            }
                        )
                        ProfileInfoRow("Account Status", profile.accountStatus.uppercase())
                        ProfileInfoRow("Official Website", AppConstants.OFFICIAL_WEBSITE_DOMAIN)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (appUpdateInfo.isUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_app_updates_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("App Version & Releases", style = MaterialTheme.typography.titleLarge)
                            }
                            StatusPill(
                                status = if (appUpdateInfo.isUpdateAvailable) "pending" else "active",
                                isDarkTheme = isDarkTheme
                            )
                        }
                        ProfileInfoRow(
                            "Installed Version",
                            "v${appUpdateInfo.currentVersionName} (Build ${appUpdateInfo.currentVersionCode})"
                        )
                        ProfileInfoRow(
                            "Latest Release",
                            if (appUpdateInfo.isUpdateAvailable) {
                                "v${appUpdateInfo.latestVersionName} (Update Available)"
                            } else {
                                "v${appUpdateInfo.latestVersionName}"
                            }
                        )
                        if (appUpdateInfo.apkFileSizeBytes > 0L) {
                            ProfileInfoRow("APK File Size", FintechFormatters.formatBytes(appUpdateInfo.apkFileSizeBytes))
                        }
                        if (appUpdateInfo.publishedAt.isNotBlank()) {
                            ProfileInfoRow("Release Date", FintechFormatters.formatIsoDate(appUpdateInfo.publishedAt))
                        }
                        if (appUpdateInfo.githubRepoSlug.isNotBlank()) {
                            ProfileInfoRow("GitHub Repository", appUpdateInfo.githubRepoSlug)
                        }
                        if (appUpdateInfo.releaseNotes.isNotBlank()) {
                            Text(
                                text = "Release Notes: ${appUpdateInfo.releaseNotes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (appUpdateInfo.downloadUrl.isNotBlank()) {
                            Text(
                                text = "APK URL: ${appUpdateInfo.downloadUrl}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        when (apkDownloadState) {
                            is ApkDownloadState.Downloading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = { (apkDownloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                    Text(
                                        text = "Downloading ${AppConstants.APK_ASSET_NAME}... ${apkDownloadState.progressPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is ApkDownloadState.Completed -> {
                                Text(
                                    text = "Downloaded ${apkDownloadState.fileName} ✓ Open Downloads to install. (If Android shows 'App not installed' over an earlier debug build, uninstall the old app first.)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is ApkDownloadState.Error -> {
                                Text(
                                    text = apkDownloadState.message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ApkDownloadState.Idle -> Unit
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCheckForUpdates,
                                enabled = !appUpdateInfo.isChecking && apkDownloadState !is ApkDownloadState.Downloading,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_check_updates_button")
                            ) {
                                if (appUpdateInfo.isChecking) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (appUpdateInfo.isChecking) "Checking..." else "Check Updates")
                            }
                            if (appUpdateInfo.downloadUrl.isNotBlank()) {
                                if (apkDownloadState is ApkDownloadState.Completed) {
                                    Button(
                                        onClick = { onOpenDownloads(appUpdateInfo.downloadUrl) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("profile_install_apk_button")
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Downloads")
                                    }
                                } else {
                                    Button(
                                        onClick = { onDownloadApk(appUpdateInfo.downloadUrl) },
                                        enabled = apkDownloadState !is ApkDownloadState.Downloading,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("profile_download_apk_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (apkDownloadState is ApkDownloadState.Downloading) {
                                                "${apkDownloadState.progressPercent}%"
                                            } else {
                                                "Download APK"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Dark Theme", style = MaterialTheme.typography.titleMedium)
                                    Text("Switch between Obsidian Dark & Alabaster Light", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = onToggleTheme,
                                modifier = Modifier.testTag("theme_toggle_switch")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile")
                        }

                        OutlinedButton(
                            onClick = onOpenVip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_vip_button")
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VIP Membership (${vipLevel.displayName})")
                        }

                        OutlinedButton(
                            onClick = onOpenSecurity,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("security_info_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Account Security, 2FA & Devices")
                        }

                        OutlinedButton(
                            onClick = onOpenWebsite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_website_button")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Official Website (${AppConstants.OFFICIAL_WEBSITE_DOMAIN})")
                        }

                        OutlinedButton(
                            onClick = onOpenLiveSupport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_live_support_button")
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val badgeSuffix = if (supportUnreadCount > 0) " ($supportUnreadCount unread)" else if (onlineAgentsCount > 0) " (Online)" else ""
                            Text("Live Customer Support$badgeSuffix")
                        }

                        if (isSupportStaff && !isAdmin) {
                            OutlinedButton(
                                onClick = onOpenSupportConsole,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("profile_support_console_button")
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Support Agent Console")
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenSupport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_support_button")
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WhatsApp Community Channel")
                        }

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logout", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(profile.fullName) }
        var user by remember { mutableStateOf(profile.username) }
        var phone by remember { mutableStateOf(profile.phone) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it.lowercase().replace(Regex("[^a-z0-9_]"), "") },
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(name, user, phone)
                        showEditDialog = false
                    },
                    enabled = !isActionBusy
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }
}
