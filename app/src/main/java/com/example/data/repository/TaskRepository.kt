package com.example.data.repository

import com.example.data.model.AppConstants
import com.example.data.model.TaskItem
import com.example.data.model.UserProfile
import com.example.data.model.UserTaskRecord
import com.example.data.model.WalletAccount
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DefaultTaskSeed(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val rewardPoints: Double = AppConstants.DEFAULT_TASK_REWARD,
    val dailyLimit: Long = 1L,
    val durationSeconds: Long = 0L,
    val isActive: Boolean = true
)

class TaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(AppConstants.PKT_TIMEZONE_ID)
    }.format(Date())

    fun observeActiveTasks(): Flow<List<TaskItem>> = flow {
        val query = db.collection("tasks").whereEqualTo("isActive", true)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.id }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "tasks")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserTasks(uid: String): Flow<List<UserTaskRecord>> = flow {
        val query = db.collection("dailyTaskCompletions").whereEqualTo("uid", uid)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserTaskRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.startedAt }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "dailyTaskCompletions")
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun ensureDefaultTasksSeeded(): Result<Unit> {
        return try {
            requireUserId()
            val existingSnap = db.collection("tasks").limit(1).get().await()
            if (!existingSnap.isEmpty) {
                return Result.success(Unit)
            }
            val batch = db.batch()
            DEFAULT_TEN_TASKS.forEach { seed ->
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
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "tasks")
            Result.failure(e)
        }
    }

    suspend fun startTask(task: TaskItem, userProfile: UserProfile): Result<UserTaskRecord> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!userProfile.isVerified) {
            return Result.failure(
                IllegalStateException(
                    "Account verification required. Submit and get a simulated PKR ${AppConstants.DEFAULT_MIN_DEPOSIT.toInt()} demo deposit approved to unlock daily tasks."
                )
            )
        }
        val dateKey = todayDateKey()
        val utId = "${uid}_${task.id}_${dateKey}"
        val docRef = db.collection("dailyTaskCompletions").document(utId)

        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val record = existing.toObject(
                    UserTaskRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )!!
                return Result.success(record)
            }
            val payload = mapOf(
                "uid" to uid,
                "taskId" to task.id,
                "taskTitle" to task.title,
                "dateKey" to dateKey,
                "status" to "in_progress",
                "rewardPoints" to task.rewardPoints,
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val saved = docRef.get().await().toObject(
                UserTaskRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserTaskRecord(
                id = utId,
                uid = uid,
                taskId = task.id,
                taskTitle = task.title,
                dateKey = dateKey,
                status = "in_progress",
                rewardPoints = task.rewardPoints
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun claimTaskReward(
        task: TaskItem,
        userProfile: UserProfile,
        maxDailyReward: Double = AppConstants.DEFAULT_MAX_DAILY_REWARD
    ): Result<Double> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!userProfile.isVerified) {
            return Result.failure(
                IllegalStateException(
                    "Account verification required. Submit and get a simulated PKR ${AppConstants.DEFAULT_MIN_DEPOSIT.toInt()} demo deposit approved to unlock daily task rewards."
                )
            )
        }
        val dateKey = todayDateKey()
        val currentTodayEarnings = if (userProfile.todayTaskDate == dateKey) userProfile.todayTaskEarnings else 0.0
        if (currentTodayEarnings + task.rewardPoints > maxDailyReward + 0.001) {
            return Result.failure(
                IllegalStateException(
                    "Daily simulated reward limit of PKR ${maxDailyReward.toInt()} reached. Tasks reset at 12:00 AM PKT."
                )
            )
        }

        val utId = "${uid}_${task.id}_${dateKey}"
        val completionRef = db.collection("dailyTaskCompletions").document(utId)
        val userRef = db.collection("users").document(uid)
        val walletRef = db.collection("wallets").document(uid)
        val txId = "tx_task_${utId}"
        val txRef = db.collection("transactions").document(txId)

        return try {
            val existingSnap = completionRef.get().await()
            val existingRecord = if (existingSnap.exists()) {
                existingSnap.toObject(UserTaskRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            if (existingRecord?.status == "claimed") {
                return Result.failure(IllegalStateException("Reward already claimed today for this task."))
            }

            val walletSnap = walletRef.get().await()
            val existingWallet = if (walletSnap.exists()) {
                walletSnap.toObject(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val batch = db.batch()
            if (existingRecord == null) {
                batch.set(
                    completionRef,
                    mapOf(
                        "uid" to uid,
                        "taskId" to task.id,
                        "taskTitle" to task.title,
                        "dateKey" to dateKey,
                        "status" to "claimed",
                        "rewardPoints" to task.rewardPoints,
                        "startedAt" to FieldValue.serverTimestamp(),
                        "completedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    completionRef,
                    mapOf(
                        "status" to "claimed",
                        "completedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                txRef,
                mapOf(
                    "uid" to uid,
                    "type" to "task_reward",
                    "amount" to task.rewardPoints,
                    "status" to "completed",
                    "description" to "Daily Task: ${task.title}",
                    "referenceId" to utId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val newTodayEarnings = currentTodayEarnings + task.rewardPoints
            val updatedRewardBal = userProfile.effectiveRewardBalance + task.rewardPoints
            batch.update(
                userRef,
                mapOf(
                    "realRewardBalance" to updatedRewardBal,
                    "demoRewardBalance" to updatedRewardBal,
                    "todayTaskEarnings" to newTodayEarnings,
                    "todayTaskDate" to dateKey,
                    "totalEarnedTasks" to (userProfile.totalEarnedTasks + task.rewardPoints),
                    "completedTasksCount" to (userProfile.completedTasksCount + 1L),
                    "lastClaimedUserTaskId" to utId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (existingWallet != null) {
                val walletTodayEarnings = if (existingWallet.todayTaskDate == dateKey) {
                    existingWallet.todayTaskEarnings + task.rewardPoints
                } else {
                    task.rewardPoints
                }
                batch.update(
                    walletRef,
                    mapOf(
                        "balance" to (existingWallet.balance + task.rewardPoints),
                        "rewardBalance" to (existingWallet.rewardBalance + task.rewardPoints),
                        "todayTaskEarnings" to walletTodayEarnings,
                        "todayTaskDate" to dateKey,
                        "totalEarnedTasks" to (existingWallet.totalEarnedTasks + task.rewardPoints),
                        "lastClaimedTaskCompletionId" to utId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(task.rewardPoints)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, completionRef.path)
            Result.failure(e)
        }
    }

    companion object {
        val DEFAULT_TEN_TASKS: List<DefaultTaskSeed> = listOf(
            DefaultTaskSeed(
                id = "task_01_daily_login",
                title = "Daily login check-in",
                description = "Check in once every 24 hours to claim your daily simulated loyalty reward.",
                category = "daily_login",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_02_educational_tip",
                title = "Read an educational tip",
                description = "Review today's financial growth principle and smart savings strategy.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_03_educational_video",
                title = "Watch an educational video",
                description = "Complete a short educational video brief on digital asset safety and wallet growth.",
                category = "watch_video",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 5L
            ),
            DefaultTaskSeed(
                id = "task_04_knowledge_quiz",
                title = "Complete a knowledge quiz",
                description = "Verify your understanding of daily budgeting and smart investment concepts.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_05_english_word",
                title = "Learn a new English word",
                description = "Expand your fintech and business vocabulary with today's featured term.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_06_financial_safety",
                title = "Read a financial safety tip",
                description = "Learn how to protect your account with strong verification and scam awareness.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_07_general_knowledge",
                title = "Answer a general knowledge question",
                description = "Engage with today's featured financial literacy and market insight question.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_08_app_tutorial",
                title = "Explore an application tutorial",
                description = "Review wallet tracking, deposit verification, and referral reward steps.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_09_learning_activity",
                title = "Complete a learning activity",
                description = "Finish today's guided wealth-building micro-lesson to unlock your reward.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            ),
            DefaultTaskSeed(
                id = "task_10_knowledge_challenge",
                title = "Daily knowledge challenge",
                description = "Complete the final daily knowledge milestone to reach your PKR 200 simulated daily reward target.",
                category = "daily_activity",
                rewardPoints = AppConstants.DEFAULT_TASK_REWARD,
                dailyLimit = 1L,
                durationSeconds = 0L
            )
        )
    }
}
