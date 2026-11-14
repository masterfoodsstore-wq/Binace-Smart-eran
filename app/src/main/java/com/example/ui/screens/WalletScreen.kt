package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.AppSettings
import com.example.data.model.DepositRequest
import com.example.data.model.DepositStatusNotification
import com.example.data.model.PaymentAccountConfig
import com.example.data.model.ReceiptUploadState
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WalletAccount
import com.example.data.model.WithdrawalRequest
import com.example.ui.components.BalanceHeroCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FintechFormatters
import com.example.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(
    profile: UserProfile,
    wallet: WalletAccount? = null,
    settings: AppSettings,
    paymentAccounts: List<PaymentAccountConfig> = emptyList(),
    receiptUploadState: ReceiptUploadState = ReceiptUploadState.Idle,
    latestDepositNotification: DepositStatusNotification? = null,
    deposits: List<DepositRequest>,
    withdrawals: List<WithdrawalRequest>,
    transactions: List<TransactionRecord>,
    initialSubTab: Int,
    isActionBusy: Boolean,
    isDarkTheme: Boolean,
    onSelectReceipt: (Uri) -> Unit = {},
    onRemoveReceipt: () -> Unit = {},
    onDismissNotification: () -> Unit = {},
    onSubmitDeposit: (
        amount: Double,
        paymentMethod: String,
        paymentAccountName: String,
        paymentAccountNumber: String,
        reference: String,
        onSubmitted: () -> Unit
    ) -> Unit,
    onSubmitWithdrawal: (amount: Double, method: String, accountRef: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialSubTab) } // 0 = History, 1 = Deposit, 2 = Withdraw
    LaunchedEffect(initialSubTab) {
        selectedTab = initialSubTab
    }

    val effectiveTotalBalance = wallet?.balance ?: profile.combinedBalance
    val effectiveDepositBalance = wallet?.depositBalance ?: profile.realBalance
    val effectiveRewardBalance = wallet?.rewardBalance ?: profile.realRewardBalance
    val effectiveWalletId = wallet?.walletId?.ifBlank { profile.walletId } ?: profile.walletId

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var depositAmountText by remember { mutableStateOf("500") }
    var isCustomAmount by remember { mutableStateOf(false) }
    var selectedProviderKey by remember { mutableStateOf("easypaisa") }
    var depositReference by remember { mutableStateOf("") }
    var previewDepositItem by remember { mutableStateOf<DepositRequest?>(null) }

    var withdrawAmountText by remember { mutableStateOf("") }
    var withdrawMethod by remember { mutableStateOf("EasyPaisa") }
    var withdrawAccountRef by remember { mutableStateOf("") }

    var txFilter by remember { mutableStateOf("all") }

    val providerOrder = listOf(
        "easypaisa" to "EasyPaisa",
        "jazzcash" to "JazzCash",
        "bank_transfer" to "Bank Transfer"
    )

    val selectedAccount = remember(paymentAccounts, selectedProviderKey) {
        paymentAccounts.find { it.providerKey == selectedProviderKey }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onSelectReceipt(uri)
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("History") },
                    modifier = Modifier.testTag("wallet_tab_history")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Deposit") },
                    modifier = Modifier.testTag("wallet_tab_deposit")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Withdraw") },
                    modifier = Modifier.testTag("wallet_tab_withdraw")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (latestDepositNotification != null) {
                    item {
                        AnimatedVisibility(visible = true) {
                            val isApproved = latestDepositNotification.status.equals("approved", ignoreCase = true)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isApproved) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Status update",
                                        tint = if (isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isApproved) {
                                                "Deposit ${latestDepositNotification.depositId} Approved"
                                            } else {
                                                "Deposit ${latestDepositNotification.depositId} Rejected"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = buildString {
                                                append(FintechFormatters.formatPkr(latestDepositNotification.amount))
                                                if (!latestDepositNotification.adminNote.isNullOrBlank()) {
                                                    append(" • ${latestDepositNotification.adminNote}")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(onClick = onDismissNotification) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss notification")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    BalanceHeroCard(
                        totalBalance = effectiveTotalBalance,
                        realBalance = effectiveDepositBalance,
                        rewardBalance = effectiveRewardBalance,
                        walletId = effectiveWalletId
                    )
                }

                when (selectedTab) {
                    1 -> {
                        // DEPOSIT TAB
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Submit Deposit Request",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Submit your transaction reference and receipt screenshot. Admin approval verifies your first deposit for the PKR ${settings.referralCommission.toInt()} referral bonus.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    Text("Select Deposit Amount (PKR)", style = MaterialTheme.typography.labelLarge)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("500" to "PKR 500", "1000" to "PKR 1,000", "2500" to "PKR 2,500", "5000" to "PKR 5,000").forEach { (preset, label) ->
                                            FilterChip(
                                                selected = !isCustomAmount && depositAmountText == preset,
                                                onClick = {
                                                    isCustomAmount = false
                                                    depositAmountText = preset
                                                },
                                                label = { Text(label) }
                                            )
                                        }
                                        FilterChip(
                                            selected = isCustomAmount,
                                            onClick = {
                                                isCustomAmount = true
                                                if (depositAmountText in listOf("500", "1000", "2500", "5000")) {
                                                    depositAmountText = ""
                                                }
                                            },
                                            label = { Text("Custom Amount") },
                                            modifier = Modifier.testTag("deposit_custom_amount_chip")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = depositAmountText,
                                        onValueChange = { raw ->
                                            val clean = raw.filter { ch -> ch.isDigit() || ch == '.' }
                                            depositAmountText = clean
                                            isCustomAmount = clean !in listOf("500", "1000", "2500", "5000")
                                        },
                                        label = { Text("Deposit Amount (PKR)") },
                                        placeholder = { Text("Enter amount (min PKR 50)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("deposit_amount_input")
                                    )

                                    Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        providerOrder.forEach { (key, fallbackLabel) ->
                                            val config = paymentAccounts.find { it.providerKey == key }
                                            val label = config?.providerName?.ifBlank { fallbackLabel } ?: fallbackLabel
                                            val enabled = config?.isEnabled ?: true
                                            FilterChip(
                                                selected = selectedProviderKey == key,
                                                onClick = { selectedProviderKey = key },
                                                label = {
                                                    Text(if (enabled) label else "$label (Off)")
                                                },
                                                modifier = Modifier.testTag("deposit_method_$key")
                                            )
                                        }
                                    }

                                    // Real-Time Payment Account Details Card
                                    PaymentAccountDetailsCard(
                                        providerFallbackName = providerOrder.firstOrNull { it.first == selectedProviderKey }?.second ?: "EasyPaisa",
                                        account = selectedAccount,
                                        onCopy = { label, value ->
                                            clipboard.setText(AnnotatedString(value))
                                            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    // Auto-populated Authenticated Account Summary
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Account Details Attached to Request",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "${profile.fullName} (@${profile.username})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "UID: ${profile.uid.take(14)} • Wallet: $effectiveWalletId",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${profile.email} • ${profile.phone}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = depositReference,
                                        onValueChange = { depositReference = it },
                                        label = { Text("Transaction Reference Number") },
                                        placeholder = { Text("e.g. TXN-REF-202601") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("deposit_reference_input")
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = receiptUploadState !is ReceiptUploadState.Uploading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("upload_receipt_button")
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            when (receiptUploadState) {
                                                is ReceiptUploadState.Uploading -> "Uploading Receipt (${receiptUploadState.progressPercent}%)..."
                                                is ReceiptUploadState.Uploaded -> "Replace Payment Proof (JPG/PNG)"
                                                else -> "Upload Receipt (Photo Picker)"
                                            }
                                        )
                                    }

                                    when (receiptUploadState) {
                                        is ReceiptUploadState.Uploading -> {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                LinearProgressIndicator(
                                                    progress = { (receiptUploadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(50))
                                                )
                                                Text(
                                                    text = "Uploading receipt... ${receiptUploadState.progressPercent}%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        is ReceiptUploadState.Uploaded -> {
                                            val previewBmp = remember(receiptUploadState.previewBase64) {
                                                FintechFormatters.decodeBase64Bitmap(receiptUploadState.previewBase64)
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (!receiptUploadState.downloadUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = receiptUploadState.downloadUrl,
                                                        contentDescription = "Uploaded receipt preview",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(150.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    )
                                                } else if (previewBmp != null) {
                                                    Image(
                                                        bitmap = previewBmp.asImageBitmap(),
                                                        contentDescription = "Selected receipt preview",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(150.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    )
                                                }
                                                Text(
                                                    text = receiptUploadState.statusNote
                                                        ?: "Uploaded to ${receiptUploadState.storagePath} ✓",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                OutlinedButton(
                                                    onClick = onRemoveReceipt,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(44.dp)
                                                        .testTag("remove_receipt_button")
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Remove Image")
                                                }
                                            }
                                        }

                                        is ReceiptUploadState.Error -> {
                                            Text(
                                                text = receiptUploadState.message,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        ReceiptUploadState.Idle -> Unit
                                    }

                                    val accountEnabled = selectedAccount?.isEnabled ?: true
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (!granted) {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }
                                            val amount = depositAmountText.toDoubleOrNull() ?: 0.0
                                            val methodLabel = selectedAccount?.providerName?.ifBlank { null }
                                                ?: providerOrder.firstOrNull { it.first == selectedProviderKey }?.second
                                                ?: "EasyPaisa"
                                            val accountHolder = selectedAccount?.accountHolderName ?: ""
                                            val accountNumber = selectedAccount?.accountNumber ?: ""
                                            onSubmitDeposit(
                                                amount,
                                                methodLabel,
                                                accountHolder,
                                                accountNumber,
                                                depositReference
                                            ) {
                                                depositReference = ""
                                            }
                                        },
                                        enabled = !isActionBusy &&
                                            receiptUploadState !is ReceiptUploadState.Uploading &&
                                            accountEnabled &&
                                            selectedAccount != null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("submit_deposit_button")
                                    ) {
                                        Text("Submit Deposit Request", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Your Deposit Requests", style = MaterialTheme.typography.titleLarge)
                        }

                        if (deposits.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = "No Deposit Requests",
                                    subtitle = "Submit a deposit above to see live pending, approved, or rejected updates."
                                )
                            }
                        } else {
                            items(deposits, key = { it.effectiveDepositId }) { dep ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(dep.effectiveDepositId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text(FintechFormatters.formatPkr(dep.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text("${dep.paymentMethod} • Ref: ${dep.effectiveReference}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (dep.paymentAccountNumber.isNotBlank()) {
                                                    Text(
                                                        text = "Paid To: ${dep.paymentAccountName} (${dep.paymentAccountNumber})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(FintechFormatters.formatDate(dep.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            StatusPill(status = dep.status, isDarkTheme = isDarkTheme)
                                        }
                                        if (!dep.adminNote.isNullOrBlank()) {
                                            Text(
                                                text = "Admin Note: ${dep.adminNote}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (dep.status.equals("rejected", ignoreCase = true)) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }
                                        if (!dep.receiptUrl.isNullOrBlank() || !dep.receiptBase64.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = { previewDepositItem = dep },
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                Text("View Uploaded Receipt")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // WITHDRAWAL TAB
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Withdrawal Request",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Available balance: ${FintechFormatters.formatPkr(effectiveTotalBalance)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    OutlinedTextField(
                                        value = withdrawAmountText,
                                        onValueChange = { withdrawAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                        label = { Text("Withdrawal Amount (PKR)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("withdraw_amount_input")
                                    )

                                    Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("EasyPaisa", "JazzCash", "Bank Transfer").forEach { method ->
                                            FilterChip(
                                                selected = withdrawMethod == method,
                                                onClick = { withdrawMethod = method },
                                                label = { Text(method) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = withdrawAccountRef,
                                        onValueChange = { withdrawAccountRef = it },
                                        label = { Text("Account Reference") },
                                        placeholder = { Text("e.g. 0300-1234567") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("withdraw_account_input")
                                    )

                                    Button(
                                        onClick = {
                                            val amount = withdrawAmountText.toDoubleOrNull() ?: 0.0
                                            onSubmitWithdrawal(amount, withdrawMethod, withdrawAccountRef)
                                            withdrawAmountText = ""
                                            withdrawAccountRef = ""
                                        },
                                        enabled = !isActionBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("submit_withdrawal_button")
                                    ) {
                                        Text("Submit Withdrawal Request", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Withdrawal Requests", style = MaterialTheme.typography.titleLarge)
                        }

                        if (withdrawals.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AccountBalance,
                                    title = "No Withdrawal Requests",
                                    subtitle = "Requests cannot exceed your available balance and are processed by an admin."
                                )
                            }
                        } else {
                            items(withdrawals, key = { it.id }) { wd ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(wd.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(FintechFormatters.formatPkr(wd.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${wd.paymentMethod} • ${wd.accountReference}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(FintechFormatters.formatDate(wd.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusPill(status = wd.status, isDarkTheme = isDarkTheme)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // TRANSACTION HISTORY TAB
                        item {
                            Text("Transaction History", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val filters = listOf(
                                    "all" to "All",
                                    "deposit" to "Deposits",
                                    "withdrawal" to "Withdrawals",
                                    "task_reward" to "Task Rewards",
                                    "referral_commission" to "Referral Rewards"
                                )
                                filters.forEach { (key, label) ->
                                    FilterChip(
                                        selected = txFilter == key,
                                        onClick = { txFilter = key },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("tx_filter_$key")
                                    )
                                }
                            }
                        }

                        val filteredTx = if (txFilter == "all") transactions else transactions.filter { it.type == txFilter }
                        if (filteredTx.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.History,
                                    title = "No Transactions Found",
                                    subtitle = "Complete daily tasks, submit a deposit, or earn referral commissions to populate your ledger."
                                )
                            }
                        } else {
                            items(filteredTx, key = { it.id }) { tx ->
                                val (icon, prefix) = when (tx.type) {
                                    "deposit" -> Icons.Default.ArrowDownward to "+"
                                    "withdrawal" -> Icons.Default.ArrowUpward to "-"
                                    "referral_commission" -> Icons.Default.CardGiftcard to "+"
                                    else -> Icons.Default.TaskAlt to "+"
                                }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.description, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(tx.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(FintechFormatters.formatDate(tx.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$prefix${FintechFormatters.formatPkr(tx.amount)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (tx.type == "withdrawal") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            StatusPill(status = tx.status, isDarkTheme = isDarkTheme)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewDepositItem?.let { dep ->
        val bmp = remember(dep.receiptBase64) { FintechFormatters.decodeBase64Bitmap(dep.receiptBase64) }
        AlertDialog(
            onDismissRequest = { previewDepositItem = null },
            title = { Text("Receipt • ${dep.effectiveDepositId}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!dep.receiptUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = dep.receiptUrl,
                            contentDescription = "Deposit receipt image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Deposit receipt thumbnail",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (!dep.receiptStoragePath.isNullOrBlank()) {
                        Text("Storage Path: ${dep.receiptStoragePath}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { previewDepositItem = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PaymentAccountDetailsCard(
    providerFallbackName: String,
    account: PaymentAccountConfig?,
    onCopy: (label: String, value: String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_account_details_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (account == null) {
                Text(
                    text = "$providerFallbackName Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Awaiting admin payment account configuration. Once configured in Admin Console, account details will appear here automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!account.isEnabled) {
                Text(
                    text = "${account.providerName} Currently Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "This payment method is temporarily disabled by the administrator. Please select another enabled payment method.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${account.providerName} Receiving Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (account.bankName.isNotBlank()) {
                    AccountFieldRow("Bank Name", account.bankName)
                }
                AccountFieldRow("Account Holder", account.accountHolderName)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Account Number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = account.accountNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = { onCopy("Account Number", account.accountNumber) },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("copy_account_number_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
                if (account.iban.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("IBAN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(account.iban, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onCopy("IBAN", account.iban) },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Text(
                    text = account.instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val qrBmp = remember(account.qrCodeData) { FintechFormatters.decodeBase64Bitmap(account.qrCodeData) }
                if (qrBmp != null) {
                    Image(
                        bitmap = qrBmp.asImageBitmap(),
                        contentDescription = "${account.providerName} QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
