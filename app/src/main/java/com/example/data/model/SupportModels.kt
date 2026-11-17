package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class SupportCategory(
    val key: String,
    val label: String,
    val quickPrompt: String
) {
    DEPOSITS("deposits", "Deposits", "Need help with a deposit request or receipt verification"),
    WITHDRAWALS("withdrawals", "Withdrawals", "Question about withdrawal eligibility or payout status"),
    DAILY_TASKS("daily_tasks", "Daily Tasks", "Issue with daily tasks, cooldown timer, or PKR 20 rewards"),
    LOGIN("login", "Login", "Account sign-in, Google session, or verification assistance"),
    REFERRALS("referrals", "Referrals", "Referral link, friend signup, or PKR 100 bonus tracking"),
    PAYMENTS("payments", "Payments", "EasyPaisa, JazzCash, or Bank Transfer account inquiry");

    companion object {
        fun fromKey(raw: String?): SupportCategory =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: DEPOSITS
    }
}

enum class SupportTicketStatus(val key: String, val label: String) {
    OPEN("open", "OPEN"),
    PENDING("pending", "PENDING"),
    RESOLVED("resolved", "RESOLVED");

    companion object {
        fun fromKey(raw: String?): SupportTicketStatus =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class SupportAgent(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val role: String = "support_agent",
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @get:PropertyName("isOnline")
    @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val userUid: String = "",
    val username: String = "",
    val userName: String = "",
    val category: String = SupportCategory.DEPOSITS.key,
    val subject: String = "",
    val status: String = SupportTicketStatus.OPEN.key,
    @get:PropertyName("isOfflineTicket")
    @field:PropertyName("isOfflineTicket")
    val isOfflineTicket: Boolean = false,
    val assignedAgentUid: String = "",
    val assignedAgentName: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderRole: String = "user",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val userIsTyping: Boolean = false,
    val agentIsTyping: Boolean = false,
    val agentTypingName: String = "",
    val unreadByUserCount: Int = 0,
    val unreadByAgentCount: Int = 0,
    val userLastReadAt: Timestamp? = null,
    val agentLastReadAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveTicketId: String
        get() = ticketId.ifBlank { id }

    @get:Exclude
    val categoryEnum: SupportCategory
        get() = SupportCategory.fromKey(category)

    @get:Exclude
    val statusEnum: SupportTicketStatus
        get() = SupportTicketStatus.fromKey(status)
}

@Keep
@IgnoreExtraProperties
data class SupportMessage(
    @DocumentId val id: String = "",
    val messageId: String = "",
    val ticketId: String = "",
    val ticketOwnerUid: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "user",
    val type: String = "text",
    val text: String = "",
    val imageUrl: String? = null,
    val imageStoragePath: String? = null,
    val imageBase64: String? = null,
    val readByUser: Boolean = false,
    val readByAgent: Boolean = false,
    val readAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveMessageId: String
        get() = messageId.ifBlank { id }

    @get:Exclude
    val isFromAgent: Boolean
        get() = senderRole.equals("agent", ignoreCase = true)

    @get:Exclude
    val hasImageAttachment: Boolean
        get() = !imageUrl.isNullOrBlank() || !imageBase64.isNullOrBlank()
}

sealed interface SupportAttachmentUploadState {
    data object Idle : SupportAttachmentUploadState
    data class Uploading(val messageId: String, val progressPercent: Int) : SupportAttachmentUploadState
    data class Ready(
        val messageId: String,
        val storagePath: String,
        val downloadUrl: String?,
        val previewBase64: String?,
        val statusNote: String? = null
    ) : SupportAttachmentUploadState
    data class Error(val message: String) : SupportAttachmentUploadState
}

data class SupportNotificationEvent(
    val ticketId: String,
    val title: String,
    val body: String,
    val isAgentIncoming: Boolean = false
)
