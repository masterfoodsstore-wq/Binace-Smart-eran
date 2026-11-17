package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportAgent
import com.example.data.model.SupportAttachmentUploadState
import com.example.data.model.SupportMessage
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.SupportPalette
import com.example.ui.theme.rememberSupportPalette
import com.example.ui.viewmodel.SupportViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportChatScreen(
    supportViewModel: SupportViewModel,
    ticketId: String,
    isAgentView: Boolean,
    viewerProfile: UserProfile,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    val senderName = remember(viewerProfile, isAgentView) {
        viewerProfile.fullName.ifBlank { "@${viewerProfile.username}" }
    }
    val exitChat = {
        supportViewModel.closeConversation(isAgent = isAgentView, agentName = senderName)
        onBack()
    }
    BackHandler(onBack = exitChat)

    LaunchedEffect(ticketId, isAgentView) {
        supportViewModel.openConversation(ticketId, asAgent = isAgentView)
    }

    DisposableEffect(ticketId, isAgentView) {
        onDispose {
            supportViewModel.closeConversation(isAgent = isAgentView, agentName = senderName)
        }
    }

    val palette = rememberSupportPalette(isDarkTheme)
    val context = LocalContext.current

    val ticket by supportViewModel.activeTicket.collectAsStateWithLifecycle()
    val messages by supportViewModel.activeMessages.collectAsStateWithLifecycle()
    val supportAgents by supportViewModel.supportAgents.collectAsStateWithLifecycle()
    val onlineAgentsCount by supportViewModel.onlineAgentsCount.collectAsStateWithLifecycle()
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

    LaunchedEffect(ticket?.effectiveTicketId, ticket?.unreadByUserCount, ticket?.unreadByAgentCount, messages.size, isAgentView) {
        if (ticket != null) {
            supportViewModel.markActiveConversationRead()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, ticket?.agentIsTyping, ticket?.userIsTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var draftText by remember(ticketId) { mutableStateOf("") }
    var previewMessage by remember { mutableStateOf<SupportMessage?>(null) }
    var showAssignDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                supportViewModel.selectAndUploadAttachment(context, uri, ticketIdHint = ticketId)
            }
        }
    )

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
                    title = ticket?.subject?.ifBlank { "Support Chat" } ?: "Support Conversation",
                    subtitle = when {
                        ticket == null -> "Connecting..."
                        isAgentView -> "@${ticket!!.username} • ${ticket!!.categoryEnum.label}"
                        ticket!!.assignedAgentName.isNotBlank() -> "Agent: ${ticket!!.assignedAgentName}"
                        onlineAgentsCount > 0 -> "$onlineAgentsCount agent${if (onlineAgentsCount == 1) "" else "s"} online"
                        else -> "Offline ticket • Waiting for agent"
                    },
                    palette = palette,
                    onBack = exitChat,
                    backTestTag = "chat_back_button",
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ticket?.let { SupportStatusPill(status = it.statusEnum, palette = palette) }
                            if (isAgentView && ticket != null) {
                                IconButton(
                                    onClick = { showAssignDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_assign_agent_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Assign support agent",
                                        tint = palette.gold
                                    )
                                }
                            }
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
            ) {
                val active = ticket
                if (active != null && isAgentView) {
                    Surface(
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SupportTicketStatus.entries.forEach { st ->
                                val selected = active.statusEnum == st
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            supportViewModel.updateTicketStatus(active.effectiveTicketId, st)
                                        }
                                    },
                                    label = { Text(st.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.gold,
                                        selectedLabelColor = palette.onGold
                                    ),
                                    modifier = Modifier.testTag("chat_status_${st.key}")
                                )
                            }
                            if (active.assignedAgentUid != viewerProfile.uid) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        supportViewModel.assignTicket(active.effectiveTicketId, viewerProfile.uid, senderName)
                                    },
                                    label = { Text("Assign to Me") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = palette.blueContainer,
                                        labelColor = palette.onBlueContainer
                                    ),
                                    modifier = Modifier.testTag("chat_assign_to_me_chip")
                                )
                            }
                        }
                    }
                } else if (active != null && !isAgentView && onlineAgentsCount == 0 && active.statusEnum != SupportTicketStatus.RESOLVED) {
                    Surface(
                        color = palette.offlineAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, palette.offlineAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Agents are currently offline. Your messages are saved and will trigger an alert when answered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.offlineAmber,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (active != null && !isAgentView && active.statusEnum == SupportTicketStatus.RESOLVED) {
                    Surface(
                        color = palette.blueContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "This conversation is marked RESOLVED. Send a reply below anytime to reopen it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onBlueContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.Send,
                                title = "Conversation Ready",
                                subtitle = "Messages and screenshots sync in real time."
                            )
                        }
                    } else {
                        items(messages, key = { it.effectiveMessageId }) { msg ->
                            SupportMessageBubble(
                                message = msg,
                                viewerIsAgent = isAgentView,
                                palette = palette,
                                onImageClick = { previewMessage = it }
                            )
                        }
                    }

                    val showCounterpartyTyping = if (isAgentView) {
                        active?.userIsTyping == true
                    } else {
                        active?.agentIsTyping == true
                    }
                    if (showCounterpartyTyping && active != null) {
                        item(key = "typing_indicator") {
                            val typingLabel = if (isAgentView) {
                                "@${active.username} is typing..."
                            } else {
                                "${active.agentTypingName.ifBlank { "Support agent" }} is typing..."
                            }
                            TypingIndicatorBubble(label = typingLabel, palette = palette)
                        }
                    }
                }

                Surface(
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SupportAttachmentPreviewBar(
                            attachmentState = attachmentState,
                            palette = palette,
                            onRemove = { supportViewModel.clearAttachment() }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = attachmentState !is SupportAttachmentUploadState.Uploading,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_attach_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach screenshot image",
                                    tint = palette.gold
                                )
                            }

                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { updated ->
                                    val limited = updated.take(2000)
                                    draftText = limited
                                    supportViewModel.onDraftMessageChanged(
                                        ticketId = ticketId,
                                        isAgent = isAgentView,
                                        agentName = senderName,
                                        text = limited
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = if (isAgentView) "Write an agent reply..." else "Type your message...",
                                        color = palette.textSecondary
                                    )
                                },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.gold,
                                    unfocusedBorderColor = palette.border,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )

                            val canSend = active != null &&
                                !isBusy &&
                                attachmentState !is SupportAttachmentUploadState.Uploading &&
                                (draftText.isNotBlank() || attachmentState is SupportAttachmentUploadState.Ready)
                            Button(
                                onClick = {
                                    val currentTicket = active ?: return@Button
                                    val outgoing = draftText
                                    supportViewModel.sendMessage(
                                        ticket = currentTicket,
                                        senderName = senderName,
                                        isAgentSender = isAgentView,
                                        text = outgoing,
                                        onSent = { draftText = "" }
                                    )
                                },
                                enabled = canSend,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.gold,
                                    contentColor = palette.onGold
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send support message",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewMessage?.let { msg ->
        FullscreenSupportImageDialog(
            message = msg,
            palette = palette,
            onDismiss = { previewMessage = null }
        )
    }

    if (showAssignDialog && ticket != null) {
        val currentTicket = ticket!!
        AssignSupportAgentDialog(
            currentAgentUid = currentTicket.assignedAgentUid,
            viewerUid = viewerProfile.uid,
            viewerName = senderName,
            activeAgents = supportAgents,
            palette = palette,
            onDismiss = { showAssignDialog = false },
            onSelectAgent = { uid, name ->
                supportViewModel.assignTicket(currentTicket.effectiveTicketId, uid, name)
                showAssignDialog = false
            }
        )
    }
}

@Composable
fun AssignSupportAgentDialog(
    currentAgentUid: String,
    viewerUid: String,
    viewerName: String,
    activeAgents: List<SupportAgent>,
    palette: SupportPalette,
    onDismiss: () -> Unit,
    onSelectAgent: (uid: String, displayName: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textPrimary,
        title = { Text("Assign Conversation", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSelectAgent(viewerUid, viewerName) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Assign to Me ($viewerName)", fontWeight = FontWeight.Bold)
                }

                activeAgents.filter { it.uid != viewerUid }.forEach { agent ->
                    val statusTag = if (agent.isOnline) "ONLINE" else "OFFLINE"
                    val isCurrent = currentAgentUid == agent.uid
                    OutlinedButton(
                        onClick = { onSelectAgent(agent.uid, agent.displayName) },
                        border = BorderStroke(1.dp, if (isCurrent) palette.gold else palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("${agent.displayName} • $statusTag")
                    }
                }

                if (currentAgentUid.isNotBlank()) {
                    TextButton(
                        onClick = { onSelectAgent("", "") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unassign Ticket", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = palette.textSecondary)
            }
        }
    )
}
