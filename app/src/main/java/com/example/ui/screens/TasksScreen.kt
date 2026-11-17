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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.TaskItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserTaskRecord
import com.example.ui.components.AccountVerificationNoticeCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.SimulatedBadge
import com.example.ui.components.StatusPill
import kotlinx.coroutines.delay

@Composable
fun TasksScreen(
    profile: UserProfile,
    settings: AppSettings = AppSettings(),
    todayDateKey: String,
    tasksState: UiState<List<TaskItem>>,
    userTasks: List<UserTaskRecord>,
    isActionBusy: Boolean,
    isDarkTheme: Boolean,
    onStartTask: (TaskItem) -> Unit,
    onClaimReward: (TaskItem) -> Unit,
    onNavigateDeposit: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Daily Tasks, 1 = Completion History
    val timers = remember { mutableStateMapOf<String, Int>() }

    // Countdown tick for active timed tasks (e.g., Watch Video)
    val activeKeys = timers.filterValues { it > 0 }.keys.toList()
    LaunchedEffect(activeKeys) {
        if (activeKeys.isNotEmpty()) {
            delay(1000L)
            activeKeys.forEach { key ->
                val current = timers[key] ?: 0
                if (current > 0) timers[key] = current - 1
            }
        }
    }

    val todayMap = remember(userTasks, todayDateKey) {
        userTasks.filter { it.dateKey == todayDateKey }.associateBy { it.taskId }
    }
    val completedTodayCount = todayMap.values.count { it.status == "claimed" }
    val todayEarned = if (profile.todayTaskDate == todayDateKey) profile.todayTaskEarnings else 0.0
    val pendingTodayCount = when (tasksState) {
        is UiState.Success -> tasksState.data.count { todayMap[it.id]?.status != "claimed" }
        else -> 0
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Resets 12:00 AM PKT ($todayDateKey) • Daily Limit: PKR ${todayEarned.toInt()} / ${settings.maxDailyReward.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SimulatedBadge(label = "SIMULATED TASKS")
                }
            }

            if (!profile.isVerified) {
                item {
                    AccountVerificationNoticeCard(
                        firstDepositStatus = profile.firstDepositStatus,
                        isDarkTheme = isDarkTheme,
                        title = "Daily Tasks Locked Until Verification",
                        message = "Submit a simulated PKR 1,000 demo deposit in Wallet > Deposit. Once approved by an admin, all 10 daily tasks (PKR 20 simulated reward each) unlock automatically.",
                        actionLabel = "Go to Simulated Deposit",
                        onActionClick = onNavigateDeposit
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pending Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pendingTodayCount tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Completed Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$completedTodayCount tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("All-Time Earned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FintechFormatters.formatPkr(profile.totalEarnedTasks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Today's Tasks") },
                        modifier = Modifier.testTag("tasks_tab_daily")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Completion History (${userTasks.count { it.status == "claimed" }})") },
                        modifier = Modifier.testTag("tasks_tab_history")
                    )
                }
            }

            if (selectedTab == 0) {
                when (tasksState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.TaskAlt,
                                title = "Unable to load tasks",
                                subtitle = tasksState.message
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (tasksState.data.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.TaskAlt,
                                    title = "Initializing Daily Tasks",
                                    subtitle = "Active daily tasks are syncing in real time. An administrator can also manage tasks in the Admin Console."
                                )
                            }
                        } else {
                            items(tasksState.data, key = { it.id }) { task ->
                                val record = todayMap[task.id]
                                val remainingSeconds = timers[task.id] ?: 0
                                DailyTaskCard(
                                    task = task,
                                    record = record,
                                    remainingSeconds = remainingSeconds,
                                    isVerified = profile.isVerified,
                                    isActionBusy = isActionBusy,
                                    isDarkTheme = isDarkTheme,
                                    onStart = {
                                        if (task.durationSeconds > 0L) {
                                            timers[task.id] = task.durationSeconds.toInt()
                                        }
                                        onStartTask(task)
                                    },
                                    onClaim = { onClaimReward(task) }
                                )
                            }
                        }
                    }
                }
            } else {
                val claimedHistory = userTasks.filter { it.status == "claimed" }
                if (claimedHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.CheckCircle,
                            title = "No Completed Tasks Yet",
                            subtitle = "Start and claim your first daily task to view your reward history."
                        )
                    }
                } else {
                    items(claimedHistory, key = { it.id }) { rec ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.taskTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = FintechFormatters.formatDate(rec.completedAt ?: rec.updatedAt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${FintechFormatters.formatPkr(rec.rewardPoints)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusPill(status = rec.status, isDarkTheme = isDarkTheme)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTaskCard(
    task: TaskItem,
    record: UserTaskRecord?,
    remainingSeconds: Int,
    isVerified: Boolean,
    isActionBusy: Boolean,
    isDarkTheme: Boolean,
    onStart: () -> Unit,
    onClaim: () -> Unit
) {
    val status = record?.status ?: "not_started"
    val icon = when (task.category) {
        "daily_login" -> Icons.AutoMirrored.Filled.Login
        "watch_video" -> Icons.Default.PlayCircle
        "referral_activity" -> Icons.Default.CardGiftcard
        else -> Icons.Default.TaskAlt
    }
    val categoryLabel = when (task.category) {
        "daily_login" -> "Daily Login"
        "watch_video" -> "Educational Video"
        "referral_activity" -> "Referral Activity"
        else -> "Daily Activity"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = categoryLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusPill(
                    status = if (status == "not_started") "pending" else status,
                    isDarkTheme = isDarkTheme
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (remainingSeconds > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verifying activity... ${remainingSeconds}s remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val total = task.durationSeconds.coerceAtLeast(1L).toFloat()
                LinearProgressIndicator(
                    progress = { ((total - remainingSeconds) / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Simulated Reward",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+${FintechFormatters.formatPkr(task.rewardPoints)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                when (status) {
                    "claimed" -> {
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Claimed Today")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FintechFormatters.formatShortDate(record?.completedAt ?: record?.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    "in_progress" -> {
                        Button(
                            onClick = onClaim,
                            enabled = isVerified && !isActionBusy && remainingSeconds == 0,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("claim_task_${task.id}")
                        ) {
                            Text(if (!isVerified) "Verify First" else if (remainingSeconds > 0) "Wait ${remainingSeconds}s" else "Claim Reward")
                        }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onStart,
                                enabled = isVerified && !isActionBusy,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("start_task_${task.id}")
                            ) {
                                Text(if (isVerified) "Start Task" else "Locked")
                            }
                            if (task.durationSeconds == 0L) {
                                Button(
                                    onClick = onClaim,
                                    enabled = isVerified && !isActionBusy,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("claim_direct_${task.id}")
                                ) {
                                    Text("Claim")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
