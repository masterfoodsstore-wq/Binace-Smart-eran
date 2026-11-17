package com.example.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SupportFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val dbId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            db.collection("userDevices").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "fcmToken" to token,
                    "platform" to "android",
                    "notificationsEnabled" to notificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync refreshed FCM token", e)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val msgType = message.data["type"].orEmpty()
        val route = message.data["route"].orEmpty()
        val ticketId = message.data["ticketId"].orEmpty()
        val isPlatformAlert = msgType in setOf("announcement", "vip", "security", "wallet", "security_otp") ||
            (route.isNotBlank() && ticketId.isBlank())

        if (isPlatformAlert) {
            val otpCode = message.data["otpCode"].orEmpty()
            val title = message.notification?.title
                ?: message.data["title"]
                ?: "Binace Smart Earn Alert"
            val baseBody = message.notification?.body
                ?: message.data["body"]
                ?: "You have a new account notification."
            val body = if (otpCode.isNotBlank() && !baseBody.contains(otpCode)) {
                "$baseBody Code: $otpCode"
            } else {
                baseBody
            }
            val effectiveRoute = when {
                route.isNotBlank() -> route
                msgType == "security" || msgType == "security_otp" -> "security"
                msgType == "vip" -> "vip"
                msgType == "wallet" -> "wallet"
                else -> "notifications"
            }
            showPlatformPushNotification(
                context = applicationContext,
                notificationId = (message.messageId ?: "${msgType}_${System.currentTimeMillis()}").hashCode(),
                route = effectiveRoute,
                title = title,
                body = body
            )
            return
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Binace Smart Earn Live Support"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new customer support update."
        showSupportPushNotification(
            context = applicationContext,
            ticketId = ticketId.ifBlank { "support" },
            title = title,
            body = body
        )
    }

    companion object {
        private const val TAG = "SupportFCM"
        const val CHANNEL_ID = "support_updates_channel"
        const val PLATFORM_CHANNEL_ID = "platform_alerts_channel"

        fun showSupportPushNotification(
            context: Context,
            ticketId: String,
            title: String,
            body: String
        ) {
            ensureChannel(
                context = context,
                channelId = CHANNEL_ID,
                name = "Live Customer Support",
                description = "Real-time notifications for live support replies, tickets, and status updates."
            )
            if (!hasNotificationPermission(context)) return

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("support_ticket_id", ticketId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                ticketId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(ticketId.hashCode(), notification)
            } catch (_: SecurityException) {
            }
        }

        fun showPlatformPushNotification(
            context: Context,
            notificationId: Int,
            route: String,
            title: String,
            body: String
        ) {
            ensureChannel(
                context = context,
                channelId = PLATFORM_CHANNEL_ID,
                name = "Account, Security & VIP Alerts",
                description = "Push alerts for OTP challenges, security warnings, VIP upgrades, and announcements."
            )
            if (!hasNotificationPermission(context)) return

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("notification_route", route)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, PLATFORM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            } catch (_: SecurityException) {
            }
        }

        private fun ensureChannel(context: Context, channelId: String, name: String, description: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (manager != null && manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                        this.description = description
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        private fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }
    }
}
