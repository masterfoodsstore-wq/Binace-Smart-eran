package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AnnouncementFeedEntry
import com.example.data.model.AnnouncementItem
import com.example.data.model.AppNotificationItem
import com.example.data.model.DirectNotificationEntry
import com.example.data.model.LeaderboardEntry
import com.example.data.model.LeaderboardPeriod
import com.example.data.model.LeaderboardPrize
import com.example.data.model.OtpChallengeState
import com.example.data.model.SecurityDeviceSession
import com.example.data.model.SecurityEvent
import com.example.data.model.SecurityProfile
import com.example.data.model.UnifiedFeedNotification
import com.example.data.model.UserProfile
import com.example.data.model.VipHistoryRecord
import com.example.data.model.VipMembership
import com.example.data.model.VipTierConfig
import com.example.data.repository.NotificationSecurityRepository
import com.example.data.repository.VipLeaderboardRepository
import com.example.util.SecurityCryptoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlatformViewModel(
    private val vipLeaderboardRepository: VipLeaderboardRepository,
    private val notificationSecurityRepository: NotificationSecurityRepository,
    private val currentUserId: String,
    private val currentDeviceId: String
) : ViewModel() {

    val vipTierConfigs: StateFlow<List<VipTierConfig>> = vipLeaderboardRepository
        .observeVipTierConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VipLeaderboardRepository.DEFAULT_VIP_TIERS)

    val myVipMembership: StateFlow<VipMembership?> = vipLeaderboardRepository
        .observeUserVipMembership(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val myVipHistory: StateFlow<List<VipHistoryRecord>> = vipLeaderboardRepository
        .observeUserVipHistory(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val leaderboardEntries: StateFlow<List<LeaderboardEntry>> = vipLeaderboardRepository
        .observeLeaderboardEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val leaderboardPrizes: StateFlow<List<LeaderboardPrize>> = vipLeaderboardRepository
        .observeLeaderboardPrizes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val rawNotifications: StateFlow<List<AppNotificationItem>> = notificationSecurityRepository
        .observeUserNotifications(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val rawAnnouncements: StateFlow<List<AnnouncementItem>> = notificationSecurityRepository
        .observePublishedAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val readAnnouncementIds: StateFlow<Set<String>> = notificationSecurityRepository
        .observeAnnouncementReads(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val unifiedNotifications: StateFlow<List<UnifiedFeedNotification>> = combine(
        rawNotifications,
        rawAnnouncements,
        readAnnouncementIds
    ) { direct, announcements, readAnnSet ->
        val list = ArrayList<UnifiedFeedNotification>(direct.size + announcements.size)
        direct.forEach { list.add(DirectNotificationEntry(it)) }
        announcements.forEach { ann ->
            list.add(AnnouncementFeedEntry(ann, readAnnSet.contains(ann.effectiveAnnouncementId)))
        }
        list.sortedByDescending { it.timestamp?.seconds ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadNotificationCount: StateFlow<Int> = unifiedNotifications
        .map { items -> items.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val securityProfile: StateFlow<SecurityProfile?> = notificationSecurityRepository
        .observeSecurityProfile(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val myDevices: StateFlow<List<SecurityDeviceSession>> = notificationSecurityRepository
        .observeUserDevices(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val mySecurityEvents: StateFlow<List<SecurityEvent>> = notificationSecurityRepository
        .observeUserSecurityEvents(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isCurrentSessionRevoked: StateFlow<Boolean> = myDevices
        .map { devices ->
            devices.firstOrNull { it.deviceId == currentDeviceId }?.status == "revoked"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _selectedPeriod = MutableStateFlow(LeaderboardPeriod.DAILY)
    val selectedPeriod: StateFlow<LeaderboardPeriod> = _selectedPeriod.asStateFlow()

    private val _otpState = MutableStateFlow<OtpChallengeState>(OtpChallengeState.Idle)
    val otpState: StateFlow<OtpChallengeState> = _otpState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    private var hasInitializedForSession = false

    fun initializeForUser(context: Context, profile: UserProfile, appVersionName: String) {
        if (hasInitializedForSession) return
        hasInitializedForSession = true
        viewModelScope.launch {
            vipLeaderboardRepository.ensureInitializedAndSync(profile)
            notificationSecurityRepository.ensureSecurityProfileAndRegisterDevice(
                context = context,
                profile = profile,
                appVersionName = appVersionName
            )
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectLeaderboardPeriod(period: LeaderboardPeriod) {
        _selectedPeriod.value = period
    }

    fun dateKeys(): Triple<String, String, String> = vipLeaderboardRepository.currentDateKeys()

    fun rankedEntries(period: LeaderboardPeriod): List<LeaderboardEntry> {
        val (dayKey, weekKey, monthKey) = vipLeaderboardRepository.currentDateKeys()
        return leaderboardEntries.value
            .sortedWith(
                compareByDescending<LeaderboardEntry> {
                    it.scoreFor(period, dayKey, weekKey, monthKey)
                }.thenByDescending { it.verifiedActivityScore }
            )
            .take(100)
    }

    fun syncVipAndLeaderboardNow() {
        viewModelScope.launch {
            _isBusy.value = true
            vipLeaderboardRepository.requestServerSync()
                .onSuccess { msg -> _feedback.value = ActionFeedback(false, msg) }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Sync failed.") }
            _isBusy.value = false
        }
    }

    fun markFeedItemRead(entry: UnifiedFeedNotification) {
        if (entry.isRead) return
        viewModelScope.launch {
            if (entry.isAnnouncement) {
                notificationSecurityRepository.markAnnouncementRead(currentUserId, entry.itemId)
            } else {
                notificationSecurityRepository.markNotificationRead(entry.itemId)
            }
        }
    }

    fun markAllNotificationsRead() {
        val unreadDirect = rawNotifications.value.filter { !it.isRead }.map { it.effectiveNotificationId }
        val readSet = readAnnouncementIds.value
        val unreadAnns = rawAnnouncements.value
            .map { it.effectiveAnnouncementId }
            .filter { !readSet.contains(it) }
        viewModelScope.launch {
            notificationSecurityRepository.markAllRead(currentUserId, unreadDirect, unreadAnns)
            _feedback.value = ActionFeedback(false, "All notifications marked as read.")
        }
    }

    fun requestOtpChallenge() {
        viewModelScope.launch {
            _otpState.value = OtpChallengeState.Requesting
            notificationSecurityRepository.requestOtpChallenge()
                .onSuccess { (challengeId, msg, expiresAt) ->
                    _otpState.value = OtpChallengeState.Sent(
                        challengeId = challengeId,
                        deliveryChannel = "fcm_push",
                        message = msg,
                        expiresAtMs = expiresAt
                    )
                }
                .onFailure { err ->
                    _otpState.value = OtpChallengeState.Error(err.message ?: "OTP request failed.")
                }
        }
    }

    fun verifyOtpCode(challengeId: String, code: String) {
        viewModelScope.launch {
            _otpState.value = OtpChallengeState.Verifying
            notificationSecurityRepository.verifyOtpChallenge(challengeId, code)
                .onSuccess {
                    _otpState.value = OtpChallengeState.Verified("OTP verified securely on server ✓")
                }
                .onFailure { err ->
                    _otpState.value = OtpChallengeState.Error(err.message ?: "Invalid OTP code.")
                }
        }
    }

    fun resetOtpState() {
        _otpState.value = OtpChallengeState.Idle
    }

    fun toggleBiometric(profile: UserProfile, enabled: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.updateBiometricEnabled(profile, enabled)
                .onSuccess {
                    _feedback.value = ActionFeedback(false, if (enabled) "Biometric lock enabled." else "Biometric lock disabled.")
                }
                .onFailure { err ->
                    _feedback.value = ActionFeedback(true, err.message ?: "Failed to update biometric setting.")
                }
            _isBusy.value = false
        }
    }

    fun enableTotp(profile: UserProfile, secret: String, code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.enableTotp2fa(profile, secret, code)
                .onSuccess { _feedback.value = ActionFeedback(false, "2FA Authenticator enabled.") }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "2FA setup failed.") }
            _isBusy.value = false
        }
    }

    fun disableTotp(profile: UserProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.disableTotp2fa(profile)
                .onSuccess { _feedback.value = ActionFeedback(false, "2FA Authenticator disabled.") }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Failed to disable 2FA.") }
            _isBusy.value = false
        }
    }

    fun verifyWithdrawalDetails(
        profile: UserProfile,
        method: String,
        accountNumber: String,
        holderName: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.saveVerifiedWithdrawalDetails(profile, method, accountNumber, holderName)
                .onSuccess { _feedback.value = ActionFeedback(false, "Verified withdrawal details saved.") }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Failed to save withdrawal details.") }
            _isBusy.value = false
        }
    }

    fun trustDevice(profile: UserProfile, session: SecurityDeviceSession) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.trustDevice(profile, session)
                .onSuccess { _feedback.value = ActionFeedback(false, "${session.deviceName} marked as trusted.") }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Failed to trust device.") }
            _isBusy.value = false
        }
    }

    fun revokeDevice(profile: UserProfile, session: SecurityDeviceSession) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.revokeDeviceSession(profile.username, session)
                .onSuccess { _feedback.value = ActionFeedback(false, "Session revoked for ${session.deviceName}.") }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Failed to revoke session.") }
            _isBusy.value = false
        }
    }

    fun triggerPasswordReset(profile: UserProfile, onGoogleAccountRedirect: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.triggerPasswordReset(profile)
                .onSuccess { result ->
                    if (result == "GOOGLE_MANAGED") {
                        _feedback.value = ActionFeedback(false, "Opening Google Account Security for Google Sign-In.")
                        onGoogleAccountRedirect()
                    } else {
                        _feedback.value = ActionFeedback(false, result)
                    }
                }
                .onFailure { err ->
                    _feedback.value = ActionFeedback(true, err.message ?: "Password reset request failed.")
                }
            _isBusy.value = false
        }
    }

    fun verifyTotpCode(code: String): Boolean {
        val secret = securityProfile.value?.totpSecret ?: return false
        return SecurityCryptoHelper.verifyTotpCode(secret, code)
    }
}
