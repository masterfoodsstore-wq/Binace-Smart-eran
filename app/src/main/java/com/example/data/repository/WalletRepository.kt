package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.model.AppConstants
import com.example.data.model.DepositRequest
import com.example.data.model.PaymentAccountConfig
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WithdrawalRequest
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.ui.components.FintechFormatters
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

data class UploadedReceiptResult(
    val depositId: String,
    val storagePath: String,
    val downloadUrl: String?,
    val previewBase64: String?,
    val statusNote: String? = null
)

class WalletRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun newDepositId(): String =
        "DEP-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"

    fun normalizeRefKey(reference: String): String =
        reference.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "_")

    fun observePaymentAccounts(): Flow<List<PaymentAccountConfig>> = flow {
        val order = mapOf("easypaisa" to 0, "jazzcash" to 1, "bank_transfer" to 2)
        db.collection("paymentAccounts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PaymentAccountConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { order[it.providerKey] ?: 99 }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "paymentAccounts")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserDeposits(uid: String): Flow<List<DepositRequest>> = flow {
        db.collection("deposits")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DepositRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "deposits")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserWithdrawals(uid: String): Flow<List<WithdrawalRequest>> = flow {
        db.collection("withdrawals")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "withdrawals")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserTransactions(uid: String): Flow<List<TransactionRecord>> = flow {
        db.collection("transactions")
            .whereEqualTo("uid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "transactions")
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun uploadReceiptImage(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit
    ): Result<UploadedReceiptResult> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val prepared = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)?.lowercase()
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure<Pair<ByteArray, String?>>(
                        IllegalArgumentException("Unable to read selected receipt image.")
                    )

                if (rawBytes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Selected receipt image is empty."))
                }
                if (rawBytes.size > MAX_RECEIPT_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Receipt image exceeds 5 MB limit. Please select a smaller screenshot.")
                    )
                }

                val isJpegMagic = rawBytes.size >= 2 &&
                    rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xD8.toByte()
                val isPngMagic = rawBytes.size >= 4 &&
                    rawBytes[0] == 0x89.toByte() && rawBytes[1] == 0x50.toByte() &&
                    rawBytes[2] == 0x4E.toByte() && rawBytes[3] == 0x47.toByte()

                val isAllowedType = mimeType in setOf("image/jpeg", "image/jpg", "image/png") ||
                    (mimeType == null && (isJpegMagic || isPngMagic))
                if (!isAllowedType) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Only JPG, JPEG, and PNG receipt images are allowed.")
                    )
                }

                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Invalid image file. Please select a JPG or PNG screenshot.")
                    )

                // Normalize uploaded artifact to receipt.jpg (max 1280px) matching storage path spec
                val maxDim = 1280
                val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = if (scale < 1f) Bitmap.createScaledBitmap(decoded, targetW, targetH, true) else decoded

                val uploadOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, uploadOut)
                val uploadBytes = uploadOut.toByteArray()

                val previewBase64 = FintechFormatters.encodeReceiptThumbnail(context, uri)
                Result.success(uploadBytes to previewBase64)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val (uploadBytes, previewBase64) = prepared.getOrElse { return Result.failure(it) }
        val depositId = newDepositId()
        val storagePath = "deposit_receipts/$uid/$depositId/receipt.jpg"

        return try {
            onProgress(5)
            val storageRef = Firebase.storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            val uploadTask = storageRef.putBytes(uploadBytes, metadata)
            uploadTask.addOnProgressListener { snap ->
                val total = snap.totalByteCount.coerceAtLeast(1L)
                val pct = ((snap.bytesTransferred * 100L) / total).toInt().coerceIn(5, 95)
                onProgress(pct)
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(100)
            Result.success(
                UploadedReceiptResult(
                    depositId = depositId,
                    storagePath = storagePath,
                    downloadUrl = downloadUrl,
                    previewBase64 = previewBase64
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If Firebase Storage bucket isn't initialized/enabled in Console yet, preserve thumbnail preview gracefully
            if (!previewBase64.isNullOrBlank()) {
                onProgress(100)
                Result.success(
                    UploadedReceiptResult(
                        depositId = depositId,
                        storagePath = storagePath,
                        downloadUrl = null,
                        previewBase64 = previewBase64,
                        statusNote = "Receipt preview attached. Enable Firebase Storage & Storage Rules in Firebase Console for cloud URLs."
                    )
                )
            } else {
                Result.failure(IllegalStateException("Receipt upload failed: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }

    suspend fun deleteDraftReceipt(storagePath: String) {
        if (storagePath.isBlank()) return
        try {
            Firebase.storage.reference.child(storagePath).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun submitDepositRequest(
        userProfile: UserProfile,
        amount: Double,
        paymentMethod: String,
        reference: String,
        receiptBase64: String? = null,
        depositId: String? = null,
        paymentAccountName: String = "",
        paymentAccountNumber: String = "",
        receiptStoragePath: String? = null,
        receiptUrl: String? = null,
        minDeposit: Double = AppConstants.DEFAULT_MIN_DEPOSIT
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanMethod = paymentMethod.trim()
        val cleanRef = reference.trim()
        val refKey = normalizeRefKey(cleanRef)
        val cleanAccountName = paymentAccountName.trim().ifBlank { cleanMethod }
        val cleanAccountNumber = paymentAccountNumber.trim().ifBlank { "Configured Account" }
        val effectiveMinDeposit = maxOf(AppConstants.DEFAULT_MIN_DEPOSIT, minDeposit)

        if (amount < effectiveMinDeposit) {
            return Result.failure(
                IllegalArgumentException("Minimum simulated demo deposit is PKR ${effectiveMinDeposit.toInt()}.")
            )
        }
        if (cleanMethod.length < 2) return Result.failure(IllegalArgumentException("Select a payment method."))
        if (cleanRef.length < 3 || refKey.length < 3) {
            return Result.failure(IllegalArgumentException("Enter a valid transaction reference ID (min 3 characters)."))
        }
        if (receiptBase64 != null && receiptBase64.length > 195_000) {
            return Result.failure(IllegalArgumentException("Receipt image is too large. Please pick a smaller image."))
        }

        val finalDepositId = depositId?.takeIf { it.isNotBlank() } ?: newDepositId()
        val docRef = db.collection("deposits").document(finalDepositId)
        val refGuardRef = db.collection("depositReferences").document(refKey)

        return try {
            val existingRefSnap = refGuardRef.get().await()
            if (existingRefSnap.exists()) {
                return Result.failure(
                    IllegalStateException("Duplicate transaction reference: '$cleanRef' has already been submitted.")
                )
            }

            val payload = mutableMapOf<String, Any>(
                "depositId" to finalDepositId,
                "uid" to uid,
                "userUid" to uid,
                "username" to userProfile.username,
                "fullName" to userProfile.fullName.ifBlank { userProfile.username },
                "userEmail" to userProfile.email,
                "userPhone" to userProfile.phone,
                "amount" to amount,
                "paymentMethod" to cleanMethod,
                "paymentAccountName" to cleanAccountName,
                "paymentAccountNumber" to cleanAccountNumber,
                "reference" to cleanRef,
                "transactionReference" to cleanRef,
                "refKey" to refKey,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!receiptStoragePath.isNullOrBlank()) {
                payload["receiptStoragePath"] = receiptStoragePath
            }
            if (!receiptUrl.isNullOrBlank()) {
                payload["receiptUrl"] = receiptUrl
            }
            if (!receiptBase64.isNullOrBlank()) {
                payload["receiptBase64"] = receiptBase64
            }

            val batch = db.batch()
            batch.set(docRef, payload)
            batch.set(
                refGuardRef,
                mapOf(
                    "refKey" to refKey,
                    "transactionReference" to cleanRef,
                    "depositId" to finalDepositId,
                    "userUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (!userProfile.isVerified && userProfile.firstDepositStatus in setOf("none", "rejected", "")) {
                batch.update(
                    db.collection("users").document(uid),
                    mapOf(
                        "firstDepositStatus" to "pending",
                        "firstDepositAmount" to amount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(finalDepositId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            val msg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Could not submit simulated deposit. Verify transaction reference is unique, amount is at least PKR ${effectiveMinDeposit.toInt()}, and account is active."
            } else {
                e.localizedMessage ?: "Simulated deposit submission failed."
            }
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun submitWithdrawalRequest(
        userProfile: UserProfile,
        amount: Double,
        paymentMethod: String,
        accountReference: String,
        existingWithdrawals: List<WithdrawalRequest>,
        minWithdrawal: Double = AppConstants.DEFAULT_MIN_WITHDRAWAL
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanMethod = paymentMethod.trim()
        val cleanAccount = accountReference.trim()
        val effectiveMinWd = maxOf(AppConstants.DEFAULT_MIN_WITHDRAWAL, minWithdrawal)

        if (!userProfile.isVerified) {
            return Result.failure(
                IllegalStateException("Account verification is required before submitting a simulated demo withdrawal.")
            )
        }
        if (amount < effectiveMinWd) {
            return Result.failure(
                IllegalArgumentException("Minimum simulated demo withdrawal is PKR ${effectiveMinWd.toInt()}.")
            )
        }
        if (amount > userProfile.combinedBalance) {
            return Result.failure(IllegalArgumentException("Insufficient simulated wallet balance."))
        }
        if (cleanMethod.length < 2) return Result.failure(IllegalArgumentException("Select a payment method."))
        if (cleanAccount.length < 3) return Result.failure(IllegalArgumentException("Enter a valid account reference."))
        if (existingWithdrawals.any { it.status.equals("pending", ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("You already have a pending simulated withdrawal request awaiting admin review."))
        }

        val withdrawalId = "WDR-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"
        val docRef = db.collection("withdrawals").document(withdrawalId)
        val payload = mapOf(
            "uid" to uid,
            "username" to userProfile.username,
            "amount" to amount,
            "paymentMethod" to cleanMethod,
            "accountReference" to cleanAccount,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(withdrawalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    private companion object {
        const val MAX_RECEIPT_BYTES = 5 * 1024 * 1024
    }
}
