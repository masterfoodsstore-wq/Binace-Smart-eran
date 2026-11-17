package com.example.util

import com.example.data.model.AppConstants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DailyResetCountdown(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val remainingMillis: Long,
    val progressFraction: Float,
    val pktDateKey: String
) {
    val formattedHms: String
        get() = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val formattedCompact: String
        get() = String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
}

object TaskResetCountdownHelper {

    fun calculateCountdown(nowMillis: Long = System.currentTimeMillis()): DailyResetCountdown {
        val pktZone = TimeZone.getTimeZone(AppConstants.PKT_TIMEZONE_ID)
        val startOfDay = Calendar.getInstance(pktZone, Locale.US).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextMidnight = (startOfDay.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val totalDayMillis = (nextMidnight.timeInMillis - startOfDay.timeInMillis).coerceAtLeast(1L)
        val remainingMillis = (nextMidnight.timeInMillis - nowMillis).coerceAtLeast(0L)
        val elapsedMillis = (nowMillis - startOfDay.timeInMillis).coerceIn(0L, totalDayMillis)
        val totalSeconds = remainingMillis / 1000L

        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val progress = (elapsedMillis.toFloat() / totalDayMillis.toFloat()).coerceIn(0f, 1f)
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = pktZone
        }.format(Date(nowMillis))

        return DailyResetCountdown(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            remainingMillis = remainingMillis,
            progressFraction = progress,
            pktDateKey = dateKey
        )
    }
}
