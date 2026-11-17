package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val referralCode: String = "",
    val referredBy: String = "",
    val referredByUid: String = "",
    val walletId: String = "",
    @get:PropertyName("isVerified")
    @field:PropertyName("isVerified")
    val isVerified: Boolean = false,
    val firstDepositStatus: String = "none", // none, pending, approved, rejected
    val firstDepositAmount: Double = 0.0,
    val verifiedAt: Timestamp? = null,
    val demoBalance: Double = 0.0,
    val demoRewardBalance: Double = 0.0,
    val realBalance: Double = 0.0,
    val realRewardBalance: Double = 0.0,
    val todayTaskEarnings: Double = 0.0,
    val todayTaskDate: String = "",
    val totalEarnedTasks: Double = 0.0,
    val completedTasksCount: Long = 0L,
    val totalReferrals: Long = 0L,
    val totalReferralCommission: Double = 0.0,
    val lastClaimedUserTaskId: String = "",
    val accountStatus: String = "active",
    val role: String = "user",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveDepositBalance: Double
        get() = maxOf(demoBalance, realBalance)

    @get:Exclude
    val effectiveRewardBalance: Double
        get() = maxOf(demoRewardBalance, realRewardBalance)

    @get:Exclude
    val combinedBalance: Double
        get() = effectiveDepositBalance + effectiveRewardBalance
}

@IgnoreExtraProperties
data class WalletAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val walletId: String = "",
    val balance: Double = 0.0,
    val depositBalance: Double = 0.0,
    val rewardBalance: Double = 0.0,
    val todayTaskEarnings: Double = 0.0,
    val todayTaskDate: String = "",
    val totalEarnedTasks: Double = 0.0,
    val totalReferralCommission: Double = 0.0,
    val lastClaimedTaskCompletionId: String = "",
    val currency: String = "PKR",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralCodeEntry(
    @DocumentId val id: String = "",
    val code: String = "",
    val uid: String = "",
    val username: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "daily_login",
    val rewardPoints: Double = 0.0,
    val dailyLimit: Long = 1L,
    val durationSeconds: Long = 0L,
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserTaskRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val dateKey: String = "",
    val status: String = "in_progress",
    val rewardPoints: Double = 0.0,
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val completedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentAccountConfig(
    @DocumentId val id: String = "",
    val providerKey: String = "",
    val providerName: String = "",
    val bankName: String = "",
    val accountHolderName: String = "",
    val accountNumber: String = "",
    val iban: String = "",
    val instructions: String = "",
    val qrCodeData: String? = null,
    @get:PropertyName("isEnabled")
    @field:PropertyName("isEnabled")
    val isEnabled: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface ReceiptUploadState {
    data object Idle : ReceiptUploadState
    data class Uploading(
        val depositId: String,
        val progressPercent: Int,
        val previewBase64: String?
    ) : ReceiptUploadState
    data class Uploaded(
        val depositId: String,
        val storagePath: String,
        val downloadUrl: String?,
        val previewBase64: String?,
        val statusNote: String? = null
    ) : ReceiptUploadState
    data class Error(val message: String) : ReceiptUploadState
}

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val downloadId: Long
    ) : ApkDownloadState
    data class Completed(
        val downloadId: Long,
        val fileName: String
    ) : ApkDownloadState
    data class Error(val message: String) : ApkDownloadState
}

data class DepositStatusNotification(
    val depositId: String,
    val amount: Double,
    val status: String,
    val adminNote: String?
)

@IgnoreExtraProperties
data class DepositRequest(
    @DocumentId val id: String = "",
    val depositId: String = "",
    val uid: String = "",
    val userUid: String = "",
    val username: String = "",
    val fullName: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "EasyPaisa",
    val paymentAccountName: String = "",
    val paymentAccountNumber: String = "",
    val reference: String = "",
    val transactionReference: String = "",
    val refKey: String = "",
    val receiptStoragePath: String? = null,
    val receiptUrl: String? = null,
    val receiptBase64: String? = null,
    val status: String = "PENDING",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val reviewedBy: String? = null,
    val approvedAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveDepositId: String
        get() = depositId.ifBlank { id }
    @get:Exclude
    val effectiveUid: String
        get() = userUid.ifBlank { uid }
    @get:Exclude
    val effectiveReference: String
        get() = transactionReference.ifBlank { reference }
}

@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "",
    val accountReference: String = "",
    val status: String = "pending",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val approvedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referredUid: String = "",
    val referredUsername: String = "",
    val referralCode: String = "",
    @ServerTimestamp val registrationDate: Timestamp? = null,
    val firstDepositVerified: Boolean = false,
    val status: String = "registered",
    val commissionAmount: Double = 0.0,
    val commissionStatus: String = "none",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CommissionRecord(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referredUid: String = "",
    val referredUsername: String = "",
    val amount: Double = 100.0,
    val currency: String = "PKR",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val approvedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val type: String = "task_reward",
    val amount: Double = 0.0,
    val status: String = "completed",
    val description: String = "",
    val referenceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "global",
    val referralCommission: Double = AppConstants.DEFAULT_REFERRAL_COMMISSION,
    val defaultTaskReward: Double = AppConstants.DEFAULT_TASK_REWARD,
    val maxDailyReward: Double = AppConstants.DEFAULT_MAX_DAILY_REWARD,
    val currency: String = AppConstants.DEFAULT_CURRENCY,
    val dailyTaskLimit: Long = AppConstants.DEFAULT_DAILY_TASK_LIMIT,
    val minWithdrawal: Double = AppConstants.DEFAULT_MIN_WITHDRAWAL,
    val minDeposit: Double = AppConstants.DEFAULT_MIN_DEPOSIT,
    val githubRepoSlug: String = "",
    val latestVersionName: String = "",
    val latestVersionCode: Long = 0L,
    val apkDownloadUrl: String = "",
    val releaseNotes: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AppUpdateInfo(
    val currentVersionName: String = "1.0.0",
    val currentVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val latestVersionCode: Int = 1,
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val apkFileSizeBytes: Long = 0L,
    val publishedAt: String = "",
    val githubRepoSlug: String = "",
    val isUpdateAvailable: Boolean = false,
    val isChecking: Boolean = false,
    val sourceLabel: String = "",
    val errorMessage: String? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetCollection: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object AppConstants {
    const val BOOTSTRAP_ADMIN_EMAIL = "masterfoodsstore@gmail.com"
    const val OFFICIAL_WEBSITE_DOMAIN = "binacesmartearn.online"
    const val OFFICIAL_WEBSITE_URL = "https://binacesmartearn.online"
    const val REFERRAL_SITE_DOMAIN = "binacesmarteran.my.canva.site"
    const val REFERRAL_BASE_URL = "https://binacesmarteran.my.canva.site/?ref="
    const val SUPPORT_WHATSAPP_CHANNEL_URL = "https://whatsapp.com/channel/0029Vb9SL1bEFeXlCASyF41z"
    const val APK_ASSET_NAME = "BINACE-SMART-EARN.apk"
    const val DEFAULT_CURRENCY = "PKR"
    const val DEFAULT_REFERRAL_COMMISSION = 100.0
    const val DEFAULT_TASK_REWARD = 20.0
    const val DEFAULT_DAILY_TASK_LIMIT = 10L
    const val DEFAULT_MAX_DAILY_REWARD = 200.0
    const val DEFAULT_MIN_DEPOSIT = 1000.0
    const val DEFAULT_MIN_WITHDRAWAL = 3500.0
    const val PKT_TIMEZONE_ID = "Asia/Karachi"
}
