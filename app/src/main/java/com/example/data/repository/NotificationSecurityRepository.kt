package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.model.AnnouncementItem
import com.example.data.model.AppNotificationItem
import com.example.data.model.SecurityDeviceSession
import com.example.data.model.SecurityEvent
import com.example.data.model.SecurityProfile
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.util.SecurityCryptoHelper
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
import java.util.Date
import java.util.UUID

class NotificationSecurityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    // ========================================================================
    // 3. NOTIFICATION CENTER & ANNOUNCEMENTS
    // ========================================================================

    fun observeUserNotifications(uid: String): Flow<List<AppNotificationItem>> = flow {
        db.collection("notifications")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observePublishedAnnouncements(): Flow<List<AnnouncementItem>> = flow {
        db.collection("announcements")
            .whereEqualTo("status", "published")
            .snapshots()
            .map { snap ->
                snap.toObjects(AnnouncementItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.publishedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeAnnouncementReads(uid: String): Flow<Set<String>> = flow {
        db.collection("announcementReads")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { it.getString("announcementId") }.toSet()
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptySet())
            }
            .collect { emit(it) }
    }

    fun observeAllAnnouncementsAdmin(): Flow<List<AnnouncementItem>> = flow {
        db.collection("announcements")
            .snapshots()
            .map { snap ->
                snap.toObjects(AnnouncementItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    suspend fun markNotificationRead(notificationId: String) {
        try {
            db.collection("notifications").document(notificationId).update(
                mapOf(
                    "isRead" to true,
                    "readAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun markAnnouncementRead(uid: String, announcementId: String) {
        try {
            val readId = "${uid}_$announcementId"
            db.collection("announcementReads").document(readId).set(
                mapOf(
                    "uid" to uid,
                    "announcementId" to announcementId,
                    "readAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun markAllRead(uid: String, unreadNotificationIds: List<String>, unreadAnnouncementIds: List<String>) {
        if (unreadNotificationIds.isEmpty() && unreadAnnouncementIds.isEmpty()) return
        try {
            val batch = db.batch()
            unreadNotificationIds.take(200).forEach { id ->
                batch.update(
                    db.collection("notifications").document(id),
                    mapOf("isRead" to true, "readAt" to FieldValue.serverTimestamp())
                )
            }
            unreadAnnouncementIds.take(200).forEach { annId ->
                batch.set(
                    db.collection("announcementReads").document("${uid}_$annId"),
                    mapOf("uid" to uid, "announcementId" to annId, "readAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun createAnnouncementAdmin(
        title: String,
        body: String,
        category: String,
        scheduleMinutesFromNow: Long?,
        sendPushNotification: Boolean
    ): Result<String> {
        val adminUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Admin session required."))
        val adminEmail = auth.currentUser?.email ?: "admin"
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanTitle.length < 2) return Result.failure(IllegalArgumentException("Enter a title (min 2 chars)."))
        if (cleanBody.length < 2) return Result.failure(IllegalArgumentException("Enter a message body (min 2 chars)."))

        val announcementId = "ANN-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"
        val isScheduled = scheduleMinutesFromNow != null && scheduleMinutesFromNow > 0L
        val status = if (isScheduled) "scheduled" else "published"
        val payload = mutableMapOf<String, Any>(
            "announcementId" to announcementId,
            "title" to cleanTitle.take(140),
            "body" to cleanBody.take(1000),
            "category" to category,
            "status" to status,
            "sendPushNotification" to sendPushNotification,
            "pushDispatched" to false,
            "createdByUid" to adminUid,
            "createdByEmail" to adminEmail,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isScheduled) {
            val scheduledDate = Date(System.currentTimeMillis() + scheduleMinutesFromNow!! * 60_000L)
            payload["scheduledFor"] = Timestamp(scheduledDate)
        } else {
            payload["publishedAt"] = FieldValue.serverTimestamp()
        }

        return try {
            db.collection("announcements").document(announcementId).set(payload).await()
            Result.success(announcementId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "announcements/$announcementId")
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncementNowAdmin(announcementId: String): Result<Unit> {
        return try {
            db.collection("announcements").document(announcementId).update(
                mapOf(
                    "status" to "published",
                    "publishedAt" to FieldValue.serverTimestamp(),
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

    suspend fun cancelAnnouncementAdmin(announcementId: String): Result<Unit> {
        return try {
            db.collection("announcements").document(announcementId).update(
                mapOf(
                    "status" to "cancelled",
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

    // ========================================================================
    // 4. ACCOUNT SECURITY (OTP, 2FA TOTP, BIOMETRICS, DEVICES, SESSIONS, EVENTS)
    // ========================================================================

    fun observeSecurityProfile(uid: String): Flow<SecurityProfile?> = flow {
        db.collection("securityProfiles").document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(SecurityProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(null)
            }
            .collect { emit(it) }
    }

    fun observeUserDevices(uid: String): Flow<List<SecurityDeviceSession>> = flow {
        db.collection("securityDevices")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityDeviceSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastActiveAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeUserSecurityEvents(uid: String): Flow<List<SecurityEvent>> = flow {
        db.collection("securityEvents")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeAllSecurityEventsAdmin(): Flow<List<SecurityEvent>> = flow {
        db.collection("securityEvents")
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    fun observeAllDevicesAdmin(): Flow<List<SecurityDeviceSession>> = flow {
        db.collection("securityDevices")
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityDeviceSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastActiveAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fs = extractFsError(error)
                if (fs == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .collect { emit(it) }
    }

    suspend fun ensureSecurityProfileAndRegisterDevice(
        context: Context,
        profile: UserProfile,
        appVersionName: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (uid != profile.uid) return

        val deviceId = SecurityCryptoHelper.getOrCreateDeviceId(context)
        val sessionId = "${uid}_$deviceId"
        val deviceName = SecurityCryptoHelper.buildDeviceDisplayName()
        val tz = SecurityCryptoHelper.currentTimezoneId()
        val locale = SecurityCryptoHelper.currentLocaleCode()

        try {
            val secRef = db.collection("securityProfiles").document(uid)
            if (!secRef.get().await().exists()) {
                secRef.set(
                    mapOf(
                        "uid" to uid,
                        "totpEnabled" to false,
                        "biometricEnabled" to false,
                        "otpVerified" to false,
                        "withdrawalDetailVerified" to false,
                        "suspiciousAlertActive" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val devRef = db.collection("securityDevices").document(sessionId)
            val existingDev = devRef.get().await()
            if (!existingDev.exists()) {
                val otherSnap = db.collection("securityDevices").whereEqualTo("uid", uid).get().await()
                val hasPreviousDevices = otherSnap.documents.any { it.id != sessionId }
                val tzKnown = otherSnap.documents.any { it.getString("timezoneId") == tz }
                val isSuspicious = hasPreviousDevices && !tzKnown || hasPreviousDevices
                val suspiciousReason = if (isSuspicious) {
                    "New device sign-in detected: $deviceName (Android ${Build.VERSION.RELEASE ?: ""}) • $tz"
                } else null

                val payload = mutableMapOf<String, Any>(
                    "sessionId" to sessionId,
                    "uid" to uid,
                    "deviceId" to deviceId,
                    "deviceName" to deviceName,
                    "manufacturer" to (Build.MANUFACTURER ?: "Android").take(60),
                    "osVersion" to (Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()).take(40),
                    "appVersion" to appVersionName.take(40),
                    "timezoneId" to tz.take(80),
                    "localeCode" to locale.take(40),
                    "isTrusted" to !hasPreviousDevices,
                    "isSuspicious" to isSuspicious,
                    "status" to "active",
                    "firstSeenAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
                if (suspiciousReason != null) {
                    payload["suspiciousReason"] = suspiciousReason.take(300)
                }
                devRef.set(payload).await()

                recordSecurityEvent(
                    uid = uid,
                    username = profile.username,
                    eventType = if (isSuspicious) "SUSPICIOUS_LOGIN_DETECTED" else "DEVICE_REGISTERED",
                    title = if (isSuspicious) "Unrecognized Device Login" else "Device Registered",
                    details = suspiciousReason ?: "Registered primary session on $deviceName.",
                    deviceId = deviceId,
                    deviceName = deviceName,
                    severity = if (isSuspicious) "warning" else "info"
                )
            } else {
                val status = existingDev.getString("status") ?: "active"
                if (status == "active") {
                    devRef.update(
                        mapOf(
                            "appVersion" to appVersionName.take(40),
                            "timezoneId" to tz.take(80),
                            "lastActiveAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun requestOtpChallenge(): Result<Triple<String, String, Long>> {
        return try {
            val result = functions.getHttpsCallable("requestOtpChallenge")
                .call(mapOf("purpose" to "account_security"))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: emptyMap()
            val challengeId = (data["challengeId"] as? String).orEmpty()
            val message = (data["message"] as? String).orEmpty()
            val channel = (data["deliveryChannel"] as? String).orEmpty()
            val expiresAtMs = (data["expiresAtMs"] as? Number)?.toLong() ?: (System.currentTimeMillis() + 600_000L)
            if (challengeId.isBlank()) {
                Result.failure(IllegalStateException("OTP server did not return a challenge ID."))
            } else {
                Result.success(Triple(challengeId, "$message ($channel)", expiresAtMs))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("OTP challenge failed: ${e.localizedMessage ?: "Deploy Cloud Functions"}"))
        }
    }

    suspend fun verifyOtpChallenge(challengeId: String, code: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("verifyOtpChallenge")
                .call(mapOf("challengeId" to challengeId, "code" to code.trim()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Invalid or expired OTP code."))
        }
    }

    suspend fun updateBiometricEnabled(profile: UserProfile, enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            db.collection("securityProfiles").document(uid).update(
                mapOf(
                    "biometricEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordSecurityEvent(
                uid = uid,
                username = profile.username,
                eventType = if (enabled) "BIOMETRIC_ENABLED" else "BIOMETRIC_DISABLED",
                title = if (enabled) "Biometric Lock Enabled" else "Biometric Lock Disabled",
                details = if (enabled) "Device biometric check required for security controls." else "Biometric lock turned off.",
                severity = "info"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enableTotp2fa(profile: UserProfile, secret: String, code: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        if (!SecurityCryptoHelper.verifyTotpCode(secret, code)) {
            return Result.failure(IllegalArgumentException("Invalid 6-digit authenticator code. Verify your device clock and try again."))
        }
        return try {
            db.collection("securityProfiles").document(uid).update(
                mapOf(
                    "totpEnabled" to true,
                    "totpSecret" to secret,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordSecurityEvent(
                uid = uid,
                username = profile.username,
                eventType = "TOTP_ENABLED",
                title = "2FA Authenticator Enabled",
                details = "TOTP 2-factor authentication enabled using Google/Standard Authenticator.",
                severity = "info"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disableTotp2fa(profile: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            db.collection("securityProfiles").document(uid).update(
                mapOf(
                    "totpEnabled" to false,
                    "totpSecret" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordSecurityEvent(
                uid = uid,
                username = profile.username,
                eventType = "TOTP_DISABLED",
                title = "2FA Authenticator Disabled",
                details = "TOTP 2-factor authentication disabled by account owner.",
                severity = "warning"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveVerifiedWithdrawalDetails(
        profile: UserProfile,
        method: String,
        accountNumber: String,
        holderName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val cleanAccount = accountNumber.trim()
        val cleanHolder = holderName.trim()
        if (cleanAccount.length < 4) return Result.failure(IllegalArgumentException("Enter a valid payout account or IBAN."))
        if (cleanHolder.length < 2) return Result.failure(IllegalArgumentException("Enter the payout account holder name."))

        return try {
            db.collection("securityProfiles").document(uid).update(
                mapOf(
                    "verifiedWithdrawMethod" to method.trim(),
                    "verifiedWithdrawAccount" to cleanAccount,
                    "verifiedWithdrawHolderName" to cleanHolder,
                    "withdrawalDetailVerified" to true,
                    "withdrawalDetailVerifiedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordSecurityEvent(
                uid = uid,
                username = profile.username,
                eventType = "WITHDRAWAL_DETAIL_VERIFIED",
                title = "Withdrawal Details Verified",
                details = "Bound verified $method payout destination ($cleanAccount • $cleanHolder).",
                severity = "info"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun trustDevice(profile: UserProfile, session: SecurityDeviceSession): Result<Unit> {
        return try {
            db.collection("securityDevices").document(session.effectiveSessionId).update(
                mapOf(
                    "isTrusted" to true,
                    "isSuspicious" to false,
                    "suspiciousReason" to null,
                    "status" to "active"
                )
            ).await()
            if (auth.currentUser?.uid == session.uid) {
                db.collection("securityProfiles").document(session.uid).update(
                    mapOf("suspiciousAlertActive" to false, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            recordSecurityEvent(
                uid = session.uid,
                username = profile.username,
                eventType = "DEVICE_TRUSTED",
                title = "Device Marked Trusted",
                details = "${session.deviceName} approved as a trusted device.",
                deviceId = session.deviceId,
                deviceName = session.deviceName,
                severity = "info"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeDeviceSession(username: String, session: SecurityDeviceSession): Result<Unit> {
        val actorEmail = auth.currentUser?.email ?: auth.currentUser?.uid ?: "user"
        return try {
            db.collection("securityDevices").document(session.effectiveSessionId).update(
                mapOf(
                    "status" to "revoked",
                    "isTrusted" to false,
                    "revokedAt" to FieldValue.serverTimestamp(),
                    "revokedBy" to actorEmail
                )
            ).await()
            recordSecurityEvent(
                uid = session.uid,
                username = username,
                eventType = "SESSION_REVOKED",
                title = "Device Session Revoked",
                details = "Session on ${session.deviceName} was revoked by $actorEmail.",
                deviceId = session.deviceId,
                deviceName = session.deviceName,
                severity = "warning"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerPasswordReset(profile: UserProfile): Result<String> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        return try {
            if (hasPasswordProvider && profile.email.isNotBlank()) {
                auth.sendPasswordResetEmail(profile.email).await()
                recordSecurityEvent(
                    uid = profile.uid,
                    username = profile.username,
                    eventType = "PASSWORD_RESET_SENT",
                    title = "Password Reset Email Dispatched",
                    details = "Sent Firebase password reset email to ${profile.email}.",
                    severity = "info"
                )
                Result.success("Password reset email sent to ${profile.email}.")
            } else {
                recordSecurityEvent(
                    uid = profile.uid,
                    username = profile.username,
                    eventType = "GOOGLE_SECURITY_CHECK",
                    title = "Google Sign-In Password Check",
                    details = "Account uses Google Sign-In; redirected to Google Account Security.",
                    severity = "info"
                )
                Result.success("GOOGLE_MANAGED")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordSecurityEvent(
        uid: String,
        username: String,
        eventType: String,
        title: String,
        details: String,
        deviceId: String? = null,
        deviceName: String? = null,
        severity: String = "info"
    ) {
        val actorUid = auth.currentUser?.uid ?: uid
        val eventId = "SEC-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"
        val payload = mutableMapOf<String, Any>(
            "eventId" to eventId,
            "uid" to uid,
            "username" to username.ifBlank { "user" },
            "eventType" to eventType,
            "title" to title.take(120),
            "details" to details.take(500),
            "severity" to severity,
            "actorUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!deviceId.isNullOrBlank()) payload["deviceId"] = deviceId
        if (!deviceName.isNullOrBlank()) payload["deviceName"] = deviceName
        try {
            db.collection("securityEvents").document(eventId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun extractFsError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
