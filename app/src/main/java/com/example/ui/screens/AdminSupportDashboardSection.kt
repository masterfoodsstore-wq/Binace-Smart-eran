package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MarkUnreadChatAlt
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportAgent
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.SupportPalette
import com.example.ui.theme.rememberSupportPalette
import com.example.ui.viewmodel.SupportViewModel

@Composable
fun SupportAgentConsoleScreen(
    supportViewModel: SupportViewModel,
    viewerProfile: UserProfile,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenSupportChat: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = rememberSupportPalette(isDarkTheme)
    val feedback by supportViewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            supportViewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.headerStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                SupportGradientHeader(
                    title = "Support Agent Console",
                    subtitle = "Multi-agent real-time ticket desk",
                    palette = palette,
                    onBack = onBack,
                    backTestTag = "agent_console_back_button"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
            ) {
                AdminSupportQueuePanel(
                    supportViewModel = supportViewModel,
                    viewerProfile = viewerProfile,
                    allUsers = emptyList(),
                    isFullAdmin = false,
                    isDarkTheme = isDarkTheme,
                    onOpenSupportChat = onOpenSupportChat
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSupportQueuePanel(
    supportViewModel: SupportViewModel,
    viewerProfile: UserProfile,
    allUsers: List<UserProfile>,
    isFullAdmin: Boolean,
    isDarkTheme: Boolean,
    onOpenSupportChat: (String) -> Unit
) {
    val palette = rememberSupportPalette(isDarkTheme)

    val staffTickets by supportViewModel.staffTickets.collectAsStateWithLifecycle()
    val activeAgents by supportViewModel.supportAgents.collectAsStateWithLifecycle()
    val allAgentRecords by supportViewModel.allAgentRecords.collectAsStateWithLifecycle()
    val myAgentRecord by supportViewModel.myAgentRecord.collectAsStateWithLifecycle()
    val isBusy by supportViewModel.isBusy.collectAsStateWithLifecycle()

    var queueFilter by remember { mutableStateOf("all") }
    var categoryFilter by remember { mutableStateOf<SupportCategory?>(null) }
    var showManageAgentDialog by remember { mutableStateOf(false) }
    var assigningTicket by remember { mutableStateOf<SupportTicket?>(null) }

    val myDisplayName = remember(viewerProfile, myAgentRecord) {
        myAgentRecord?.displayName?.ifBlank { viewerProfile.fullName }
            ?: viewerProfile.fullName.ifBlank { "@${viewerProfile.username}" }
    }
    val amIOnline = myAgentRecord?.isOnline == true

    // Live metrics computed directly from Firestore snapshot state (no hardcoded stats)
    val openCount = staffTickets.count { it.statusEnum == SupportTicketStatus.OPEN }
    val pendingCount = staffTickets.count { it.statusEnum == SupportTicketStatus.PENDING }
    val resolvedCount = staffTickets.count { it.statusEnum == SupportTicketStatus.RESOLVED }
    val unreadCount = staffTickets.sumOf { it.unreadByAgentCount }
    val offlineTicketsCount = staffTickets.count { it.isOfflineTicket && it.statusEnum != SupportTicketStatus.RESOLVED }

    val filteredTickets = staffTickets.filter { ticket ->
        val matchesQueue = when (queueFilter) {
            "open" -> ticket.statusEnum == SupportTicketStatus.OPEN
            "pending" -> ticket.statusEnum == SupportTicketStatus.PENDING
            "resolved" -> ticket.statusEnum == SupportTicketStatus.RESOLVED
            "unassigned" -> ticket.assignedAgentUid.isBlank()
            "mine" -> ticket.assignedAgentUid == viewerProfile.uid
            else -> true
        }
        val matchesCategory = categoryFilter == null || ticket.categoryEnum == categoryFilter
        matchesQueue && matchesCategory
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, if (amIOnline) palette.onlineGreen else palette.gold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (amIOnline) "Desk Status: ONLINE" else "Desk Status: OFFLINE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (amIOnline) palette.onlineGreen else palette.gold
                        )
                        Text(
                            text = if (amIOnline) {
                                "Signed-in users see live chat availability as online."
                            } else {
                                "Switch on to accept live customer chats as $myDisplayName."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = amIOnline,
                        onCheckedChange = { checked ->
                            supportViewModel.toggleMyOnlineStatus(myDisplayName, checked)
                        },
                        enabled = !isBusy,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onGold,
                            checkedTrackColor = palette.gold
                        ),
                        modifier = Modifier.testTag("agent_online_toggle_switch")
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Live Queue Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportMetricTile("Open", "$openCount", palette.electricBlue, palette, Modifier.weight(1f))
                    SupportMetricTile("Pending", "$pendingCount", palette.gold, palette, Modifier.weight(1f))
                    SupportMetricTile("Resolved", "$resolvedCount", palette.onlineGreen, palette, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportMetricTile("Unread Msgs", "$unreadCount", palette.gold, palette, Modifier.weight(1f))
                    SupportMetricTile("Offline Queue", "$offlineTicketsCount", palette.offlineAmber, palette, Modifier.weight(1f))
                    SupportMetricTile("Online Agents", "${activeAgents.count { it.isOnline }}", palette.onlineGreen, palette, Modifier.weight(1f))
                }
            }
        }

        if (isFullAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multi-Agent Team (${allAgentRecords.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Authorize verified agents to answer tickets cleanly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )
                            }
                            Button(
                                onClick = { showManageAgentDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("admin_add_support_agent_button")
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Agent", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (allAgentRecords.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                allAgentRecords.forEach { agent ->
                                    SupportAgentRosterRow(
                                        agent = agent,
                                        palette = palette,
                                        isBusy = isBusy,
                                        onToggleActive = {
                                            supportViewModel.upsertSupportAgent(
                                                uid = agent.uid,
                                                displayName = agent.displayName,
                                                role = agent.role,
                                                isActive = !agent.isActive
                                            )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "All (${staffTickets.size})",
                        "open" to "Open ($openCount)",
                        "pending" to "Pending ($pendingCount)",
                        "unassigned" to "Unassigned",
                        "mine" to "Assigned to Me",
                        "resolved" to "Resolved"
                    ).forEach { (key, label) ->
                        val selected = queueFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { queueFilter = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.royalBlue,
                                selectedLabelColor = Color.White,
                                containerColor = palette.surface,
                                labelColor = palette.textPrimary
                            ),
                            modifier = Modifier.testTag("staff_support_filter_$key")
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("All Categories") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.gold,
                            selectedLabelColor = palette.onGold
                        )
                    )
                    SupportCategory.entries.forEach { cat ->
                        val selected = categoryFilter == cat
                        FilterChip(
                            selected = selected,
                            onClick = { categoryFilter = if (selected) null else cat },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.gold,
                                selectedLabelColor = palette.onGold
                            )
                        )
                    }
                }
            }
        }

        if (filteredTickets.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.MarkUnreadChatAlt,
                    title = "No Support Tickets in Queue",
                    subtitle = "Incoming user live chats and offline tickets appear here in real time."
                )
            }
        } else {
            items(filteredTickets, key = { it.effectiveTicketId }) { ticket ->
                StaffTicketQueueCard(
                    ticket = ticket,
                    viewerUid = viewerProfile.uid,
                    viewerName = myDisplayName,
                    palette = palette,
                    isBusy = isBusy,
                    onAssignToMe = {
                        supportViewModel.assignTicket(ticket.effectiveTicketId, viewerProfile.uid, myDisplayName)
                    },
                    onOpenAssignDialog = { assigningTicket = ticket },
                    onOpenChat = { onOpenSupportChat(ticket.effectiveTicketId) }
                )
            }
        }
    }

    if (showManageAgentDialog) {
        AddSupportAgentDialog(
            allUsers = allUsers,
            existingAgentUids = allAgentRecords.map { it.uid }.toSet(),
            palette = palette,
            isBusy = isBusy,
            onDismiss = { showManageAgentDialog = false },
            onSaveAgent = { uid, displayName ->
                supportViewModel.upsertSupportAgent(
                    uid = uid,
                    displayName = displayName,
                    role = "support_agent",
                    isActive = true
                )
                showManageAgentDialog = false
            }
        )
    }

    assigningTicket?.let { ticket ->
        AssignSupportAgentDialog(
            currentAgentUid = ticket.assignedAgentUid,
            viewerUid = viewerProfile.uid,
            viewerName = myDisplayName,
            activeAgents = activeAgents,
            palette = palette,
            onDismiss = { assigningTicket = null },
            onSelectAgent = { uid, name ->
                supportViewModel.assignTicket(ticket.effectiveTicketId, uid, name)
                assigningTicket = null
            }
        )
    }
}

@Composable
private fun SupportMetricTile(
    title: String,
    value: String,
    accent: Color,
    palette: SupportPalette,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun SupportAgentRosterRow(
    agent: SupportAgent,
    palette: SupportPalette,
    isBusy: Boolean,
    onToggleActive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (agent.isOnline) palette.onlineGreen else palette.offlineAmber)
            )
            Column {
                Text(
                    text = "${agent.displayName} (${agent.role})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Text(
                    text = "UID: ${agent.uid.take(14)} • ${if (agent.isActive) "Active" else "Disabled"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
        OutlinedButton(
            onClick = onToggleActive,
            enabled = !isBusy,
            modifier = Modifier.height(42.dp)
        ) {
            Text(if (agent.isActive) "Disable" else "Enable")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffTicketQueueCard(
    ticket: SupportTicket,
    viewerUid: String,
    viewerName: String,
    palette: SupportPalette,
    isBusy: Boolean,
    onAssignToMe: () -> Unit,
    onOpenAssignDialog: () -> Unit,
    onOpenChat: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (ticket.unreadByAgentCount > 0) palette.gold else palette.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_ticket_card_${ticket.effectiveTicketId}")
            .clickable(onClick = onOpenChat)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${ticket.userName.ifBlank { "@${ticket.username}" }} (@${ticket.username})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "${ticket.effectiveTicketId} • ${FintechFormatters.formatDate(ticket.lastMessageAt ?: ticket.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (ticket.unreadByAgentCount > 0) {
                        Surface(shape = RoundedCornerShape(50), color = palette.gold) {
                            Text(
                                text = "${ticket.unreadByAgentCount} unread",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.onGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    SupportStatusPill(status = ticket.statusEnum, palette = palette)
                }
            }

            Text(
                text = ticket.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.electricBlue
            )
            Text(
                text = ticket.lastMessageText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupportCategoryChip(category = ticket.categoryEnum, palette = palette)
                if (ticket.isOfflineTicket) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = palette.offlineAmber.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "OFFLINE TICKET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.offlineAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (ticket.assignedAgentName.isNotBlank()) palette.blueContainer else palette.surfaceElevated
                ) {
                    Text(
                        text = if (ticket.assignedAgentName.isNotBlank()) {
                            "Assigned: ${ticket.assignedAgentName}"
                        } else {
                            "Unassigned"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.royalBlue, contentColor = Color.White),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Chat")
                }
                if (ticket.assignedAgentUid != viewerUid) {
                    OutlinedButton(
                        onClick = onAssignToMe,
                        enabled = !isBusy,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Take ($viewerName)")
                    }
                }
                OutlinedButton(
                    onClick = onOpenAssignDialog,
                    enabled = !isBusy,
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reassign")
                }
            }
        }
    }
}

@Composable
private fun AddSupportAgentDialog(
    allUsers: List<UserProfile>,
    existingAgentUids: Set<String>,
    palette: SupportPalette,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveAgent: (uid: String, displayName: String) -> Unit
) {
    var uidInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    val candidateUsers = remember(allUsers, existingAgentUids) {
        allUsers.filter { it.uid !in existingAgentUids }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textPrimary,
        title = { Text("Authorize Support Agent", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidateUsers.isNotEmpty()) {
                    Text(
                        text = "Quick Select Registered User:",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.gold
                    )
                    candidateUsers.forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                uidInput = candidate.uid
                                nameInput = candidate.fullName.ifBlank { "@${candidate.username}" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${candidate.fullName} (@${candidate.username})")
                        }
                    }
                }
                OutlinedTextField(
                    value = uidInput,
                    onValueChange = { uidInput = it.trim() },
                    label = { Text("Firebase User UID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_agent_uid_input")
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(80) },
                    label = { Text("Agent Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_agent_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAgent(uidInput, nameInput) },
                enabled = !isBusy && uidInput.length >= 2 && nameInput.trim().length >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save Agent", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        }
    )
}
