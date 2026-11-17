package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LeaderboardPeriod
import com.example.data.model.LeaderboardPrize
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.rememberSupportPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaderboardScreen(
    currentUserId: String,
    selectedPeriod: LeaderboardPeriod,
    dateKeys: Triple<String, String, String>,
    entries: List<LeaderboardEntry>,
    prizes: List<LeaderboardPrize>,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSyncLeaderboard: () -> Unit
) {
    val palette = rememberSupportPalette(isDarkTheme)
    val (todayKey, weekKey, monthKey) = dateKeys

    val sortedEntries = remember(entries, selectedPeriod, todayKey, weekKey, monthKey) {
        entries.sortedWith(
            compareByDescending<LeaderboardEntry> {
                it.scoreFor(selectedPeriod, todayKey, weekKey, monthKey)
            }.thenByDescending { it.verifiedActivityScore }
        ).take(100)
    }

    val personalRankIndex = sortedEntries.indexOfFirst { it.uid == currentUserId }
    val personalEntry = sortedEntries.getOrNull(personalRankIndex) ?: entries.firstOrNull { it.uid == currentUserId }
    val activePrizes = remember(prizes, selectedPeriod) {
        prizes.filter { it.isActive && it.periodType == selectedPeriod.key }
    }

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(palette.headerStart, palette.headerEnd)))
                        .padding(16.dp)
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
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Global Leaderboard",
                                    tint = palette.gold,
                                    modifier = Modifier.size(30.dp)
                                )
                                Column {
                                    Text(
                                        text = "Global Leaderboard",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.gold
                                    )
                                    Text(
                                        text = "Top 100 Verified Activity Rankings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.userBubbleText.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSyncLeaderboard,
                                enabled = !isBusy,
                                modifier = Modifier.testTag("leaderboard_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync rankings",
                                    tint = palette.gold
                                )
                            }
                        }

                        Surface(
                            color = palette.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Your Current Standing",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.textSecondary
                                    )
                                    Text(
                                        text = if (personalRankIndex >= 0) "Rank #${personalRankIndex + 1}" else "Unranked (Top 100)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.gold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val scoreLabel = formatScore(
                                        entry = personalEntry,
                                        period = selectedPeriod,
                                        todayKey = todayKey,
                                        weekKey = weekKey,
                                        monthKey = monthKey
                                    )
                                    Text(
                                        text = scoreLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "VIP: ${personalEntry?.vipLevelEnum?.displayName ?: "Bronze"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.electricBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LeaderboardPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { onSelectPeriod(period) },
                            label = { Text(period.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.goldContainer,
                                selectedLabelColor = palette.gold
                            ),
                            modifier = Modifier.testTag("leaderboard_filter_${period.key}")
                        )
                    }
                }
            }

            if (activePrizes.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.goldContainer.copy(alpha = 0.72f)),
                        border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Configurable Promotional Prizes (${selectedPeriod.label})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.gold
                            )
                            activePrizes.forEach { prize ->
                                Text(
                                    text = "Ranks #${prize.rankFrom}-#${prize.rankTo}: ${prize.title} • Up to ${FintechFormatters.formatPkr(prize.rewardAmountPkr)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.onGoldContainer
                                )
                                Text(
                                    text = prize.rewardDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (sortedEntries.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Leaderboard,
                        title = "No Verified Rankings Yet",
                        subtitle = "Complete daily tasks and verified referrals to populate live server-calculated rankings."
                    )
                }
            } else {
                itemsIndexed(sortedEntries, key = { _, item -> item.uid }) { index, entry ->
                    val rank = index + 1
                    val isMe = entry.uid == currentUserId
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMe) palette.blueContainer else palette.surface
                        ),
                        border = BorderStroke(
                            width = if (isMe || rank <= 3) 1.4.dp else 1.dp,
                            color = if (rank <= 3 || isMe) palette.gold else palette.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leaderboard_row_$rank")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (rank <= 3) palette.goldContainer else palette.surfaceElevated,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#$rank",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rank <= 3) palette.gold else palette.textPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "@${entry.username}" + if (isMe) " (You)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary
                                    )
                                    if (entry.isVerified) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified member",
                                            tint = palette.onlineGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${entry.vipLevelEnum.displayName} VIP • ${entry.completedTasksCount} tasks • ${entry.qualifiedReferralsCount} referrals",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary
                                )
                            }
                            Text(
                                text = formatScore(entry, selectedPeriod, todayKey, weekKey, monthKey),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.gold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatScore(
    entry: LeaderboardEntry?,
    period: LeaderboardPeriod,
    todayKey: String,
    weekKey: String,
    monthKey: String
): String {
    if (entry == null) return "0 pts"
    val raw = entry.scoreFor(period, todayKey, weekKey, monthKey)
    return when (period) {
        LeaderboardPeriod.REFERRALS -> "${entry.qualifiedReferralsCount} refs"
        LeaderboardPeriod.VIP -> "${entry.vipLevelEnum.displayName} (${entry.verifiedActivityScore.toInt()} pts)"
        else -> "${raw.toInt()} pts"
    }
}
