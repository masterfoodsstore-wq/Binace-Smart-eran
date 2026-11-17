package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AnnouncementItem
import com.example.data.model.LeaderboardPeriod
import com.example.data.model.LeaderboardPrize
import com.example.data.model.SecurityDeviceSession
import com.example.data.model.SecurityEvent
import com.example.data.model.UserProfile
import com.example.data.model.VipLevel
import com.example.data.model.VipMembership
import com.example.data.model.VipTierConfig
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.AdminPlatformViewModel

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.vipAdminItems(
    vipConfigs: List<VipTierConfig>,
    vipMemberships: List<VipMembership>,
    users: List<UserProfile>,
    isBusy: Boolean,
    adminPlatformViewModel: AdminPlatformViewModel
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VIP Membership Management", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { adminPlatformViewModel.seedVipAndPrizes() },
                enabled = !isBusy,
                modifier = Modifier.testTag("admin_seed_vip_button")
            ) {
                Text("Seed Defaults")
            }
        }
    }

    items(vipConfigs, key = { "cfg_${it.levelKey}" }) { cfg ->
        var minDep by remember(cfg.levelKey, cfg.minVerifiedDepositsPkr) {
            mutableStateOf(cfg.minVerifiedDepositsPkr.toInt().toString())
        }
        var minTasks by remember(cfg.levelKey, cfg.minCompletedTasks) {
            mutableStateOf(cfg.minCompletedTasks.toString())
        }
        var minRefs by remember(cfg.levelKey, cfg.minQualifiedReferrals) {
            mutableStateOf(cfg.minQualifiedReferrals.toString())
        }
        var priorityLabel by remember(cfg.levelKey, cfg.prioritySupportLabel) {
            mutableStateOf(cfg.prioritySupportLabel)
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${cfg.tierName} (Tier ${cfg.tierOrder})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minDep,
                        onValueChange = { minDep = it.filter { c -> c.isDigit() } },
                        label = { Text("Min Deposits (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minTasks,
                        onValueChange = { minTasks = it.filter { c -> c.isDigit() } },
                        label = { Text("Min Tasks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minRefs,
                        onValueChange = { minRefs = it.filter { c -> c.isDigit() } },
                        label = { Text("Min Refs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = priorityLabel,
                    onValueChange = { priorityLabel = it },
                    label = { Text("Priority Support Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        adminPlatformViewModel.saveVipTierConfig(
                            cfg.copy(
                                minVerifiedDepositsPkr = minDep.toDoubleOrNull() ?: cfg.minVerifiedDepositsPkr,
                                minCompletedTasks = minTasks.toIntOrNull() ?: cfg.minCompletedTasks,
                                minQualifiedReferrals = minRefs.toIntOrNull() ?: cfg.minQualifiedReferrals,
                                prioritySupportLabel = priorityLabel
                            )
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save ${cfg.tierName} Config")
                }
            }
        }
    }

    item {
        Text("User VIP Assignments (${users.size})", style = MaterialTheme.typography.titleLarge)
    }

    if (users.isEmpty()) {
        item { EmptyStateCard(Icons.Default.WorkspacePremium, "No Users", "Registered members appear here.") }
    } else {
        items(users, key = { "vip_u_${it.uid}" }) { user ->
            val currentVip = vipMemberships.firstOrNull { it.uid == user.uid }
            val currentLevel = currentVip?.levelEnum ?: VipLevel.BRONZE
            var selectedOverride by remember(user.uid, currentLevel) { mutableStateOf(currentLevel) }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "@${user.username} • Current: ${currentLevel.displayName} (${currentVip?.assignedBy ?: "auto"})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VipLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = selectedOverride == lvl,
                                onClick = { selectedOverride = lvl },
                                label = { Text(lvl.displayName) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            adminPlatformViewModel.assignUserVip(
                                user = user,
                                level = selectedOverride,
                                reason = "Admin tier assignment to ${selectedOverride.displayName}"
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply ${selectedOverride.displayName} Override")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.leaderboardPrizesAdminItems(
    prizes: List<LeaderboardPrize>,
    isBusy: Boolean,
    adminPlatformViewModel: AdminPlatformViewModel
) {
    item {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            var period by remember { mutableStateOf(LeaderboardPeriod.WEEKLY.key) }
            var title by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            var amount by remember { mutableStateOf("1000") }
            var rankFrom by remember { mutableStateOf("1") }
            var rankTo by remember { mutableStateOf("3") }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Global Leaderboard & Promotional Prizes", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { adminPlatformViewModel.rebuildAllVipAndLeaderboard() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_rebuild_leaderboard_button")
                ) {
                    Text("Recalculate All VIP & Leaderboard Rankings")
                }

                Text("Add Promotional Prize", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LeaderboardPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p.key, onClick = { period = p.key }, label = { Text(p.label) })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Prize Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Promotional Description (no guaranteed returns)") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = rankFrom, onValueChange = { rankFrom = it.filter { c -> c.isDigit() } }, label = { Text("Rank From") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rankTo, onValueChange = { rankTo = it.filter { c -> c.isDigit() } }, label = { Text("Rank To") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("PKR") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        adminPlatformViewModel.savePrize(
                            LeaderboardPrize(
                                periodType = period,
                                rankFrom = rankFrom.toIntOrNull() ?: 1,
                                rankTo = rankTo.toIntOrNull() ?: 3,
                                title = title,
                                rewardDescription = desc.ifBlank { "Promotional prize for top verified contributors." },
                                rewardAmountPkr = amount.toDoubleOrNull() ?: 0.0,
                                isActive = true
                            )
                        )
                        title = ""
                        desc = ""
                    },
                    enabled = !isBusy && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Promotional Prize")
                }
            }
        }
    }

    if (prizes.isEmpty()) {
        item { EmptyStateCard(Icons.Default.EmojiEvents, "No Promotional Prizes Configured", "Create a promotional leaderboard prize above or seed defaults.") }
    } else {
        items(prizes, key = { it.effectivePrizeId }) { prize ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${prize.title} (${prize.periodType.uppercase()})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Ranks #${prize.rankFrom}-#${prize.rankTo} • ${FintechFormatters.formatPkr(prize.rewardAmountPkr)}", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = { adminPlatformViewModel.deletePrize(prize.effectivePrizeId) }, enabled = !isBusy) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.announcementsAdminItems(
    announcements: List<AnnouncementItem>,
    isBusy: Boolean,
    adminPlatformViewModel: AdminPlatformViewModel
) {
    item {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("announcement") }
        var scheduleMinutes by remember { mutableStateOf("0") }
        var sendPush by remember { mutableStateOf(true) }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Broadcast & Scheduled Announcements", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("announcement", "promotion", "security", "maintenance").forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Announcement Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Broadcast Message") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = scheduleMinutes,
                    onValueChange = { scheduleMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Delay in Minutes (0 = Publish Immediately)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Send FCM Push Notification")
                    Switch(checked = sendPush, onCheckedChange = { sendPush = it })
                }
                Button(
                    onClick = {
                        val mins = scheduleMinutes.toLongOrNull()
                        adminPlatformViewModel.createAnnouncement(title, body, category, mins, sendPush)
                        title = ""
                        body = ""
                        scheduleMinutes = "0"
                    },
                    enabled = !isBusy && title.isNotBlank() && body.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_publish_announcement_button")
                ) {
                    Text(if ((scheduleMinutes.toLongOrNull() ?: 0L) > 0L) "Schedule Announcement" else "Broadcast Now")
                }
            }
        }
    }

    if (announcements.isEmpty()) {
        item { EmptyStateCard(Icons.Default.Campaign, "No Broadcasts Yet", "Published and scheduled announcements appear here.") }
    } else {
        items(announcements, key = { it.effectiveAnnouncementId }) { ann ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(ann.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(ann.body, style = MaterialTheme.typography.bodySmall)
                    if (ann.status == "scheduled") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { adminPlatformViewModel.publishAnnouncementNow(ann.effectiveAnnouncementId) }, enabled = !isBusy) {
                                Text("Publish Now")
                            }
                            OutlinedButton(onClick = { adminPlatformViewModel.cancelAnnouncement(ann.effectiveAnnouncementId) }, enabled = !isBusy) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.securityAuditAdminItems(
    events: List<SecurityEvent>,
    devices: List<SecurityDeviceSession>,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    adminPlatformViewModel: AdminPlatformViewModel
) {
    item {
        Text("Active & Suspicious User Devices (${devices.size})", style = MaterialTheme.typography.titleLarge)
    }

    if (devices.isEmpty()) {
        item { EmptyStateCard(Icons.Default.Security, "No Registered Devices", "User device sessions appear here.") }
    } else {
        items(devices.take(30), key = { "adm_dev_${it.effectiveSessionId}" }) { dev ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${dev.deviceName} • UID ${dev.uid.take(8)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (dev.isSuspicious) "Suspicious: ${dev.suspiciousReason ?: "Unrecognized"}" else "Android ${dev.osVersion} • ${dev.timezoneId ?: "UTC"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (dev.status == "active") {
                        OutlinedButton(
                            onClick = { adminPlatformViewModel.revokeUserDevice(dev) },
                            enabled = !isBusy
                        ) {
                            Text("Revoke")
                        }
                    } else {
                        StatusPill(status = "suspended", isDarkTheme = isDarkTheme)
                    }
                }
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text("Platform Security Audit Events (${events.size})", style = MaterialTheme.typography.titleLarge)
    }

    items(events.take(50), key = { "adm_sec_${it.effectiveEventId}" }) { event ->
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("@${event.username} • ${event.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(event.severity.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(event.details, style = MaterialTheme.typography.bodySmall)
                Text(FintechFormatters.formatShortDate(event.createdAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
