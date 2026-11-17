package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.OtpChallengeState
import com.example.data.model.SecurityProfile
import com.example.util.SecurityCryptoHelper

@Composable
fun OtpChallengeDialog(
    otpState: OtpChallengeState,
    onDismiss: () -> Unit,
    onRequestOtp: () -> Unit,
    onVerifyOtp: (challengeId: String, code: String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server-Validated OTP Check") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Request a 6-digit OTP code from Cloud Functions. The code is hashed server-side and delivered via encrypted FCM push notification to this device.",
                    style = MaterialTheme.typography.bodySmall
                )
                when (otpState) {
                    OtpChallengeState.Idle -> {
                        Button(
                            onClick = onRequestOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_send_button")
                        ) {
                            Text("Send 6-Digit OTP")
                        }
                    }
                    OtpChallengeState.Requesting -> {
                        Text("Issuing secure OTP challenge...", style = MaterialTheme.typography.labelMedium)
                    }
                    is OtpChallengeState.Sent -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = otpState.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { raw -> codeInput = raw.filter { it.isDigit() }.take(6) },
                            label = { Text("Enter 6-Digit OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_code_input")
                        )
                    }
                    OtpChallengeState.Verifying -> {
                        Text("Verifying OTP hash on server...", style = MaterialTheme.typography.labelMedium)
                    }
                    is OtpChallengeState.Verified -> {
                        Text(
                            text = otpState.message,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is OtpChallengeState.Error -> {
                        Text(
                            text = otpState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRequestOtp, modifier = Modifier.fillMaxWidth()) {
                            Text("Request New OTP")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (otpState is OtpChallengeState.Sent) {
                Button(
                    onClick = { onVerifyOtp(otpState.challengeId, codeInput) },
                    enabled = codeInput.length == 6,
                    modifier = Modifier.testTag("otp_verify_button")
                ) {
                    Text("Verify OTP")
                }
            } else {
                Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (otpState is OtpChallengeState.Sent) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun TotpSetupDialog(
    userEmail: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmSetup: (secret: String, code: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val generatedSecret = remember { SecurityCryptoHelper.generateBase32Secret() }
    val otpUri = remember(userEmail, generatedSecret) {
        SecurityCryptoHelper.buildOtpAuthUri(userEmail, generatedSecret)
    }
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable 2FA Authenticator (TOTP)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add this secret key to Google Authenticator or any RFC 6238 authenticator app, then enter the generated 6-digit code to activate 2FA.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Setup Secret Key (Base32)", style = MaterialTheme.typography.labelSmall)
                        Text(generatedSecret, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(generatedSecret))
                                    Toast.makeText(context, "2FA Secret copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Secret")
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(otpUri))
                                    Toast.makeText(context, "otpauth URI copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Copy URI")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { raw -> codeInput = raw.filter { it.isDigit() }.take(6) },
                    label = { Text("Enter 6-Digit Authenticator Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("totp_verify_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSetup(generatedSecret, codeInput) },
                enabled = !isBusy && codeInput.length == 6,
                modifier = Modifier.testTag("totp_activate_button")
            ) {
                Text("Activate 2FA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawalDetailsDialog(
    existing: SecurityProfile?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (method: String, accountNumber: String, holderName: String) -> Unit
) {
    var method by remember { mutableStateOf(existing?.verifiedWithdrawMethod?.ifBlank { "EasyPaisa" } ?: "EasyPaisa") }
    var accountNumber by remember { mutableStateOf(existing?.verifiedWithdrawAccount ?: "") }
    var holderName by remember { mutableStateOf(existing?.verifiedWithdrawHolderName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Withdrawal Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pre-verify your payout account to protect withdrawals against unauthorized destination changes.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EasyPaisa", "JazzCash", "Bank Transfer").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                    }
                }
                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it },
                    label = { Text("Account Holder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("Account Number / IBAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(method, accountNumber, holderName) },
                enabled = !isBusy && holderName.trim().length >= 2 && accountNumber.trim().length >= 4,
                modifier = Modifier.testTag("save_verified_withdrawal_button")
            ) {
                Text("Verify & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
