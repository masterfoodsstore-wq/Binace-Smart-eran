package com.example.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecurityCryptoHelper {

    private const val DEVICE_PREFS = "bse_security_device_prefs"
    private const val DEVICE_ID_KEY = "stable_device_id"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_ID_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "DEV-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
        prefs.edit().putString(DEVICE_ID_KEY, generated).apply()
        return generated
    }

    fun buildDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = when {
            model.startsWith(manufacturer, ignoreCase = true) && model.isNotBlank() -> model
            manufacturer.isNotBlank() && model.isNotBlank() -> "$manufacturer $model"
            else -> model.ifBlank { "Android Device" }
        }
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }.take(90)
    }

    fun currentTimezoneId(): String = TimeZone.getDefault().id ?: "UTC"

    fun currentLocaleCode(): String = Locale.getDefault().toLanguageTag()

    // ========================================================================
    // RFC 6238 TOTP (HMAC-SHA1, 30s period, 6 digits, Base32 secret)
    // ========================================================================

    fun generateBase32Secret(byteLength: Int = 20): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return encodeBase32(randomBytes)
    }

    fun buildOtpAuthUri(accountEmail: String, secret: String, issuer: String = "Binace Smart Earn"): String {
        val encIssuer = URLEncoder.encode(issuer, "UTF-8")
        val encLabel = URLEncoder.encode("$issuer:${accountEmail.ifBlank { "user" }}", "UTF-8")
        return "otpauth://totp/$encLabel?secret=$secret&issuer=$encIssuer&algorithm=SHA1&digits=6&period=30"
    }

    fun generateTotpCode(
        base32Secret: String,
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Long = 30L
    ): String {
        val keyBytes = decodeBase32(base32Secret)
        if (keyBytes.isEmpty()) return ""
        val timeStep = (timestampMillis / 1000L) / periodSeconds
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    fun verifyTotpCode(
        base32Secret: String,
        submittedCode: String,
        timestampMillis: Long = System.currentTimeMillis(),
        windowSteps: Int = 1
    ): Boolean {
        val clean = submittedCode.trim()
        if (!clean.matches(Regex("^\\d{6}$"))) return false
        for (offset in -windowSteps..windowSteps) {
            val checkTime = timestampMillis + offset * 30_000L
            if (generateTotpCode(base32Secret, checkTime) == clean) {
                return true
            }
        }
        return false
    }

    private fun encodeBase32(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                out.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(BASE32_ALPHABET[index])
        }
        return out.toString()
    }

    private fun decodeBase32(input: String): ByteArray {
        val normalized = input.uppercase(Locale.US).replace(Regex("[^A-Z2-7]"), "")
        val out = ArrayList<Byte>(normalized.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in normalized) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    // ========================================================================
    // Android BiometricPrompt Helper (supports API 24-36 safely)
    // ========================================================================

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val status = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            val message = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device does not have biometric hardware."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or biometric credential enrolled on this device."
                else -> "Biometric authentication is not ready on this device."
            }
            onError(message)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    } else {
                        onError("Biometric verification cancelled.")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }
}
