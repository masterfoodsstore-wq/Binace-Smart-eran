package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Premium Royal Blue & Metallic Gold design palette dedicated to the
 * Real-Time Live Customer Support System (preserving dark/light accessibility).
 */
@Immutable
data class SupportPalette(
    val background: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val royalBlue: Color,
    val electricBlue: Color,
    val blueContainer: Color,
    val onBlueContainer: Color,
    val gold: Color,
    val goldMuted: Color,
    val goldContainer: Color,
    val onGoldContainer: Color,
    val onGold: Color,
    val userBubbleBg: Color,
    val userBubbleText: Color,
    val agentBubbleBg: Color,
    val agentBubbleBorder: Color,
    val agentBubbleText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val onlineGreen: Color,
    val offlineAmber: Color
)

private val DarkSupportPalette = SupportPalette(
    background = Color(0xFF060D1B),
    headerStart = Color(0xFF0B1C3D),
    headerEnd = Color(0xFF122C5C),
    surface = Color(0xFF0C1830),
    surfaceElevated = Color(0xFF132446),
    border = Color(0xFF233B6B),
    royalBlue = Color(0xFF2563EB),
    electricBlue = Color(0xFF60A5FA),
    blueContainer = Color(0xFF132B5E),
    onBlueContainer = Color(0xFFDBEAFE),
    gold = Color(0xFFF5B82E),
    goldMuted = Color(0xFFD99B16),
    goldContainer = Color(0xFF332609),
    onGoldContainer = Color(0xFFFDE68A),
    onGold = Color(0xFF1A1200),
    userBubbleBg = Color(0xFF1D4ED8),
    userBubbleText = Color(0xFFF8FAFC),
    agentBubbleBg = Color(0xFF10203F),
    agentBubbleBorder = Color(0xFFF5B82E),
    agentBubbleText = Color(0xFFF1F5F9),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF9DB2D6),
    onlineGreen = Color(0xFF22C55E),
    offlineAmber = Color(0xFFF59E0B)
)

private val LightSupportPalette = SupportPalette(
    background = Color(0xFFF3F6FC),
    headerStart = Color(0xFF0B2559),
    headerEnd = Color(0xFF1D4ED8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEAF1FF),
    border = Color(0xFFCBD9F2),
    royalBlue = Color(0xFF1E40AF),
    electricBlue = Color(0xFF2563EB),
    blueContainer = Color(0xFFDCE8FF),
    onBlueContainer = Color(0xFF0F296B),
    gold = Color(0xFFC88A0B),
    goldMuted = Color(0xFFA66F00),
    goldContainer = Color(0xFFFEF3C7),
    onGoldContainer = Color(0xFF6D4500),
    onGold = Color(0xFFFFFFFF),
    userBubbleBg = Color(0xFF1E40AF),
    userBubbleText = Color(0xFFFFFFFF),
    agentBubbleBg = Color(0xFFFFFBEB),
    agentBubbleBorder = Color(0xFFD99B16),
    agentBubbleText = Color(0xFF0F172A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    onlineGreen = Color(0xFF16A34A),
    offlineAmber = Color(0xFFD97706)
)

@Composable
fun rememberSupportPalette(isDarkTheme: Boolean): SupportPalette =
    if (isDarkTheme) DarkSupportPalette else LightSupportPalette
