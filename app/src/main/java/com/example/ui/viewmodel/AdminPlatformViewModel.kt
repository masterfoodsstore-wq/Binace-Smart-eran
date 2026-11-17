package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AnnouncementItem
import com.example.data.model.LeaderboardPrize
import com.example.data.model.SecurityDeviceSession
import com.example.data.model.SecurityEvent
import com.example.data.model.UserProfile
import com.example.data.model.VipLevel
import com.example.data.model.VipMembership
import com.example.data.model.VipTierConfig
import com.example.data.repository.NotificationSecurityRepository
import com.example.data.repository.VipLeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminPlatformViewModel(
    private val vipLeaderboardRepository: VipLeaderboardRepository,
    private val notificationSecurityRepository: NotificationSecurityRepository
) : ViewModel() {

    val vipTierConfigs: StateFlow<List<VipTierConfig>> = vipLeaderboardRepository
        .observeAllVipTierConfigsAdmin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VipLeaderboardRepository.DEFAULT_VIP_TIERS)

    val allVipMemberships: StateFlow<List<VipMembership>> = vipLeaderboardRepository
        .observeAllVipMembershipsAdmin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val leaderboardPrizes: StateFlow<List<LeaderboardPrize>> = vipLeaderboardRepository
        .observeLeaderboardPrizes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allAnnouncements: StateFlow<List<AnnouncementItem>> = notificationSecurityRepository
        .observeAllAnnouncementsAdmin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allSecurityEvents: StateFlow<List<SecurityEvent>> = notificationSecurityRepository
        .observeAllSecurityEventsAdmin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allSecurityDevices: StateFlow<List<SecurityDeviceSession>> = notificationSecurityRepository
        .observeAllDevicesAdmin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun seedVipAndPrizes() {
        runUnit("Default VIP tiers & promotional prize pools seeded.") {
            vipLeaderboardRepository.seedDefaultVipAndPrizesAdmin()
        }
    }

    fun saveVipTierConfig(config: VipTierConfig) {
        runUnit("${config.tierName} VIP configuration updated.") {
            vipLeaderboardRepository.saveVipTierConfig(config)
        }
    }

    fun assignUserVip(user: UserProfile, level: VipLevel, reason: String) {
        runUnit("@${user.username} assigned to ${level.displayName} VIP.") {
            vipLeaderboardRepository.assignUserVipTierAdmin(user, level, reason)
        }
    }

    fun savePrize(prize: LeaderboardPrize) {
        runUnit("Leaderboard promotional prize saved.") {
            vipLeaderboardRepository.saveLeaderboardPrize(prize)
        }
    }

    fun deletePrize(prizeId: String) {
        runUnit("Promotional prize removed.") {
            vipLeaderboardRepository.deleteLeaderboardPrize(prizeId)
        }
    }

    fun rebuildAllVipAndLeaderboard() {
        viewModelScope.launch {
            _isBusy.value = true
            vipLeaderboardRepository.rebuildAllLeaderboardAdmin()
                .onSuccess { msg -> _feedback.value = ActionFeedback(false, msg) }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Rebuild failed.") }
            _isBusy.value = false
        }
    }

    fun createAnnouncement(
        title: String,
        body: String,
        category: String,
        scheduleMinutesFromNow: Long?,
        sendPush: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            notificationSecurityRepository.createAnnouncementAdmin(
                title = title,
                body = body,
                category = category,
                scheduleMinutesFromNow = scheduleMinutesFromNow,
                sendPushNotification = sendPush
            ).onSuccess {
                val msg = if (scheduleMinutesFromNow != null && scheduleMinutesFromNow > 0L) {
                    "Announcement scheduled for +${scheduleMinutesFromNow}m."
                } else {
                    "Broadcast announcement published."
                }
                _feedback.value = ActionFeedback(false, msg)
            }.onFailure { err ->
                _feedback.value = ActionFeedback(true, err.message ?: "Failed to publish announcement.")
            }
            _isBusy.value = false
        }
    }

    fun publishAnnouncementNow(announcementId: String) {
        runUnit("Announcement published immediately.") {
            notificationSecurityRepository.publishAnnouncementNowAdmin(announcementId)
        }
    }

    fun cancelAnnouncement(announcementId: String) {
        runUnit("Announcement cancelled.") {
            notificationSecurityRepository.cancelAnnouncementAdmin(announcementId)
        }
    }

    fun revokeUserDevice(session: SecurityDeviceSession) {
        runUnit("Revoked device session ${session.deviceName}.") {
            notificationSecurityRepository.revokeDeviceSession(session.uid.take(10), session)
        }
    }

    private fun runUnit(successMsg: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isBusy.value = true
            block()
                .onSuccess { _feedback.value = ActionFeedback(false, successMsg) }
                .onFailure { err -> _feedback.value = ActionFeedback(true, err.message ?: "Admin action failed.") }
            _isBusy.value = false
        }
    }
}
