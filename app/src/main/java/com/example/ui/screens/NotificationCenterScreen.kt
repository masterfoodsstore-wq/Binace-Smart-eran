package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.NotificationCategory
import com.example.data.model.UnifiedFeedNotification
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.rememberSupportPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterScreen(
    feed: List<UnifiedFeedNotification>,
    unreadCount: Int,
    isDarkTheme: Boolean,
    onMarkRead: (UnifiedFeedNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onSyncPushToken: () -> Unit
) {
    val context = LocalContext.current
    val palette = rememberSupportPalette(isDarkTheme)
    var selectedFilter by remember { mutableStateOf("all") }

    var hasPushPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPushPermission = granted
            if (granted) onSyncPushToken()
        }
    )

    val filteredFeed = remember(feed, selectedFilter) {
        when (selectedFilter) {
            "all" -> feed
            "unread" -> feed.filter { !it.isRead }
            "announcement" -> feed.filter { it.isAnnouncement || it.category == "announcement" || it.category == "promotion" || it.category == "maintenance" }
            else -> feed.filter { it.category.equals(selectedFilter, ignoreCase = true) }
        }
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
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Notification Center",
                                    tint = palette.gold,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "Notification Center",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.gold
                                    )
                                    Text(
                                        text = if (unreadCount > 0) "$unreadCount unread alerts & broadcasts" else "All caught up",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.userBubbleText.copy(alpha = 0.88f)
                                    )
                                }
                            }
                            if (unreadCount > 0) {
                                OutlinedButton(
                                    onClick = onMarkAllRead,
                                    border = BorderStroke(1.dp, palette.gold),
                                    modifier = Modifier.testTag("notifications_mark_all_read")
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = palette.gold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mark All Read", color = palette.gold)
                                }
                            }
                        }

                        if (!hasPushPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.gold,
                                    contentColor = palette.onGold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("enable_push_notifications_button")
                            ) {
                                Text("Enable Push Alerts (FCM & OTP)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                val filterChips = listOf(
                    "all" to "All (${feed.size})",
                    "unread" to "Unread ($unreadCount)",
                    NotificationCategory.ANNOUNCEMENT.key to "Announcements",
                    NotificationCategory.VIP.key to "VIP",
                    NotificationCategory.WALLET.key to "Wallet",
                    NotificationCategory.SECURITY.key to "Security",
                    NotificationCategory.SUPPORT.key to "Support"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterChips.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.goldContainer,
                                selectedLabelColor = palette.gold
                            ),
                            modifier = Modifier.testTag("notif_filter_$key")
                        )
                    }
                }
            }

            if (filteredFeed.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Notifications,
                        title = "No Notifications Found",
                        subtitle = "VIP promotions, security warnings, deposit updates, and admin broadcasts appear here."
                    )
                }
            } else {
                items(filteredFeed, key = { "${it.isAnnouncement}_${it.itemId}" }) { item ->
                    val icon = when (item.category.lowercase()) {
                        "security" -> Icons.Default.Security
                        "vip" -> Icons.Default.WorkspacePremium
                        "announcement", "promotion", "maintenance" -> Icons.Default.Campaign
                        else -> Icons.Default.Notifications
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isRead) palette.surface else palette.surfaceElevated
                        ),
                        border = BorderStroke(
                            width = if (item.isRead) 1.dp else 1.4.dp,
                            color = if (item.isRead) palette.border else palette.gold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_item_${item.itemId}")
                            .clickable { onMarkRead(item) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (item.category == "security") MaterialTheme.colorScheme.errorContainer else palette.goldContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = item.category,
                                        tint = if (item.category == "security") MaterialTheme.colorScheme.error else palette.gold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                        color = palette.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!item.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(palette.gold)
                                        )
                                    }
                                }
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = "${item.category.uppercase()} • ${FintechFormatters.formatDate(item.timestamp)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.goldMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
