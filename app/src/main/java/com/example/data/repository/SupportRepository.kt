package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.SupportAgent
import com.example.data.model.SupportCategory
import com.example.data.model.SupportMessage
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
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
import com.google.firebase.messaging.FirebaseMessaging
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

data class UploadedSupportAttachment(
    val messageId: String,
    val storagePath: String,
    val downloadUrl: String?,
    val previewBase64: String?,
    val statusNote: String? = null
)

class SupportRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun newTicketId(): String =
        "SUP-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"

    fun newMessageId(): String =
        "MSG-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"

    fun observeSupportAgents(): Flow<List<SupportAgent>> = flow {
        db.collection("supportAgents")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportAgent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.isActive }
                    .sortedWith(compareByDescending<SupportAgent> { it.isOnline }.thenBy { it.displayName.lowercase() })
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, "supportAgents")
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAllAgentRecords(): Flow<List<SupportAgent>> = flow {
        db.collection("supportAgents")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportAgent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<SupportAgent> { it.isActive }.thenByDescending { it.isOnline }.thenBy { it.displayName.lowercase() })
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, "supportAgents")
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeMyAgentRecord(uid: String): Flow<SupportAgent?> = flow {
        val docRef = db.collection("supportAgents").document(uid)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(SupportAgent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.GET, docRef.path)
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeUserTickets(uid: String): Flow<List<SupportTicket>> = flow {
        db.collection("supportTickets")
            .whereEqualTo("userUid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, "supportTickets")
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAllTickets(): Flow<List<SupportTicket>> = flow {
        db.collection("supportTickets")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<SupportTicket> {
                            when (it.statusEnum) {
                                SupportTicketStatus.OPEN -> 0
                                SupportTicketStatus.PENDING -> 1
                                SupportTicketStatus.RESOLVED -> 2
                            }
                        }.thenByDescending { (it.lastMessageAt ?: it.updatedAt)?.seconds ?: 0L }
                    )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, "supportTickets")
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeTicket(ticketId: String): Flow<SupportTicket?> = flow {
        val docRef = db.collection("supportTickets").document(ticketId)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.GET, docRef.path)
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeTicketMessages(
        ticketId: String,
        ticketOwnerUid: String,
        isStaffView: Boolean
    ): Flow<List<SupportMessage>> = flow {
        val collRef = db.collection("supportTickets").document(ticketId).collection("messages")
        val query = if (isStaffView) {
            collRef
        } else {
            collRef.whereEqualTo("ticketOwnerUid", ticketOwnerUid)
        }
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(SupportMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { (it.createdAt?.seconds ?: Long.MAX_VALUE) * 1_000_000_000L + (it.createdAt?.nanoseconds ?: 0) }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    (fsErr ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, "supportTickets/$ticketId/messages")
                    }
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun uploadSupportImage(
        context: Context,
        uri: Uri,
        ticketId: String,
        onProgress: (Int) -> Unit
    ): Result<UploadedSupportAttachment> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val messageId = newMessageId()

        val prepared = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)?.lowercase()
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure<Pair<ByteArray, String?>>(
                        IllegalArgumentException("Unable to read selected image.")
                    )
                if (rawBytes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Selected image is empty."))
                }
                if (rawBytes.size > MAX_IMAGE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Image exceeds 5 MB limit. Please select a smaller screenshot.")
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
                        IllegalArgumentException("Only JPG, JPEG, and PNG images are allowed.")
                    )
                }

                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Invalid image file. Please select a JPG or PNG image.")
                    )

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
        val storagePath = "support_attachments/$uid/$ticketId/$messageId.jpg"

        return try {
            onProgress(10)
            val storageRef = Firebase.storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            val uploadTask = storageRef.putBytes(uploadBytes, metadata)
            uploadTask.addOnProgressListener { snap ->
                val total = snap.totalByteCount.coerceAtLeast(1L)
                val pct = ((snap.bytesTransferred * 100L) / total).toInt().coerceIn(10, 95)
                onProgress(pct)
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(100)
            Result.success(
                UploadedSupportAttachment(
                    messageId = messageId,
                    storagePath = storagePath,
                    downloadUrl = downloadUrl,
                    previewBase64 = previewBase64
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!previewBase64.isNullOrBlank()) {
                onProgress(100)
                Result.success(
                    UploadedSupportAttachment(
                        messageId = messageId,
                        storagePath = storagePath,
                        downloadUrl = null,
                        previewBase64 = previewBase64,
                        statusNote = "Image attached via compressed preview fallback."
                    )
                )
            } else {
                Result.failure(IllegalStateException("Image upload failed: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }

    suspend fun createSupportTicket(
        profile: UserProfile,
        category: SupportCategory,
        subject: String,
        initialMessage: String,
        isOfflineTicket: Boolean,
        attachment: UploadedSupportAttachment? = null
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanSubject = subject.trim().take(140)
        val cleanMessage = initialMessage.trim().take(2000)
        if (cleanSubject.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter a brief issue subject (min 2 chars)."))
        }
        if (cleanMessage.isEmpty() && attachment == null) {
            return Result.failure(IllegalArgumentException("Enter a message or attach a screenshot."))
        }

        val ticketId = newTicketId()
        val messageId = attachment?.messageId ?: newMessageId()
        val displayPreview = if (cleanMessage.isNotEmpty()) {
            cleanMessage.take(500)
        } else {
            "[Image Attachment]"
        }
        val senderName = profile.fullName.ifBlank { "@${profile.username}" }.take(80)
        val safeUsername = profile.username.ifBlank { "user_${uid.take(6)}" }.take(40)

        val ticketRef = db.collection("supportTickets").document(ticketId)
        val msgRef = ticketRef.collection("messages").document(messageId)

        val ticketPayload = mapOf(
            "ticketId" to ticketId,
            "userUid" to uid,
            "username" to safeUsername,
            "userName" to senderName,
            "category" to category.key,
            "subject" to cleanSubject,
            "status" to SupportTicketStatus.OPEN.key,
            "isOfflineTicket" to isOfflineTicket,
            "assignedAgentUid" to "",
            "assignedAgentName" to "",
            "lastMessageText" to displayPreview,
            "lastMessageSenderRole" to "user",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "userIsTyping" to false,
            "agentIsTyping" to false,
            "agentTypingName" to "",
            "unreadByUserCount" to 0,
            "unreadByAgentCount" to 1,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val msgPayload = mutableMapOf<String, Any>(
            "messageId" to messageId,
            "ticketId" to ticketId,
            "ticketOwnerUid" to uid,
            "senderUid" to uid,
            "senderName" to senderName,
            "senderRole" to "user",
            "type" to if (attachment != null) "image" else "text",
            "text" to cleanMessage,
            "readByUser" to true,
            "readByAgent" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (attachment != null) {
            attachment.downloadUrl?.takeIf { it.isNotBlank() }?.let { msgPayload["imageUrl"] = it }
            attachment.storagePath.takeIf { it.isNotBlank() }?.let { msgPayload["imageStoragePath"] = it }
            attachment.previewBase64?.takeIf { it.isNotBlank() && it.length <= 195_000 }?.let {
                msgPayload["imageBase64"] = it
            }
        }

        return try {
            val batch = db.batch()
            batch.set(ticketRef, ticketPayload)
            batch.set(msgRef, msgPayload)
            batch.commit().await()
            Result.success(ticketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ticketRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        ticket: SupportTicket,
        senderName: String,
        isAgentSender: Boolean,
        text: String,
        attachment: UploadedSupportAttachment? = null,
        hasOnlineAgents: Boolean = true
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanText = text.trim().take(2000)
        if (cleanText.isEmpty() && attachment == null) {
            return Result.failure(IllegalArgumentException("Enter a message or attach an image."))
        }

        val ticketId = ticket.effectiveTicketId
        val messageId = attachment?.messageId ?: newMessageId()
        val senderRole = if (isAgentSender) "agent" else "user"
        val cleanSenderName = senderName.trim().ifBlank { if (isAgentSender) "Support Agent" else "@${ticket.username}" }.take(80)
        val preview = if (cleanText.isNotEmpty()) cleanText.take(500) else "[Image Attachment]"

        val ticketRef = db.collection("supportTickets").document(ticketId)
        val msgRef = ticketRef.collection("messages").document(messageId)

        val msgPayload = mutableMapOf<String, Any>(
            "messageId" to messageId,
            "ticketId" to ticketId,
            "ticketOwnerUid" to ticket.userUid,
            "senderUid" to uid,
            "senderName" to cleanSenderName,
            "senderRole" to senderRole,
            "type" to if (attachment != null) "image" else "text",
            "text" to cleanText,
            "readByUser" to !isAgentSender,
            "readByAgent" to isAgentSender,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (attachment != null) {
            attachment.downloadUrl?.takeIf { it.isNotBlank() }?.let { msgPayload["imageUrl"] = it }
            attachment.storagePath.takeIf { it.isNotBlank() }?.let { msgPayload["imageStoragePath"] = it }
            attachment.previewBase64?.takeIf { it.isNotBlank() && it.length <= 195_000 }?.let {
                msgPayload["imageBase64"] = it
            }
        }

        val ticketUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to preview,
            "lastMessageSenderRole" to senderRole,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isAgentSender) {
            ticketUpdates["agentIsTyping"] = false
            ticketUpdates["agentTypingName"] = ""
            ticketUpdates["unreadByUserCount"] = FieldValue.increment(1)
            ticketUpdates["unreadByAgentCount"] = 0
            ticketUpdates["agentLastReadAt"] = FieldValue.serverTimestamp()
            if (ticket.assignedAgentUid.isBlank()) {
                ticketUpdates["assignedAgentUid"] = uid
                ticketUpdates["assignedAgentName"] = cleanSenderName
            }
            if (ticket.statusEnum == SupportTicketStatus.OPEN) {
                ticketUpdates["status"] = SupportTicketStatus.PENDING.key
            }
        } else {
            ticketUpdates["userIsTyping"] = false
            ticketUpdates["unreadByAgentCount"] = FieldValue.increment(1)
            ticketUpdates["unreadByUserCount"] = 0
            ticketUpdates["userLastReadAt"] = FieldValue.serverTimestamp()
            ticketUpdates["isOfflineTicket"] = !hasOnlineAgents
            if (ticket.statusEnum == SupportTicketStatus.RESOLVED) {
                ticketUpdates["status"] = SupportTicketStatus.OPEN.key
            }
        }

        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(ticketRef, ticketUpdates)
            batch.commit().await()
            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(
        ticketId: String,
        isAgent: Boolean,
        isTyping: Boolean,
        agentName: String = ""
    ) {
        if (auth.currentUser == null || ticketId.isBlank()) return
        val updates = if (isAgent) {
            mapOf(
                "agentIsTyping" to isTyping,
                "agentTypingName" to if (isTyping) agentName.take(80) else "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "userIsTyping" to isTyping,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
        try {
            db.collection("supportTickets").document(ticketId).update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun markConversationRead(
        ticket: SupportTicket,
        messages: List<SupportMessage>,
        viewerIsAgent: Boolean
    ) {
        if (auth.currentUser == null) return
        val ticketId = ticket.effectiveTicketId
        if (ticketId.isBlank()) return

        val unreadMsgs = if (viewerIsAgent) {
            messages.filter { !it.isFromAgent && !it.readByAgent }
        } else {
            messages.filter { it.isFromAgent && !it.readByUser }
        }
        val needsTicketReset = if (viewerIsAgent) ticket.unreadByAgentCount > 0 else ticket.unreadByUserCount > 0
        if (unreadMsgs.isEmpty() && !needsTicketReset) return

        try {
            val batch = db.batch()
            unreadMsgs.take(40).forEach { msg ->
                val msgRef = db.collection("supportTickets").document(ticketId)
                    .collection("messages").document(msg.effectiveMessageId)
                val receiptUpdates = if (viewerIsAgent) {
                    mapOf("readByAgent" to true, "readAt" to FieldValue.serverTimestamp())
                } else {
                    mapOf("readByUser" to true, "readAt" to FieldValue.serverTimestamp())
                }
                batch.update(msgRef, receiptUpdates)
            }
            val ticketUpdates = if (viewerIsAgent) {
                mapOf(
                    "unreadByAgentCount" to 0,
                    "agentLastReadAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "unreadByUserCount" to 0,
                    "userLastReadAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            batch.update(db.collection("supportTickets").document(ticketId), ticketUpdates)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "supportTickets/$ticketId")
        }
    }

    suspend fun assignTicket(ticketId: String, agentUid: String, agentName: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val updates = mapOf(
            "assignedAgentUid" to agentUid.trim().take(128),
            "assignedAgentName" to agentName.trim().take(80),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.collection("supportTickets").document(ticketId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "supportTickets/$ticketId")
            Result.failure(e)
        }
    }

    suspend fun updateTicketStatus(ticketId: String, status: SupportTicketStatus): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val updates = mutableMapOf<String, Any?>(
            "status" to status.key,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (status == SupportTicketStatus.RESOLVED) {
            updates["resolvedAt"] = FieldValue.serverTimestamp()
            updates["agentIsTyping"] = false
        }
        return try {
            db.collection("supportTickets").document(ticketId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "supportTickets/$ticketId")
            Result.failure(e)
        }
    }

    suspend fun toggleMyOnlineStatus(
        uid: String,
        displayName: String,
        isAdmin: Boolean,
        isOnline: Boolean
    ): Result<Unit> {
        val currentUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (currentUid != uid) {
            return Result.failure(IllegalStateException("Cannot toggle another agent's availability."))
        }
        val ref = db.collection("supportAgents").document(uid)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (isAdmin) {
                ref.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName.trim().ifBlank { "Admin Support" }.take(80),
                        "role" to "admin",
                        "isActive" to true,
                        "isOnline" to isOnline,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                return Result.failure(IllegalStateException("Your account is not registered as a support agent."))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun upsertSupportAgent(
        uid: String,
        displayName: String,
        role: String,
        isActive: Boolean,
        isOnline: Boolean = false
    ): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanUid = uid.trim()
        val cleanName = displayName.trim().take(80)
        val safeRole = if (role == "admin") "admin" else "support_agent"
        if (cleanUid.length < 2 || cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Valid user ID and display name are required."))
        }

        val ref = db.collection("supportAgents").document(cleanUid)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "displayName" to cleanName,
                        "role" to safeRole,
                        "isActive" to isActive,
                        "isOnline" to if (isActive) isOnline else false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "uid" to cleanUid,
                        "displayName" to cleanName,
                        "role" to safeRole,
                        "isActive" to isActive,
                        "isOnline" to isOnline,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun syncFcmDeviceToken(uid: String, notificationsEnabled: Boolean) {
        if (auth.currentUser?.uid != uid) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.length >= 10) {
                db.collection("userDevices").document(uid).set(
                    mapOf(
                        "uid" to uid,
                        "fcmToken" to token,
                        "platform" to "android",
                        "notificationsEnabled" to notificationsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "FCM token sync skipped/unavailable: ${e.message}")
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "SupportRepo"
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }
}
