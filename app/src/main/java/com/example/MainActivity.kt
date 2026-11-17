package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VipLevel
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.SupportFirebaseMessagingService
import com.example.data.remote.configureAppCheck
import com.example.data.repository.AdminRepository
import com.example.data.repository.NotificationSecurityRepository
import com.example.data.repository.ReferralRepository
import com.example.data.repository.SupportRepository
import com.example.data.repository.TaskRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.VipLeaderboardRepository
import com.example.data.repository.WalletRepository
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.navigation.ScrollableBottomNavBar
import com.example.ui.navigation.ScrollableNavRail
import com.example.ui.navigation.buildPrimaryNavTabs
import com.example.ui.screens.AccountSecurityScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CompleteRegistrationScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.LiveSupportScreen
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReferralsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.SupportAgentConsoleScreen
import com.example.ui.screens.SupportChatScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.VipMembershipScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.BinaceSmartEarnTheme
import com.example.ui.viewmodel.AdminPlatformViewModel
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PlatformViewModel
import com.example.ui.viewmodel.SupportViewModel
import com.example.util.PlatformNotificationHelper
import com.example.util.ReferralLinkHelper
import com.example.util.SecurityCryptoHelper
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
    const val VIP = "vip"
    const val LEADERBOARD = "leaderboard"
    const val NOTIFICATIONS = "notifications"
    const val SECURITY = "security"
    const val ADMIN = "admin"
    const val LIVE_SUPPORT = "live_support"
    const val SUPPORT_CONSOLE = "support_console"
    const val SUPPORT_CHAT_PATTERN = "support_chat/{ticketId}/{asAgent}"

    fun supportChatRoute(ticketId: String, asAgent: Boolean): String =
        "support_chat/$ticketId/$asAgent"
}

class MainActivity : FragmentActivity() {
    private val pendingReferralCodeState = mutableStateOf("")
    private val pendingSupportTicketIdState = mutableStateOf<String?>(null)
    private val pendingNotificationRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check before any Compose UI, ViewModels, or Firebase calls
        configureAppCheck(applicationContext, intent)
        enableEdgeToEdge()
        extractLaunchExtras(intent)

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
                    pendingSupportTicketId = pendingSupportTicketIdState.value,
                    pendingNotificationRoute = pendingNotificationRouteState.value,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { enabled ->
                        scope.launch { themePrefs.setDarkTheme(enabled) }
                    },
                    onClearPendingReferral = {
                        pendingReferralCodeState.value = ""
                        scope.launch { themePrefs.clearPendingReferralCode() }
                    },
                    onConsumeSupportTicket = { pendingSupportTicketIdState.value = null },
                    onConsumeNotificationRoute = { pendingNotificationRouteState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractLaunchExtras(intent)
    }

    private fun extractLaunchExtras(intent: Intent?) {
        val code = ReferralLinkHelper.extractReferralCode(intent?.data)
        if (!code.isNullOrBlank()) {
            pendingReferralCodeState.value = code
        }
        val ticketId = intent?.getStringExtra("support_ticket_id")
        if (!ticketId.isNullOrBlank()) {
            pendingSupportTicketIdState.value = ticketId
        }
        val route = intent?.getStringExtra("notification_route")
        if (!route.isNullOrBlank()) {
            pendingNotificationRouteState.value = route
        }
    }
}

@Composable
private fun AppContent(
    pendingReferralCode: String,
    pendingSupportTicketId: String?,
    pendingNotificationRoute: String?,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onClearPendingReferral: () -> Unit,
    onConsumeSupportTicket: () -> Unit,
    onConsumeNotificationRoute: () -> Unit
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
                pendingSupportTicketId = pendingSupportTicketId,
                pendingNotificationRoute = pendingNotificationRoute,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onClearPendingReferral = onClearPendingReferral,
                onConsumeSupportTicket = onConsumeSupportTicket,
                onConsumeNotificationRoute = onConsumeNotificationRoute,
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
    pendingSupportTicketId: String?,
    pendingNotificationRoute: String?,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onClearPendingReferral: () -> Unit,
    onConsumeSupportTicket: () -> Unit,
    onConsumeNotificationRoute: () -> Unit,
    onLogout: () -> Unit
) {
    val mainViewModel: MainViewModel = viewModel(
        key = "main_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
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

    val supportViewModel: SupportViewModel = viewModel(
        key = "support_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SupportViewModel(
                    supportRepository = SupportRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val platformViewModel: PlatformViewModel = viewModel(
        key = "platform_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                PlatformViewModel(
                    vipLeaderboardRepository = VipLeaderboardRepository(db),
                    notificationSecurityRepository = NotificationSecurityRepository(db),
                    currentUserId = currentUserId,
                    currentDeviceId = SecurityCryptoHelper.getOrCreateDeviceId(app)
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
                    supportViewModel = supportViewModel,
                    platformViewModel = platformViewModel,
                    profile = profile,
                    isAdmin = isAdmin,
                    isDarkTheme = isDarkTheme,
                    pendingSupportTicketId = pendingSupportTicketId,
                    pendingNotificationRoute = pendingNotificationRoute,
                    onConsumeSupportTicket = onConsumeSupportTicket,
                    onConsumeNotificationRoute = onConsumeNotificationRoute,
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
    supportViewModel: SupportViewModel,
    platformViewModel: PlatformViewModel,
    profile: UserProfile,
    isAdmin: Boolean,
    isDarkTheme: Boolean,
    pendingSupportTicketId: String?,
    pendingNotificationRoute: String?,
    onConsumeSupportTicket: () -> Unit,
    onConsumeNotificationRoute: () -> Unit,
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

    val isSupportStaff by supportViewModel.isSupportStaff.collectAsStateWithLifecycle()
    val supportUnreadCount by supportViewModel.userUnreadTotal.collectAsStateWithLifecycle()
    val onlineAgentsCount by supportViewModel.onlineAgentsCount.collectAsStateWithLifecycle()
    val latestSupportNotif by supportViewModel.latestNotification.collectAsStateWithLifecycle()
    val supportFeedback by supportViewModel.feedback.collectAsStateWithLifecycle()

    val vipTierConfigs by platformViewModel.vipTierConfigs.collectAsStateWithLifecycle()
    val myVipMembership by platformViewModel.myVipMembership.collectAsStateWithLifecycle()
    val myVipHistory by platformViewModel.myVipHistory.collectAsStateWithLifecycle()
    val selectedPeriod by platformViewModel.selectedPeriod.collectAsStateWithLifecycle()
    val leaderboardEntries by platformViewModel.leaderboardEntries.collectAsStateWithLifecycle()
    val leaderboardPrizes by platformViewModel.leaderboardPrizes.collectAsStateWithLifecycle()
    val unifiedNotifications by platformViewModel.unifiedNotifications.collectAsStateWithLifecycle()
    val unreadNotificationCount by platformViewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val securityProfile by platformViewModel.securityProfile.collectAsStateWithLifecycle()
    val myDevices by platformViewModel.myDevices.collectAsStateWithLifecycle()
    val mySecurityEvents by platformViewModel.mySecurityEvents.collectAsStateWithLifecycle()
    val isCurrentSessionRevoked by platformViewModel.isCurrentSessionRevoked.collectAsStateWithLifecycle()
    val otpState by platformViewModel.otpState.collectAsStateWithLifecycle()
    val platformBusy by platformViewModel.isBusy.collectAsStateWithLifecycle()
    val platformFeedback by platformViewModel.feedback.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val currentDeviceId = remember(context) { SecurityCryptoHelper.getOrCreateDeviceId(context) }
    var walletInitialTab by remember { mutableIntStateOf(0) }
    var securityBiometricUnlocked by remember(profile.uid) { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            mainViewModel.clearFeedback()
        }
    }
    LaunchedEffect(platformFeedback) {
        platformFeedback?.let {
            snackbarHostState.showSnackbar(it.message)
            platformViewModel.clearFeedback()
        }
    }
    LaunchedEffect(supportFeedback) {
        supportFeedback?.let {
            snackbarHostState.showSnackbar(it.message)
            supportViewModel.clearFeedback()
        }
    }

    LaunchedEffect(isAdmin) {
        supportViewModel.setAdminFlag(isAdmin)
    }

    LaunchedEffect(profile.uid) {
        supportViewModel.syncFcmToken(PlatformNotificationHelper.hasPostNotificationPermission(context))
        platformViewModel.initializeForUser(context, profile, appUpdateInfo.currentVersionName)
    }

    LaunchedEffect(isCurrentSessionRevoked) {
        if (isCurrentSessionRevoked) {
            Toast.makeText(context, "This device session was revoked.", Toast.LENGTH_LONG).show()
            onLogout()
        }
    }

    LaunchedEffect(latestDepositNotification) {
        latestDepositNotification?.let { PlatformNotificationHelper.showDepositStatusNotification(context, it) }
    }
    LaunchedEffect(accountVerifiedBanner) {
        if (accountVerifiedBanner) PlatformNotificationHelper.showAccountVerifiedNotification(context, profile.username)
    }
    LaunchedEffect(latestSupportNotif) {
        latestSupportNotif?.let { event ->
            SupportFirebaseMessagingService.showSupportPushNotification(
                context = context,
                ticketId = event.ticketId,
                title = event.title,
                body = event.body
            )
            supportViewModel.dismissNotification()
        }
    }

    val tabs = remember { buildPrimaryNavTabs() }
    val primaryRoutes = remember(tabs) { tabs.map { it.route }.toSet() }

    fun performTabNavigation(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateTab(route: String) {
        val needsBioGate = route == Routes.SECURITY &&
            securityProfile?.biometricEnabled == true &&
            !securityBiometricUnlocked &&
            activity != null &&
            SecurityCryptoHelper.isBiometricHardwareAvailable(context)
        if (needsBioGate && activity != null) {
            SecurityCryptoHelper.launchBiometricPrompt(
                activity = activity,
                title = "Unlock Account Security",
                subtitle = "Confirm biometric identity to manage 2FA and sessions",
                onSuccess = {
                    securityBiometricUnlocked = true
                    performTabNavigation(Routes.SECURITY)
                },
                onError = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            performTabNavigation(route)
        }
    }

    LaunchedEffect(pendingSupportTicketId) {
        if (!pendingSupportTicketId.isNullOrBlank()) {
            if (pendingSupportTicketId == "support") {
                navController.navigate(Routes.LIVE_SUPPORT)
            } else {
                navController.navigate(Routes.supportChatRoute(pendingSupportTicketId, asAgent = false))
            }
            onConsumeSupportTicket()
        }
    }

    LaunchedEffect(pendingNotificationRoute) {
        val target = pendingNotificationRoute
        if (!target.isNullOrBlank()) {
            when (target) {
                Routes.SECURITY, Routes.VIP, Routes.NOTIFICATIONS, Routes.WALLET, Routes.LEADERBOARD -> navigateTab(target)
                Routes.LIVE_SUPPORT -> navController.navigate(Routes.LIVE_SUPPORT)
                else -> navigateTab(Routes.NOTIFICATIONS)
            }
            onConsumeNotificationRoute()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp
        val isPrimaryDestination = currentRoute in primaryRoutes
        val showBottomBar = isPrimaryDestination && !isExpanded

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    ScrollableBottomNavBar(
                        tabs = tabs,
                        currentRoute = currentRoute,
                        unreadAlertsCount = unreadNotificationCount,
                        onSelectTab = ::navigateTab
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded && isPrimaryDestination) {
                    ScrollableNavRail(
                        tabs = tabs,
                        currentRoute = currentRoute,
                        unreadAlertsCount = unreadNotificationCount,
                        onSelectTab = ::navigateTab
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (isPrimaryDestination) {
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
                            vipLevel = myVipMembership?.levelEnum ?: VipLevel.BRONZE,
                            unreadAlertsCount = unreadNotificationCount,
                            supportUnreadCount = supportUnreadCount,
                            onlineAgentsCount = onlineAgentsCount,
                            isSupportStaff = isSupportStaff,
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
                            onOpenVip = { navigateTab(Routes.VIP) },
                            onOpenLeaderboard = { navigateTab(Routes.LEADERBOARD) },
                            onOpenAlerts = { navigateTab(Routes.NOTIFICATIONS) },
                            onOpenSecurity = { navigateTab(Routes.SECURITY) },
                            onOpenLiveSupport = { navController.navigate(Routes.LIVE_SUPPORT) },
                            onOpenSupportConsole = { navController.navigate(Routes.SUPPORT_CONSOLE) },
                            onOpenSupport = { PlatformNotificationHelper.openWhatsAppSupportChannel(context) },
                            onOpenAdmin = { if (isAdmin) navController.navigate(Routes.ADMIN) }
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
                            securityProfile = securityProfile,
                            onOpenSecurity = { navigateTab(Routes.SECURITY) },
                            onSelectReceipt = { uri -> mainViewModel.selectAndUploadReceipt(context, uri) },
                            onRemoveReceipt = { mainViewModel.removeSelectedReceipt() },
                            onDismissNotification = { mainViewModel.dismissDepositNotification() },
                            onSubmitDeposit = { amt, method, acctName, acctNum, ref, onSubmitted ->
                                mainViewModel.submitDeposit(profile, amt, method, acctName, acctNum, ref, onSubmitted)
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
                            vipLevel = myVipMembership?.levelEnum ?: VipLevel.BRONZE,
                            securityProfile = securityProfile,
                            supportUnreadCount = supportUnreadCount,
                            onlineAgentsCount = onlineAgentsCount,
                            isSupportStaff = isSupportStaff,
                            isAdmin = isAdmin,
                            isDarkTheme = isDarkTheme,
                            isActionBusy = isActionBusy,
                            appUpdateInfo = appUpdateInfo,
                            apkDownloadState = apkDownloadState,
                            onToggleTheme = onToggleTheme,
                            onUpdateProfile = { name, user, phone -> mainViewModel.updateProfile(name, user, phone) },
                            onCheckForUpdates = { mainViewModel.checkForUpdates(manual = true) },
                            onDownloadApk = { url -> mainViewModel.startApkDownload(context, url) },
                            onOpenDownloads = { url -> mainViewModel.openDownloadedApkInstaller(context, url) },
                            onOpenAdmin = { if (isAdmin) navController.navigate(Routes.ADMIN) },
                            onOpenVip = { navigateTab(Routes.VIP) },
                            onOpenSecurity = { navigateTab(Routes.SECURITY) },
                            onOpenLiveSupport = { navController.navigate(Routes.LIVE_SUPPORT) },
                            onOpenSupportConsole = { navController.navigate(Routes.SUPPORT_CONSOLE) },
                            onOpenWebsite = { PlatformNotificationHelper.openOfficialWebsite(context) },
                            onOpenSupport = { PlatformNotificationHelper.openWhatsAppSupportChannel(context) },
                            onLogout = onLogout
                        )
                    }

                    composable(Routes.VIP) {
                        VipMembershipScreen(
                            profile = profile,
                            vipMembership = myVipMembership,
                            vipConfigs = vipTierConfigs,
                            vipHistory = myVipHistory,
                            isBusy = platformBusy,
                            isDarkTheme = isDarkTheme,
                            onSyncVip = { platformViewModel.syncVipAndLeaderboardNow() },
                            onOpenSupport = { navController.navigate(Routes.LIVE_SUPPORT) }
                        )
                    }

                    composable(Routes.LEADERBOARD) {
                        LeaderboardScreen(
                            currentUserId = profile.uid,
                            selectedPeriod = selectedPeriod,
                            dateKeys = platformViewModel.dateKeys(),
                            entries = leaderboardEntries,
                            prizes = leaderboardPrizes,
                            isBusy = platformBusy,
                            isDarkTheme = isDarkTheme,
                            onSelectPeriod = { platformViewModel.selectLeaderboardPeriod(it) },
                            onSyncLeaderboard = { platformViewModel.syncVipAndLeaderboardNow() }
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationCenterScreen(
                            feed = unifiedNotifications,
                            unreadCount = unreadNotificationCount,
                            isDarkTheme = isDarkTheme,
                            onMarkRead = { platformViewModel.markFeedItemRead(it) },
                            onMarkAllRead = { platformViewModel.markAllNotificationsRead() },
                            onSyncPushToken = {
                                supportViewModel.syncFcmToken(
                                    PlatformNotificationHelper.hasPostNotificationPermission(context)
                                )
                            }
                        )
                    }

                    composable(Routes.SECURITY) {
                        AccountSecurityScreen(
                            profile = profile,
                            securityProfile = securityProfile,
                            currentDeviceId = currentDeviceId,
                            devices = myDevices,
                            securityEvents = mySecurityEvents,
                            otpState = otpState,
                            isBusy = platformBusy,
                            isDarkTheme = isDarkTheme,
                            onRequestOtp = { platformViewModel.requestOtpChallenge() },
                            onVerifyOtp = { challengeId, code -> platformViewModel.verifyOtpCode(challengeId, code) },
                            onResetOtpState = { platformViewModel.resetOtpState() },
                            onToggleBiometric = { enabled -> platformViewModel.toggleBiometric(profile, enabled) },
                            onEnableTotp = { secret, code -> platformViewModel.enableTotp(profile, secret, code) },
                            onDisableTotp = { platformViewModel.disableTotp(profile) },
                            onSaveWithdrawDetails = { method, number, holder ->
                                platformViewModel.verifyWithdrawalDetails(profile, method, number, holder)
                            },
                            onTrustDevice = { platformViewModel.trustDevice(profile, it) },
                            onRevokeDevice = { platformViewModel.revokeDevice(profile, it) },
                            onTriggerPasswordReset = { redirect ->
                                platformViewModel.triggerPasswordReset(profile, redirect)
                            }
                        )
                    }

                    composable(Routes.LIVE_SUPPORT) {
                        LiveSupportScreen(
                            supportViewModel = supportViewModel,
                            profile = profile,
                            isDarkTheme = isDarkTheme,
                            onBack = { navController.popBackStack() },
                            onOpenTicketChat = { ticketId ->
                                navController.navigate(Routes.supportChatRoute(ticketId, asAgent = false))
                            },
                            onOpenWhatsAppChannel = { PlatformNotificationHelper.openWhatsAppSupportChannel(context) }
                        )
                    }

                    composable(Routes.SUPPORT_CONSOLE) {
                        SupportAgentConsoleScreen(
                            supportViewModel = supportViewModel,
                            viewerProfile = profile,
                            isDarkTheme = isDarkTheme,
                            onBack = { navController.popBackStack() },
                            onOpenSupportChat = { ticketId ->
                                navController.navigate(Routes.supportChatRoute(ticketId, asAgent = true))
                            }
                        )
                    }

                    composable(
                        route = Routes.SUPPORT_CHAT_PATTERN,
                        arguments = listOf(
                            navArgument("ticketId") { type = NavType.StringType },
                            navArgument("asAgent") { type = NavType.BoolType }
                        )
                    ) { entry ->
                        val ticketId = entry.arguments?.getString("ticketId").orEmpty()
                        val asAgent = entry.arguments?.getBoolean("asAgent") ?: false
                        SupportChatScreen(
                            supportViewModel = supportViewModel,
                            ticketId = ticketId,
                            isAgentView = asAgent,
                            viewerProfile = profile,
                            isDarkTheme = isDarkTheme,
                            onBack = { navController.popBackStack() }
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
                            val adminPlatformViewModel: AdminPlatformViewModel = viewModel(
                                key = "admin_platform_${profile.uid}",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                        AdminPlatformViewModel(
                                            vipLeaderboardRepository = VipLeaderboardRepository(db),
                                            notificationSecurityRepository = NotificationSecurityRepository(db)
                                        )
                                    }
                                }
                            )
                            AdminDashboardScreen(
                                adminViewModel = adminViewModel,
                                supportViewModel = supportViewModel,
                                adminPlatformViewModel = adminPlatformViewModel,
                                viewerProfile = profile,
                                isDarkTheme = isDarkTheme,
                                onOpenSupportChat = { ticketId ->
                                    navController.navigate(Routes.supportChatRoute(ticketId, asAgent = true))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
