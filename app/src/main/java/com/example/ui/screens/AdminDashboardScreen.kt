package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AppConstants
import com.example.data.model.DepositRequest
import com.example.data.model.PaymentAccountConfig
import com.example.data.model.TaskItem
import com.example.data.model.UserProfile
import com.example.data.repository.AdminRepository
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.SimulatedBadge
import com.example.ui.components.StatMiniCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VerifiedAccountBadge
import com.example.ui.theme.AmberPending
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val wallets by adminViewModel.wallets.collectAsStateWithLifecycle()
    val tasks by adminViewModel.tasks.collectAsStateWithLifecycle()
    val userTasks by adminViewModel.userTasks.collectAsStateWithLifecycle()
    val deposits by adminViewModel.deposits.collectAsStateWithLifecycle()
    val withdrawals by adminViewModel.withdrawals.collectAsStateWithLifecycle()
    val paymentAccounts by adminViewModel.paymentAccounts.collectAsStateWithLifecycle()
    val referrals by adminViewModel.referrals.collectAsStateWithLifecycle()
    val commissions by adminViewModel.commissions.collectAsStateWithLifecycle()
    val transactions by adminViewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val settings by adminViewModel.settings.collectAsStateWithLifecycle()
    val isBusy by adminViewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by adminViewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            adminViewModel.clearFeedback()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var userSearch by remember { mutableStateOf("") }
    var userVerificationFilter by remember { mutableStateOf("all") }
    var depositFilter by remember { mutableStateOf("all") }
    var selectedUserDetail by remember { mutableStateOf<UserProfile?>(null) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var previewDepositReceipt by remember { mutableStateOf<DepositRequest?>(null) }
    var reviewingDepositAction by remember { mutableStateOf<Pair<DepositRequest, Boolean>?>(null) } // Boolean = approve

    val tabs = listOf(
        "Overview",
        "Users",
        "Tasks",
        "Deposits",
        "Withdrawals",
        "Payment Accounts",
        "Referrals & Settings",
        "Transactions",
        "Audit Logs"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Admin Console", style = MaterialTheme.typography.titleLarge)
                        SimulatedBadge(label = "ADMIN")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to App")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
            ) {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            modifier = Modifier.testTag("admin_tab_$index")
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // OVERVIEW STATS
                            val activeUsers = users.count { it.accountStatus == "active" }
                            val verifiedUsers = users.count { it.isVerified }
                            val unverifiedUsers = users.size - verifiedUsers
                            val completedTasks = userTasks.count { it.status == "claimed" }
                            val pendingDeposits = deposits.count { it.status.equals("pending", ignoreCase = true) }
                            val pendingWithdrawals = withdrawals.count { it.status.equals("pending", ignoreCase = true) }
                            val pendingCommissions = commissions.count { it.status.equals("pending", ignoreCase = true) }
                            val approvedCommissions = commissions.count { it.status.equals("approved", ignoreCase = true) }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Platform Statistics", style = MaterialTheme.typography.titleLarge)
                                    OutlinedButton(onClick = { adminViewModel.seedDefaults() }, enabled = !isBusy) {
                                        Text("Seed Defaults")
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        StatMiniCard("Registered Users", "${users.size}", Icons.Default.Group, Modifier.weight(1f))
                                        StatMiniCard("Active Users", "$activeUsers", Icons.Default.CheckCircle, Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        StatMiniCard("Verified Users", "$verifiedUsers", Icons.Default.CheckCircle, Modifier.weight(1f))
                                        StatMiniCard("Unverified Users", "$unverifiedUsers", Icons.Default.HourglassTop, Modifier.weight(1f), AmberPending)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        StatMiniCard("Total Tasks", "${tasks.size}", Icons.Default.TaskAlt, Modifier.weight(1f))
                                        StatMiniCard("Completed Tasks", "$completedTasks", Icons.Default.CheckCircle, Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        StatMiniCard("Pending Deposits", "$pendingDeposits", Icons.Default.HourglassTop, Modifier.weight(1f), AmberPending)
                                        StatMiniCard("Pending Withdrawals", "$pendingWithdrawals", Icons.Default.HourglassTop, Modifier.weight(1f), AmberPending)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        StatMiniCard("Total Referrals", "${referrals.size}", Icons.Default.Group, Modifier.weight(1f))
                                        StatMiniCard("Pending Commissions", "$pendingCommissions", Icons.Default.HourglassTop, Modifier.weight(1f), AmberPending)
                                    }
                                    StatMiniCard("Approved Commissions", "$approvedCommissions", Icons.Default.CheckCircle, Modifier.fillMaxWidth())
                                }
                            }
                        }

                        1 -> {
                            // USER MANAGEMENT
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = userSearch,
                                        onValueChange = { userSearch = it },
                                        label = { Text("Search by name, @username, email, or User ID") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            "all" to "All (${users.size})",
                                            "verified" to "Verified (${users.count { it.isVerified }})",
                                            "unverified" to "Unverified (${users.count { !it.isVerified }})"
                                        ).forEach { (key, label) ->
                                            FilterChip(
                                                selected = userVerificationFilter == key,
                                                onClick = { userVerificationFilter = key },
                                                label = { Text(label) },
                                                modifier = Modifier.testTag("admin_users_filter_$key")
                                            )
                                        }
                                    }
                                }
                            }
                            val filteredUsers = users.filter { user ->
                                val matchesSearch = userSearch.isBlank() ||
                                    user.fullName.contains(userSearch, ignoreCase = true) ||
                                    user.username.contains(userSearch, ignoreCase = true) ||
                                    user.email.contains(userSearch, ignoreCase = true) ||
                                    user.uid.contains(userSearch, ignoreCase = true)
                                val matchesVerify = when (userVerificationFilter) {
                                    "verified" -> user.isVerified
                                    "unverified" -> !user.isVerified
                                    else -> true
                                }
                                matchesSearch && matchesVerify
                            }
                            if (filteredUsers.isEmpty()) {
                                item { EmptyStateCard(Icons.Default.Group, "No Matching Users", "Registered accounts appear here.") }
                            } else {
                                items(filteredUsers, key = { it.uid }) { user ->
                                    val liveWalletBalance = wallets.find { it.uid == user.uid }?.balance ?: user.combinedBalance
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${user.fullName} (@${user.username})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Text("${user.email} • UID: ${user.uid.take(10)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("Wallet: ${FintechFormatters.formatPkr(liveWalletBalance)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                                    if (user.isVerified && user.verifiedAt != null) {
                                                        Text(
                                                            text = "Verified: ${FintechFormatters.formatDate(user.verifiedAt)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else if (!user.isVerified && user.firstDepositStatus == "pending") {
                                                        Text(
                                                            text = "First Deposit Pending Review",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberPending
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    VerifiedAccountBadge(isVerified = user.isVerified, isDarkTheme = isDarkTheme)
                                                    StatusPill(status = user.accountStatus, isDarkTheme = isDarkTheme)
                                                }
                                            }
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { selectedUserDetail = user },
                                                    modifier = Modifier.height(44.dp)
                                                ) {
                                                    Text("View History")
                                                }
                                                Button(
                                                    onClick = { adminViewModel.verifyUserAccount(user, !user.isVerified) },
                                                    enabled = !isBusy,
                                                    modifier = Modifier
                                                        .height(44.dp)
                                                        .testTag("admin_verify_user_${user.uid}")
                                                ) {
                                                    Text(if (user.isVerified) "Revoke Verify" else "Verify Account")
                                                }
                                                Button(
                                                    onClick = { adminViewModel.toggleUserStatus(user) },
                                                    enabled = !isBusy,
                                                    colors = if (user.accountStatus == "active") {
                                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                                    } else {
                                                        ButtonDefaults.buttonColors()
                                                    },
                                                    modifier = Modifier.height(44.dp)
                                                ) {
                                                    Text(if (user.accountStatus == "active") "Suspend" else "Activate")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TASK MANAGEMENT
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Daily Task Catalog (${tasks.size})", style = MaterialTheme.typography.titleLarge)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { adminViewModel.initializeTenTasks() },
                                            enabled = !isBusy,
                                            modifier = Modifier.testTag("admin_init_10_tasks_button")
                                        ) {
                                            Text("Init 10 Daily Tasks")
                                        }
                                        Button(
                                            onClick = { showCreateTaskDialog = true },
                                            modifier = Modifier.testTag("admin_create_task_button")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Create Task")
                                        }
                                    }
                                }
                            }
                            items(tasks, key = { it.id }) { task ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            StatusPill(status = if (task.isActive) "active" else "suspended", isDarkTheme = isDarkTheme)
                                        }
                                        Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Reward: ${FintechFormatters.formatPkr(task.rewardPoints)} • Limit: ${task.dailyLimit}/day • Category: ${task.category}", style = MaterialTheme.typography.labelMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { editingTask = task }) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    adminViewModel.saveTask(
                                                        task.id,
                                                        task.title,
                                                        task.description,
                                                        task.category,
                                                        task.rewardPoints,
                                                        task.dailyLimit,
                                                        task.durationSeconds,
                                                        !task.isActive
                                                    )
                                                },
                                                enabled = !isBusy
                                            ) {
                                                Text(if (task.isActive) "Deactivate" else "Activate")
                                            }
                                            IconButton(onClick = { adminViewModel.deleteTask(task) }, enabled = !isBusy) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // DEPOSIT MANAGEMENT
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Deposit Management (${deposits.size})", style = MaterialTheme.typography.titleLarge)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            "all" to "All",
                                            "pending" to "Pending",
                                            "approved" to "Approved",
                                            "rejected" to "Rejected"
                                        ).forEach { (key, label) ->
                                            FilterChip(
                                                selected = depositFilter == key,
                                                onClick = { depositFilter = key },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                            val filteredDeposits = deposits.filter { dep ->
                                depositFilter == "all" || dep.status.equals(depositFilter, ignoreCase = true)
                            }
                            if (filteredDeposits.isEmpty()) {
                                item { EmptyStateCard(Icons.AutoMirrored.Filled.ReceiptLong, "No Deposit Requests", "Deposit requests appear here in real time.") }
                            } else {
                                items(filteredDeposits, key = { it.effectiveDepositId }) { dep ->
                                    val ownerProfile = users.find { it.uid == dep.effectiveUid }
                                    val displayName = dep.fullName.ifBlank { ownerProfile?.fullName ?: "@${dep.username}" }
                                    val displayPhone = dep.userPhone.ifBlank { ownerProfile?.phone ?: "N/A" }
                                    val hasReceipt = !dep.receiptUrl.isNullOrBlank() || !dep.receiptBase64.isNullOrBlank()
                                    val isPending = dep.status.equals("pending", ignoreCase = true)

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Request ID: ${dep.effectiveDepositId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    Text(FintechFormatters.formatPkr(dep.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                }
                                                StatusPill(status = dep.status, isDarkTheme = isDarkTheme)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                            Text("User: $displayName (@${dep.username})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("User ID: ${dep.effectiveUid}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Phone: $displayPhone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "Payment Method: ${dep.paymentMethod}" + if (dep.paymentAccountNumber.isNotBlank()) " (${dep.paymentAccountName} • ${dep.paymentAccountNumber})" else "",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text("Transaction Ref: ${dep.effectiveReference}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text("Submitted: ${FintechFormatters.formatDate(dep.createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = if (hasReceipt) "Uploaded Receipt: Attached ✓" else "Uploaded Receipt: Not attached",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (hasReceipt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!dep.adminNote.isNullOrBlank()) {
                                                Text("Admin Note: ${dep.adminNote}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (!dep.reviewedBy.isNullOrBlank()) {
                                                Text("Reviewed by ${dep.reviewedBy} • ${FintechFormatters.formatDate(dep.reviewedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (hasReceipt) {
                                                    OutlinedButton(
                                                        onClick = { previewDepositReceipt = dep },
                                                        modifier = Modifier.testTag("admin_view_receipt_${dep.effectiveDepositId}")
                                                    ) {
                                                        Text("View Receipt")
                                                    }
                                                }
                                                if (isPending) {
                                                    Button(
                                                        onClick = { reviewingDepositAction = dep to true },
                                                        enabled = !isBusy,
                                                        modifier = Modifier.testTag("admin_approve_dep_${dep.effectiveDepositId}")
                                                    ) {
                                                        Text("Approve Request")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { reviewingDepositAction = dep to false },
                                                        enabled = !isBusy,
                                                        modifier = Modifier.testTag("admin_reject_dep_${dep.effectiveDepositId}")
                                                    ) {
                                                        Text("Reject Request")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // WITHDRAWAL MANAGEMENT
                            if (withdrawals.isEmpty()) {
                                item { EmptyStateCard(Icons.AutoMirrored.Filled.ReceiptLong, "No Withdrawal Requests", "Withdrawal requests appear here.") }
                            } else {
                                items(withdrawals, key = { it.id }) { wd ->
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("@${wd.username} • ${FintechFormatters.formatPkr(wd.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Text("${wd.paymentMethod} • ${wd.accountReference}", style = MaterialTheme.typography.bodyMedium)
                                                    Text("${wd.id} • ${FintechFormatters.formatDate(wd.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                StatusPill(status = wd.status, isDarkTheme = isDarkTheme)
                                            }
                                            if (wd.status.equals("pending", ignoreCase = true)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { adminViewModel.approveWithdrawal(wd) },
                                                        enabled = !isBusy
                                                    ) {
                                                        Text("Approve & Deduct")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { adminViewModel.rejectWithdrawal(wd, "Rejected by admin") },
                                                        enabled = !isBusy
                                                    ) {
                                                        Text("Reject")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            // PAYMENT ACCOUNT SETTINGS
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("PAYMENT ACCOUNT SETTINGS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure receiving account details below. Updates sync in real time to every user's Wallet > Deposit screen.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val mergedAccounts = AdminRepository.DEFAULT_PAYMENT_ACCOUNTS.map { fallback ->
                                paymentAccounts.find { it.providerKey == fallback.providerKey } ?: fallback
                            }
                            items(mergedAccounts, key = { it.providerKey }) { account ->
                                PaymentAccountEditorCard(
                                    account = account,
                                    isBusy = isBusy,
                                    isDarkTheme = isDarkTheme,
                                    onSave = { updated -> adminViewModel.savePaymentAccount(updated) }
                                )
                            }
                        }

                        6 -> {
                            // REFERRAL MANAGEMENT, REWARD & APK RELEASE SETTINGS
                            item {
                                var commInput by remember(settings.referralCommission) {
                                    mutableStateOf(settings.referralCommission.toInt().toString())
                                }
                                var taskRewardInput by remember(settings.defaultTaskReward) {
                                    mutableStateOf(settings.defaultTaskReward.toInt().toString())
                                }
                                var maxDailyInput by remember(settings.maxDailyReward) {
                                    mutableStateOf(settings.maxDailyReward.toInt().toString())
                                }
                                var taskLimitInput by remember(settings.dailyTaskLimit) {
                                    mutableStateOf(settings.dailyTaskLimit.toString())
                                }
                                var minDepositInput by remember(settings.minDeposit) {
                                    mutableStateOf(maxOf(AppConstants.DEFAULT_MIN_DEPOSIT, settings.minDeposit).toInt().toString())
                                }
                                var minWithdrawalInput by remember(settings.minWithdrawal) {
                                    mutableStateOf(maxOf(AppConstants.DEFAULT_MIN_WITHDRAWAL, settings.minWithdrawal).toInt().toString())
                                }
                                var repoSlugInput by remember(settings.githubRepoSlug) {
                                    mutableStateOf(settings.githubRepoSlug)
                                }
                                var latestVersionInput by remember(settings.latestVersionName) {
                                    mutableStateOf(settings.latestVersionName)
                                }
                                var latestCodeInput by remember(settings.latestVersionCode) {
                                    mutableStateOf(if (settings.latestVersionCode > 0L) settings.latestVersionCode.toString() else "")
                                }
                                var apkUrlInput by remember(settings.apkDownloadUrl) {
                                    mutableStateOf(settings.apkDownloadUrl)
                                }
                                var releaseNotesInput by remember(settings.releaseNotes) {
                                    mutableStateOf(settings.releaseNotes)
                                }
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Reward, Commission & APK Release Settings", style = MaterialTheme.typography.titleLarge)
                                        }
                                        OutlinedTextField(
                                            value = commInput,
                                            onValueChange = { commInput = it.filter { c -> c.isDigit() } },
                                            label = { Text("Referral Commission Amount (PKR)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = taskRewardInput,
                                                onValueChange = { taskRewardInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Task Reward (PKR)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = maxDailyInput,
                                                onValueChange = { maxDailyInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Max Daily Reward (PKR)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = minDepositInput,
                                                onValueChange = { minDepositInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Min Deposit (PKR)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = minWithdrawalInput,
                                                onValueChange = { minWithdrawalInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Min Withdrawal (PKR)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        OutlinedTextField(
                                            value = taskLimitInput,
                                            onValueChange = { taskLimitInput = it.filter { c -> c.isDigit() } },
                                            label = { Text("Global Daily Task Limit") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                        Text(
                                            text = "GitHub APK Release & Update Checker",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        OutlinedTextField(
                                            value = repoSlugInput,
                                            onValueChange = { repoSlugInput = it },
                                            label = { Text("GitHub Repository (owner/repo)") },
                                            placeholder = { Text("username/binace-smart-earn") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = latestVersionInput,
                                                onValueChange = { latestVersionInput = it },
                                                label = { Text("Latest Version") },
                                                placeholder = { Text("1.0.2") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = latestCodeInput,
                                                onValueChange = { latestCodeInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Build Code") },
                                                placeholder = { Text("2") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        OutlinedTextField(
                                            value = apkUrlInput,
                                            onValueChange = { apkUrlInput = it },
                                            label = { Text("Public APK Download URL (optional override)") },
                                            placeholder = { Text("https://github.com/owner/repo/releases/latest/download/${AppConstants.APK_ASSET_NAME}") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = releaseNotesInput,
                                            onValueChange = { releaseNotesInput = it },
                                            label = { Text("Release Notes") },
                                            maxLines = 3,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                val comm = commInput.toDoubleOrNull() ?: AppConstants.DEFAULT_REFERRAL_COMMISSION
                                                val rewardVal = taskRewardInput.toDoubleOrNull() ?: AppConstants.DEFAULT_TASK_REWARD
                                                val maxDailyVal = maxDailyInput.toDoubleOrNull() ?: AppConstants.DEFAULT_MAX_DAILY_REWARD
                                                val limit = taskLimitInput.toLongOrNull() ?: AppConstants.DEFAULT_DAILY_TASK_LIMIT
                                                val minDep = (minDepositInput.toDoubleOrNull() ?: AppConstants.DEFAULT_MIN_DEPOSIT)
                                                    .coerceAtLeast(AppConstants.DEFAULT_MIN_DEPOSIT)
                                                val minWd = (minWithdrawalInput.toDoubleOrNull() ?: AppConstants.DEFAULT_MIN_WITHDRAWAL)
                                                    .coerceAtLeast(AppConstants.DEFAULT_MIN_WITHDRAWAL)
                                                val versionCodeVal = latestCodeInput.toLongOrNull() ?: 0L
                                                adminViewModel.updateSettings(
                                                    referralCommission = comm,
                                                    defaultTaskReward = rewardVal,
                                                    maxDailyReward = maxDailyVal,
                                                    dailyTaskLimit = limit,
                                                    minDeposit = minDep,
                                                    minWithdrawal = minWd,
                                                    githubRepoSlug = repoSlugInput,
                                                    latestVersionName = latestVersionInput,
                                                    latestVersionCode = versionCodeVal,
                                                    apkDownloadUrl = apkUrlInput,
                                                    releaseNotes = releaseNotesInput
                                                )
                                            },
                                            enabled = !isBusy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Save Platform & Release Settings")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val rewardVal = taskRewardInput.toDoubleOrNull() ?: 20.0
                                                adminViewModel.applyTaskRewardToAll(rewardVal)
                                            },
                                            enabled = !isBusy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Apply Task Reward (PKR ${taskRewardInput.ifBlank { "20" }}) to All Tasks")
                                        }
                                    }
                                }
                            }

                            item {
                                Text("All Referrals & Commissions (${referrals.size})", style = MaterialTheme.typography.titleLarge)
                            }
                            if (referrals.isEmpty()) {
                                item { EmptyStateCard(Icons.Default.Group, "No Referrals Yet", "Referral relationships appear here for verification.") }
                            } else {
                                items(referrals, key = { it.id }) { ref ->
                                    val referrerUser = users.find { it.uid == ref.referrerUid }
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Referrer: @${referrerUser?.username ?: ref.referrerUid.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Text("Referred: @${ref.referredUsername} (Code: ${ref.referralCode})", style = MaterialTheme.typography.bodyMedium)
                                                    Text("Deposit Verified: ${ref.firstDepositVerified} • Commission: ${FintechFormatters.formatPkr(if (ref.commissionAmount > 0) ref.commissionAmount else settings.referralCommission)}", style = MaterialTheme.typography.labelMedium)
                                                }
                                                StatusPill(status = ref.commissionStatus, isDarkTheme = isDarkTheme)
                                            }
                                            if (ref.commissionStatus != "approved" && ref.commissionStatus != "rejected") {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { adminViewModel.approveCommission(ref) },
                                                        enabled = !isBusy
                                                    ) {
                                                        Text("Verify & Approve PKR ${settings.referralCommission.toInt()}")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { adminViewModel.rejectCommission(ref) },
                                                        enabled = !isBusy
                                                    ) {
                                                        Text("Reject")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        7 -> {
                            // FULL PLATFORM TRANSACTION HISTORY
                            if (transactions.isEmpty()) {
                                item { EmptyStateCard(Icons.AutoMirrored.Filled.ReceiptLong, "No Transactions Yet", "User task rewards, deposits, withdrawals, and referral commissions appear here in real time.") }
                            } else {
                                item {
                                    Text("All Platform Transactions (${transactions.size})", style = MaterialTheme.typography.titleLarge)
                                }
                                items(transactions, key = { it.id }) { tx ->
                                    val ownerUser = users.find { it.uid == tx.uid }
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
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(tx.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                Text("@${ownerUser?.username ?: tx.uid.take(10)} • ${tx.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${tx.id} • ${FintechFormatters.formatShortDate(tx.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = FintechFormatters.formatPkr(tx.amount),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (tx.type == "withdrawal") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                StatusPill(status = tx.status, isDarkTheme = isDarkTheme)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // AUDIT LOGS
                            if (auditLogs.isEmpty()) {
                                item { EmptyStateCard(Icons.AutoMirrored.Filled.ReceiptLong, "No Audit Logs Yet", "Admin actions are recorded immutably here.") }
                            } else {
                                items(auditLogs, key = { it.id }) { log ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                                Text(FintechFormatters.formatShortDate(log.createdAt), style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                            Text("By ${log.adminEmail} • ${log.targetCollection}/${log.targetId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedUserDetail?.let { user ->
        val userDeps = deposits.count { it.effectiveUid == user.uid }
        val userWds = withdrawals.count { it.uid == user.uid }
        val userComms = commissions.filter { it.referrerUid == user.uid && it.status == "approved" }.sumOf { it.amount }
        AlertDialog(
            onDismissRequest = { selectedUserDetail = null },
            title = { Text("@${user.username} Account History") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${user.fullName}")
                    Text("UID: ${user.uid}")
                    Text("Wallet ID: ${user.walletId}")
                    Text("Phone: ${user.phone}")
                    Text("Wallet Balance: ${FintechFormatters.formatPkr(user.realBalance)}")
                    Text("Reward Balance: ${FintechFormatters.formatPkr(user.realRewardBalance)}")
                    Text("Completed Tasks: ${user.completedTasksCount}")
                    Text("Deposit Requests: $userDeps | Withdrawals: $userWds")
                    Text("Approved Commissions: ${FintechFormatters.formatPkr(userComms)}")
                }
            },
            confirmButton = {
                Button(onClick = { selectedUserDetail = null }) { Text("Close") }
            }
        )
    }

    previewDepositReceipt?.let { dep ->
        val bmp = remember(dep.receiptBase64) { FintechFormatters.decodeBase64Bitmap(dep.receiptBase64) }
        AlertDialog(
            onDismissRequest = { previewDepositReceipt = null },
            title = { Text("Deposit Receipt (${dep.effectiveDepositId})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!dep.receiptUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = dep.receiptUrl,
                            contentDescription = "Uploaded deposit receipt",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Uploaded deposit receipt",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text("Could not decode receipt image.")
                    }
                    if (!dep.receiptStoragePath.isNullOrBlank()) {
                        Text("Storage Path: ${dep.receiptStoragePath}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { previewDepositReceipt = null }) { Text("Close") }
            }
        )
    }

    reviewingDepositAction?.let { (dep, isApprove) ->
        var noteInput by remember(dep.effectiveDepositId, isApprove) {
            mutableStateOf(
                if (isApprove) "Verified by admin" else "Invalid or unverified transaction reference"
            )
        }
        AlertDialog(
            onDismissRequest = { reviewingDepositAction = null },
            title = {
                Text(if (isApprove) "Approve Deposit ${dep.effectiveDepositId}" else "Reject Deposit ${dep.effectiveDepositId}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${dep.fullName.ifBlank { "@${dep.username}" }} • ${FintechFormatters.formatPkr(dep.amount)} via ${dep.paymentMethod}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Ref: ${dep.effectiveReference}", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Admin Note") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_deposit_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isApprove) {
                            adminViewModel.approveDeposit(dep, noteInput)
                        } else {
                            adminViewModel.rejectDeposit(dep, noteInput)
                        }
                        reviewingDepositAction = null
                    },
                    enabled = !isBusy,
                    colors = if (isApprove) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.testTag("admin_confirm_deposit_review_button")
                ) {
                    Text(if (isApprove) "Confirm Approval" else "Confirm Rejection")
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewingDepositAction = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateTaskDialog || editingTask != null) {
        TaskEditorDialog(
            existing = editingTask,
            isBusy = isBusy,
            onDismiss = {
                showCreateTaskDialog = false
                editingTask = null
            },
            onSave = { title, desc, cat, reward, limit, duration, active ->
                adminViewModel.saveTask(editingTask?.id, title, desc, cat, reward, limit, duration, active)
                showCreateTaskDialog = false
                editingTask = null
            }
        )
    }
}

@Composable
private fun PaymentAccountEditorCard(
    account: PaymentAccountConfig,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    onSave: (PaymentAccountConfig) -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember(account.providerKey, account.isEnabled) { mutableStateOf(account.isEnabled) }
    var bankName by remember(account.providerKey, account.bankName) { mutableStateOf(account.bankName) }
    var holderName by remember(account.providerKey, account.accountHolderName) { mutableStateOf(account.accountHolderName) }
    var accountNumber by remember(account.providerKey, account.accountNumber) { mutableStateOf(account.accountNumber) }
    var iban by remember(account.providerKey, account.iban) { mutableStateOf(account.iban) }
    var instructions by remember(account.providerKey, account.instructions) { mutableStateOf(account.instructions) }
    var qrCodeData by remember(account.providerKey, account.qrCodeData) { mutableStateOf(account.qrCodeData) }

    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                qrCodeData = FintechFormatters.encodeReceiptThumbnail(context, uri)
            }
        }
    )

    Card(
        shape = RoundedCornerShape(18.dp),
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
                Column {
                    Text(account.providerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusPill(status = if (isEnabled) "active" else "suspended", isDarkTheme = isDarkTheme)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnabled) "Enabled" else "Disabled", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("admin_payment_switch_${account.providerKey}")
                    )
                }
            }

            if (account.providerKey == "bank_transfer") {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("Account Holder Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_payment_holder_${account.providerKey}")
            )
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it },
                label = { Text("Account Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_payment_number_${account.providerKey}")
            )
            if (account.providerKey == "bank_transfer") {
                OutlinedTextField(
                    value = iban,
                    onValueChange = { iban = it },
                    label = { Text("IBAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Account Instructions") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (account.providerKey != "bank_transfer") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            qrPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (qrCodeData.isNullOrBlank()) "Upload QR Code (Optional)" else "Replace QR Code")
                    }
                    if (!qrCodeData.isNullOrBlank()) {
                        OutlinedButton(onClick = { qrCodeData = null }) {
                            Text("Remove")
                        }
                    }
                }
                val qrBmp = remember(qrCodeData) { FintechFormatters.decodeBase64Bitmap(qrCodeData) }
                if (qrBmp != null) {
                    Image(
                        bitmap = qrBmp.asImageBitmap(),
                        contentDescription = "${account.providerName} QR code preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        account.copy(
                            bankName = bankName,
                            accountHolderName = holderName,
                            accountNumber = accountNumber,
                            iban = iban,
                            instructions = instructions,
                            qrCodeData = qrCodeData,
                            isEnabled = isEnabled
                        )
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_save_payment_${account.providerKey}")
            ) {
                Text("Save ${account.providerName} Account")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorDialog(
    existing: TaskItem?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Long, Long, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "daily_activity") }
    var reward by remember { mutableStateOf((existing?.rewardPoints ?: 25.0).toInt().toString()) }
    var limit by remember { mutableStateOf((existing?.dailyLimit ?: 1L).toString()) }
    var duration by remember { mutableStateOf((existing?.durationSeconds ?: 0L).toString()) }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Daily Task" else "Edit Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 3)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("daily_login", "watch_video", "daily_activity", "referral_activity").forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reward,
                        onValueChange = { reward = it.filter { c -> c.isDigit() } },
                        label = { Text("Reward (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it.filter { c -> c.isDigit() } },
                        label = { Text("Daily Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Wait Duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Active")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        desc,
                        category,
                        reward.toDoubleOrNull() ?: 25.0,
                        (limit.toLongOrNull() ?: 1L).coerceIn(1L, 10L),
                        (duration.toLongOrNull() ?: 0L).coerceIn(0L, 600L),
                        isActive
                    )
                },
                enabled = !isBusy && title.isNotBlank() && desc.isNotBlank()
            ) {
                Text("Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
