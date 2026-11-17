package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class VipLevel(
    val key: String,
    val displayName: String,
    val order: Int
) {
    BRONZE("bronze", "Bronze", 1),
    SILVER("silver", "Silver", 2),
    GOLD("gold", "Gold", 3),
    PLATINUM("platinum", "Platinum", 4),
    DIAMOND("diamond", "Diamond", 5);

    companion object {
        fun fromKey(raw: String?): VipLevel =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: BRONZE
    }
}

@Keep
@IgnoreExtraProperties
data class VipTierConfig(
    @DocumentId val id: String = "",
    val levelKey: String = VipLevel.BRONZE.key,
    val tierName: String = VipLevel.BRONZE.displayName,
    val tierOrder: Int = 1,
    val minVerifiedDepositsPkr: Double = 0.0,
    val minCompletedTasks: Int = 0,
    val minQualifiedReferrals: Int = 0,
    val taskBonusPercent: Int = 0,
    val prioritySupportLabel: String = "Standard Queue",
    val benefits: List<String> = emptyList(),
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val levelEnum: VipLevel
        get() = VipLevel.fromKey(levelKey.ifBlank { id })
}

@Keep
@IgnoreExtraProperties
data class VipMembership(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val currentLevel: String = VipLevel.BRONZE.key,
    val currentTierName: String = VipLevel.BRONZE.displayName,
    val tierOrder: Int = 1,
    val verifiedDepositTotal: Double = 0.0,
    val completedTasksTotal: Int = 0,
    val qualifiedReferralsCount: Int = 0,
    val progressPercent: Int = 0,
    val nextLevelKey: String = VipLevel.SILVER.key,
    val assignedBy: String = "system_auto",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val levelEnum: VipLevel
        get() = VipLevel.fromKey(currentLevel)
}

@Keep
@IgnoreExtraProperties
data class VipHistoryRecord(
    @DocumentId val id: String = "",
    val historyId: String = "",
    val uid: String = "",
    val previousLevel: String = "bronze",
    val newLevel: String = "silver",
    val reason: String = "",
    val source: String = "server_auto",
    val changedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveHistoryId: String
        get() = historyId.ifBlank { id }
}

enum class LeaderboardPeriod(
    val key: String,
    val label: String
) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    REFERRALS("referrals", "Top Referrers"),
    VIP("vip", "VIP Rankings");

    companion object {
        fun fromKey(raw: String?): LeaderboardPeriod =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: DAILY
    }
}

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val vipLevel: String = VipLevel.BRONZE.key,
    @get:PropertyName("isVerified")
    @field:PropertyName("isVerified")
    val isVerified: Boolean = false,
    val dailyPoints: Double = 0.0,
    val dailyDateKey: String = "",
    val weeklyPoints: Double = 0.0,
    val weekKey: String = "",
    val monthlyPoints: Double = 0.0,
    val monthKey: String = "",
    val allTimeTaskPoints: Double = 0.0,
    val completedTasksCount: Int = 0,
    val qualifiedReferralsCount: Int = 0,
    val verifiedActivityScore: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val vipLevelEnum: VipLevel
        get() = VipLevel.fromKey(vipLevel)

    @Exclude
    fun scoreFor(
        period: LeaderboardPeriod,
        todayKey: String,
        currentWeekKey: String,
        currentMonthKey: String
    ): Double = when (period) {
        LeaderboardPeriod.DAILY -> if (dailyDateKey == todayKey) dailyPoints else 0.0
        LeaderboardPeriod.WEEKLY -> if (weekKey == currentWeekKey) weeklyPoints else 0.0
        LeaderboardPeriod.MONTHLY -> if (monthKey == currentMonthKey) monthlyPoints else 0.0
        LeaderboardPeriod.REFERRALS -> qualifiedReferralsCount.toDouble()
        LeaderboardPeriod.VIP -> (vipLevelEnum.order * 100000.0) + verifiedActivityScore
    }
}

@Keep
@IgnoreExtraProperties
data class LeaderboardPrize(
    @DocumentId val id: String = "",
    val prizeId: String = "",
    val periodType: String = LeaderboardPeriod.WEEKLY.key,
    val rankFrom: Int = 1,
    val rankTo: Int = 3,
    val title: String = "",
    val rewardDescription: String = "",
    val rewardAmountPkr: Double = 0.0,
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val effectivePrizeId: String
        get() = prizeId.ifBlank { id }
}

enum class NotificationCategory(
    val key: String,
    val label: String
) {
    ALL("all", "All"),
    ANNOUNCEMENT("announcement", "Announcements"),
    VIP("vip", "VIP"),
    LEADERBOARD("leaderboard", "Leaderboard"),
    WALLET("wallet", "Wallet"),
    SECURITY("security", "Security"),
    SUPPORT("support", "Support"),
    SYSTEM("system", "System");

    companion object {
        fun fromKey(raw: String?): NotificationCategory =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

@Keep
@IgnoreExtraProperties
data class AppNotificationItem(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val uid: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "system",
    @get:PropertyName("isRead")
    @field:PropertyName("isRead")
    val isRead: Boolean = false,
    val readAt: Timestamp? = null,
    val referenceId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveNotificationId: String
        get() = notificationId.ifBlank { id }
}

@Keep
@IgnoreExtraProperties
data class AnnouncementItem(
    @DocumentId val id: String = "",
    val announcementId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "announcement",
    val status: String = "published", // scheduled | published | cancelled
    val scheduledFor: Timestamp? = null,
    val publishedAt: Timestamp? = null,
    val sendPushNotification: Boolean = true,
    val pushDispatched: Boolean = false,
    val createdByUid: String = "",
    val createdByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveAnnouncementId: String
        get() = announcementId.ifBlank { id }
}

sealed interface UnifiedFeedNotification {
    val itemId: String
    val title: String
    val body: String
    val category: String
    val isRead: Boolean
    val isAnnouncement: Boolean
    val timestamp: Timestamp?
}

data class DirectNotificationEntry(
    val notification: AppNotificationItem
) : UnifiedFeedNotification {
    override val itemId: String = notification.effectiveNotificationId
    override val title: String = notification.title
    override val body: String = notification.body
    override val category: String = notification.category
    override val isRead: Boolean = notification.isRead
    override val isAnnouncement: Boolean = false
    override val timestamp: Timestamp? = notification.createdAt
}

data class AnnouncementFeedEntry(
    val announcement: AnnouncementItem,
    override val isRead: Boolean
) : UnifiedFeedNotification {
    override val itemId: String = announcement.effectiveAnnouncementId
    override val title: String = announcement.title
    override val body: String = announcement.body
    override val category: String = announcement.category
    override val isAnnouncement: Boolean = true
    override val timestamp: Timestamp? = announcement.publishedAt ?: announcement.createdAt
}

@Keep
@IgnoreExtraProperties
data class SecurityProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val totpEnabled: Boolean = false,
    val totpSecret: String? = null,
    val biometricEnabled: Boolean = false,
    val otpVerified: Boolean = false,
    val lastOtpVerifiedAt: Timestamp? = null,
    val verifiedWithdrawMethod: String? = null,
    val verifiedWithdrawAccount: String? = null,
    val verifiedWithdrawHolderName: String? = null,
    val withdrawalDetailVerified: Boolean = false,
    val withdrawalDetailVerifiedAt: Timestamp? = null,
    val suspiciousAlertActive: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SecurityDeviceSession(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val uid: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val manufacturer: String? = null,
    val osVersion: String = "",
    val appVersion: String = "",
    val timezoneId: String? = null,
    val localeCode: String? = null,
    @get:PropertyName("isTrusted")
    @field:PropertyName("isTrusted")
    val isTrusted: Boolean = false,
    @get:PropertyName("isSuspicious")
    @field:PropertyName("isSuspicious")
    val isSuspicious: Boolean = false,
    val suspiciousReason: String? = null,
    val status: String = "active", // active | revoked
    @ServerTimestamp val firstSeenAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    val revokedAt: Timestamp? = null,
    val revokedBy: String? = null
) {
    @get:Exclude
    val effectiveSessionId: String
        get() = sessionId.ifBlank { id }
}

@Keep
@IgnoreExtraProperties
data class SecurityEvent(
    @DocumentId val id: String = "",
    val eventId: String = "",
    val uid: String = "",
    val username: String = "",
    val eventType: String = "",
    val title: String = "",
    val details: String = "",
    val deviceId: String? = null,
    val deviceName: String? = null,
    val severity: String = "info", // info | warning | critical
    val actorUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveEventId: String
        get() = eventId.ifBlank { id }
}

sealed interface OtpChallengeState {
    data object Idle : OtpChallengeState
    data object Requesting : OtpChallengeState
    data class Sent(
        val challengeId: String,
        val deliveryChannel: String,
        val message: String,
        val expiresAtMs: Long
    ) : OtpChallengeState
    data object Verifying : OtpChallengeState
    data class Verified(val message: String) : OtpChallengeState
    data class Error(val message: String) : OtpChallengeState
}
