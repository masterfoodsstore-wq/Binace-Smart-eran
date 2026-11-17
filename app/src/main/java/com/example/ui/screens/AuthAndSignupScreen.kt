package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.components.SimulatedBadge
import com.example.ui.theme.EmeraldPrimary

@Composable
fun SignInScreen(
    pendingReferralCode: String = "",
    onAuthSuccess: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_emblem),
                        contentDescription = stringResource(R.string.cd_app_emblem),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    SimulatedBadge(label = "SIMULATED DEMO FINTECH")
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Binace Smart Earn",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Simulated daily tasks, demo PKR wallet tracking, and PKR 100 simulated referral rewards secured by Firebase.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (pendingReferralCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_referral_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                                Column {
                                    Text(
                                        text = "Invite Code Captured: $pendingReferralCode",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Sign in with Google below to auto-apply your referral link.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(26.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FeatureBullet(
                                icon = Icons.Default.TaskAlt,
                                title = "Daily Verified Tasks",
                                subtitle = "Complete daily logins, educational briefs, and activities for simulated PKR rewards."
                            )
                            FeatureBullet(
                                icon = Icons.Default.CardGiftcard,
                                title = "PKR 100 Simulated Referral Rewards",
                                subtitle = "Share your invite link for simulated PKR 100 demo credit after first deposit verification."
                            )
                            FeatureBullet(
                                icon = Icons.Default.Wallet,
                                title = "Simulated Demo Wallet & Ledger",
                                subtitle = "Submit simulated deposits, track demo withdrawals, and inspect full transaction history."
                            )
                            FeatureBullet(
                                icon = Icons.Default.Security,
                                title = "Zero-Trust Firebase Security",
                                subtitle = "Google Identity sign-in with atomic server-validated balance rules."
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    GoogleSignInButton(
                        onAuthSuccess = {
                            errorMessage = null
                            onAuthSuccess()
                        },
                        onAuthError = { msg -> errorMessage = msg }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.security_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompleteRegistrationScreen(
    googleDisplayName: String,
    googleEmail: String,
    initialReferralCode: String,
    isBusy: Boolean,
    errorMessage: String?,
    onCreateAccount: (fullName: String, username: String, email: String, phone: String, referralCode: String) -> Unit,
    onSwitchAccount: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf(googleDisplayName.ifBlank { "" }) }
    var username by rememberSaveable {
        mutableStateOf(
            googleDisplayName.lowercase().replace(Regex("[^a-z0-9_]"), "").take(14)
        )
    }
    var phone by rememberSaveable { mutableStateOf("") }
    var referralCode by rememberSaveable(initialReferralCode) { mutableStateOf(initialReferralCode.uppercase()) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Complete Registration",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Initialize your PKR wallet & unique referral code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_full_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().replace(Regex("[^a-z0-9_]"), "") },
                    label = { Text("Username (letters, numbers, _)") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_username_input")
                )

                OutlinedTextField(
                    value = googleEmail,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Verified Google Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (e.g. +923001234567)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_phone_input")
                )

                OutlinedTextField(
                    value = referralCode,
                    onValueChange = { referralCode = it.uppercase().replace(Regex("[^A-Z0-9_-]"), "") },
                    label = { Text("Referral Code (Optional)") },
                    leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null) },
                    supportingText = {
                        Text(
                            if (initialReferralCode.isNotBlank()) {
                                "Captured automatically from your invite link."
                            } else {
                                "Enter a friend's referral code to link your account."
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_referral_input")
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.security_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Button(
                    onClick = { onCreateAccount(fullName, username, googleEmail, phone, referralCode) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_account_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Create Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = onSwitchAccount,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("switch_google_account_button")
                ) {
                    Text("Already have another account? Sign out & switch")
                }
            }
        }
    }
}
