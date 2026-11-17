package com.example.data.repository

import com.example.data.model.LeaderboardEntry
import com.example.data.model.LeaderboardPeriod
import com.example.data.model.LeaderboardPrize
import com.example.data.model.UserProfile
import com.example.data.model.VipHistoryRecord
import com.example.data.model.VipLevel
import com.example.data.model.VipMembership
import com.example.data.model.VipTierConfig
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class VipLeaderboardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    fun currentDateKeys(): Triple<String, String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val now = Date()
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
        val dayKey = dayFmt.format(now)
        val monthKey = dayKey.substring(0, 7)

        val cal = Calendar.getInstance(utc).apply {
            minimalDaysInFirstWeek = 4
            firstDayOfWeek = Calendar.MONDAY
            time = now
        }
        val weekYear = cal.weekYear
        val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
        val weekKey = "$weekYear-W${weekNum.toString().padStart(2, '0')}"
        return Triple(dayKey, weekKey, monthKey)
    }

    fun observeVipTierConfigs(): Flow<List<VipTierConfig>> = flow {
        db.collection("vipConfigs")
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(VipTierConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.isActive }
                    .sortedBy { it.tierOrder }
                if (list.isEmpty()) DEFAULT_VIP_TIERS else list
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(DEFAULT_VIP_TIERS)
            }
            .collect { emit(it) }
    }

    fun observeAllVipTierConfigsAdmin(): Flow<List<VipTierConfig>> = flow {
        db.collection("vipConfigs")
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(VipTierConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.tierOrder }
                if (list.isEmpty()) DEFAULT_VIP_TIERS else list
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(DEFAULT_VIP_TIERS)
            }
            .collect { emit(it) }
    }

    fun observeUserVipMembership(uid: String): Flow<VipMembership?> = flow {
        db.collection("vipMemberships").document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(VipMembership::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                (fs ?: error as? Exception)?.let {
                    if (auth.currentUser?.uid == uid) handleFirestoreError(it, OperationType.GET, "vipMemberships/$uid")
                }
                emit(null)
            }
            .collect { emit(it) }
    }

    fun observeUserVipHistory(uid: String): Flow<List<VipHistoryRecord>> = flow {
        db.collection("vipHistory")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(VipHistoryRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeAllVipMembershipsAdmin(): Flow<List<VipMembership>> = flow {
        db.collection("vipMemberships")
            .snapshots()
            .map { snap ->
                snap.toObjects(VipMembership::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<VipMembership> { it.tierOrder }.thenByDescending { it.verifiedDepositTotal })
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeLeaderboardEntries(): Flow<List<LeaderboardEntry>> = flow {
        db.collection("leaderboardEntries")
            .snapshots()
            .map { snap ->
                snap.toObjects(LeaderboardEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeLeaderboardPrizes(): Flow<List<LeaderboardPrize>> = flow {
        db.collection("leaderboardPrizes")
            .snapshots()
            .map { snap ->
                snap.toObjects(LeaderboardPrize::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.rankFrom }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    suspend fun ensureInitializedAndSync(profile: UserProfile) {
        val uid = auth.currentUser?.uid ?: return
        if (uid != profile.uid) return
        val (dayKey, weekKey, monthKey) = currentDateKeys()

        try {
            val vipRef = db.collection("vipMemberships").document(uid)
            if (!vipRef.get().await().exists()) {
                vipRef.set(
                    mapOf(
                        "uid" to uid,
                        "username" to profile.username.ifBlank { "user" },
                        "currentLevel" to VipLevel.BRONZE.key,
                        "currentTierName" to VipLevel.BRONZE.displayName,
                        "tierOrder" to 1,
                        "verifiedDepositTotal" to 0.0,
                        "completedTasksTotal" to 0,
                        "qualifiedReferralsCount" to 0,
                        "progressPercent" to 0,
                        "nextLevelKey" to VipLevel.SILVER.key,
                        "assignedBy" to "system_auto",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val lbRef = db.collection("leaderboardEntries").document(uid)
            if (!lbRef.get().await().exists()) {
                lbRef.set(
                    mapOf(
                        "uid" to uid,
                        "username" to profile.username.ifBlank { "user" },
                        "displayName" to profile.fullName.ifBlank { profile.username }.take(60),
                        "vipLevel" to VipLevel.BRONZE.key,
                        "isVerified" to profile.isVerified,
                        "dailyPoints" to 0.0,
                        "dailyDateKey" to dayKey,
                        "weeklyPoints" to 0.0,
                        "weekKey" to weekKey,
                        "monthlyPoints" to 0.0,
                        "monthKey" to monthKey,
                        "allTimeTaskPoints" to 0.0,
                        "completedTasksCount" to 0,
                        "qualifiedReferralsCount" to 0,
                        "verifiedActivityScore" to 0.0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        // Trigger server-side verification & computation via Cloud Function (if deployed)
        try {
            functions.getHttpsCallable("syncVipAndLeaderboard").call().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun requestServerSync(): Result<String> {
        return try {
            functions.getHttpsCallable("syncVipAndLeaderboard").call().await()
            Result.success("VIP level & verified leaderboard standing synced from server.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Deploy Firebase Cloud Functions to enable on-demand server recalculation (${e.localizedMessage ?: "offline"})."
                )
            )
        }
    }

    // ========================================================================
    // ADMIN CONTROLS
    // ========================================================================

    suspend fun seedDefaultVipAndPrizesAdmin(): Result<Unit> {
        return try {
            val batch = db.batch()
            DEFAULT_VIP_TIERS.forEach { tier ->
                val ref = db.collection("vipConfigs").document(tier.levelKey)
                batch.set(
                    ref,
                    mapOf(
                        "levelKey" to tier.levelKey,
                        "tierName" to tier.tierName,
                        "tierOrder" to tier.tierOrder,
                        "minVerifiedDepositsPkr" to tier.minVerifiedDepositsPkr,
                        "minCompletedTasks" to tier.minCompletedTasks,
                        "minQualifiedReferrals" to tier.minQualifiedReferrals,
                        "taskBonusPercent" to tier.taskBonusPercent,
                        "prioritySupportLabel" to tier.prioritySupportLabel,
                        "benefits" to tier.benefits,
                        "isActive" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            DEFAULT_PROMO_PRIZES.forEach { prize ->
                val ref = db.collection("leaderboardPrizes").document(prize.effectivePrizeId)
                batch.set(
                    ref,
                    mapOf(
                        "prizeId" to prize.effectivePrizeId,
                        "periodType" to prize.periodType,
                        "rankFrom" to prize.rankFrom,
                        "rankTo" to prize.rankTo,
                        "title" to prize.title,
                        "rewardDescription" to prize.rewardDescription,
                        "rewardAmountPkr" to prize.rewardAmountPkr,
                        "isActive" to prize.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "vipConfigs")
            Result.failure(e)
        }
    }

    suspend fun saveVipTierConfig(config: VipTierConfig): Result<Unit> {
        return try {
            db.collection("vipConfigs").document(config.levelKey).set(
                mapOf(
                    "levelKey" to config.levelKey,
                    "tierName" to config.tierName.trim(),
                    "tierOrder" to config.tierOrder,
                    "minVerifiedDepositsPkr" to config.minVerifiedDepositsPkr.coerceAtLeast(0.0),
                    "minCompletedTasks" to config.minCompletedTasks.coerceAtLeast(0),
                    "minQualifiedReferrals" to config.minQualifiedReferrals.coerceAtLeast(0),
                    "taskBonusPercent" to config.taskBonusPercent.coerceIn(0, 100),
                    "prioritySupportLabel" to config.prioritySupportLabel.trim(),
                    "benefits" to config.benefits.filter { it.isNotBlank() },
                    "isActive" to config.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignUserVipTierAdmin(
        targetUser: UserProfile,
        newLevel: VipLevel,
        reason: String
    ): Result<Unit> {
        val adminEmail = auth.currentUser?.email ?: "admin"
        return try {
            val vipRef = db.collection("vipMemberships").document(targetUser.uid)
            val existingSnap = vipRef.get().await()
            val prevLevel = existingSnap.getString("currentLevel") ?: VipLevel.BRONZE.key
            val verifiedDepositTotal = existingSnap.getDouble("verifiedDepositTotal") ?: targetUser.realBalance
            val completedTasksTotal = (existingSnap.getLong("completedTasksTotal") ?: targetUser.completedTasksCount).toInt()
            val qualifiedReferrals = (existingSnap.getLong("qualifiedReferralsCount") ?: targetUser.totalReferrals).toInt()
            val nextKey = VipLevel.entries.firstOrNull { it.order == newLevel.order + 1 }?.key ?: ""

            val historyId = "VIPH-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"
            val notifId = "NTF-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"

            val batch = db.batch()
            batch.set(
                vipRef,
                mapOf(
                    "uid" to targetUser.uid,
                    "username" to targetUser.username,
                    "currentLevel" to newLevel.key,
                    "currentTierName" to newLevel.displayName,
                    "tierOrder" to newLevel.order,
                    "verifiedDepositTotal" to verifiedDepositTotal,
                    "completedTasksTotal" to completedTasksTotal,
                    "qualifiedReferralsCount" to qualifiedReferrals,
                    "progressPercent" to if (nextKey.isEmpty()) 100 else 50,
                    "nextLevelKey" to nextKey,
                    "assignedBy" to "admin_override",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("vipHistory").document(historyId),
                mapOf(
                    "historyId" to historyId,
                    "uid" to targetUser.uid,
                    "previousLevel" to prevLevel,
                    "newLevel" to newLevel.key,
                    "reason" to reason.ifBlank { "Updated by admin review" },
                    "source" to "admin",
                    "changedBy" to adminEmail,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("notifications").document(notifId),
                mapOf(
                    "notificationId" to notifId,
                    "uid" to targetUser.uid,
                    "title" to "VIP Status Updated: ${newLevel.displayName}",
                    "body" to "An administrator updated your VIP level to ${newLevel.displayName}. ${reason.ifBlank { "" }}".trim(),
                    "category" to "vip",
                    "isRead" to false,
                    "referenceId" to historyId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveLeaderboardPrize(prize: LeaderboardPrize): Result<Unit> {
        val prizeId = prize.effectivePrizeId.ifBlank {
            "PRIZE-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        }
        return try {
            db.collection("leaderboardPrizes").document(prizeId).set(
                mapOf(
                    "prizeId" to prizeId,
                    "periodType" to prize.periodType,
                    "rankFrom" to prize.rankFrom.coerceIn(1, 100),
                    "rankTo" to prize.rankTo.coerceIn(prize.rankFrom.coerceIn(1, 100), 100),
                    "title" to prize.title.trim(),
                    "rewardDescription" to prize.rewardDescription.trim(),
                    "rewardAmountPkr" to prize.rewardAmountPkr.coerceAtLeast(0.0),
                    "isActive" to prize.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLeaderboardPrize(prizeId: String): Result<Unit> {
        return try {
            db.collection("leaderboardPrizes").document(prizeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebuildAllLeaderboardAdmin(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("rebuildAllVipAndLeaderboard").call().await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any?>
            val count = map?.get("updatedCount") ?: "all"
            Result.success("Recalculated verified VIP & Leaderboard standing for $count accounts.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Cloud Function rebuild failed: ${e.localizedMessage}"))
        }
    }

    private fun extractFsError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        val DEFAULT_VIP_TIERS = listOf(
            VipTierConfig(
                levelKey = "bronze",
                tierName = "Bronze",
                tierOrder = 1,
                minVerifiedDepositsPkr = 0.0,
                minCompletedTasks = 0,
                minQualifiedReferrals = 0,
                taskBonusPercent = 0,
                prioritySupportLabel = "Standard Support Queue",
                benefits = listOf(
                    "Standard dashboard, wallet, & referral link tools",
                    "Real-time progress tracking toward Silver VIP",
                    "Community announcements & security alerts"
                ),
                isActive = true
            ),
            VipTierConfig(
                levelKey = "silver",
                tierName = "Silver",
                tierOrder = 2,
                minVerifiedDepositsPkr = 1000.0,
                minCompletedTasks = 5,
                minQualifiedReferrals = 0,
                taskBonusPercent = 5,
                prioritySupportLabel = "Silver Priority Queue",
                benefits = listOf(
                    "Verified member status + Silver VIP emblem",
                    "Priority Live Customer Support queue tag",
                    "Eligible for seasonal promotional leaderboard events"
                ),
                isActive = true
            ),
            VipTierConfig(
                levelKey = "gold",
                tierName = "Gold",
                tierOrder = 3,
                minVerifiedDepositsPkr = 5000.0,
                minCompletedTasks = 25,
                minQualifiedReferrals = 2,
                taskBonusPercent = 10,
                prioritySupportLabel = "Gold Fast-Track Support",
                benefits = listOf(
                    "Gold VIP crest on Global Leaderboard & Profile",
                    "Fast-track Live Support escalation",
                    "Eligible for Gold promotional prize draws"
                ),
                isActive = true
            ),
            VipTierConfig(
                levelKey = "platinum",
                tierName = "Platinum",
                tierOrder = 4,
                minVerifiedDepositsPkr = 15000.0,
                minCompletedTasks = 75,
                minQualifiedReferrals = 5,
                taskBonusPercent = 15,
                prioritySupportLabel = "Platinum Concierge Desk",
                benefits = listOf(
                    "Platinum badge + dedicated support routing",
                    "Enhanced session & withdrawal security insights",
                    "Eligible for Platinum community competitions"
                ),
                isActive = true
            ),
            VipTierConfig(
                levelKey = "diamond",
                tierName = "Diamond",
                tierOrder = 5,
                minVerifiedDepositsPkr = 50000.0,
                minCompletedTasks = 150,
                minQualifiedReferrals = 10,
                taskBonusPercent = 25,
                prioritySupportLabel = "Diamond Executive Support",
                benefits = listOf(
                    "Top Diamond VIP tier & prestige ranking badge",
                    "Highest priority support desk response",
                    "Full access to top promotional prize pools"
                ),
                isActive = true
            )
        )

        val DEFAULT_PROMO_PRIZES = listOf(
            LeaderboardPrize(
                prizeId = "PRIZE-WEEKLY-1",
                periodType = LeaderboardPeriod.WEEKLY.key,
                rankFrom = 1,
                rankTo = 3,
                title = "Weekly Top 3 Promotional Pool",
                rewardDescription = "Promotional bonus credited after weekly audit of genuine verified task & referral activity. No guaranteed returns.",
                rewardAmountPkr = 1500.0,
                isActive = true
            ),
            LeaderboardPrize(
                prizeId = "PRIZE-MONTHLY-1",
                periodType = LeaderboardPeriod.MONTHLY.key,
                rankFrom = 1,
                rankTo = 10,
                title = "Monthly Top 10 Community Trophy",
                rewardDescription = "Promotional recognition prize for Top 10 verified monthly contributors (non-guaranteed seasonal event).",
                rewardAmountPkr = 3000.0,
                isActive = true
            )
        )
    }
}
