package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportAttachmentUploadState
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.SupportPalette
import com.example.ui.theme.rememberSupportPalette
import com.example.ui.viewmodel.SupportViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveSupportScreen(
    supportViewModel: SupportViewModel,
    profile: UserProfile,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenTicketChat: (String) -> Unit,
    onOpenWhatsAppChannel: () -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = rememberSupportPalette(isDarkTheme)
    val context = LocalContext.current

    val tickets by supportViewModel.userTickets.collectAsStateWithLifecycle()
    val onlineAgentsCount by supportViewModel.onlineAgentsCount.collectAsStateWithLifecycle()
    val unreadTotal by supportViewModel.userUnreadTotal.collectAsStateWithLifecycle()
    val attachmentState by supportViewModel.attachmentState.collectAsStateWithLifecycle()
    val isBusy by supportViewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by supportViewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            supportViewModel.clearFeedback()
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            supportViewModel.syncFcmToken(granted)
        }
    )

    var selectedStatusFilter by remember { mutableStateOf("all") }
    var selectedCategoryFilter by remember { mutableStateOf<SupportCategory?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var draftCategory by remember { mutableStateOf(SupportCategory.DEPOSITS) }

    val filteredTickets = tickets.filter { ticket ->
        val matchesStatus = when (selectedStatusFilter) {
            "open" -> ticket.statusEnum == SupportTicketStatus.OPEN
            "pending" -> ticket.statusEnum == SupportTicketStatus.PENDING
            "resolved" -> ticket.statusEnum == SupportTicketStatus.RESOLVED
            else -> true
        }
        val matchesCategory = selectedCategoryFilter == null || ticket.categoryEnum == selectedCategoryFilter
        matchesStatus && matchesCategory
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
                    title = "Live Customer Support",
                    subtitle = if (onlineAgentsCount > 0) {
                        "$onlineAgentsCount live agent${if (onlineAgentsCount == 1) "" else "s"} available now"
                    } else {
                        "Submit an offline support ticket anytime"
                    },
                    palette = palette,
                    onBack = onBack,
                    backTestTag = "support_back_button",
                    trailingContent = {
                        IconButton(
                            onClick = onOpenWhatsAppChannel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("support_whatsapp_channel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "Open WhatsApp Support Channel",
                                tint = palette.gold
                            )
                        }
                    }
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.65f)),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(palette.headerStart, palette.headerEnd)
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OnlineAvailabilityBadge(
                                        onlineAgentsCount = onlineAgentsCount,
                                        palette = palette
                                    )
                                    if (unreadTotal > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = palette.gold
                                        ) {
                                            Text(
                                                text = "$unreadTotal Unread",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.onGold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (onlineAgentsCount > 0) {
                                        "Priority Live Support Desk"
                                    } else {
                                        "24/7 Offline Ticket Queue"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (onlineAgentsCount > 0) {
                                        "Chat directly with verified agents in real time for deposits, withdrawals, daily tasks, referrals, or account issues."
                                    } else {
                                        "Support agents are currently offline. Submit an offline ticket with screenshots and receive an alert as soon as an agent replies."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFDBEAFE)
                                )
                                Button(
                                    onClick = {
                                        draftCategory = SupportCategory.DEPOSITS
                                        showCreateDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.gold,
                                        contentColor = palette.onGold
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("support_new_ticket_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (onlineAgentsCount > 0) "Start Live Support Chat" else "Submit Offline Ticket",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.goldContainer),
                            border = BorderStroke(1.dp, palette.gold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = palette.gold
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Support Alerts",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onGoldContainer
                                    )
                                    Text(
                                        text = "Receive instant notifications when an agent replies to your ticket.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.onGoldContainer
                                    )
                                }
                                Button(
                                    onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Enable", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Quick Issue Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Tap any topic below to start a pre-filled live support conversation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        SupportCategorySelectorGrid(
                            selected = draftCategory,
                            palette = palette,
                            onSelect = { category ->
                                draftCategory = category
                                showCreateDialog = true
                            }
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "My Support History (${tickets.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            TextButton(onClick = onOpenWhatsAppChannel) {
                                Text("WhatsApp Channel", color = palette.gold)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "all" to "All (${tickets.size})",
                                "open" to "Open",
                                "pending" to "Pending",
                                "resolved" to "Resolved"
                            ).forEach { (key, label) ->
                                val isSelected = selectedStatusFilter == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedStatusFilter = key },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.royalBlue,
                                        selectedLabelColor = Color.White,
                                        containerColor = palette.surface,
                                        labelColor = palette.textPrimary
                                    ),
                                    modifier = Modifier.testTag("support_filter_$key")
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All Topics") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.gold,
                                    selectedLabelColor = palette.onGold
                                )
                            )
                            SupportCategory.entries.forEach { cat ->
                                val selected = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCategoryFilter = if (selected) null else cat
                                    },
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
                            icon = Icons.Default.SupportAgent,
                            title = if (tickets.isEmpty()) "No Support Conversations Yet" else "No Matching Tickets",
                            subtitle = if (tickets.isEmpty()) {
                                "Tap 'Start Live Support Chat' or select a quick issue category above to connect with our team."
                            } else {
                                "Try selecting a different status or category filter."
                            }
                        )
                    }
                } else {
                    items(filteredTickets, key = { it.effectiveTicketId }) { ticket ->
                        SupportTicketSummaryCard(
                            ticket = ticket,
                            palette = palette,
                            unreadCount = ticket.unreadByUserCount,
                            onClick = { onOpenTicketChat(ticket.effectiveTicketId) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSupportTicketDialog(
            initialCategory = draftCategory,
            isOfflineMode = onlineAgentsCount == 0,
            attachmentState = attachmentState,
            isBusy = isBusy,
            palette = palette,
            onDismiss = {
                supportViewModel.clearAttachment()
                showCreateDialog = false
            },
            onSelectImage = { uri ->
                supportViewModel.selectAndUploadAttachment(context, uri, ticketIdHint = "new_tickets")
            },
            onRemoveAttachment = { supportViewModel.clearAttachment() },
            onSubmit = { category, subject, message ->
                supportViewModel.createTicket(
                    profile = profile,
                    category = category,
                    subject = subject,
                    initialMessage = message,
                    onCreated = { ticketId ->
                        showCreateDialog = false
                        onOpenTicketChat(ticketId)
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportTicketSummaryCard(
    ticket: SupportTicket,
    palette: SupportPalette,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (unreadCount > 0) palette.gold else palette.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_ticket_card_${ticket.effectiveTicketId}")
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ticket.effectiveTicketId,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.electricBlue,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (unreadCount > 0) {
                        Surface(shape = RoundedCornerShape(50), color = palette.gold) {
                            Text(
                                text = "$unreadCount new",
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
                if (ticket.assignedAgentName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = palette.blueContainer
                    ) {
                        Text(
                            text = "Agent: ${ticket.assignedAgentName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onBlueContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "Updated ${FintechFormatters.formatDate(ticket.lastMessageAt ?: ticket.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun CreateSupportTicketDialog(
    initialCategory: SupportCategory,
    isOfflineMode: Boolean,
    attachmentState: SupportAttachmentUploadState,
    isBusy: Boolean,
    palette: SupportPalette,
    onDismiss: () -> Unit,
    onSelectImage: (android.net.Uri) -> Unit,
    onRemoveAttachment: () -> Unit,
    onSubmit: (SupportCategory, String, String) -> Unit
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var subject by remember(initialCategory) { mutableStateOf("${initialCategory.label} Support") }
    var message by remember(initialCategory) { mutableStateOf(initialCategory.quickPrompt) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) onSelectImage(uri)
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textPrimary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isOfflineMode) "Submit Offline Support Ticket" else "Start Live Support Chat",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOfflineMode) {
                        "No agents are currently online. Your ticket enters the priority queue."
                    } else {
                        "Online support agents will receive your message immediately."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.gold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SupportCategorySelectorGrid(
                    selected = category,
                    palette = palette,
                    onSelect = { picked ->
                        category = picked
                        subject = "${picked.label} Support"
                        if (message.isBlank() || SupportCategory.entries.any { it.quickPrompt == message }) {
                            message = picked.quickPrompt
                        }
                    }
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(140) },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_dialog_subject_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(2000) },
                    label = { Text("Describe your issue") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_dialog_message_input")
                )
                SupportAttachmentPreviewBar(
                    attachmentState = attachmentState,
                    palette = palette,
                    onRemove = onRemoveAttachment
                )
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = attachmentState !is SupportAttachmentUploadState.Uploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("support_dialog_attach_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attach Screenshot (Photo Picker)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(category, subject, message) },
                enabled = !isBusy &&
                    attachmentState !is SupportAttachmentUploadState.Uploading &&
                    subject.trim().length >= 2 &&
                    (message.isNotBlank() || attachmentState is SupportAttachmentUploadState.Ready),
                colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("support_dialog_submit_button")
            ) {
                Text(if (isOfflineMode) "Submit Ticket" else "Start Chat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}
