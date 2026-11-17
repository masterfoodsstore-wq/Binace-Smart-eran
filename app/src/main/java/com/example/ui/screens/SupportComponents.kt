package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.SupportAttachmentUploadState
import com.example.data.model.SupportCategory
import com.example.data.model.SupportMessage
import com.example.data.model.SupportTicketStatus
import com.example.ui.components.FintechFormatters
import com.example.ui.theme.SupportPalette

@Composable
fun SupportGradientHeader(
    title: String,
    subtitle: String,
    palette: SupportPalette,
    onBack: () -> Unit,
    backTestTag: String = "support_back_button",
    trailingContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.headerStart, palette.headerEnd)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(backTestTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailingContent()
        }
    }
}

@Composable
fun SupportStatusPill(
    status: SupportTicketStatus,
    palette: SupportPalette,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        SupportTicketStatus.OPEN -> palette.blueContainer to palette.electricBlue
        SupportTicketStatus.PENDING -> palette.goldContainer to palette.gold
        SupportTicketStatus.RESOLVED -> Color(0xFF0B2F1D) to palette.onlineGreen
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SupportCategoryChip(
    category: SupportCategory,
    palette: SupportPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = palette.goldContainer,
        border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.45f))
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.gold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OnlineAvailabilityBadge(
    onlineAgentsCount: Int,
    palette: SupportPalette,
    modifier: Modifier = Modifier
) {
    val isOnline = onlineAgentsCount > 0
    val accent = if (isOnline) palette.onlineGreen else palette.offlineAmber
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = if (isOnline) {
                    "$onlineAgentsCount Agent${if (onlineAgentsCount == 1) "" else "s"} Online"
                } else {
                    "Offline Ticket Mode"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportCategorySelectorGrid(
    selected: SupportCategory,
    palette: SupportPalette,
    onSelect: (SupportCategory) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SupportCategory.entries.forEach { category ->
            val isSelected = selected == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category) },
                label = { Text(category.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.gold,
                    selectedLabelColor = palette.onGold,
                    containerColor = palette.surfaceElevated,
                    labelColor = palette.textPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = palette.border,
                    selectedBorderColor = palette.gold
                ),
                modifier = Modifier.testTag("support_cat_${category.key}")
            )
        }
    }
}

@Composable
fun SupportMessageBubble(
    message: SupportMessage,
    viewerIsAgent: Boolean,
    palette: SupportPalette,
    onImageClick: (SupportMessage) -> Unit
) {
    val isMine = if (viewerIsAgent) message.isFromAgent else !message.isFromAgent
    val bubbleBg = if (message.isFromAgent) palette.agentBubbleBg else palette.userBubbleBg
    val bubbleText = if (message.isFromAgent) palette.agentBubbleText else palette.userBubbleText
    val bubbleBorder = if (message.isFromAgent) BorderStroke(1.dp, palette.agentBubbleBorder) else null
    val bmp = remember(message.imageBase64) { FintechFormatters.decodeBase64Bitmap(message.imageBase64) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            color = bubbleBg,
            border = bubbleBorder,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.isFromAgent) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "Support agent",
                            tint = palette.gold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = message.senderName.ifBlank { if (message.isFromAgent) "Support Agent" else "User" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isFromAgent) palette.gold else palette.onBlueContainer
                    )
                }

                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Support chat attachment image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message) }
                    )
                } else if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Support chat attachment image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message) }
                    )
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bubbleText
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (message.createdAt != null) {
                            FintechFormatters.formatDate(message.createdAt)
                        } else {
                            "Sending..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleText.copy(alpha = 0.72f)
                    )
                    if (isMine) {
                        val isReadByCounterparty = if (viewerIsAgent) message.readByUser else message.readByAgent
                        val hasServerTimestamp = message.createdAt != null
                        Icon(
                            imageVector = when {
                                !hasServerTimestamp -> Icons.Default.Schedule
                                isReadByCounterparty -> Icons.Default.DoneAll
                                else -> Icons.Default.Done
                            },
                            contentDescription = when {
                                !hasServerTimestamp -> "Sending"
                                isReadByCounterparty -> "Read"
                                else -> "Delivered"
                            },
                            tint = if (isReadByCounterparty) palette.gold else bubbleText.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble(
    label: String,
    palette: SupportPalette
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typingPulse"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceElevated,
        border = BorderStroke(1.dp, palette.gold.copy(alpha = 0.4f)),
        modifier = Modifier
            .alpha(pulse)
            .testTag("chat_typing_indicator")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.gold,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun SupportAttachmentPreviewBar(
    attachmentState: SupportAttachmentUploadState,
    palette: SupportPalette,
    onRemove: () -> Unit
) {
    when (attachmentState) {
        SupportAttachmentUploadState.Idle -> Unit
        is SupportAttachmentUploadState.Uploading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Uploading screenshot... ${attachmentState.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.gold
                )
                LinearProgressIndicator(
                    progress = { (attachmentState.progressPercent / 100f).coerceIn(0f, 1f) },
                    color = palette.gold,
                    trackColor = palette.border,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }
        is SupportAttachmentUploadState.Ready -> {
            val bmp = remember(attachmentState.previewBase64) {
                FintechFormatters.decodeBase64Bitmap(attachmentState.previewBase64)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached screenshot preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screenshot attached ✓",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onlineGreen
                    )
                    if (!attachmentState.statusNote.isNullOrBlank()) {
                        Text(
                            text = attachmentState.statusNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = palette.textSecondary)
                }
            }
        }
        is SupportAttachmentUploadState.Error -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = attachmentState.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss upload error")
                }
            }
        }
    }
}

@Composable
fun FullscreenSupportImageDialog(
    message: SupportMessage,
    palette: SupportPalette,
    onDismiss: () -> Unit
) {
    val bmp = remember(message.imageBase64) { FintechFormatters.decodeBase64Bitmap(message.imageBase64) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.gold)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Full support attachment",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Full support attachment",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.gold, contentColor = palette.onGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Close Preview", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
