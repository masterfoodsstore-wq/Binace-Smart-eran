package com.example.data.repository

import com.example.data.model.AppConstants
import com.example.data.model.ReferralCodeEntry
import com.example.data.model.UserProfile
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
import kotlin.random.Random

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(AppConstants.PKT_TIMEZONE_ID)
    }.format(Date())

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeWallet(uid: String): Flow<WalletAccount?> = flow {
        val docRef = db.collection("wallets").document(uid)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(WalletAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun ensureWalletExists(profile: UserProfile) {
        val uid = auth.currentUser?.uid ?: return
        if (uid != profile.uid) return
        val walletRef = db.collection("wallets").document(uid)
        try {
            val snap = walletRef.get().await()
            if (!snap.exists()) {
                walletRef.set(
                    mapOf(
                        "uid" to uid,
                        "walletId" to profile.walletId,
                        "balance" to 0.0,
                        "depositBalance" to 0.0,
                        "rewardBalance" to 0.0,
                        "todayTaskEarnings" to 0.0,
                        "todayTaskDate" to todayDateKey(),
                        "totalEarnedTasks" to 0.0,
                        "totalReferralCommission" to 0.0,
                        "currency" to AppConstants.DEFAULT_CURRENCY,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-blocking fallback if wallet already initialized or cannot be created with 0
        }
    }

    suspend fun isUserAdmin(uid: String): Boolean {
        val user = auth.currentUser
        if (user?.email?.equals(AppConstants.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) == true && user.isEmailVerified) {
            return true
        }
        val path = "admins/$uid"
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    suspend fun registerUser(
        fullName: String,
        username: String,
        email: String,
        phone: String,
        referralCodeInput: String
    ): Result<UserProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val cleanFullName = fullName.trim()
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
        val cleanEmail = email.trim()
        val cleanPhone = phone.trim()
        val cleanRefInput = referralCodeInput.trim().uppercase()

        if (cleanFullName.length < 2) return Result.failure(IllegalArgumentException("Full name must be at least 2 characters."))
        if (cleanUsername.length < 3 || cleanUsername.length > 30) {
            return Result.failure(IllegalArgumentException("Username must be 3-30 alphanumeric characters."))
        }
        if (cleanPhone.length < 5) return Result.failure(IllegalArgumentException("Enter a valid phone number."))

        return try {
            var referrerUid = ""
            var validatedRefCode = ""
            if (cleanRefInput.isNotEmpty()) {
                val refCodePath = "referralCodes/$cleanRefInput"
                val refSnap = try {
                    db.collection("referralCodes").document(cleanRefInput).get().await()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.GET, refCodePath)
                    throw e
                }
                val entry = refSnap.toObject(ReferralCodeEntry::class.java)
                    ?: return Result.failure(IllegalArgumentException("Referral code '$cleanRefInput' was not found."))
                if (entry.uid == uid) {
                    return Result.failure(IllegalArgumentException("Self-referral is not allowed."))
                }
                referrerUid = entry.uid
                validatedRefCode = entry.code
            }

            // Generate guaranteed-unique referral code & wallet ID
            val generatedCode = allocateUniqueReferralCode(cleanUsername, uid)
            val walletId = "BSE-WLT-${uid.take(6).uppercase()}"
            val today = todayDateKey()

            val batch = db.batch()
            val userRef = db.collection("users").document(uid)
            val userPayload = mutableMapOf<String, Any>(
                "uid" to uid,
                "fullName" to cleanFullName,
                "username" to cleanUsername,
                "email" to cleanEmail,
                "phone" to cleanPhone,
                "referralCode" to generatedCode,
                "walletId" to walletId,
                "isVerified" to false,
                "firstDepositStatus" to "none",
                "firstDepositAmount" to 0.0,
                "demoBalance" to 0.0,
                "demoRewardBalance" to 0.0,
                "realBalance" to 0.0,
                "realRewardBalance" to 0.0,
                "todayTaskEarnings" to 0.0,
                "todayTaskDate" to today,
                "totalEarnedTasks" to 0.0,
                "completedTasksCount" to 0L,
                "totalReferrals" to 0L,
                "totalReferralCommission" to 0.0,
                "accountStatus" to "active",
                "role" to "user",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (validatedRefCode.isNotEmpty() && referrerUid.isNotEmpty()) {
                userPayload["referredBy"] = validatedRefCode
                userPayload["referredByUid"] = referrerUid
            }
            batch.set(userRef, userPayload)

            val walletRef = db.collection("wallets").document(uid)
            batch.set(
                walletRef,
                mapOf(
                    "uid" to uid,
                    "walletId" to walletId,
                    "balance" to 0.0,
                    "depositBalance" to 0.0,
                    "rewardBalance" to 0.0,
                    "todayTaskEarnings" to 0.0,
                    "todayTaskDate" to today,
                    "totalEarnedTasks" to 0.0,
                    "totalReferralCommission" to 0.0,
                    "currency" to AppConstants.DEFAULT_CURRENCY,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val refCodeRef = db.collection("referralCodes").document(generatedCode)
            batch.set(
                refCodeRef,
                mapOf(
                    "code" to generatedCode,
                    "uid" to uid,
                    "username" to cleanUsername,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            if (validatedRefCode.isNotEmpty() && referrerUid.isNotEmpty()) {
                val referralRef = db.collection("referrals").document(uid)
                batch.set(
                    referralRef,
                    mapOf(
                        "referrerUid" to referrerUid,
                        "referredUid" to uid,
                        "referredUsername" to cleanUsername,
                        "referralCode" to validatedRefCode,
                        "registrationDate" to FieldValue.serverTimestamp(),
                        "firstDepositVerified" to false,
                        "status" to "registered",
                        "commissionAmount" to 0.0,
                        "commissionStatus" to "none",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            val created = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    uid = uid,
                    fullName = cleanFullName,
                    username = cleanUsername,
                    email = cleanEmail,
                    phone = cleanPhone,
                    referralCode = generatedCode,
                    referredBy = validatedRefCode,
                    referredByUid = referrerUid,
                    walletId = walletId,
                    todayTaskDate = today
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fullName: String, username: String, phone: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = fullName.trim()
        val cleanUser = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
        val cleanPhone = phone.trim()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Name too short."))
        if (cleanUser.length < 3) return Result.failure(IllegalArgumentException("Username must be at least 3 chars."))
        if (cleanPhone.length < 5) return Result.failure(IllegalArgumentException("Enter a valid phone number."))

        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "fullName" to cleanName,
                    "username" to cleanUser,
                    "phone" to cleanPhone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    private suspend fun allocateUniqueReferralCode(username: String, uid: String): String {
        val prefix = username.uppercase().replace(Regex("[^A-Z0-9]"), "").take(5).ifEmpty { "USER" }
        repeat(4) {
            val candidate = "$prefix${Random.nextInt(1000, 9999)}"
            val exists = try {
                db.collection("referralCodes").document(candidate).get().await().exists()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!exists) return candidate
        }
        return "$prefix${uid.take(4).uppercase()}${Random.nextInt(10, 99)}"
    }
}
