package com.example.ui.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.ApkDownloadState
import com.example.data.model.AppConstants
import com.example.data.model.AppSettings
import com.example.data.model.AppUpdateInfo
import com.example.data.model.CommissionRecord
import com.example.data.model.DepositRequest
import com.example.data.model.DepositStatusNotification
import com.example.data.model.PaymentAccountConfig
import com.example.data.model.ReceiptUploadState
import com.example.data.model.ReferralRecord
import com.example.data.model.TaskItem
import com.example.data.model.TransactionRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserTaskRecord
import com.example.data.model.WalletAccount
import com.example.data.model.WithdrawalRequest
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ReferralRepository
import com.example.data.repository.TaskRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.WalletRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActionFeedback(
    val isError: Boolean,
    val message: String
)

class MainViewModel(
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    private val walletRepository: WalletRepository,
    private val referralRepository: ReferralRepository,
    val currentUserId: String,
    private val appUpdateRepository: AppUpdateRepository = AppUpdateRepository()
) : ViewModel() {

    private var knownVerificationState: Boolean? = null
    private val _accountVerifiedBanner = MutableStateFlow(false)
    val accountVerifiedBanner: StateFlow<Boolean> = _accountVerifiedBanner.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> = userRepository.observeUserProfile(currentUserId)
        .onEach { profile ->
            if (profile != null) {
                val prev = knownVerificationState
                if (prev == false && profile.isVerified) {
                    _accountVerifiedBanner.value = true
                    _feedback.value = ActionFeedback(
                        isError = false,
                        message = "Account Verified! Daily tasks, referrals & simulated demo withdrawals are now unlocked."
                    )
                }
                knownVerificationState = profile.isVerified
            }
        }
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val wallet: StateFlow<WalletAccount?> = userRepository.observeWallet(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing wallet", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val paymentAccounts: StateFlow<List<PaymentAccountConfig>> = walletRepository.observePaymentAccounts()
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing paymentAccounts", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val activeTasksState: StateFlow<UiState<List<TaskItem>>> = taskRepository.observeActiveTasks()
        .map<List<TaskItem>, UiState<List<TaskItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", root)
            emit(UiState.Error(root.message ?: "Failed to load tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val userTasks: StateFlow<List<UserTaskRecord>> = taskRepository.observeUserTasks(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing dailyTaskCompletions", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private var hasLoadedInitialDeposits = false
    private val knownDepositStatuses = mutableMapOf<String, String>()

    private val _latestDepositNotification = MutableStateFlow<DepositStatusNotification?>(null)
    val latestDepositNotification: StateFlow<DepositStatusNotification?> = _latestDepositNotification.asStateFlow()

    val deposits: StateFlow<List<DepositRequest>> = walletRepository.observeUserDeposits(currentUserId)
        .onEach { list ->
            if (!hasLoadedInitialDeposits) {
                hasLoadedInitialDeposits = true
                list.forEach { dep ->
                    knownDepositStatuses[dep.effectiveDepositId] = dep.status.uppercase()
                }
            } else {
                list.forEach { dep ->
                    val depId = dep.effectiveDepositId
                    val newStatus = dep.status.uppercase()
                    val oldStatus = knownDepositStatuses[depId]
                    if (oldStatus == "PENDING" && (newStatus == "APPROVED" || newStatus == "REJECTED")) {
                        val notif = DepositStatusNotification(
                            depositId = depId,
                            amount = dep.amount,
                            status = newStatus,
                            adminNote = dep.adminNote
                        )
                        _latestDepositNotification.value = notif
                        _feedback.value = ActionFeedback(
                            isError = newStatus == "REJECTED",
                            message = if (newStatus == "APPROVED") {
                                "Deposit $depId (PKR ${dep.amount.toInt()}) APPROVED!"
                            } else {
                                "Deposit $depId REJECTED${dep.adminNote?.let { ": $it" } ?: "."}"
                            }
                        )
                    }
                    knownDepositStatuses[depId] = newStatus
                }
            }
        }
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing deposits", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val withdrawals: StateFlow<List<WithdrawalRequest>> = walletRepository.observeUserWithdrawals(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing withdrawals", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val transactions: StateFlow<List<TransactionRecord>> = walletRepository.observeUserTransactions(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing transactions", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val referrals: StateFlow<List<ReferralRecord>> = referralRepository.observeMyReferrals(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing referrals", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val commissions: StateFlow<List<CommissionRecord>> = referralRepository.observeMyCommissions(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing commissions", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val appSettings: StateFlow<AppSettings> = referralRepository.observeGlobalSettings()
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppSettings())

    private val _receiptUploadState = MutableStateFlow<ReceiptUploadState>(ReceiptUploadState.Idle)
    val receiptUploadState: StateFlow<ReceiptUploadState> = _receiptUploadState.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow(
        AppUpdateInfo(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            latestVersionName = BuildConfig.VERSION_NAME,
            latestVersionCode = BuildConfig.VERSION_CODE,
            githubRepoSlug = BuildConfig.GITHUB_REPOSITORY,
            downloadUrl = AppUpdateRepository.buildPublicLatestApkUrl(BuildConfig.GITHUB_REPOSITORY)
        )
    )
    val appUpdateInfo: StateFlow<AppUpdateInfo> = _appUpdateInfo.asStateFlow()

    private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val apkDownloadState: StateFlow<ApkDownloadState> = _apkDownloadState.asStateFlow()
    private var apkDownloadMonitorJob: Job? = null

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    init {
        viewModelScope.launch {
            _isAdmin.value = userRepository.isUserAdmin(currentUserId)
        }
        viewModelScope.launch {
            taskRepository.ensureDefaultTasksSeeded()
        }
        viewModelScope.launch {
            try {
                val existingProfile = userRepository.observeUserProfile(currentUserId).filterNotNull().first()
                userRepository.ensureWalletExists(existingProfile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch {
            appSettings.collect { settings ->
                refreshAppUpdate(settings = settings, manual = false)
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        viewModelScope.launch {
            refreshAppUpdate(settings = appSettings.value, manual = manual)
        }
    }

    private suspend fun refreshAppUpdate(settings: AppSettings, manual: Boolean) {
        _appUpdateInfo.value = _appUpdateInfo.value.copy(isChecking = true, errorMessage = null)
        val info = appUpdateRepository.fetchLatestUpdate(settings)
        _appUpdateInfo.value = info
        if (manual) {
            _feedback.value = when {
                info.isUpdateAvailable -> ActionFeedback(
                    isError = false,
                    message = "New APK v${info.latestVersionName} is available to download."
                )
                info.githubRepoSlug.isBlank() && settings.apkDownloadUrl.isBlank() -> ActionFeedback(
                    isError = false,
                    message = "You are on v${info.currentVersionName}. Configure GitHub repository in Admin Settings to enable live GitHub Release checks."
                )
                info.errorMessage != null -> ActionFeedback(
                    isError = true,
                    message = info.errorMessage
                )
                else -> ActionFeedback(
                    isError = false,
                    message = "Binace Smart Earn v${info.currentVersionName} is up to date."
                )
            }
        }
    }

    fun todayDateKey(): String = taskRepository.todayDateKey()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun dismissDepositNotification() {
        _latestDepositNotification.value = null
    }

    fun dismissVerifiedBanner() {
        _accountVerifiedBanner.value = false
    }

    fun dismissApkDownloadState() {
        _apkDownloadState.value = ApkDownloadState.Idle
    }

    fun startApkDownload(context: Context, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) {
            _apkDownloadState.value = ApkDownloadState.Error("Invalid APK download URL. Configure a valid HTTPS release URL.")
            return
        }
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            _apkDownloadState.value = ApkDownloadState.Error("System DownloadManager unavailable on this device.")
            return
        }

        val versionTag = _appUpdateInfo.value.latestVersionName
            .ifBlank { AppConstants.APK_ASSET_NAME }
            .replace(Regex("[^A-Za-z0-9._-]"), "")
        val fileName = if (versionTag.isNotBlank() && versionTag != AppConstants.APK_ASSET_NAME) {
            "binace-smart-earn-v$versionTag.apk"
        } else {
            AppConstants.APK_ASSET_NAME
        }
        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(trimmed))
                .setTitle("Binace Smart Earn v$versionTag")
                .setDescription("Downloading $fileName")
                .addRequestHeader("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .addRequestHeader("User-Agent", "BinaceSmartEarn-Android")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } catch (_: Exception) {
                request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            _apkDownloadState.value = ApkDownloadState.Error(
                e.localizedMessage ?: "Failed to start APK download."
            )
            return
        }

        apkDownloadMonitorJob?.cancel()
        _apkDownloadState.value = ApkDownloadState.Downloading(
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = _appUpdateInfo.value.apkFileSizeBytes.coerceAtLeast(0L),
            downloadId = downloadId
        )

        apkDownloadMonitorJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    var finished = false
                    downloadManager.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val dlIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_RUNNING
                            val dlBytes = if (dlIdx >= 0) cursor.getLong(dlIdx).coerceAtLeast(0L) else 0L
                            val rawTotal = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
                            val effectiveTotal = if (rawTotal > 0L) rawTotal else _appUpdateInfo.value.apkFileSizeBytes
                            val pct = if (effectiveTotal > 0L) {
                                ((dlBytes * 100L) / effectiveTotal).toInt().coerceIn(0, 99)
                            } else {
                                0
                            }

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    if (dlBytes in 1L..500_000L) {
                                        _apkDownloadState.value = ApkDownloadState.Error(
                                            "Downloaded file looks incomplete (${dlBytes / 1024} KB). Tap Download again or open the release in your browser."
                                        )
                                    } else {
                                        _apkDownloadState.value = ApkDownloadState.Completed(
                                            downloadId = downloadId,
                                            fileName = fileName
                                        )
                                        _feedback.value = ActionFeedback(
                                            isError = false,
                                            message = "Downloaded $fileName. Tap Install / Open Downloads; if upgrading over a debug build, uninstall the old app first."
                                        )
                                    }
                                    finished = true
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                                    _apkDownloadState.value = ApkDownloadState.Error(
                                        "APK download failed (code $reason). Verify the GitHub Release asset exists or try browser download."
                                    )
                                    finished = true
                                }
                                else -> {
                                    _apkDownloadState.value = ApkDownloadState.Downloading(
                                        progressPercent = pct,
                                        downloadedBytes = dlBytes,
                                        totalBytes = effectiveTotal.coerceAtLeast(0L),
                                        downloadId = downloadId
                                    )
                                }
                            }
                        } else {
                            _apkDownloadState.value = ApkDownloadState.Error("Download was cancelled.")
                            finished = true
                        }
                    }
                    if (finished) break
                    delay(400L)
                }
            }
        }
    }

    fun openDownloadedApkInstaller(context: Context, fallbackUrl: String) {
        val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(downloadsIntent)
        } catch (_: Exception) {
            if (fallbackUrl.startsWith("https://")) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                    _feedback.value = ActionFeedback(true, "Open your system Downloads folder to install ${AppConstants.APK_ASSET_NAME}.")
                }
            }
        }
    }

    fun registerUser(
        fullName: String,
        username: String,
        email: String,
        phone: String,
        referralCode: String
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = userRepository.registerUser(fullName, username, email, phone, referralCode)
            _isActionBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(false, "Account created! Welcome @${it.username}.")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Signup failed.")
            }
        }
    }

    fun updateProfile(fullName: String, username: String, phone: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = userRepository.updateProfile(fullName, username, phone)
            _isActionBusy.value = false
            res.onSuccess {
                _feedback.value = ActionFeedback(false, "Profile updated successfully.")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to update profile.")
            }
        }
    }

    fun startTask(task: TaskItem, profile: UserProfile) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = taskRepository.startTask(task, profile)
            _isActionBusy.value = false
            res.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Could not start task.")
            }
        }
    }

    fun claimTaskReward(task: TaskItem, profile: UserProfile) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = taskRepository.claimTaskReward(
                task = task,
                userProfile = profile,
                maxDailyReward = appSettings.value.maxDailyReward
            )
            _isActionBusy.value = false
            res.onSuccess { reward ->
                _feedback.value = ActionFeedback(false, "Claimed PKR ${reward.toInt()} simulated reward!")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Could not claim reward.")
            }
        }
    }

    fun selectAndUploadReceipt(context: Context, uri: Uri) {
        viewModelScope.launch {
            val previous = _receiptUploadState.value
            if (previous is ReceiptUploadState.Uploaded && previous.downloadUrl != null) {
                walletRepository.deleteDraftReceipt(previous.storagePath)
            }
            val pendingId = walletRepository.newDepositId()
            _receiptUploadState.value = ReceiptUploadState.Uploading(
                depositId = pendingId,
                progressPercent = 0,
                previewBase64 = null
            )
            val result = walletRepository.uploadReceiptImage(context, uri) { pct ->
                val cur = _receiptUploadState.value
                val preview = (cur as? ReceiptUploadState.Uploading)?.previewBase64
                _receiptUploadState.value = ReceiptUploadState.Uploading(
                    depositId = pendingId,
                    progressPercent = pct,
                    previewBase64 = preview
                )
            }
            result.onSuccess { uploaded ->
                _receiptUploadState.value = ReceiptUploadState.Uploaded(
                    depositId = uploaded.depositId,
                    storagePath = uploaded.storagePath,
                    downloadUrl = uploaded.downloadUrl,
                    previewBase64 = uploaded.previewBase64,
                    statusNote = uploaded.statusNote
                )
            }.onFailure { err ->
                _receiptUploadState.value = ReceiptUploadState.Error(err.message ?: "Receipt upload failed.")
                _feedback.value = ActionFeedback(true, err.message ?: "Receipt upload failed.")
            }
        }
    }

    fun removeSelectedReceipt() {
        viewModelScope.launch {
            val current = _receiptUploadState.value
            if (current is ReceiptUploadState.Uploaded && !current.downloadUrl.isNullOrBlank()) {
                walletRepository.deleteDraftReceipt(current.storagePath)
            }
            _receiptUploadState.value = ReceiptUploadState.Idle
        }
    }

    fun submitDeposit(
        profile: UserProfile,
        amount: Double,
        paymentMethod: String,
        paymentAccountName: String,
        paymentAccountNumber: String,
        reference: String,
        onSubmitted: () -> Unit = {}
    ) {
        val uploadState = _receiptUploadState.value
        if (uploadState is ReceiptUploadState.Uploading) {
            _feedback.value = ActionFeedback(true, "Please wait for receipt upload to finish.")
            return
        }
        val uploaded = uploadState as? ReceiptUploadState.Uploaded
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = walletRepository.submitDepositRequest(
                userProfile = profile,
                amount = amount,
                paymentMethod = paymentMethod,
                reference = reference,
                receiptBase64 = uploaded?.previewBase64,
                depositId = uploaded?.depositId,
                paymentAccountName = paymentAccountName,
                paymentAccountNumber = paymentAccountNumber,
                receiptStoragePath = uploaded?.storagePath,
                receiptUrl = uploaded?.downloadUrl,
                minDeposit = appSettings.value.minDeposit
            )
            _isActionBusy.value = false
            res.onSuccess { id ->
                _receiptUploadState.value = ReceiptUploadState.Idle
                _feedback.value = ActionFeedback(false, "Simulated demo deposit $id submitted for verification.")
                onSubmitted()
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Simulated deposit request failed.")
            }
        }
    }

    fun submitWithdrawal(profile: UserProfile, amount: Double, method: String, accountRef: String) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val res = walletRepository.submitWithdrawalRequest(
                userProfile = profile,
                amount = amount,
                paymentMethod = method,
                accountReference = accountRef,
                existingWithdrawals = withdrawals.value,
                minWithdrawal = appSettings.value.minWithdrawal
            )
            _isActionBusy.value = false
            res.onSuccess { id ->
                _feedback.value = ActionFeedback(false, "Simulated demo withdrawal $id submitted for admin review.")
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Simulated withdrawal request failed.")
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "MainVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
