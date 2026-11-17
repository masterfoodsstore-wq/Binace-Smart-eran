package com.example.util

import android.net.Uri
import com.example.data.model.AppConstants
import java.net.URI
import java.net.URLEncoder

object ReferralLinkHelper {

    private val VALID_CODE_REGEX = Regex("^[A-Z0-9_-]{4,24}$")
    private val VALID_UID_REGEX = Regex("^[A-Za-z0-9_-]{4,128}$")
    private val ACCEPTED_HOSTS = setOf(
        "binacesmarteran.my.canva.site",
        "www.binacesmarteran.my.canva.site",
        "binacesmartearn.online",
        "www.binacesmartearn.online",
        "signup"
    )

    fun sanitizeCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "")
        return cleaned.takeIf { VALID_CODE_REGEX.matches(it) }
    }

    fun sanitizeUserId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "")
        return cleaned.takeIf { VALID_UID_REGEX.matches(it) }
    }

    fun buildReferralLink(referralCode: String, userId: String = ""): String {
        val clean = referralCode.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "")
        val cleanUid = sanitizeUserId(userId)
        return if (cleanUid != null) {
            "${AppConstants.REFERRAL_BASE_URL}$clean&uid=${URLEncoder.encode(cleanUid, "UTF-8")}"
        } else {
            "${AppConstants.REFERRAL_BASE_URL}$clean"
        }
    }

    fun extractReferralCode(uri: Uri?): String? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme !in setOf("https", "http", "binacesmartearn")) return null
        if (host.isNotBlank() && host !in ACCEPTED_HOSTS) return null

        val rawParam = uri.getQueryParameter("ref")
            ?: uri.getQueryParameter("referral")
            ?: uri.getQueryParameter("code")
        return sanitizeCode(rawParam)
    }

    fun extractReferrerUid(uri: Uri?): String? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme !in setOf("https", "http", "binacesmartearn")) return null
        if (host.isNotBlank() && host !in ACCEPTED_HOSTS) return null

        val rawUid = uri.getQueryParameter("uid")
            ?: uri.getQueryParameter("userId")
            ?: uri.getQueryParameter("user_id")
        return sanitizeUserId(rawUid)
    }

    fun extractReferralCodeFromUrl(url: String?): String? {
        val pairs = parseQueryPairs(url) ?: return null
        val candidate = pairs["ref"] ?: pairs["referral"] ?: pairs["code"]
        return sanitizeCode(candidate)
    }

    fun extractReferrerUidFromUrl(url: String?): String? {
        val pairs = parseQueryPairs(url) ?: return null
        val candidate = pairs["uid"] ?: pairs["userid"] ?: pairs["user_id"]
        return sanitizeUserId(candidate)
    }

    private fun parseQueryPairs(url: String?): Map<String, String>? {
        if (url.isNullOrBlank()) return null
        return try {
            val parsed = URI(url.trim())
            val scheme = parsed.scheme?.lowercase().orEmpty()
            val host = parsed.host?.lowercase().orEmpty()
            if (scheme !in setOf("https", "http", "binacesmartearn")) return null
            if (host.isNotBlank() && host !in ACCEPTED_HOSTS) return null
            val query = parsed.rawQuery ?: return null
            query.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else {
                    val key = java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8").lowercase()
                    val value = java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                    key to value
                }
            }.toMap()
        } catch (_: Exception) {
            null
        }
    }

    fun buildShareMessage(referralCode: String, userId: String = ""): String {
        val link = buildReferralLink(referralCode, userId)
        val uidNote = sanitizeUserId(userId)?.let { " (User ID: $it)" } ?: ""
        return "Join Binace Smart Earn with my referral code $referralCode$uidNote for a simulated Rs. 100 demo commission after your first verified simulated deposit! Sign up here: $link"
    }

    fun whatsAppShareUrl(message: String): String =
        "https://wa.me/?text=${encode(message)}"

    fun telegramShareUrl(referralLink: String, message: String): String =
        "https://t.me/share/url?url=${encode(referralLink)}&text=${encode(message)}"

    fun facebookShareUrl(referralLink: String, message: String): String =
        "https://www.facebook.com/sharer/sharer.php?u=${encode(referralLink)}&quote=${encode(message)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
