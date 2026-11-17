package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.AppConstants
import com.example.data.model.DepositStatusNotification

object PlatformNotificationHelper {

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openOfficialWebsite(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.OFFICIAL_WEBSITE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open website", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsAppSupportChannel(context: Context) {
        val uri = Uri.parse(AppConstants.SUPPORT_WHATSAPP_CHANNEL_URL)
        val whatsAppIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(whatsAppIntent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open WhatsApp channel", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showDepositStatusNotification(
        context: Context,
        notif: DepositStatusNotification
    ) {
        val channelId = "deposit_status_channel"
        ensureDepositChannel(context, channelId)
        if (!hasPostNotificationPermission(context)) return

        val isApproved = notif.status.equals("approved", ignoreCase = true)
        val title = if (isApproved) {
            "Deposit Approved • PKR ${notif.amount.toInt()}"
        } else {
            "Deposit Rejected • PKR ${notif.amount.toInt()}"
        }
        val body = buildString {
            append("Request ${notif.depositId} has been ${notif.status.lowercase()}.")
            if (!notif.adminNote.isNullOrBlank()) {
                append(" Note: ${notif.adminNote}")
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notif.depositId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun showAccountVerifiedNotification(context: Context, username: String) {
        val channelId = "deposit_status_channel"
        ensureDepositChannel(context, channelId)
        if (!hasPostNotificationPermission(context)) return

        val body = "Your @${username} account is now VERIFIED. Daily tasks, referral sharing, and simulated demo withdrawals are unlocked."
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Account Verified • Binace Smart Earn")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9001, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureDepositChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Deposit Status Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when an administrator verifies your account or updates your deposit request."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
