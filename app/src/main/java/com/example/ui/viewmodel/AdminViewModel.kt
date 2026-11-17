package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSettings
import com.example.data.model.AuditLog
import com.example.data.model.CommissionRecord
import com.example.data.model.DepositRequest
import com.example.data.model.PaymentAccountConfig
import com.example.data.model.ReferralRecord
import com.example.data.model.TaskItem
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTaskRecord
import com.example.data.model.WalletAccount
import com.example.data.model.WithdrawalRequest
import com.example.data.repository.AdminRepository
import com.example.data.repository.ReferralRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepository: AdminRepository,
    referralRepository: ReferralRepository
) : ViewModel() {

    val users: StateFlow<List<UserProfile>> = adminRepository.observeAllUsers()
        .catch { e -> handleStreamError("users", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wallets: StateFlow<List<WalletAccount>> = adminRepository.observeAllWallets()
        .catch { e -> handleStreamError("wallets", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val tasks: StateFlow<List<TaskItem>> = adminRepository.observeAllTasks()
        .catch { e -> handleStreamError("tasks", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userTasks: StateFlow<List<UserTaskRecord>> = adminRepository.observeAllUserTasks()
        .catch { e -> handleStreamError("dailyTaskCompletions", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val deposits: StateFlow<List<DepositRequest>> = adminRepository.observeAllDeposits()
        .catch { e -> handleStreamError("deposits", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val withdrawals: StateFlow<List<WithdrawalRequest>> = adminRepository.observeAllWithdrawals()
        .catch { e -> handleStreamError("withdrawals", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val paymentAccounts: StateFlow<List<PaymentAccountConfig>> = adminRepository.observePaymentAccounts()
        .catch { e -> handleStreamError("paymentAccounts", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val referrals: StateFlow<List<ReferralRecord>> = adminRepository.observeAllReferrals()
        .catch { e -> handleStreamError("referrals", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val commissions: StateFlow<List<CommissionRecord>> = adminRepository.observeAllCommissions()
        .catch { e -> handleStreamError("commissions", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val transactions: StateFlow<List<TransactionRecord>> = adminRepository.observeAllTransactions()
        .catch { e -> handleStreamError("transactions", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = adminRepository.observeAuditLogs()
        .catch { e -> handleStreamError("adminLogs", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val settings: StateFlow<AppSettings> = referralRepository.observeGlobalSettings()
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettings())

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    init {
        viewModelScope.launch {
            adminRepository.seedDefaultsIfEmpty()
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun seedDefaults() {
        runAction("10 daily tasks, settings & payment accounts initialized.") {
            adminRepository.seedDefaultsIfEmpty()
        }
    }

    fun savePaymentAccount(config: PaymentAccountConfig) {
        runAction("${config.providerName} account settings saved.") {
            adminRepository.savePaymentAccount(config)
        }
    }

    fun initializeTenTasks() {
        runAction("All 10 daily tasks initialized and activated at PKR 20 each.") {
            adminRepository.initializeTenDefaultTasks()
        }
    }

    fun saveTask(
        taskId: String?,
        title: String,
        description: String,
        category: String,
        rewardPoints: Double,
        dailyLimit: Long,
        durationSeconds: Long,
        isActive: Boolean
    ) {
        runAction(if (taskId.isNullOrBlank()) "Task created." else "Task updated.") {
            adminRepository.saveTask(taskId, title, description, category, rewardPoints, dailyLimit, durationSeconds, isActive)
        }
    }

    fun deleteTask(task: TaskItem) {
        runAction("Task deleted.") {
            adminRepository.deleteTask(task.id, task.title)
        }
    }

    fun toggleUserStatus(user: UserProfile) {
        val next = if (user.accountStatus == "active") "suspended" else "active"
        runAction("@${user.username} is now $next.") {
            adminRepository.toggleUserStatus(user)
        }
    }

    fun verifyUserAccount(user: UserProfile, verified: Boolean) {
        val label = if (verified) {
            "@${user.username} verified & features unlocked."
        } else {
            "@${user.username} verification revoked."
        }
        runAction(label) {
            adminRepository.verifyUserAccount(user, verified)
        }
    }

    fun approveDeposit(deposit: DepositRequest, adminNote: String = "Verified by admin") {
        runAction("Deposit approved & qualifying referral verified.") {
            adminRepository.approveDeposit(
                deposit = deposit,
                adminNote = adminNote,
                autoApproveReferralCommission = true
            )
        }
    }

    fun rejectDeposit(deposit: DepositRequest, reason: String) {
        runAction("Deposit rejected.") {
            adminRepository.rejectDeposit(deposit, reason)
        }
    }

    fun approveWithdrawal(withdrawal: WithdrawalRequest) {
        runAction("Withdrawal approved & balance deducted.") {
            adminRepository.approveWithdrawal(withdrawal)
        }
    }

    fun rejectWithdrawal(withdrawal: WithdrawalRequest, reason: String) {
        runAction("Withdrawal rejected.") {
            adminRepository.rejectWithdrawal(withdrawal, reason)
        }
    }

    fun approveCommission(referral: ReferralRecord) {
        runAction("Referral commission approved.") {
            adminRepository.approveReferralCommission(referral)
        }
    }

    fun rejectCommission(referral: ReferralRecord) {
        runAction("Referral commission rejected.") {
            adminRepository.rejectReferralCommission(referral)
        }
    }

    fun updateSettings(
        referralCommission: Double,
        defaultTaskReward: Double,
        maxDailyReward: Double,
        dailyTaskLimit: Long,
        minDeposit: Double,
        minWithdrawal: Double,
        githubRepoSlug: String = "",
        latestVersionName: String = "",
        latestVersionCode: Long = 0L,
        apkDownloadUrl: String = "",
        releaseNotes: String = ""
    ) {
        runAction("Platform, commission & release settings updated.") {
            adminRepository.updateCommissionSettings(
                referralCommission = referralCommission,
                defaultTaskReward = defaultTaskReward,
                maxDailyReward = maxDailyReward,
                dailyTaskLimit = dailyTaskLimit,
                minDeposit = minDeposit,
                minWithdrawal = minWithdrawal,
                githubRepoSlug = githubRepoSlug,
                latestVersionName = latestVersionName,
                latestVersionCode = latestVersionCode,
                apkDownloadUrl = apkDownloadUrl,
                releaseNotes = releaseNotes
            )
        }
    }

    fun applyTaskRewardToAll(rewardPoints: Double) {
        runAction("Updated reward to PKR ${rewardPoints.toInt()} across all tasks.") {
            adminRepository.applyTaskRewardToAllTasks(rewardPoints)
        }
    }

    private fun runAction(successMsg: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = block()
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(false, successMsg)
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Admin operation failed.")
            }
        }
    }

    private fun handleStreamError(name: String, error: Throwable) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        Log.w("AdminVM", "Error observing $name", fsErr ?: error)
    }
}
