package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.VipHistoryRecord
import com.example.data.model.VipLevel
import com.example.data.model.VipMembership
import com.example.data.model.VipTierConfig
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.SupportPalette
import com.example.ui.theme.rememberSupportPalette

@Composable
fun VipMembershipScreen(
    profile: UserProfile,
    vipMembership: VipMembership?,
    vipConfigs: List<VipTierConfig>,
    vipHistory: List<VipHistoryRecord>,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    onSyncVip: () -> Unit,
    onOpenSupport: () -> Unit
) {
    val palette = rememberSupportPalette(isDarkTheme)
    val sortedConfigs = remember(vipConfigs) { vipConfigs.sortedBy { it.tierOrder } }
    val currentLevel = vipMembership?.levelEnum ?: VipLevel.BRONZE
    var inspectedLevel by remember(currentLevel) { mutableStateOf(currentLevel) }

    val activeConfig = sortedConfigs.firstOrNull { it.levelEnum == currentLevel } ?: sortedConfigs.firstOrNull()
    val inspectedConfig = sortedConfigs.firstOrNull { it.levelEnum == inspectedLevel } ?: activeConfig
    val nextConfig = sortedConfigs.firstOrNull { it.tierOrder > (activeConfig?.tierOrder ?: 1) }

    val depositTotal = vipMembership?.verifiedDepositTotal ?: profile.realBalance
    val tasksTotal = vipMembership?.completedTasksTotal ?: profile.completedTasksCount.toInt()
    val referralsTotal = vipMembership?.qualifiedReferralsCount ?: profile.totalReferrals.toInt()
    val progressPct = vipMembership?.progressPercent ?: 0

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                VipHeroCard(
                    palette = palette,
                    username = profile.username,
                    currentLevel = currentLevel,
                    priorityLabel = activeConfig?.prioritySupportLabel ?: "Standard Queue",
                    progressPercent = progressPct,
                    nextTierName = nextConfig?.tierName,
                    isBusy = isBusy,
                    onSyncVip = onSyncVip
                )
            }

            if (nextConfig != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Progress to ${nextConfig.tierName} VIP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.gold
                            )
                            VipMetricProgressRow(
                                label = "Verified Deposits",
                                currentText = FintechFormatters.formatPkr(depositTotal),
                                targetText = FintechFormatters.formatPkr(nextConfig.minVerifiedDepositsPkr),
                                ratio = if (nextConfig.minVerifiedDepositsPkr > 0) (depositTotal / nextConfig.minVerifiedDepositsPkr).toFloat().coerceIn(0f, 1f) else 1f,
                                palette = palette
                            )
                            VipMetricProgressRow(
                                label = "Completed Daily Tasks",
                                currentText = "$tasksTotal",
                                targetText = "${nextConfig.minCompletedTasks}",
                                ratio = if (nextConfig.minCompletedTasks > 0) (tasksTotal.toFloat() / nextConfig.minCompletedTasks).coerceIn(0f, 1f) else 1f,
                                palette = palette
                            )
                            VipMetricProgressRow(
                                label = "Verified Referrals",
                                currentText = "$referralsTotal",
                                targetText = "${nextConfig.minQualifiedReferrals}",
                                ratio = if (nextConfig.minQualifiedReferrals > 0) (referralsTotal.toFloat() / nextConfig.minQualifiedReferrals).coerceIn(0f, 1f) else 1f,
                                palette = palette
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "VIP Membership Tiers",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedConfigs, key = { it.levelKey }) { tier ->
                        val selected = tier.levelEnum == inspectedLevel
                        val unlocked = tier.tierOrder <= currentLevel.order
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) palette.goldContainer else palette.surface
                            ),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) palette.gold else palette.border
                            ),
                            modifier = Modifier
                                .width(136.dp)
                                .testTag("vip_tier_card_${tier.levelKey}")
                                .clickable { inspectedLevel = tier.levelEnum }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (tier.levelEnum == VipLevel.DIAMOND) Icons.Default.Diamond else Icons.Default.WorkspacePremium,
                                    contentDescription = tier.tierName,
                                    tint = if (unlocked) palette.gold else palette.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = tier.tierName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.onGoldContainer else palette.textPrimary
                                )
                                Text(
                                    text = if (tier.levelEnum == currentLevel) "CURRENT" else if (unlocked) "UNLOCKED" else "TIER ${tier.tierOrder}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unlocked) palette.onlineGreen else palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (inspectedConfig != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
                        border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${inspectedConfig.tierName} Benefits & Criteria",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.gold
                                )
                                Surface(
                                    color = palette.blueContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = inspectedConfig.prioritySupportLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.onBlueContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Eligibility: Deposits >= ${FintechFormatters.formatPkr(inspectedConfig.minVerifiedDepositsPkr)} • Tasks >= ${inspectedConfig.minCompletedTasks} • Verified Referrals >= ${inspectedConfig.minQualifiedReferrals}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                            inspectedConfig.benefits.forEach { benefit ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.gold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = benefit,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                            Button(
                                onClick = onOpenSupport,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.royalBlue,
                                    contentColor = palette.userBubbleText
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("vip_priority_support_button")
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Priority Live Support")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "VIP Upgrade History (${vipHistory.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            if (vipHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.MilitaryTech,
                        title = "No VIP Level Changes Yet",
                        subtitle = "Complete verified deposits, daily tasks, and qualified referrals to advance from Bronze."
                    )
                }
            } else {
                items(vipHistory, key = { it.effectiveHistoryId }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.previousLevel.uppercase()} -> ${item.newLevel.uppercase()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.gold
                                )
                                Text(
                                    text = FintechFormatters.formatShortDate(item.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                            }
                            Text(
                                text = item.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Source: ${item.source} • By: ${item.changedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VipHeroCard(
    palette: SupportPalette,
    username: String,
    currentLevel: VipLevel,
    priorityLabel: String,
    progressPercent: Int,
    nextTierName: String?,
    isBusy: Boolean,
    onSyncVip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(palette.headerStart, palette.headerEnd)))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.goldContainer,
                        border = BorderStroke(1.dp, palette.gold),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (currentLevel == VipLevel.DIAMOND) Icons.Default.Diamond else Icons.Default.Star,
                                contentDescription = "VIP tier icon",
                                tint = palette.gold,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "${currentLevel.displayName} VIP Member",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.gold
                        )
                        Text(
                            text = "@$username • $priorityLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.userBubbleText.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            if (nextTierName != null) {
                Text(
                    text = "$progressPercent% toward $nextTierName VIP",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onGoldContainer
                )
                LinearProgressIndicator(
                    progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                    color = palette.gold,
                    trackColor = palette.blueContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )
            } else {
                Text(
                    text = "Maximum Diamond VIP Tier Unlocked",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.gold
                )
            }

            Button(
                onClick = onSyncVip,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.gold,
                    contentColor = palette.onGold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("vip_sync_server_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.onGold)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify & Sync VIP Eligibility", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VipMetricProgressRow(
    label: String,
    currentText: String,
    targetText: String,
    ratio: Float,
    palette: SupportPalette
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
            Text("$currentText / $targetText", style = MaterialTheme.typography.labelMedium, color = palette.gold)
        }
        LinearProgressIndicator(
            progress = { ratio },
            color = palette.gold,
            trackColor = palette.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
        )
    }
}
