package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AnnouncementFeedEntry
import com.example.data.model.AnnouncementItem
import com.example.data.model.AppNotificationItem
import com.example.data.model.DirectNotificationEntry
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LeaderboardPeriod
import com.example.data.model.SupportCategory
import com.example.data.model.SupportTicketStatus
import com.example.data.model.VipLevel
import com.example.util.SecurityCryptoHelper
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformSystemsTest {

    @Test
    fun `totp generation and verification follow RFC 6238 30s window`() {
        val secret = "JBSWY3DPEHPK3PXP"
        val now = 1_710_000_000_000L
        val code = SecurityCryptoHelper.generateTotpCode(secret, now)
        assertEquals(6, code.length)
        assertTrue(SecurityCryptoHelper.verifyTotpCode(secret, code, now))
        // Still valid within +/- 1 30s step tolerance
        assertTrue(SecurityCryptoHelper.verifyTotpCode(secret, code, now + 29_000L))
        // Reject wrong code
        assertFalse(SecurityCryptoHelper.verifyTotpCode(secret, "000000", now))
        // Reject malformed code
        assertFalse(SecurityCryptoHelper.verifyTotpCode(secret, "abc123", now))
    }

    @Test
    fun `otpauth URI contains issuer and secret`() {
        val uri = SecurityCryptoHelper.buildOtpAuthUri("user@example.com", "JBSWY3DPEHPK3PXP")
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"))
        assertTrue(uri.contains("digits=6"))
        assertTrue(uri.contains("period=30"))
    }

    @Test
    fun `stable device id persists across calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SecurityCryptoHelper.getOrCreateDeviceId(context)
        val second = SecurityCryptoHelper.getOrCreateDeviceId(context)
        assertTrue(first.startsWith("DEV-"))
        assertEquals(first, second)
    }

    @Test
    fun `vip levels order correctly from bronze to diamond`() {
        assertEquals(VipLevel.BRONZE, VipLevel.fromKey("unknown"))
        assertEquals(VipLevel.DIAMOND, VipLevel.fromKey("diamond"))
        assertTrue(VipLevel.SILVER.order > VipLevel.BRONZE.order)
        assertTrue(VipLevel.GOLD.order > VipLevel.SILVER.order)
        assertTrue(VipLevel.PLATINUM.order > VipLevel.GOLD.order)
        assertTrue(VipLevel.DIAMOND.order > VipLevel.PLATINUM.order)
    }

    @Test
    fun `leaderboard entry scores respect period keys and stale periods zero out`() {
        val entry = LeaderboardEntry(
            uid = "u1",
            username = "ali",
            vipLevel = "gold",
            isVerified = true,
            dailyPoints = 120.0,
            dailyDateKey = "2026-11-17",
            weeklyPoints = 640.0,
            weekKey = "2026-W47",
            monthlyPoints = 1800.0,
            monthKey = "2026-11",
            qualifiedReferralsCount = 7,
            verifiedActivityScore = 1800.0
        )

        assertEquals(120.0, entry.scoreFor(LeaderboardPeriod.DAILY, "2026-11-17", "2026-W47", "2026-11"), 0.001)
        assertEquals(0.0, entry.scoreFor(LeaderboardPeriod.DAILY, "2026-11-18", "2026-W47", "2026-11"), 0.001)
        assertEquals(640.0, entry.scoreFor(LeaderboardPeriod.WEEKLY, "2026-11-18", "2026-W47", "2026-11"), 0.001)
        assertEquals(0.0, entry.scoreFor(LeaderboardPeriod.WEEKLY, "2026-11-18", "2026-W48", "2026-11"), 0.001)
        assertEquals(1800.0, entry.scoreFor(LeaderboardPeriod.MONTHLY, "2026-11-18", "2026-W47", "2026-11"), 0.001)
        assertEquals(7.0, entry.scoreFor(LeaderboardPeriod.REFERRALS, "2026-11-17", "2026-W47", "2026-11"), 0.001)

        val diamondEntry = entry.copy(vipLevel = "diamond", verifiedActivityScore = 100.0)
        val vipGoldScore = entry.scoreFor(LeaderboardPeriod.VIP, "2026-11-17", "2026-W47", "2026-11")
        val vipDiamondScore = diamondEntry.scoreFor(LeaderboardPeriod.VIP, "2026-11-17", "2026-W47", "2026-11")
        assertTrue(vipDiamondScore > vipGoldScore)
    }

    @Test
    fun `unified notification feed tracks read vs unread across direct and announcement items`() {
        val directUnread = DirectNotificationEntry(
            AppNotificationItem(
                notificationId = "n1",
                uid = "u1",
                title = "VIP Upgrade",
                body = "Welcome to Silver VIP",
                category = "vip",
                isRead = false,
                createdAt = Timestamp(2000, 0)
            )
        )
        val announcementRead = AnnouncementFeedEntry(
            announcement = AnnouncementItem(
                announcementId = "a1",
                title = "Maintenance Window",
                body = "Zero downtime update",
                category = "announcement",
                publishedAt = Timestamp(1000, 0)
            ),
            isRead = true
        )
        val feed = listOf(directUnread, announcementRead)
        assertEquals(1, feed.count { !it.isRead })
        assertNotEquals(directUnread.itemId, announcementRead.itemId)
    }

    @Test
    fun `support categories and statuses map cleanly`() {
        assertEquals(6, SupportCategory.entries.size)
        assertEquals(SupportCategory.WITHDRAWALS, SupportCategory.fromKey("withdrawals"))
        assertEquals(SupportTicketStatus.RESOLVED, SupportTicketStatus.fromKey("RESOLVED"))
        assertEquals(SupportTicketStatus.OPEN, SupportTicketStatus.fromKey("invalid"))
    }
}
