package com.example.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.Routes

data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

fun buildPrimaryNavTabs(): List<NavTab> = listOf(
    NavTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(Routes.TASKS, "Tasks", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    NavTab(Routes.WALLET, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    NavTab(Routes.REFERRALS, "Referrals", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
    NavTab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    NavTab(Routes.VIP, "VIP", Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium),
    NavTab(Routes.LEADERBOARD, "Ranks", Icons.Filled.EmojiEvents, Icons.Filled.EmojiEvents),
    NavTab(Routes.NOTIFICATIONS, "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    NavTab(Routes.SECURITY, "Security", Icons.Filled.Security, Icons.Outlined.Security)
)

@Composable
fun ScrollableBottomNavBar(
    tabs: List<NavTab>,
    currentRoute: String,
    unreadAlertsCount: Int,
    onSelectTab: (String) -> Unit
) {
    Surface(
        color = NavigationBarDefaults.containerColor,
        tonalElevation = NavigationBarDefaults.Elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .widthIn(min = 68.dp)
                            .defaultMinSize(minWidth = 68.dp, minHeight = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTab(tab.route) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("nav_${tab.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NavTabIcon(
                                    tab = tab,
                                    selected = selected,
                                    unreadAlertsCount = unreadAlertsCount,
                                    tint = contentColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableNavRail(
    tabs: List<NavTab>,
    currentRoute: String,
    unreadAlertsCount: Int,
    onSelectTab: (String) -> Unit
) {
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelectTab(tab.route) },
                    icon = {
                        NavTabIcon(
                            tab = tab,
                            selected = selected,
                            unreadAlertsCount = unreadAlertsCount,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    label = { Text(tab.label, maxLines = 1) },
                    modifier = Modifier.testTag("rail_${tab.route}")
                )
            }
        }
    }
}

@Composable
private fun NavTabIcon(
    tab: NavTab,
    selected: Boolean,
    unreadAlertsCount: Int,
    tint: androidx.compose.ui.graphics.Color
) {
    val iconVector = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == Routes.NOTIFICATIONS && unreadAlertsCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (unreadAlertsCount > 99) "99+" else "$unreadAlertsCount")
                }
            }
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    } else {
        Icon(
            imageVector = iconVector,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
