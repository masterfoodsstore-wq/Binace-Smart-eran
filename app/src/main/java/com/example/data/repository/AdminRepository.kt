package com.example.data.repository

import com.example.data.model.AppConstants
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
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireAdminIdentity(): Pair<String, String> {
        val user = auth.currentUser ?: throw IllegalStateException("Admin must be signed in.")
        return user.uid to (user.email ?: "admin")
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = observeCollection("users") { snap ->
        snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    fun observeAllWallets(): Flow<List<WalletAccount>> = observeCollection("wallets") { snap ->
        snap.toObjects(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }

    fun observeAllTasks(): Flow<List<TaskItem>> = observeCollection("tasks") { snap ->
        snap.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.id }
    }

    fun observeAllUserTasks(): Flow<List<UserTaskRecord>> = observeCollection("dailyTaskCompletions") { snap ->
        snap.toObjects(UserTaskRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
    }

    fun observeAllDeposits(): Flow<List<DepositRequest>> = observeCollection("deposits") { snap ->
        snap.toObjects(DepositRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    fun observeAllWithdrawals(): Flow<List<WithdrawalRequest>> = observeCollection("withdrawals") { snap ->
        snap.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    fun observeAllReferrals(): Flow<List<ReferralRecord>> = observeCollection("referrals") { snap ->
        snap.toObjects(ReferralRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.registrationDate ?: Timestamp(0, 0) }
    }

    fun observeAllCommissions(): Flow<List<CommissionRecord>> = observeCollection("commissions") { snap ->
        snap.toObjects(CommissionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    fun observeAllTransactions(): Flow<List<TransactionRecord>> = observeCollection("transactions") { snap ->
        snap.toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    fun observePaymentAccounts(): Flow<List<PaymentAccountConfig>> = observeCollection("paymentAccounts") { snap ->
        val order = mapOf("easypaisa" to 0, "jazzcash" to 1, "bank_transfer" to 2)
        snap.toObjects(PaymentAccountConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { order[it.providerKey] ?: 99 }
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = observeCollection("adminLogs") { snap ->
        snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
    }

    private fun <T> observeCollection(
        collection: String,
        mapper: (com.google.firebase.firestore.QuerySnapshot) -> List<T>
    ): Flow<List<T>> = flow {
        db.collection(collection)
            .snapshots()
            .map(mapper)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, collection)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun seedDefaultsIfEmpty(): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val settingsRef = db.collection("settings").document("global")
            val settingsSnap = settingsRef.get().await()
            val existingTasksSnap = db.collection("tasks").get().await()
            val existingIds = existingTasksSnap.documents.map { it.id }.toSet()
            val existingPaymentAccountsSnap = db.collection("paymentAccounts").get().await()
            val existingPaymentIds = existingPaymentAccountsSnap.documents.map { it.id }.toSet()

            val batch = db.batch()
            var hasWrites = false

            if (!settingsSnap.exists()) {
                hasWrites = true
                batch.set(
                    settingsRef,
                    mapOf(
                        "referralCommission" to AppConstants.DEFAULT_REFERRAL_COMMISSION,
                        "defaultTaskReward" to AppConstants.DEFAULT_TASK_REWARD,
                        "maxDailyReward" to AppConstants.DEFAULT_MAX_DAILY_REWARD,
                        "currency" to AppConstants.DEFAULT_CURRENCY,
                        "dailyTaskLimit" to AppConstants.DEFAULT_DAILY_TASK_LIMIT,
                        "minWithdrawal" to AppConstants.DEFAULT_MIN_WITHDRAWAL,
                        "minDeposit" to AppConstants.DEFAULT_MIN_DEPOSIT,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (existingTasksSnap.isEmpty) {
                hasWrites = true
                TaskRepository.DEFAULT_TEN_TASKS.forEach { seed ->
                    if (!existingIds.contains(seed.id)) {
                        batch.set(
                            db.collection("tasks").document(seed.id),
                            mapOf(
                                "title" to seed.title,
                                "description" to seed.description,
                                "category" to seed.category,
                                "rewardPoints" to seed.rewardPoints,
                                "dailyLimit" to seed.dailyLimit,
                                "durationSeconds" to seed.durationSeconds,
                                "isActive" to seed.isActive,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            DEFAULT_PAYMENT_ACCOUNTS.forEach { seedAccount ->
                if (!existingPaymentIds.contains(seedAccount.providerKey)) {
                    hasWrites = true
                    batch.set(
                        db.collection("paymentAccounts").document(seedAccount.providerKey),
                        buildPaymentAccountPayload(seedAccount, adminUid)
                    )
                }
            }

            if (hasWrites) {
                val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
                batch.set(
                    db.collection("adminLogs").document(logId),
                    mapOf(
                        "adminUid" to adminUid,
                        "adminEmail" to adminEmail,
                        "action" to "SEED_DEFAULTS",
                        "targetCollection" to "tasks_settings_payments",
                        "targetId" to "global",
                        "details" to "Initialized 10 daily tasks, PKR 100 commission settings, and default payment accounts.",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "settings/global")
            Result.failure(e)
        }
    }

    suspend fun savePaymentAccount(config: PaymentAccountConfig): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val key = config.providerKey.trim().lowercase()
        if (key !in setOf("easypaisa", "jazzcash", "bank_transfer")) {
            return Result.failure(IllegalArgumentException("Unsupported payment provider key: $key"))
        }
        val cleanHolder = config.accountHolderName.trim()
        val cleanNumber = config.accountNumber.trim()
        val cleanInstructions = config.instructions.trim()
        val cleanBank = config.bankName.trim()
        val cleanIban = config.iban.trim()

        if (cleanHolder.length < 2) return Result.failure(IllegalArgumentException("Account Holder Name is required."))
        if (cleanNumber.length < 4) return Result.failure(IllegalArgumentException("Account Number must be at least 4 characters."))
        if (cleanInstructions.length < 2) return Result.failure(IllegalArgumentException("Account Instructions are required."))
        if (key == "bank_transfer" && cleanBank.length < 2) {
            return Result.failure(IllegalArgumentException("Bank Name is required for Bank Transfer."))
        }

        val normalized = config.copy(
            providerKey = key,
            providerName = when (key) {
                "easypaisa" -> "EasyPaisa"
                "jazzcash" -> "JazzCash"
                else -> "Bank Transfer"
            },
            bankName = cleanBank,
            accountHolderName = cleanHolder,
            accountNumber = cleanNumber,
            iban = cleanIban,
            instructions = cleanInstructions
        )

        return try {
            val batch = db.batch()
            batch.set(
                db.collection("paymentAccounts").document(key),
                buildPaymentAccountPayload(normalized, adminUid)
            )
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "UPDATE_PAYMENT_ACCOUNT",
                    "targetCollection" to "paymentAccounts",
                    "targetId" to key,
                    "details" to "Updated ${normalized.providerName} ($cleanHolder / $cleanNumber, enabled=${normalized.isEnabled})",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "paymentAccounts/$key")
            Result.failure(e)
        }
    }

    private fun buildPaymentAccountPayload(config: PaymentAccountConfig, adminUid: String): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "providerKey" to config.providerKey,
            "providerName" to config.providerName,
            "accountHolderName" to config.accountHolderName,
            "accountNumber" to config.accountNumber,
            "instructions" to config.instructions,
            "isEnabled" to config.isEnabled,
            "updatedBy" to adminUid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (config.bankName.isNotBlank()) payload["bankName"] = config.bankName
        if (config.iban.isNotBlank()) payload["iban"] = config.iban
        if (!config.qrCodeData.isNullOrBlank()) payload["qrCodeData"] = config.qrCodeData
        return payload
    }

    suspend fun initializeTenDefaultTasks(): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val batch = db.batch()
            TaskRepository.DEFAULT_TEN_TASKS.forEach { seed ->
                batch.set(
                    db.collection("tasks").document(seed.id),
                    mapOf(
                        "title" to seed.title,
                        "description" to seed.description,
                        "category" to seed.category,
                        "rewardPoints" to seed.rewardPoints,
                        "dailyLimit" to seed.dailyLimit,
                        "durationSeconds" to seed.durationSeconds,
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "INIT_10_TASKS",
                    "targetCollection" to "tasks",
                    "targetId" to "10_daily_tasks",
                    "details" to "Initialized and activated all 10 daily tasks at PKR 20 each.",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "tasks")
            Result.failure(e)
        }
    }

    suspend fun saveTask(
        taskId: String?,
        title: String,
        description: String,
        category: String,
        rewardPoints: Double,
        dailyLimit: Long,
        durationSeconds: Long,
        isActive: Boolean
    ): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val id = taskId?.takeIf { it.isNotBlank() } ?: "task_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val docRef = db.collection("tasks").document(id)
        return try {
            val batch = db.batch()
            if (taskId.isNullOrBlank()) {
                batch.set(
                    docRef,
                    mapOf(
                        "title" to title.trim(),
                        "description" to description.trim(),
                        "category" to category,
                        "rewardPoints" to rewardPoints,
                        "dailyLimit" to dailyLimit,
                        "durationSeconds" to durationSeconds,
                        "isActive" to isActive,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    docRef,
                    mapOf(
                        "title" to title.trim(),
                        "description" to description.trim(),
                        "category" to category,
                        "rewardPoints" to rewardPoints,
                        "dailyLimit" to dailyLimit,
                        "durationSeconds" to durationSeconds,
                        "isActive" to isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to if (taskId.isNullOrBlank()) "CREATE_TASK" else "UPDATE_TASK",
                    "targetCollection" to "tasks",
                    "targetId" to id,
                    "details" to "Saved task '$title' (PKR $rewardPoints, active=$isActive)",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String, title: String): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val batch = db.batch()
            batch.delete(db.collection("tasks").document(taskId))
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "DELETE_TASK",
                    "targetCollection" to "tasks",
                    "targetId" to taskId,
                    "details" to "Deleted task '$title'",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "tasks/$taskId")
            Result.failure(e)
        }
    }

    suspend fun toggleUserStatus(user: UserProfile): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val newStatus = if (user.accountStatus == "active") "suspended" else "active"
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(user.uid),
                mapOf(
                    "accountStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "TOGGLE_USER_STATUS",
                    "targetCollection" to "users",
                    "targetId" to user.uid,
                    "details" to "Changed ${user.username} status to $newStatus",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${user.uid}")
            Result.failure(e)
        }
    }

    suspend fun verifyUserAccount(user: UserProfile, verified: Boolean): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val batch = db.batch()
            val updates = mutableMapOf<String, Any?>(
                "isVerified" to verified,
                "firstDepositStatus" to if (verified) "approved" else "none",
                "verifiedAt" to if (verified) FieldValue.serverTimestamp() else null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.update(db.collection("users").document(user.uid), updates)
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to if (verified) "VERIFY_USER_ACCOUNT" else "REVOKE_USER_VERIFICATION",
                    "targetCollection" to "users",
                    "targetId" to user.uid,
                    "details" to if (verified) {
                        "Manually verified @${user.username} and unlocked simulated demo features."
                    } else {
                        "Revoked verification status for @${user.username}."
                    },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${user.uid}")
            Result.failure(e)
        }
    }

    suspend fun approveDeposit(
        deposit: DepositRequest,
        adminNote: String = "Verified by admin",
        autoApproveReferralCommission: Boolean = true
    ): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val depositDocId = deposit.effectiveDepositId
        val targetUid = deposit.effectiveUid
        val cleanNote = adminNote.trim().ifBlank { "Verified by admin" }.take(300)

        return try {
            db.runTransaction { tx ->
                // 1. ALL READS FIRST
                val depositRef = db.collection("deposits").document(depositDocId)
                val depositSnap = tx.get(depositRef)
                if (!depositSnap.exists()) {
                    throw IllegalStateException("Deposit request not found.")
                }
                val currentStatus = depositSnap.getString("status") ?: ""
                if (!currentStatus.equals("pending", ignoreCase = true)) {
                    throw IllegalStateException("Deposit has already been reviewed ($currentStatus).")
                }

                val userRef = db.collection("users").document(targetUid)
                val userSnap = tx.get(userRef)
                val user = userSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("User not found.")

                val userWalletRef = db.collection("wallets").document(targetUid)
                val userWalletSnap = tx.get(userWalletRef)
                val userWallet = if (userWalletSnap.exists()) {
                    userWalletSnap.toObject(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null

                val settingsSnap = tx.get(db.collection("settings").document("global"))
                val commissionAmount = settingsSnap.toObject(AppSettings::class.java)?.referralCommission
                    ?: AppConstants.DEFAULT_REFERRAL_COMMISSION

                val referralRef = db.collection("referrals").document(targetUid)
                val referralSnap = tx.get(referralRef)
                val referral = if (referralSnap.exists()) {
                    referralSnap.toObject(ReferralRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null

                val commissionId = "comm_$targetUid"
                val commissionRef = db.collection("commissions").document(commissionId)
                val commissionExists = tx.get(commissionRef).exists()

                val needsReferrer = referral != null && !referral.firstDepositVerified && !commissionExists
                val referrerUserRef = if (needsReferrer) db.collection("users").document(referral!!.referrerUid) else null
                val referrerWalletRef = if (needsReferrer) db.collection("wallets").document(referral!!.referrerUid) else null
                val referrerSnap = referrerUserRef?.let { tx.get(it) }
                val referrerWalletSnap = referrerWalletRef?.let { tx.get(it) }

                // 2. ALL WRITES AFTER READS
                tx.update(
                    depositRef,
                    mapOf(
                        "status" to "APPROVED",
                        "adminNote" to cleanNote,
                        "reviewedBy" to adminEmail,
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "approvedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val newDepositBal = user.effectiveDepositBalance + deposit.amount
                val userUpdates = mutableMapOf<String, Any>(
                    "realBalance" to newDepositBal,
                    "demoBalance" to newDepositBal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!user.isVerified) {
                    userUpdates["isVerified"] = true
                    userUpdates["firstDepositStatus"] = "approved"
                    userUpdates["firstDepositAmount"] = maxOf(user.firstDepositAmount, deposit.amount)
                    userUpdates["verifiedAt"] = FieldValue.serverTimestamp()
                }
                tx.update(userRef, userUpdates)

                if (userWallet != null) {
                    tx.update(
                        userWalletRef,
                        mapOf(
                            "balance" to (newDepositBal + user.effectiveRewardBalance),
                            "depositBalance" to newDepositBal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.set(
                    db.collection("transactions").document("tx_dep_$depositDocId"),
                    mapOf(
                        "uid" to targetUid,
                        "type" to "deposit",
                        "amount" to deposit.amount,
                        "status" to "approved",
                        "description" to "Simulated Demo Deposit (${deposit.paymentMethod} • ${deposit.effectiveReference})",
                        "referenceId" to depositDocId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                if (needsReferrer && referrerSnap != null && referrerSnap.exists()) {
                    val referrer = referrerSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val referrerWallet = if (referrerWalletSnap != null && referrerWalletSnap.exists()) {
                        referrerWalletSnap.toObject(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                    val commStatus = if (autoApproveReferralCommission) "approved" else "pending"
                    val refStatus = if (autoApproveReferralCommission) "rewarded" else "qualified"

                    tx.update(
                        referralRef,
                        mapOf(
                            "firstDepositVerified" to true,
                            "status" to refStatus,
                            "commissionAmount" to commissionAmount,
                            "commissionStatus" to commStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    val commPayload = mutableMapOf<String, Any>(
                        "referrerUid" to referral!!.referrerUid,
                        "referredUid" to referral.referredUid,
                        "referredUsername" to referral.referredUsername,
                        "amount" to commissionAmount,
                        "currency" to AppConstants.DEFAULT_CURRENCY,
                        "status" to commStatus,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (autoApproveReferralCommission) {
                        commPayload["approvedAt"] = FieldValue.serverTimestamp()
                    }
                    tx.set(commissionRef, commPayload)

                    if (autoApproveReferralCommission && referrer != null && referrerUserRef != null) {
                        val newReferrerReward = referrer.effectiveRewardBalance + commissionAmount
                        tx.update(
                            referrerUserRef,
                            mapOf(
                                "realRewardBalance" to newReferrerReward,
                                "demoRewardBalance" to newReferrerReward,
                                "totalReferrals" to (referrer.totalReferrals + 1L),
                                "totalReferralCommission" to (referrer.totalReferralCommission + commissionAmount),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        if (referrerWallet != null && referrerWalletRef != null) {
                            tx.update(
                                referrerWalletRef,
                                mapOf(
                                    "balance" to (referrerWallet.balance + commissionAmount),
                                    "rewardBalance" to (referrerWallet.rewardBalance + commissionAmount),
                                    "totalReferralCommission" to (referrerWallet.totalReferralCommission + commissionAmount),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        tx.set(
                            db.collection("transactions").document("tx_comm_$targetUid"),
                            mapOf(
                                "uid" to referral.referrerUid,
                                "type" to "referral_commission",
                                "amount" to commissionAmount,
                                "status" to "completed",
                                "description" to "Referral Commission from @${referral.referredUsername}",
                                "referenceId" to commissionId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
                tx.set(
                    db.collection("adminLogs").document(logId),
                    mapOf(
                        "adminUid" to adminUid,
                        "adminEmail" to adminEmail,
                        "action" to "APPROVE_DEPOSIT",
                        "targetCollection" to "deposits",
                        "targetId" to depositDocId,
                        "details" to "Approved deposit PKR ${deposit.amount} (${deposit.paymentMethod}) for @${deposit.username} | Note: $cleanNote",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "deposits/$depositDocId")
            Result.failure(e)
        }
    }

    suspend fun rejectDeposit(deposit: DepositRequest, reason: String): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val depositDocId = deposit.effectiveDepositId
        val targetUid = deposit.effectiveUid
        val cleanReason = reason.trim().ifBlank { "Rejected by admin" }.take(300)

        return try {
            db.runTransaction { tx ->
                val depositRef = db.collection("deposits").document(depositDocId)
                val depositSnap = tx.get(depositRef)
                if (!depositSnap.exists()) {
                    throw IllegalStateException("Deposit request not found.")
                }
                val currentStatus = depositSnap.getString("status") ?: ""
                if (!currentStatus.equals("pending", ignoreCase = true)) {
                    throw IllegalStateException("Deposit has already been reviewed ($currentStatus).")
                }

                val userRef = db.collection("users").document(targetUid)
                val userSnap = tx.get(userRef)
                val user = if (userSnap.exists()) {
                    userSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null

                tx.update(
                    depositRef,
                    mapOf(
                        "status" to "REJECTED",
                        "adminNote" to cleanReason,
                        "reviewedBy" to adminEmail,
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (user != null && !user.isVerified && user.firstDepositStatus == "pending") {
                    tx.update(
                        userRef,
                        mapOf(
                            "firstDepositStatus" to "rejected",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.set(
                    db.collection("transactions").document("tx_dep_rej_$depositDocId"),
                    mapOf(
                        "uid" to targetUid,
                        "type" to "deposit",
                        "amount" to deposit.amount,
                        "status" to "rejected",
                        "description" to "Rejected Simulated Deposit (${deposit.effectiveReference})",
                        "referenceId" to depositDocId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
                tx.set(
                    db.collection("adminLogs").document(logId),
                    mapOf(
                        "adminUid" to adminUid,
                        "adminEmail" to adminEmail,
                        "action" to "REJECT_DEPOSIT",
                        "targetCollection" to "deposits",
                        "targetId" to depositDocId,
                        "details" to "Rejected deposit PKR ${deposit.amount} for @${deposit.username} | Note: $cleanReason",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "deposits/$depositDocId")
            Result.failure(e)
        }
    }

    suspend fun approveWithdrawal(withdrawal: WithdrawalRequest): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val userRef = db.collection("users").document(withdrawal.uid)
            val user = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("User not found."))

            if (user.combinedBalance < withdrawal.amount) {
                return Result.failure(IllegalStateException("User balance is insufficient to approve withdrawal."))
            }

            val walletRef = db.collection("wallets").document(withdrawal.uid)
            val walletSnap = walletRef.get().await()
            val walletExists = walletSnap.exists()

            val currentDeposit = user.effectiveDepositBalance
            val currentReward = user.effectiveRewardBalance
            val deductFromReal = minOf(currentDeposit, withdrawal.amount)
            val remaining = withdrawal.amount - deductFromReal
            val newRealBalance = (currentDeposit - deductFromReal).coerceAtLeast(0.0)
            val newRewardBalance = (currentReward - remaining).coerceAtLeast(0.0)

            val batch = db.batch()
            batch.update(
                db.collection("withdrawals").document(withdrawal.id),
                mapOf(
                    "status" to "approved",
                    "adminNote" to "Approved by admin",
                    "approvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "realBalance" to newRealBalance,
                    "realRewardBalance" to newRewardBalance,
                    "demoBalance" to newRealBalance,
                    "demoRewardBalance" to newRewardBalance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (walletExists) {
                batch.update(
                    walletRef,
                    mapOf(
                        "balance" to (newRealBalance + newRewardBalance),
                        "depositBalance" to newRealBalance,
                        "rewardBalance" to newRewardBalance,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                db.collection("transactions").document("tx_wdr_${withdrawal.id}"),
                mapOf(
                    "uid" to withdrawal.uid,
                    "type" to "withdrawal",
                    "amount" to withdrawal.amount,
                    "status" to "approved",
                    "description" to "Withdrawal (${withdrawal.paymentMethod})",
                    "referenceId" to withdrawal.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "APPROVE_WITHDRAWAL",
                    "targetCollection" to "withdrawals",
                    "targetId" to withdrawal.id,
                    "details" to "Approved withdrawal PKR ${withdrawal.amount} for @${withdrawal.username}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "withdrawals/${withdrawal.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectWithdrawal(withdrawal: WithdrawalRequest, reason: String): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("withdrawals").document(withdrawal.id),
                mapOf(
                    "status" to "rejected",
                    "adminNote" to reason.ifBlank { "Rejected by admin" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("transactions").document("tx_wdr_rej_${withdrawal.id}"),
                mapOf(
                    "uid" to withdrawal.uid,
                    "type" to "withdrawal",
                    "amount" to withdrawal.amount,
                    "status" to "rejected",
                    "description" to "Rejected Withdrawal (${withdrawal.paymentMethod})",
                    "referenceId" to withdrawal.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "REJECT_WITHDRAWAL",
                    "targetCollection" to "withdrawals",
                    "targetId" to withdrawal.id,
                    "details" to "Rejected withdrawal PKR ${withdrawal.amount} for @${withdrawal.username}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "withdrawals/${withdrawal.id}")
            Result.failure(e)
        }
    }

    suspend fun approveReferralCommission(referral: ReferralRecord): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val commissionId = "comm_${referral.referredUid}"
            val commissionRef = db.collection("commissions").document(commissionId)
            val commSnap = commissionRef.get().await()
            if (commSnap.exists() && commSnap.getString("status") == "approved") {
                return Result.failure(IllegalStateException("Commission already approved for this referral."))
            }

            val settingsSnap = db.collection("settings").document("global").get().await()
            val amount = if (commSnap.exists()) {
                commSnap.getDouble("amount") ?: AppConstants.DEFAULT_REFERRAL_COMMISSION
            } else {
                settingsSnap.toObject(AppSettings::class.java)?.referralCommission ?: AppConstants.DEFAULT_REFERRAL_COMMISSION
            }

            val referrerRef = db.collection("users").document(referral.referrerUid)
            val referrer = referrerRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Referrer user not found."))

            val referrerWalletRef = db.collection("wallets").document(referral.referrerUid)
            val referrerWalletSnap = referrerWalletRef.get().await()
            val referrerWallet = if (referrerWalletSnap.exists()) {
                referrerWalletSnap.toObject(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val batch = db.batch()
            batch.update(
                db.collection("referrals").document(referral.id),
                mapOf(
                    "firstDepositVerified" to true,
                    "status" to "rewarded",
                    "commissionAmount" to amount,
                    "commissionStatus" to "approved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (commSnap.exists()) {
                batch.update(
                    commissionRef,
                    mapOf(
                        "status" to "approved",
                        "approvedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    commissionRef,
                    mapOf(
                        "referrerUid" to referral.referrerUid,
                        "referredUid" to referral.referredUid,
                        "referredUsername" to referral.referredUsername,
                        "amount" to amount,
                        "currency" to AppConstants.DEFAULT_CURRENCY,
                        "status" to "approved",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "approvedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val updatedReferrerReward = referrer.effectiveRewardBalance + amount
            batch.update(
                referrerRef,
                mapOf(
                    "realRewardBalance" to updatedReferrerReward,
                    "demoRewardBalance" to updatedReferrerReward,
                    "totalReferrals" to (referrer.totalReferrals + 1L),
                    "totalReferralCommission" to (referrer.totalReferralCommission + amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (referrerWallet != null) {
                batch.update(
                    referrerWalletRef,
                    mapOf(
                        "balance" to (referrerWallet.balance + amount),
                        "rewardBalance" to (referrerWallet.rewardBalance + amount),
                        "totalReferralCommission" to (referrerWallet.totalReferralCommission + amount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                db.collection("transactions").document("tx_comm_${referral.referredUid}"),
                mapOf(
                    "uid" to referral.referrerUid,
                    "type" to "referral_commission",
                    "amount" to amount,
                    "status" to "completed",
                    "description" to "Referral Commission from @${referral.referredUsername}",
                    "referenceId" to commissionId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "APPROVE_COMMISSION",
                    "targetCollection" to "commissions",
                    "targetId" to commissionId,
                    "details" to "Approved PKR $amount commission for @${referral.referredUsername}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "referrals/${referral.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectReferralCommission(referral: ReferralRecord): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val commissionId = "comm_${referral.referredUid}"
            val commissionRef = db.collection("commissions").document(commissionId)
            val commSnap = commissionRef.get().await()

            val batch = db.batch()
            batch.update(
                db.collection("referrals").document(referral.id),
                mapOf(
                    "status" to "rejected",
                    "commissionStatus" to "rejected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (commSnap.exists()) {
                batch.update(
                    commissionRef,
                    mapOf(
                        "status" to "rejected",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "REJECT_COMMISSION",
                    "targetCollection" to "commissions",
                    "targetId" to commissionId,
                    "details" to "Rejected referral commission for @${referral.referredUsername}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "referrals/${referral.id}")
            Result.failure(e)
        }
    }

    suspend fun updateCommissionSettings(
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
    ): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeReward = defaultTaskReward.coerceAtLeast(1.0)
        val safeMaxDaily = maxDailyReward.coerceAtLeast(safeReward)
        val cleanSlug = githubRepoSlug.trim().take(160)
        val cleanVersion = latestVersionName.trim().take(40)
        val safeVersionCode = latestVersionCode.coerceIn(0L, 1_000_000L)
        val cleanUrl = apkDownloadUrl.trim().take(600)
        val cleanNotes = releaseNotes.trim().take(1000)

        return try {
            val payload = mutableMapOf<String, Any>(
                "referralCommission" to referralCommission,
                "defaultTaskReward" to safeReward,
                "maxDailyReward" to safeMaxDaily,
                "currency" to AppConstants.DEFAULT_CURRENCY,
                "dailyTaskLimit" to dailyTaskLimit,
                "minDeposit" to minDeposit,
                "minWithdrawal" to minWithdrawal,
                "githubRepoSlug" to cleanSlug,
                "latestVersionName" to cleanVersion,
                "latestVersionCode" to safeVersionCode,
                "apkDownloadUrl" to cleanUrl,
                "releaseNotes" to cleanNotes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.set(db.collection("settings").document("global"), payload)
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "UPDATE_SETTINGS",
                    "targetCollection" to "settings",
                    "targetId" to "global",
                    "details" to "Updated commission=PKR $referralCommission, taskReward=PKR $safeReward, maxDaily=PKR $safeMaxDaily, release=${cleanVersion.ifBlank { "default" }}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "settings/global")
            Result.failure(e)
        }
    }

    suspend fun applyTaskRewardToAllTasks(rewardPoints: Double): Result<Unit> {
        val (adminUid, adminEmail) = try {
            requireAdminIdentity()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeReward = rewardPoints.coerceIn(1.0, 50000.0)
        return try {
            val snap = db.collection("tasks").get().await()
            val batch = db.batch()
            snap.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "rewardPoints" to safeReward,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val logId = "LOG-${UUID.randomUUID().toString().take(8)}"
            batch.set(
                db.collection("adminLogs").document(logId),
                mapOf(
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "action" to "BULK_TASK_REWARD",
                    "targetCollection" to "tasks",
                    "targetId" to "all",
                    "details" to "Applied PKR $safeReward reward to ${snap.size()} tasks.",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "tasks")
            Result.failure(e)
        }
    }

    companion object {
        val DEFAULT_PAYMENT_ACCOUNTS = listOf(
            PaymentAccountConfig(
                id = "easypaisa",
                providerKey = "easypaisa",
                providerName = "EasyPaisa",
                accountHolderName = "Tariq Mehmood",
                accountNumber = "0345-0001122",
                instructions = "Send the exact deposit amount to this EasyPaisa account and submit your transaction reference with receipt proof.",
                isEnabled = true
            ),
            PaymentAccountConfig(
                id = "jazzcash",
                providerKey = "jazzcash",
                providerName = "JazzCash",
                accountHolderName = "Bilal Ahmed",
                accountNumber = "0301-0003344",
                instructions = "Transfer the exact deposit amount to this JazzCash wallet and upload your payment screenshot.",
                isEnabled = true
            ),
            PaymentAccountConfig(
                id = "bank_transfer",
                providerKey = "bank_transfer",
                providerName = "Bank Transfer",
                bankName = "Meezan Bank (Test Branch)",
                accountHolderName = "Binace Smart Earn Treasury",
                accountNumber = "0102-0100998877",
                iban = "PK36MEZN0001020100998877",
                instructions = "Send an IBFT transfer to the bank account or IBAN above and upload your transfer receipt.",
                isEnabled = true
            )
        )
    }
}
