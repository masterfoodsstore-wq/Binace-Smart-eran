package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppConstants
import com.example.data.model.UiState
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.AdminRepository
import com.example.data.repository.ReferralRepository
import com.example.data.repository.TaskRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.WalletRepository
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CompleteRegistrationScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReferralsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.BinaceSmartEarnTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ReferralLinkHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val REFERRALS = "referrals"
    const val PROFILE = "profile"
    const val ADMIN = "admin"
}

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private val pendingReferralCodeState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractReferralFromIntent(intent)

        setContent {
            val context = LocalContext.current
            val themePrefs = remember { ThemePreferences(context.applicationContext) }
            val savedDarkTheme by themePrefs.isDarkTheme.collectAsStateWithLifecycle(initialValue = null)
            val savedReferralCode by themePrefs.pendingReferralCode.collectAsStateWithLifecycle(initialValue = "")
            val isDarkTheme = savedDarkTheme ?: isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            LaunchedEffect(pendingReferralCodeState.value) {
                val captured = pendingReferralCodeState.value
                if (captured.isNotBlank()) {
                    themePrefs.setPendingReferralCode(captured)
                }
            }

            val effectiveReferralCode = pendingReferralCodeState.value.ifBlank { savedReferralCode }

            BinaceSmartEarnTheme(darkTheme = isDarkTheme) {
                AppContent(
                    pendingReferralCode = effectiveReferralCode,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { enabled ->
                        scope.launch { themePrefs.setDarkTheme(enabled) }
                    },
                    onClearPendingReferral = {
                        pendingReferralCodeState.value = ""
                        scope.launch { themePrefs.clearPendingReferralCode() }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractReferralFromIntent(intent)
    }

    private fun extractReferralFromIntent(intent: Intent?) {
        val code = ReferralLinkHelper.extractReferralCode(intent?.data)
        if (!code.isNullOrBlank()) {
            pendingReferralCodeState.value = code
        }
    }
}

@Composable
private fun AppContent(
    pendingReferralCode: String,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onClearPendingReferral: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = {},
            scope = scope
        )
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(
            pendingReferralCode = pendingReferralCode,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedShell(
                currentUserId = user.uid,
                googleDisplayName = user.displayName ?: "",
                googleEmail = user.email ?: "",
                pendingReferralCode = pendingReferralCode,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onClearPendingReferral = onClearPendingReferral,
                onLogout = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    googleDisplayName: String,
    googleEmail: String,
    pendingReferralCode: String,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onClearPendingReferral: () -> Unit,
    onLogout: () -> Unit
) {
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                MainViewModel(
                    userRepository = UserRepository(db),
                    taskRepository = TaskRepository(db),
                    walletRepository = WalletRepository(db),
                    referralRepository = ReferralRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val isAdmin by mainViewModel.isAdmin.collectAsStateWithLifecycle()
    val isActionBusy by mainViewModel.isActionBusy.collectAsStateWithLifecycle()
    val feedback by mainViewModel.feedback.collectAsStateWithLifecycle()

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onLogout) { Text("Sign Out") }
                }
            }
        }
        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                CompleteRegistrationScreen(
                    googleDisplayName = googleDisplayName,
                    googleEmail = googleEmail,
                    initialReferralCode = pendingReferralCode,
                    isBusy = isActionBusy,
                    errorMessage = if (feedback?.isError == true) feedback?.message else null,
                    onCreateAccount = { fullName, username, email, phone, refCode ->
                        mainViewModel.clearFeedback()
                        mainViewModel.registerUser(fullName, username, email, phone, refCode)
                    },
                    onSwitchAccount = onLogout
                )
            } else {
                LaunchedEffect(profile.uid) {
                    onClearPendingReferral()
                }
                MainAppScaffold(
                    mainViewModel = mainViewModel,
                    profile = profile,
                    isAdmin = isAdmin,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun MainAppScaffold(
    mainViewModel: MainViewModel,
    profile: com.example.data.model.UserProfile,
    isAdmin: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    val tasksState by mainViewModel.activeTasksState.collectAsStateWithLifecycle()
    val userTasks by mainViewModel.userTasks.collectAsStateWithLifecycle()
    val wallet by mainViewModel.wallet.collectAsStateWithLifecycle()
    val deposits by mainViewModel.deposits.collectAsStateWithLifecycle()
    val withdrawals by mainViewModel.withdrawals.collectAsStateWithLifecycle()
    val transactions by mainViewModel.transactions.collectAsStateWithLifecycle()
    val referrals by mainViewModel.referrals.collectAsStateWithLifecycle()
    val commissions by mainViewModel.commissions.collectAsStateWithLifecycle()
    val settings by mainViewModel.appSettings.collectAsStateWithLifecycle()
    val paymentAccounts by mainViewModel.paymentAccounts.collectAsStateWithLifecycle()
    val receiptUploadState by mainViewModel.receiptUploadState.collectAsStateWithLifecycle()
    val latestDepositNotification by mainViewModel.latestDepositNotification.collectAsStateWithLifecycle()
    val accountVerifiedBanner by mainViewModel.accountVerifiedBanner.collectAsStateWithLifecycle()
    val appUpdateInfo by mainViewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val apkDownloadState by mainViewModel.apkDownloadState.collectAsStateWithLifecycle()
    val isActionBusy by mainViewModel.isActionBusy.collectAsStateWithLifecycle()
    val feedback by mainViewModel.feedback.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var walletInitialTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            mainViewModel.clearFeedback()
        }
    }

    LaunchedEffect(latestDepositNotification) {
        latestDepositNotification?.let { notif ->
            showDepositStatusNotification(context, notif)
        }
    }

    LaunchedEffect(accountVerifiedBanner) {
        if (accountVerifiedBanner) {
            showAccountVerifiedNotification(context, profile.username)
        }
    }

    val tabs = remember {
        listOf(
            NavTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
            NavTab(Routes.TASKS, "Tasks", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
            NavTab(Routes.WALLET, "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
            NavTab(Routes.REFERRALS, "Referrals", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
            NavTab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp
        val showBottomBar = currentRoute != Routes.ADMIN && !isExpanded

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded && currentRoute != Routes.ADMIN) {
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (currentRoute != Routes.ADMIN) {
                                Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            } else Modifier
                        )
                ) {
                composable(Routes.HOME) {
                    HomeDashboardScreen(
                        profile = profile,
                        todayDateKey = mainViewModel.todayDateKey(),
                        isAdmin = isAdmin,
                        isDarkTheme = isDarkTheme,
                        showVerifiedBanner = accountVerifiedBanner,
                        onDismissVerifiedBanner = { mainViewModel.dismissVerifiedBanner() },
                        deposits = deposits,
                        withdrawals = withdrawals,
                        referrals = referrals,
                        appUpdateInfo = appUpdateInfo,
                        apkDownloadState = apkDownloadState,
                        onDownloadApk = { url -> mainViewModel.startApkDownload(context, url) },
                        onOpenDownloads = { url -> mainViewModel.openDownloadedApkInstaller(context, url) },
                        onNavigateTasks = { navigateTab(Routes.TASKS) },
                        onNavigateDeposit = {
                            walletInitialTab = 1
                            navigateTab(Routes.WALLET)
                        },
                        onNavigateWithdraw = {
                            walletInitialTab = 2
                            navigateTab(Routes.WALLET)
                        },
                        onNavigateReferrals = { navigateTab(Routes.REFERRALS) },
                        onNavigateHistory = {
                            walletInitialTab = 0
                            navigateTab(Routes.WALLET)
                        },
                        onNavigateProfile = { navigateTab(Routes.PROFILE) },
                        onOpenSupport = { openWhatsAppSupportChannel(context) },
                        onOpenAdmin = {
                            if (isAdmin) navController.navigate(Routes.ADMIN)
                        }
                    )
                }

                composable(Routes.TASKS) {
                    TasksScreen(
                        profile = profile,
                        settings = settings,
                        todayDateKey = mainViewModel.todayDateKey(),
                        tasksState = tasksState,
                        userTasks = userTasks,
                        isActionBusy = isActionBusy,
                        isDarkTheme = isDarkTheme,
                        onStartTask = { mainViewModel.startTask(it, profile) },
                        onClaimReward = { mainViewModel.claimTaskReward(it, profile) },
                        onNavigateDeposit = {
                            walletInitialTab = 1
                            navigateTab(Routes.WALLET)
                        }
                    )
                }

                composable(Routes.WALLET) {
                    WalletScreen(
                        profile = profile,
                        wallet = wallet,
                        settings = settings,
                        paymentAccounts = paymentAccounts,
                        receiptUploadState = receiptUploadState,
                        latestDepositNotification = latestDepositNotification,
                        deposits = deposits,
                        withdrawals = withdrawals,
                        transactions = transactions,
                        initialSubTab = walletInitialTab,
                        isActionBusy = isActionBusy,
                        isDarkTheme = isDarkTheme,
                        onSelectReceipt = { uri -> mainViewModel.selectAndUploadReceipt(context, uri) },
                        onRemoveReceipt = { mainViewModel.removeSelectedReceipt() },
                        onDismissNotification = { mainViewModel.dismissDepositNotification() },
                        onSubmitDeposit = { amt, method, acctName, acctNum, ref, onSubmitted ->
                            mainViewModel.submitDeposit(
                                profile = profile,
                                amount = amt,
                                paymentMethod = method,
                                paymentAccountName = acctName,
                                paymentAccountNumber = acctNum,
                                reference = ref,
                                onSubmitted = onSubmitted
                            )
                        },
                        onSubmitWithdrawal = { amt, method, acc ->
                            mainViewModel.submitWithdrawal(profile, amt, method, acc)
                        }
                    )
                }

                composable(Routes.REFERRALS) {
                    ReferralsScreen(
                        profile = profile,
                        settings = settings,
                        referrals = referrals,
                        commissions = commissions,
                        isDarkTheme = isDarkTheme,
                        onNavigateDeposit = {
                            walletInitialTab = 1
                            navigateTab(Routes.WALLET)
                        }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        profile = profile,
                        wallet = wallet,
                        isAdmin = isAdmin,
                        isDarkTheme = isDarkTheme,
                        isActionBusy = isActionBusy,
                        appUpdateInfo = appUpdateInfo,
                        apkDownloadState = apkDownloadState,
                        onToggleTheme = onToggleTheme,
                        onUpdateProfile = { name, user, phone ->
                            mainViewModel.updateProfile(name, user, phone)
                        },
                        onCheckForUpdates = { mainViewModel.checkForUpdates(manual = true) },
                        onDownloadApk = { url -> mainViewModel.startApkDownload(context, url) },
                        onOpenDownloads = { url -> mainViewModel.openDownloadedApkInstaller(context, url) },
                        onOpenAdmin = {
                            if (isAdmin) navController.navigate(Routes.ADMIN)
                        },
                        onOpenWebsite = { openOfficialWebsite(context) },
                        onOpenSupport = { openWhatsAppSupportChannel(context) },
                        onLogout = onLogout
                    )
                }

                composable(Routes.ADMIN) {
                    if (isAdmin) {
                        val adminViewModel: AdminViewModel = viewModel(
                            key = "admin_${profile.uid}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                    AdminViewModel(
                                        adminRepository = AdminRepository(db),
                                        referralRepository = ReferralRepository(db)
                                    )
                                }
                            }
                        )
                        AdminDashboardScreen(
                            adminViewModel = adminViewModel,
                            isDarkTheme = isDarkTheme,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
    }
}

private fun openOfficialWebsite(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.OFFICIAL_WEBSITE_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open website", Toast.LENGTH_SHORT).show()
    }
}

private fun openWhatsAppSupportChannel(context: Context) {
    val uri = Uri.parse(AppConstants.SUPPORT_WHATSAPP_CHANNEL_URL)
    val whatsAppIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(whatsAppIntent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open WhatsApp channel", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun showDepositStatusNotification(
    context: Context,
    notif: com.example.data.model.DepositStatusNotification
) {
    val channelId = "deposit_status_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager != null && manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Deposit Status Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an administrator approves or rejects your deposit request."
            }
            manager.createNotificationChannel(channel)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val isApproved = notif.status.equals("approved", ignoreCase = true)
    val title = if (isApproved) {
        "Deposit Approved • PKR ${notif.amount.toInt()}"
    } else {
        "Deposit Rejected • PKR ${notif.amount.toInt()}"
    }
    val body = buildString {
        append("Request ${notif.depositId} has been ${notif.status.lowercase()}.")
        if (!notif.adminNote.isNullOrBlank()) {
            append(" Note: ${notif.adminNote}")
        }
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(notif.depositId.hashCode(), notification)
    } catch (_: SecurityException) {
    }
}

private fun showAccountVerifiedNotification(context: Context, username: String) {
    val channelId = "deposit_status_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager != null && manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Deposit Status Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an administrator verifies your account or updates your deposit request."
            }
            manager.createNotificationChannel(channel)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val body = "Your @${username} account is now VERIFIED. Daily tasks, referral sharing, and simulated demo withdrawals are unlocked."
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle("Account Verified • Binace Smart Earn")
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(9001, notification)
    } catch (_: SecurityException) {
    }
}
