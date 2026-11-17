package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SupportAgent
import com.example.data.model.SupportAttachmentUploadState
import com.example.data.model.SupportCategory
import com.example.data.model.SupportMessage
import com.example.data.model.SupportNotificationEvent
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.data.repository.SupportRepository
import com.example.data.repository.UploadedSupportAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModel(
    private val supportRepository: SupportRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val supportAgents: StateFlow<List<SupportAgent>> = supportRepository.observeSupportAgents()
        .catch { e -> handleStreamError("supportAgents", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val onlineAgentsCount: StateFlow<Int> = supportAgents
        .map { list -> list.count { it.isOnline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val myAgentRecord: StateFlow<SupportAgent?> = supportRepository.observeMyAgentRecord(currentUserId)
        .catch { e -> handleStreamError("myAgentRecord", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val isSupportStaff: StateFlow<Boolean> = combine(_isAdmin, myAgentRecord) { admin, agent ->
        admin || (agent?.isActive == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val allAgentRecords: StateFlow<List<SupportAgent>> = _isAdmin
        .flatMapLatest { admin ->
            if (admin) supportRepository.observeAllAgentRecords() else flowOf(emptyList())
        }
        .catch { e -> handleStreamError("allAgentRecords", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var hasLoadedUserTickets = false
    private val knownUserUnreadByTicket = mutableMapOf<String, Int>()
    private var hasLoadedStaffTickets = false
    private val knownStaffUnreadByTicket = mutableMapOf<String, Int>()

    private val _latestNotification = MutableStateFlow<SupportNotificationEvent?>(null)
    val latestNotification: StateFlow<SupportNotificationEvent?> = _latestNotification.asStateFlow()

    private val _activeTicketId = MutableStateFlow<String?>(null)
    val activeTicketId: StateFlow<String?> = _activeTicketId.asStateFlow()

    private val _activeViewerIsAgent = MutableStateFlow(false)

    val userTickets: StateFlow<List<SupportTicket>> = supportRepository.observeUserTickets(currentUserId)
        .onEach { list ->
            if (!hasLoadedUserTickets) {
                hasLoadedUserTickets = true
                list.forEach { knownUserUnreadByTicket[it.effectiveTicketId] = it.unreadByUserCount }
            } else {
                list.forEach { ticket ->
                    val id = ticket.effectiveTicketId
                    val prevUnread = knownUserUnreadByTicket[id] ?: 0
                    val isCurrentlyViewing = _activeTicketId.value == id && !_activeViewerIsAgent.value
                    if (ticket.unreadByUserCount > prevUnread && !isCurrentlyViewing) {
                        val sender = ticket.assignedAgentName.ifBlank { "Support Agent" }
                        _latestNotification.value = SupportNotificationEvent(
                            ticketId = id,
                            title = "Live Support • $sender",
                            body = ticket.lastMessageText.ifBlank { "New reply on ${ticket.subject}" },
                            isAgentIncoming = false
                        )
                    }
                    knownUserUnreadByTicket[id] = ticket.unreadByUserCount
                }
            }
        }
        .catch { e -> handleStreamError("userTickets", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val userUnreadTotal: StateFlow<Int> = userTickets
        .map { list -> list.sumOf { it.unreadByUserCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val staffTickets: StateFlow<List<SupportTicket>> = isSupportStaff
        .flatMapLatest { staff ->
            if (staff) supportRepository.observeAllTickets() else flowOf(emptyList())
        }
        .onEach { list ->
            if (!hasLoadedStaffTickets) {
                hasLoadedStaffTickets = true
                list.forEach { knownStaffUnreadByTicket[it.effectiveTicketId] = it.unreadByAgentCount }
            } else {
                list.forEach { ticket ->
                    val id = ticket.effectiveTicketId
                    val prevUnread = knownStaffUnreadByTicket[id] ?: 0
                    val isCurrentlyViewing = _activeTicketId.value == id && _activeViewerIsAgent.value
                    if (ticket.unreadByAgentCount > prevUnread && !isCurrentlyViewing && ticket.userUid != currentUserId) {
                        _latestNotification.value = SupportNotificationEvent(
                            ticketId = id,
                            title = if (ticket.isOfflineTicket) {
                                "New Offline Ticket • @${ticket.username}"
                            } else {
                                "Support Message • @${ticket.username}"
                            },
                            body = ticket.lastMessageText.ifBlank { ticket.subject },
                            isAgentIncoming = true
                        )
                    }
                    knownStaffUnreadByTicket[id] = ticket.unreadByAgentCount
                }
            }
        }
        .catch { e -> handleStreamError("staffTickets", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val staffUnreadTotal: StateFlow<Int> = staffTickets
        .map { list -> list.sumOf { it.unreadByAgentCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val activeTicket: StateFlow<SupportTicket?> = _activeTicketId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null) else supportRepository.observeTicket(id)
        }
        .catch { e -> handleStreamError("activeTicket", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeMessages: StateFlow<List<SupportMessage>> = combine(
        activeTicket,
        _activeViewerIsAgent
    ) { ticket, isAgentView ->
        ticket to isAgentView
    }.flatMapLatest { (ticket, isAgentView) ->
        if (ticket == null) {
            flowOf(emptyList())
        } else {
            supportRepository.observeTicketMessages(
                ticketId = ticket.effectiveTicketId,
                ticketOwnerUid = ticket.userUid,
                isStaffView = isAgentView
            )
        }
    }
        .catch { e -> handleStreamError("activeMessages", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _attachmentState = MutableStateFlow<SupportAttachmentUploadState>(SupportAttachmentUploadState.Idle)
    val attachmentState: StateFlow<SupportAttachmentUploadState> = _attachmentState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    private var typingClearJob: Job? = null

    fun setAdminFlag(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun dismissNotification() {
        _latestNotification.value = null
    }

    fun openConversation(ticketId: String, asAgent: Boolean) {
        _activeViewerIsAgent.value = asAgent
        _activeTicketId.value = ticketId
        _attachmentState.value = SupportAttachmentUploadState.Idle
    }

    fun closeConversation(isAgent: Boolean, agentName: String = "") {
        val currentId = _activeTicketId.value
        typingClearJob?.cancel()
        if (!currentId.isNullOrBlank()) {
            viewModelScope.launch {
                supportRepository.setTypingStatus(currentId, isAgent, false, agentName)
            }
        }
        _activeTicketId.value = null
        _attachmentState.value = SupportAttachmentUploadState.Idle
    }

    fun markActiveConversationRead() {
        val ticket = activeTicket.value ?: return
        val msgs = activeMessages.value
        val viewerIsAgent = _activeViewerIsAgent.value
        viewModelScope.launch {
            supportRepository.markConversationRead(ticket, msgs, viewerIsAgent)
        }
    }

    fun onDraftMessageChanged(
        ticketId: String,
        isAgent: Boolean,
        agentName: String,
        text: String
    ) {
        if (ticketId.isBlank()) return
        typingClearJob?.cancel()
        if (text.isBlank()) {
            viewModelScope.launch {
                supportRepository.setTypingStatus(ticketId, isAgent, false, agentName)
            }
            return
        }
        viewModelScope.launch {
            supportRepository.setTypingStatus(ticketId, isAgent, true, agentName)
        }
        typingClearJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            supportRepository.setTypingStatus(ticketId, isAgent, false, agentName)
        }
    }

    fun selectAndUploadAttachment(context: Context, uri: Uri, ticketIdHint: String = "drafts") {
        viewModelScope.launch {
            val draftMsgId = supportRepository.newMessageId()
            _attachmentState.value = SupportAttachmentUploadState.Uploading(draftMsgId, 0)
            val result = supportRepository.uploadSupportImage(
                context = context,
                uri = uri,
                ticketId = ticketIdHint.ifBlank { "drafts" }
            ) { pct ->
                _attachmentState.value = SupportAttachmentUploadState.Uploading(draftMsgId, pct)
            }
            result.onSuccess { uploaded ->
                _attachmentState.value = SupportAttachmentUploadState.Ready(
                    messageId = uploaded.messageId,
                    storagePath = uploaded.storagePath,
                    downloadUrl = uploaded.downloadUrl,
                    previewBase64 = uploaded.previewBase64,
                    statusNote = uploaded.statusNote
                )
            }.onFailure { err ->
                _attachmentState.value = SupportAttachmentUploadState.Error(err.message ?: "Image upload failed.")
                _feedback.value = ActionFeedback(true, err.message ?: "Image upload failed.")
            }
        }
    }

    fun clearAttachment() {
        _attachmentState.value = SupportAttachmentUploadState.Idle
    }

    fun createTicket(
        profile: UserProfile,
        category: SupportCategory,
        subject: String,
        initialMessage: String,
        onCreated: (String) -> Unit
    ) {
        val currentUpload = _attachmentState.value
        if (currentUpload is SupportAttachmentUploadState.Uploading) {
            _feedback.value = ActionFeedback(true, "Please wait for the image upload to finish.")
            return
        }
        val attachment = (currentUpload as? SupportAttachmentUploadState.Ready)?.let {
            UploadedSupportAttachment(
                messageId = it.messageId,
                storagePath = it.storagePath,
                downloadUrl = it.downloadUrl,
                previewBase64 = it.previewBase64,
                statusNote = it.statusNote
            )
        }
        val isOfflineTicket = onlineAgentsCount.value == 0
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.createSupportTicket(
                profile = profile,
                category = category,
                subject = subject,
                initialMessage = initialMessage,
                isOfflineTicket = isOfflineTicket,
                attachment = attachment
            )
            _isBusy.value = false
            res.onSuccess { ticketId ->
                _attachmentState.value = SupportAttachmentUploadState.Idle
                _feedback.value = ActionFeedback(
                    isError = false,
                    message = if (isOfflineTicket) {
                        "Offline ticket $ticketId submitted. We will notify you once an agent replies."
                    } else {
                        "Live support session $ticketId started."
                    }
                )
                onCreated(ticketId)
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to create support ticket.")
            }
        }
    }

    fun sendMessage(
        ticket: SupportTicket,
        senderName: String,
        isAgentSender: Boolean,
        text: String,
        onSent: () -> Unit = {}
    ) {
        val currentUpload = _attachmentState.value
        if (currentUpload is SupportAttachmentUploadState.Uploading) {
            _feedback.value = ActionFeedback(true, "Please wait for image upload to finish.")
            return
        }
        val attachment = (currentUpload as? SupportAttachmentUploadState.Ready)?.let {
            UploadedSupportAttachment(
                messageId = it.messageId,
                storagePath = it.storagePath,
                downloadUrl = it.downloadUrl,
                previewBase64 = it.previewBase64,
                statusNote = it.statusNote
            )
        }
        typingClearJob?.cancel()
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.sendMessage(
                ticket = ticket,
                senderName = senderName,
                isAgentSender = isAgentSender,
                text = text,
                attachment = attachment,
                hasOnlineAgents = onlineAgentsCount.value > 0
            )
            _isBusy.value = false
            res.onSuccess {
                _attachmentState.value = SupportAttachmentUploadState.Idle
                onSent()
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to send message.")
            }
        }
    }

    fun assignTicket(ticketId: String, agentUid: String, agentName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.assignTicket(ticketId, agentUid, agentName)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(
                    false,
                    if (agentName.isNotBlank()) "Assigned to $agentName." else "Ticket unassigned."
                )
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to assign ticket.")
            }
        }
    }

    fun updateTicketStatus(ticketId: String, status: SupportTicketStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.updateTicketStatus(ticketId, status)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(false, "Ticket marked ${status.label}.")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to update ticket status.")
            }
        }
    }

    fun toggleMyOnlineStatus(displayName: String, isOnline: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.toggleMyOnlineStatus(
                uid = currentUserId,
                displayName = displayName,
                isAdmin = _isAdmin.value,
                isOnline = isOnline
            )
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(
                    false,
                    if (isOnline) "You are now ONLINE for live support." else "You are now OFFLINE."
                )
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Could not update online availability.")
            }
        }
    }

    fun upsertSupportAgent(uid: String, displayName: String, role: String, isActive: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = supportRepository.upsertSupportAgent(
                uid = uid,
                displayName = displayName,
                role = role,
                isActive = isActive,
                isOnline = false
            )
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(false, "Support agent '$displayName' saved.")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Could not update support agent.")
            }
        }
    }

    fun syncFcmToken(notificationsEnabled: Boolean) {
        viewModelScope.launch {
            supportRepository.syncFcmDeviceToken(currentUserId, notificationsEnabled)
        }
    }

    private fun handleStreamError(name: String, error: Throwable) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing $name", fsErr ?: error)
    }

    private companion object {
        const val TAG = "SupportVM"
        const val STOP_TIMEOUT_MS = 5000L
        const val TYPING_IDLE_MS = 3500L
    }
}
