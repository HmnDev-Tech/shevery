@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedItem
import moe.shizuku.manager.ui.compose.ExpressiveSegmentedSection
import moe.shizuku.manager.ui.compose.LocalReducedMotion
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.view.Menu
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import moe.shizuku.manager.ui.compose.LocalFloatingNavBarVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hamondev.shevery.tasker.Command
import com.hamondev.shevery.tasker.PluginContract
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import moe.shizuku.manager.ui.compose.scrollMotionBlur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.guide.GuideTopic
import moe.shizuku.manager.guide.LocalShakeGuideActive
import moe.shizuku.manager.guide.ShakeGuideHost
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.settings.DeveloperOptionsHideManager
import moe.shizuku.manager.settings.DeveloperOptionsHideService
import moe.shizuku.manager.settings.UsbDebuggingHideManager
import moe.shizuku.manager.settings.UsbDebuggingHideService
import moe.shizuku.manager.settings.SettingsSection
import moe.shizuku.manager.adb.AdbArm
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.module.AdbModuleManager
import moe.shizuku.manager.module.InstallZipModuleActivity
import moe.shizuku.manager.module.update.SheveryAppUpdateDialog
import moe.shizuku.manager.module.update.SheveryAppUpdateResult
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.worker.WifiDebugReassert
import moe.shizuku.manager.ui.compose.HtmlText
import moe.shizuku.manager.ui.compose.htmlToPlainText
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.ui.compose.ExpressiveFloatingNavigationBar
import moe.shizuku.manager.ui.compose.RefreshedNavigationBar
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.NavItem
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.update.SheveryUpdateChecker
import moe.shizuku.manager.compat.StubManager
import moe.shizuku.manager.utils.ShizukuStateMachine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

abstract class HomeActivity : AppActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkServerStatus()
        appsModel.load()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        AdbModuleManager.resetServiceRunGuard()
        checkServerStatus()
    }

    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()
    private val permissionRefreshTick = mutableIntStateOf(0)
    private val isAppUnlocked = mutableStateOf(true)

    private var pendingLocalNetworkAction: (() -> Unit)? = null

    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRefreshTick.intValue++
        val action = pendingLocalNetworkAction
        pendingLocalNetworkAction = null
        if (buildLocalNetworkPermissionState().granted) {
            EnvironmentUtils.requestIgnoreBatteryOptimizations(this@HomeActivity)
            action?.invoke()
        } else {
            Toast.makeText(this, R.string.home_local_network_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            moe.shizuku.manager.service.SheveryNotificationManager.updateNotification(this)
        }
    }

    private val manageAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        appsModel.load(onlyCount = true)
    }

    private var onNavigateAutomationSettings: (() -> Unit)? = null

    private val incomingZipPath = mutableStateOf<String?>(null)

    private fun consumeZipIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_ZIP_PATH) ?: return
        incomingZipPath.value = path
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeZipIntent(intent)
        if (intent.getStringExtra(PluginContract.EXTRA_TARGET_SECTION) ==
            PluginContract.TARGET_SECTION_AUTOMATION
        ) {
            onNavigateAutomationSettings?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeZipIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ModuleSettings.isCompatibilityStubEnabled() && !StubManager.isInstalled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    StubManager.install(applicationContext)
                } catch (_: Throwable) {
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Arm adbd on launch only when the server is not already running
                // and no live TCP port is active; flipping adb_wifi_enabled 0 -> 1
                // while TCP mode (port 5555) is running restarts adbd and drops
                // the active session on OEM ROMs (Issue #278).
                if (!Shizuku.pingBinder() && (!ShizukuSettings.isTcpMode() || EnvironmentUtils.getLiveAdbTcpPort() <= 0)) {
                    AdbArm.arm(applicationContext)
                }
            } catch (_: Throwable) {
            }
        }

        isAppUnlocked.value = !SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.APP_OPEN) || AuthManager.isAppOpenSessionValid()

        setContent {
            val serviceResource by homeModel.serviceStatus.observeAsState()
            val serverState by homeModel.serverState.collectAsState()
            val grantedResource by appsModel.grantedCount.observeAsState()
            val localNetworkPermissionState = remember(permissionRefreshTick.intValue) {
                buildLocalNetworkPermissionState()
            }

            var showTcpPromptDialog by rememberSaveable { mutableStateOf(false) }
            var doNotRemindChecked by rememberSaveable { mutableStateOf(false) }
            var tcpPromptPortInput by rememberSaveable {
                mutableStateOf(
                    if (ShizukuSettings.getPreferences().contains(ShizukuSettings.TCP_MODE_PORT) &&
                        !ShizukuSettings.isTcpPortRandomized()
                    ) {
                        ShizukuSettings.getTcpModePort().toString()
                    } else {
                        ShizukuSettings.randomTcpPort().toString()
                    }
                )
            }
            var showStopDialog by rememberSaveable { mutableStateOf(false) }
            var showAdbCommandDialog by rememberSaveable { mutableStateOf(false) }
            var showWadbNotEnabledDialog by rememberSaveable { mutableStateOf(false) }
            var showAdbDiscoveryDialog by rememberSaveable { mutableStateOf(false) }
            var showAdbPairDialog by rememberSaveable { mutableStateOf(false) }

            // Experimental (Extras > Experimental): observed at activity scope so
            // the features work no matter which tab is visible. A Home-tab-scoped
            // implementation missed transitions whenever the home page was not
            // composed, which made the settings behave like dead switches.
            val serviceRunning = serverState == ShizukuStateMachine.State.RUNNING ||
                serviceResource?.data?.isRunning == true
            val serviceStarting = serverState == ShizukuStateMachine.State.STARTING
            val experimentalHaptics = ModuleSettings.isServiceStateHapticsEnabled()
            val keepScreenOnWhileStarting = ModuleSettings.isKeepScreenOnWhileStarting()
            val activityView = LocalView.current
            val activityContext = LocalContext.current

            var wasServiceRunning by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(serviceRunning) {
                if (wasServiceRunning != null && wasServiceRunning != serviceRunning && experimentalHaptics) {
                    // CONFIRM only exists since API 30 and is silently ignored on
                    // older devices, so fall back to CONTEXT_CLICK (API 23) instead
                    // of doing nothing on Android 7-10.
                    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.view.HapticFeedbackConstants.CONFIRM
                    } else {
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK
                    }
                    activityView.performHapticFeedback(feedback)
                }
                wasServiceRunning = serviceRunning
            }

            DisposableEffect(serviceStarting, keepScreenOnWhileStarting) {
                var ctx: Context? = activityContext
                while (ctx is ContextWrapper && ctx !is Activity) {
                    ctx = ctx.baseContext
                }
                val window = (ctx as? Activity)?.window
                if (serviceStarting && keepScreenOnWhileStarting) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    // Never leave the flag stuck: leaving this composition or
                    // finishing the activity must restore normal screen behavior.
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(serviceResource?.status, serviceResource?.data?.uid) {
                val status = serviceResource?.data ?: return@LaunchedEffect
                if (serviceResource?.status == Status.SUCCESS && status.isRunning) {
                    ShizukuSettings.setLastLaunchMode(
                        if (status.uid == 0) {
                            ShizukuSettings.LaunchMethod.ROOT
                        } else {
                            ShizukuSettings.LaunchMethod.ADB
                        }
                    )
                    try {
                        AdbModuleManager.runEnabledServicesIfAllowed(applicationContext)
                        WifiDebugReassert.reassertIfEnabled(applicationContext)
                    } catch (_: Throwable) {
                    }

                    val isAdbRunning = status.uid != 0
                    val needsTcpPrompt = isAdbRunning &&
                        !ShizukuSettings.isTcpMode() &&
                        !ShizukuSettings.isTcpModePromptSuppressed()

                    if (!hasPromptedTcpDialogThisSession && needsTcpPrompt) {
                        hasPromptedTcpDialogThisSession = true
                        showTcpPromptDialog = true
                    }
                }
            }

            val isTargetAutomation = remember(intent) {
                intent?.getStringExtra(PluginContract.EXTRA_TARGET_SECTION) ==
                    PluginContract.TARGET_SECTION_AUTOMATION
            }
            var selectedTab by rememberSaveable { mutableIntStateOf(if (isTargetAutomation) 3 else 0) }
            var settingsTargetSection by remember {
                mutableStateOf(if (isTargetAutomation) SettingsSection.AUTOMATION else null)
            }
            var settingsTargetNav by remember {
                mutableStateOf<moe.shizuku.manager.settings.SettingsNav?>(null)
            }

            DisposableEffect(Unit) {
                onNavigateAutomationSettings = {
                    settingsTargetSection = SettingsSection.AUTOMATION
                    selectedTab = 3
                }
                onDispose {
                    onNavigateAutomationSettings = null
                }
            }

            // Hoisted above the AnimatedContent tab switch: tab screens leave
            // composition on change, so state kept here survives; saveable
            // so it also survives rotation.
            val settingsListState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            val modulesListState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            val computListState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            val cachedModules = remember {
                mutableStateOf<List<moe.shizuku.manager.module.AdbModule>>(emptyList(), neverEqualPolicy())
            }
            val modulesReloadTick by AdbModuleManager.modulesReloadTick.collectAsState()
            LaunchedEffect(modulesReloadTick) {
                cachedModules.value = AdbModuleManager.listModules(applicationContext)
            }
            // ZIP handed over by another app through "Install Module":
            // land on ADB Modules immediately and stream installation in the Live Console
            // on AdbModuleManager.operationScope so recomposition never cancels it.
            val incomingZip = incomingZipPath.value
            LaunchedEffect(incomingZip) {
                val path = incomingZip ?: return@LaunchedEffect
                incomingZipPath.value = null
                val zipFile = InstallZipModuleActivity.takeConfirmedArchive(path)
                    ?: return@LaunchedEffect
                selectedTab = 1
                AdbModuleManager.launchInstallFromUri(
                    context = applicationContext,
                    uri = Uri.fromFile(zipFile),
                    displayName = zipFile.name,
                    deleteFile = zipFile
                ) { result ->
                    result.onSuccess { module ->
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.modules_install_success, module.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.modules_install_failed, it.message ?: it.javaClass.simpleName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            val homeListState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            var useClassicNav by remember { mutableStateOf(ShizukuSettings.isClassicNav()) }
            var swipeBetweenTabs by remember { mutableStateOf(ShizukuSettings.isSwipeBetweenTabs()) }
            var motionBlurEnabled by remember { mutableStateOf(ShizukuSettings.isMotionBlurEnabled()) }
            var motionBlurIntensity by remember { mutableIntStateOf(ShizukuSettings.getMotionBlurIntensity()) }
            var motionBlurStrength by remember { mutableIntStateOf(ShizukuSettings.getMotionBlurStrength()) }
            DisposableEffect(Unit) {
                val prefs = ShizukuSettings.getPreferences()
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == ShizukuSettings.USE_CLASSIC_NAV) {
                        useClassicNav = ShizukuSettings.isClassicNav()
                    }
                    if (key == ShizukuSettings.SWIPE_BETWEEN_TABS) {
                        swipeBetweenTabs = ShizukuSettings.isSwipeBetweenTabs()
                    }
                    if (key == ShizukuSettings.MOTION_BLUR_ENABLED) {
                        motionBlurEnabled = ShizukuSettings.isMotionBlurEnabled()
                    }
                    if (key == ShizukuSettings.MOTION_BLUR_INTENSITY) {
                        motionBlurIntensity = ShizukuSettings.getMotionBlurIntensity()
                    }
                    if (key == ShizukuSettings.MOTION_BLUR_STRENGTH) {
                        motionBlurStrength = ShizukuSettings.getMotionBlurStrength()
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val unlocked by isAppUnlocked

            LaunchedEffect(unlocked) {
                if (!unlocked) {
                    AuthManager.authenticate(
                        activity = this@HomeActivity,
                        title = getString(R.string.security_auth_prompt_title),
                        subtitle = getString(R.string.security_auth_prompt_app_open),
                        onResult = { authenticated ->
                            if (authenticated) {
                                AuthManager.markAppOpenAuthenticated()
                                isAppUnlocked.value = true
                            }
                        }
                    )
                }
            }

            val floatingNavBarVisible = remember { mutableStateOf(true) }

            LaunchedEffect(selectedTab) {
                if (selectedTab != 3) {
                    floatingNavBarVisible.value = true
                }
            }

            ShizukuExpressiveTheme {
                CompositionLocalProvider(
                    LocalFloatingNavBarVisible provides floatingNavBarVisible
                ) {
                    if (!unlocked) {
                        LockedScreen(
                            onUnlock = {
                                AuthManager.authenticate(
                                    activity = this@HomeActivity,
                                    title = getString(R.string.security_auth_prompt_title),
                                    subtitle = getString(R.string.security_auth_prompt_app_open),
                                    onResult = { authenticated ->
                                        if (authenticated) {
                                            AuthManager.markAppOpenAuthenticated()
                                            isAppUnlocked.value = true
                                        }
                                    }
                                )
                            }
                        )
                    } else {
                        BackHandler(enabled = selectedTab != 0) {
                            selectedTab = 0
                        }
                        Box(Modifier.fillMaxSize()) {
                        Scaffold(
                        contentWindowInsets = WindowInsets(0.dp)
                    ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        val pagerState = rememberPagerState(
                            initialPage = selectedTab.coerceIn(0, 3),
                            pageCount = { 4 }
                        )
                        LaunchedEffect(selectedTab, swipeBetweenTabs) {
                            if (swipeBetweenTabs && pagerState.currentPage != selectedTab) {
                                try {
                                    pagerState.animateScrollToPage(selectedTab.coerceIn(0, 3))
                                } catch (_: Exception) {
                                }
                            }
                        }
                        LaunchedEffect(
                            pagerState.currentPage,
                            pagerState.isScrollInProgress,
                            swipeBetweenTabs
                        ) {
                            if (swipeBetweenTabs && !pagerState.isScrollInProgress &&
                                selectedTab != pagerState.currentPage
                            ) {
                                selectedTab = pagerState.currentPage
                            }
                        }
                        val tabContent: @Composable (Int) -> Unit = { targetTab ->
                            // Pages 1 (Modules) and 3 (Settings hub) scroll inside
                            // ShizukuLazyScaffold, which applies directional motion blur
                            // internally; pages 0 (Home) and 2 (Comput) use raw
                            // LazyColumns, so the blur is applied here instead.
                            // This keeps exactly one blur layer per page.
                            val needsOuterBlur = targetTab == 0 || targetTab == 2
                            val verticalListState = when (targetTab) {
                                0 -> homeListState
                                else -> computListState
                            }
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .then(
                                        if (needsOuterBlur) {
                                            Modifier.scrollMotionBlur(
                                                listState = verticalListState,
                                                enabled = motionBlurEnabled,
                                                intensity = motionBlurIntensity,
                                                strength = motionBlurStrength
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                            when (targetTab) {
                                0 -> HomeScreen(
                                    serviceResource = serviceResource,
                                    serverState = serverState,
                                    grantedResource = grantedResource,
                                    localNetworkPermissionState = localNetworkPermissionState,
                                    isPrimaryUser = UserHandleCompat.myUserId() == 0,
                                    isRooted = EnvironmentUtils.isRooted(),
                                    onRefresh = {
                                        appsModel.load()
                                        homeModel.reload()
                                    },
                                    onStop = {
                                        AuthManager.executeWithAuth(
                                            activity = this@HomeActivity,
                                            action = SecuritySettings.ProtectedAction.SERVER_TOGGLE,
                                            title = getString(R.string.security_auth_prompt_title),
                                            subtitle = getString(R.string.security_auth_prompt_server)
                                        ) {
                                            if (!Shizuku.pingBinder()) {
                                                checkServerStatus()
                                                moe.shizuku.manager.service.SheveryNotificationManager.updateNotification(this@HomeActivity)
                                                Toast.makeText(this@HomeActivity, R.string.service_already_stopped, Toast.LENGTH_SHORT).show()
                                            } else {
                                                showStopDialog = true
                                            }
                                        }
                                    },
                                    onManageApps = {
                                        AuthManager.executeWithAuth(
                                            activity = this@HomeActivity,
                                            action = SecuritySettings.ProtectedAction.PERMISSIONS,
                                            title = getString(R.string.security_auth_prompt_title),
                                            subtitle = getString(R.string.security_auth_prompt_permissions)
                                        ) {
                                            manageAppsLauncher.launch(Intent(this@HomeActivity, ApplicationManagementActivity::class.java))
                                        }
                                    },
                                    onTerminal = { startActivity(Intent(this@HomeActivity, ShellTutorialActivity::class.java)) },
                                    onStartRoot = {
                                        AuthManager.executeWithAuth(
                                            activity = this@HomeActivity,
                                            action = SecuritySettings.ProtectedAction.SERVER_TOGGLE,
                                            title = getString(R.string.security_auth_prompt_title),
                                            subtitle = getString(R.string.security_auth_prompt_server)
                                        ) {
                                            startRoot()
                                        }
                                    },
                                    onStartWirelessAdb = {
                                        AuthManager.executeWithAuth(
                                            activity = this@HomeActivity,
                                            action = SecuritySettings.ProtectedAction.SERVER_TOGGLE,
                                            title = getString(R.string.security_auth_prompt_title),
                                            subtitle = getString(R.string.security_auth_prompt_server)
                                        ) {
                                            runWithLocalNetworkAccess {
                                                startWirelessAdb(
                                                    onShowDiscoveryDialog = { showAdbDiscoveryDialog = true },
                                                    onWadbNotEnabled = { showWadbNotEnabledDialog = true }
                                                )
                                            }
                                        }
                                    },
                                    onPairWirelessAdb = {
                                        runWithLocalNetworkAccess {
                                            pairWirelessAdb(
                                                onShowPairDialog = { showAdbPairDialog = true }
                                            )
                                        }
                                    },
                                    onOpenWirelessGuide = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_ANDROID11.get()) },
                                    onShowAdbCommand = { showAdbCommandDialog = true },
                                    onOpenAdbHelp = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB.get()) },
                                    onOpenAdbPermissionHelp = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_PERMISSION.get()) },
                                    onRequestLocalNetworkPermission = {
                                        requestLocalNetworkPermission { permissionRefreshTick.intValue++ }
                                    },
                                    onOpenAutomationSettings = {
                                        settingsTargetSection = SettingsSection.AUTOMATION
                                        selectedTab = 3
                                    },
                                    onOpenModules = { selectedTab = 1 },
                                    onOpenComput = { selectedTab = 2 },
                                    onOpenDeviceOwner = {
                                        AuthManager.executeWithAuth(
                                            activity = this@HomeActivity,
                                            action = SecuritySettings.ProtectedAction.DEVICE_OWNER,
                                            title = getString(R.string.security_auth_prompt_title),
                                            subtitle = getString(R.string.security_auth_prompt_device_owner)
                                        ) {
                                            settingsTargetSection = SettingsSection.DEVICE_OWNER
                                            selectedTab = 3
                                        }
                                    },
                                    onOpenCompatStubs = {
                                        settingsTargetNav = moe.shizuku.manager.settings.SettingsNav.CompatStubs
                                        selectedTab = 3
                                    },
                                    listState = homeListState
                                )
                                1 -> moe.shizuku.manager.module.ModulesScreen(onOpenWebUi = {
                                    startActivity(
                                        Intent(this@HomeActivity, moe.shizuku.manager.module.ModuleWebViewActivity::class.java)
                                            .putExtra(moe.shizuku.manager.module.ModuleWebViewActivity.EXTRA_MODULE_ID, it)
                                    )
                                },
                                    listState = modulesListState,
                                    modulesState = cachedModules,
                                    onOpenModuleSettings = {
                                        settingsTargetSection = SettingsSection.MODULES
                                        selectedTab = 3
                                    }
                                )
                                2 -> moe.shizuku.manager.logs.ComputScreen(listState = computListState)
                                3 -> moe.shizuku.manager.settings.SettingsScreen(
                                    listState = settingsListState,
                                    targetSection = settingsTargetSection,
                                    onTargetSectionConsumed = { settingsTargetSection = null },
                                    targetNav = settingsTargetNav,
                                    onTargetNavConsumed = { settingsTargetNav = null }
                                )
                            }
                                }
                        }
                        if (swipeBetweenTabs) {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = true,
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize()
                                    .scrollMotionBlur(
                                        pagerState = pagerState,
                                        enabled = motionBlurEnabled && swipeBetweenTabs,
                                        intensity = motionBlurIntensity,
                                        strength = motionBlurStrength
                                    )
                            ) { page ->
                                val pageOffset = pagerState.currentPage - page +
                                    pagerState.currentPageOffsetFraction
                                val horizontalBlur = pagerOffsetBlur(
                                    enabled = motionBlurEnabled && swipeBetweenTabs,
                                    offsetFraction = pageOffset,
                                    intensity = motionBlurIntensity,
                                    strength = motionBlurStrength
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .then(if (horizontalBlur > 0.dp) Modifier.blur(horizontalBlur) else Modifier)
                                ) {
                                    CompositionLocalProvider(
                                        LocalShakeGuideActive provides (page == pagerState.currentPage)
                                    ) {
                                        tabContent(page)
                                    }
                                }
                            }
                        } else {
                            val motion = MaterialTheme.motionScheme
                            androidx.compose.animation.AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = motion.defaultEffectsSpec()) togetherWith fadeOut(animationSpec = motion.defaultEffectsSpec())
                                },
                                label = "tab_transition"
                            ) { targetTab ->
                                CompositionLocalProvider(
                                    LocalShakeGuideActive provides (targetTab == selectedTab)
                                ) {
                                    tabContent(targetTab)
                                }
                            }
                        }
                    }
                }
                val navItems = listOf(
                    NavItem(stringResource(R.string.app_name), R.drawable.ic_symbol_home_24),
                    NavItem(stringResource(R.string.modules_title), R.drawable.ic_symbol_apps_24),
                    NavItem(stringResource(R.string.comput_title), R.drawable.ic_terminal_24),
                    NavItem(stringResource(R.string.settings_title), R.drawable.ic_settings_outline_24dp)
                )

                if (useClassicNav) {
                    RefreshedNavigationBar(
                        items = navItems,
                        selectedIndex = selectedTab,
                        onItemSelected = { selectedTab = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    ExpressiveFloatingNavigationBar(
                        items = navItems,
                        selectedIndex = selectedTab,
                        onItemSelected = { selectedTab = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                if (showTcpPromptDialog && !ShizukuSettings.isTcpMode()) {
                    val parsedPromptPort = tcpPromptPortInput.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                    AlertDialog(
                        onDismissRequest = {
                            hasPromptedTcpDialogThisSession = true
                            if (doNotRemindChecked) {
                                ShizukuSettings.setTcpModePromptSuppressed(true)
                            }
                            showTcpPromptDialog = false
                        },
                        title = {
                            DialogTitleText(
                                text = stringResource(R.string.tcp_prompt_dialog_title)
                            )
                        },
                        text = {
                            ShakeGuideHost(GuideTopic.WIRELESS_DEBUGGING)
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = stringResource(R.string.tcp_prompt_dialog_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = tcpPromptPortInput,
                                    onValueChange = { value ->
                                        tcpPromptPortInput = value.filter { it.isDigit() }.take(5)
                                    },
                                    label = { Text(stringResource(R.string.dialog_adb_port)) },
                                    supportingText = if (parsedPromptPort == null) {
                                        { Text(stringResource(R.string.dialog_adb_invalid_port)) }
                                    } else {
                                        null
                                    },
                                    isError = parsedPromptPort == null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                tcpPromptPortInput = ShizukuSettings.randomTcpPort().toString()
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_server_restart),
                                                contentDescription = stringResource(R.string.settings_tcp_port_randomize_action)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { doNotRemindChecked = !doNotRemindChecked }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = doNotRemindChecked,
                                        onCheckedChange = { doNotRemindChecked = it }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.tcp_prompt_dialog_do_not_remind),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = parsedPromptPort != null,
                                onClick = {
                                    val chosenPort = parsedPromptPort ?: return@Button
                                    hasPromptedTcpDialogThisSession = true
                                    if (doNotRemindChecked) {
                                        ShizukuSettings.setTcpModePromptSuppressed(true)
                                        ShizukuSettings.setTcpPortRandomized(false)
                                    } else {
                                        ShizukuSettings.setTcpPortRandomized(true)
                                    }
                                    ShizukuSettings.setTcpModePort(chosenPort)
                                    showTcpPromptDialog = false
                                    ShizukuSettings.setTcpMode(true)

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val isAlreadyOnTarget = EnvironmentUtils.isAdbPortLive(chosenPort)
                                        if (isAlreadyOnTarget) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@HomeActivity, R.string.settings_tcp_mode, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val port = EnvironmentUtils.getActiveAdbPort().takeIf { it > 0 }
                                            if (port != null) {
                                                withContext(Dispatchers.Main) {
                                                    moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
                                                    startActivity(
                                                        Intent(this@HomeActivity, StarterActivity::class.java).apply {
                                                            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                                                            putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                                                            putExtra(StarterActivity.EXTRA_PORT, port)
                                                        }
                                                    )
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@HomeActivity, R.string.dialog_wireless_adb_not_enabled, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.tcp_prompt_dialog_enable))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    hasPromptedTcpDialogThisSession = true
                                    if (doNotRemindChecked) {
                                        ShizukuSettings.setTcpModePromptSuppressed(true)
                                    }
                                    showTcpPromptDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.tcp_prompt_dialog_later))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }

                if (showStopDialog) {
                    AlertDialog(
                        onDismissRequest = { showStopDialog = false },
                        title = {
                            DialogTitleText(
                                text = stringResource(R.string.action_stop)
                            )
                        },
                        text = {
                            ShakeGuideHost(GuideTopic.STARTUP)
                            Text(
                                text = stringResource(R.string.dialog_stop_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showStopDialog = false
                                    lifecycleScope.launch {
                                        val result = moe.shizuku.manager.service.WatchdogManager.stopServerAndWait(
                                            this@HomeActivity,
                                            userInitiated = true
                                        )
                                        checkServerStatus()
                                        appsModel.load(onlyCount = true)
                                        moe.shizuku.manager.service.SheveryNotificationManager.updateNotification(this@HomeActivity)

                                        if (result.stopped) {
                                            Toast.makeText(this@HomeActivity, R.string.service_stop_success, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val reason = result.error ?: getString(R.string.service_stop_still_running)
                                            Toast.makeText(
                                                this@HomeActivity,
                                                getString(R.string.service_stop_failed, reason),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStopDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }

                if (showAdbCommandDialog) {
                    AlertDialog(
                        onDismissRequest = { showAdbCommandDialog = false },
                        title = {
                            DialogTitleText(
                                text = stringResource(R.string.home_adb_button_view_command)
                            )
                        },
                        text = {
                            ShakeGuideHost(GuideTopic.STARTUP)
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MonospaceLog(text = Starter.adbCommand)
                                Text(
                                    text = stringResource(R.string.home_adb_dialog_view_command_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        var intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                                        }
                                        intent = Intent.createChooser(
                                            intent,
                                            getString(R.string.home_adb_dialog_view_command_button_send)
                                        )
                                        startActivity(intent)
                                    }
                                ) {
                                    Text(stringResource(R.string.home_adb_dialog_view_command_button_send))
                                }
                                Button(
                                    onClick = {
                                        if (ClipboardUtils.put(this@HomeActivity, Starter.adbCommand)) {
                                            Toast.makeText(
                                                this@HomeActivity,
                                                getString(R.string.toast_copied_to_clipboard, Starter.adbCommand),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        showAdbCommandDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.home_adb_dialog_view_command_copy_button))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAdbCommandDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }

                if (showWadbNotEnabledDialog) {
                    AlertDialog(
                        onDismissRequest = { showWadbNotEnabledDialog = false },
                        text = {
                            ShakeGuideHost(GuideTopic.WIRELESS_DEBUGGING)
                            Text(
                                text = stringResource(R.string.dialog_wireless_adb_not_enabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showWadbNotEnabledDialog = false
                                    val intent = Intent(this@HomeActivity, StarterActivity::class.java).apply {
                                        putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                                        putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                                        putExtra(StarterActivity.EXTRA_PORT, AdbStarter.TCP_MODE_PORT)
                                    }
                                    startActivity(intent)
                                }
                            ) {
                                Text(stringResource(R.string.home_quick_tcp))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWadbNotEnabledDialog = false }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (showAdbDiscoveryDialog) {
                        AdbDiscoveryDialog(
                            onDismissRequest = { showAdbDiscoveryDialog = false },
                            onStartService = { port ->
                                showAdbDiscoveryDialog = false
                                val intent = Intent(this@HomeActivity, StarterActivity::class.java).apply {
                                    putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                                    putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                                    putExtra(StarterActivity.EXTRA_PORT, port)
                                }
                                startActivity(intent)
                            }
                        )
                    }

                    if (showAdbPairDialog) {
                        val inPairingWindow = (display?.displayId ?: -1) > 0 || isInMultiWindowMode
                        AdbPairDialog(
                            inPairingWindow = inPairingWindow,
                            onDismissRequest = { showAdbPairDialog = false },
                            onPairSuccess = {
                                showAdbPairDialog = false
                                showAdbDiscoveryDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}
}

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        UsbDebuggingHideManager.restore(this)
        UsbDebuggingHideService.refresh(this)
        DeveloperOptionsHideManager.restore(this)
        DeveloperOptionsHideService.refresh(this)
        if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.APP_OPEN) && !AuthManager.isAppOpenSessionValid()) {
            isAppUnlocked.value = false
        }
        if (ModuleSettings.isAutoRefreshOnResume()) {
            checkServerStatus()
        }
        StubManager.notifyStubStateChanged()
        permissionRefreshTick.intValue++
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        return false
    }

    private fun startRoot() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        startActivity(
            Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, true)
            }
        )
    }

    private fun startWirelessAdb(
        onShowDiscoveryDialog: () -> Unit,
        onWadbNotEnabled: () -> Unit
    ) {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            onShowDiscoveryDialog()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val livePort = EnvironmentUtils.getActiveAdbPort().takeIf { it > 0 }
            withContext(Dispatchers.Main) {
                if (livePort != null) {
                    startActivity(
                        Intent(this@HomeActivity, StarterActivity::class.java).apply {
                            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                            putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                            putExtra(StarterActivity.EXTRA_PORT, livePort)
                        }
                    )
                } else {
                    onWadbNotEnabled()
                }
            }
        }
    }

    private fun pairWirelessAdb(onShowPairDialog: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        if ((display?.displayId ?: -1) > 0 || isInMultiWindowMode) {
            onShowPairDialog()
        } else {
            startActivity(Intent(this, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
        }
    }

    private fun runWithLocalNetworkAccess(action: () -> Unit) {
        val state = buildLocalNetworkPermissionState()
        if (!state.required || state.granted) {
            EnvironmentUtils.requestIgnoreBatteryOptimizations(this@HomeActivity)
            action()
            return
        }

        pendingLocalNetworkAction = action
        localNetworkPermissionLauncher.launch(state.missingPermissions.toTypedArray())
    }

    private fun requestLocalNetworkPermission(onGranted: () -> Unit) {
        val state = buildLocalNetworkPermissionState()
        if (!state.required || state.granted) {
            onGranted()
            return
        }

        pendingLocalNetworkAction = onGranted
        localNetworkPermissionLauncher.launch(state.missingPermissions.toTypedArray())
    }

    private fun buildLocalNetworkPermissionState(): LocalNetworkPermissionState {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= SDK_ANDROID_17) {
                add(PERMISSION_ACCESS_LOCAL_NETWORK)
                if (isPermissionDefined(PERMISSION_USE_LOOPBACK_INTERFACE)) {
                    add(PERMISSION_USE_LOOPBACK_INTERFACE)
                }
            }
            if (Build.VERSION.SDK_INT >= SDK_ANDROID_13) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return LocalNetworkPermissionState(
            permissions = permissions,
            missingPermissions = missingPermissions
        )
    }

    private fun isPermissionDefined(permission: String): Boolean {
        return try {
            packageManager.getPermissionInfo(permission, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val SDK_ANDROID_13 = 33
        private const val SDK_ANDROID_17 = 37
        private const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val PERMISSION_USE_LOOPBACK_INTERFACE = "android.permission.USE_LOOPBACK_INTERFACE"
        private var hasPromptedTcpDialogThisSession = false

        const val EXTRA_ZIP_PATH = "moe.shizuku.manager.extra.ZIP_PATH"

        /** Main-thread only: guards against re-installing the same shared ZIP after a config change. */
    }
}

private data class LocalNetworkPermissionState(
    val permissions: List<String>,
    val missingPermissions: List<String>
) {
    val required: Boolean
        get() = permissions.isNotEmpty()
    val granted: Boolean
        get() = missingPermissions.isEmpty()
    val label: String
        get() = permissions.takeIf { it.isNotEmpty() }
            ?.joinToString { it.substringAfterLast('.') }
            ?: "none"
}

private fun pagerOffsetBlur(
    enabled: Boolean,
    offsetFraction: Float,
    intensity: Int,
    strength: Int
): androidx.compose.ui.unit.Dp {
    if (!enabled) return 0.dp
    val offset = kotlin.math.abs(offsetFraction).coerceIn(0f, 1f)
    if (offset <= 0.001f) return 0.dp
    val maxRadius = (intensity.coerceIn(0, 100) / 100f) * 24f
    if (maxRadius <= 0.01f) return 0.dp
    val sensitivity = 0.5f + (strength.coerceIn(0, 100) / 100f)
    return (offset * maxRadius * sensitivity).dp
}

private data class HomeButtonSpec(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun HomeScreen(
    serviceResource: Resource<ServiceStatus>?,
    serverState: ShizukuStateMachine.State = ShizukuStateMachine.State.STOPPED,
    grantedResource: Resource<Int>?,
    localNetworkPermissionState: LocalNetworkPermissionState,
    isPrimaryUser: Boolean,
    isRooted: Boolean,
    onRefresh: () -> Job,
    onStop: () -> Unit,
    onManageApps: () -> Unit,
    onTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onPairWirelessAdb: () -> Unit,
    onOpenWirelessGuide: () -> Unit,
    onShowAdbCommand: () -> Unit,
    onOpenAdbHelp: () -> Unit,
    onOpenAdbPermissionHelp: () -> Unit,
    onRequestLocalNetworkPermission: () -> Unit,
    onOpenAutomationSettings: () -> Unit = {},
    onOpenCompatStubs: () -> Unit = {},
    onOpenModules: () -> Unit,
    onOpenComput: () -> Unit,
    onOpenDeviceOwner: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    ShakeGuideHost(GuideTopic.STARTUP)
    val context = LocalContext.current
    val status = serviceResource?.data ?: ServiceStatus()
    val grantedCount = grantedResource?.data ?: 0
    val running = serverState == ShizukuStateMachine.State.RUNNING
    val changing = serverState == ShizukuStateMachine.State.STARTING || serverState == ShizukuStateMachine.State.STOPPING
    val hasDetails = running && status.uid >= 0
    val adbPermission = status.permission
    val canUseWirelessAdb by produceState(
        initialValue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ShizukuSettings.isTcpMode(),
        key1 = running
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ShizukuSettings.isTcpMode()) {
            value = true
        } else {
            value = withContext(Dispatchers.IO) {
                EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT) ||
                    EnvironmentUtils.getLiveAdbTcpPort() > 0
            }
        }
    }
    var showSmartLogsDialog by remember { mutableStateOf(false) }

    // Experimental (Extras > Experimental): uptime itself is recorded by the
    // process-scoped [ServiceUptime] tracker (backed by the state machine, so it
    // survives tab switches); this block only polls it for display while the
    // home tab is visible. One-second ticks keep values like "45s" moving
    // instead of appearing frozen behind a 30s refresh.
    val showUptime = ModuleSettings.isServiceUptimeShown()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val homeActive = LocalShakeGuideActive.current
    val uptimeNow by produceState(
        initialValue = ServiceUptime.uptimeMs(),
        running, showUptime, homeActive, lifecycle
    ) {
        if (!showUptime || !running || !homeActive) return@produceState
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                value = ServiceUptime.uptimeMs()
                delay(1_000L)
            }
        }
    }
    val uptimeText = if (showUptime && running && ServiceUptime.sinceMs != 0L) {
        formatServiceUptime(uptimeNow)
    } else {
        null
    }
    val stubTick by StubManager.stubStateTick.collectAsState()
    var packageChangeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                if (pkg == null || pkg == StubManager.STUB_PACKAGE) {
                    packageChangeTick++
                    StubManager.notifyStubStateChanged()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            pkgReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(pkgReceiver) }
        }
    }
    val shizukuStubInstalled = remember(context, stubTick, packageChangeTick, localNetworkPermissionState, running) {
        StubManager.isInstalled(context, StubManager.StubType.SHIZUKU)
    }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    val pendingUpdate by produceState<Pair<String, String>?>(null, homeActive) {
        if (homeActive) {
            val version = ModuleSettings.getPendingUpdateVersion()
            val url = ModuleSettings.getPendingUpdateUrl()
            value = when {
                version.isNullOrEmpty() || url.isNullOrEmpty() -> null
                SheveryUpdateChecker.isTagNewerThanInstalled(version) -> version to url
                else -> {
                    ModuleSettings.clearPendingUpdate()
                    null
                }
            }
        }
    }
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            PullToRefreshBox(
                modifier = Modifier.widthIn(max = 880.dp).fillMaxSize(),
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) scope.launch {
                        isRefreshing = true
                        try {
                            onRefresh().join()
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                state = pullToRefreshState,
                indicator = {}
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp + topInset, end = 16.dp, bottom = 80.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "service") {
                        HomeServicePanel(
                            state = serverState,
                            checking = serviceResource == null && serverState == ShizukuStateMachine.State.STOPPED,
                            readFailed = serviceResource?.status == Status.ERROR,
                            status = status,
                            launchMode = when {
                                status.uid == 0 -> R.string.home_launch_root
                                hasDetails && StarterActivity.isWirelessSession(Shizuku.getBinder()) -> R.string.home_quick_wireless
                                else -> R.string.home_launch_adb
                            },
                            onRetry = { onRefresh() },
                            isPrimaryUser = isPrimaryUser,
                            isRooted = isRooted,
                            canUseWirelessAdb = canUseWirelessAdb,
                            onStartRoot = onStartRoot,
                            onStartWirelessAdb = onStartWirelessAdb,
                            onShowAdbCommand = onShowAdbCommand,
                            onStop = onStop,
                            uptime = uptimeText
                        )
                    }

                    pendingUpdate?.let { (version, url) ->
                        item(key = "available-update") {
                            var showAppUpdateInstall by rememberSaveable { mutableStateOf(false) }
                            if (showAppUpdateInstall) {
                                SheveryAppUpdateDialog(
                                    result = SheveryAppUpdateResult(
                                        hasUpdate = true,
                                        currentVersion = BuildConfig.VERSION_NAME,
                                        latestVersion = version,
                                        releaseTitle = null,
                                        releaseNotes = null,
                                        downloadUrl = url,
                                        htmlUrl = null,
                                        isPreRelease = false,
                                        publishedAt = null,
                                        error = null
                                    ),
                                    onDismiss = { showAppUpdateInstall = false },
                                )
                            }
                            HomeCard(
                                icon = R.drawable.ic_outline_info_24,
                                title = stringResource(R.string.home_update_available_title, version),
                                body = stringResource(R.string.home_update_available_body),
                                onClick = { showAppUpdateInstall = true },
                            )
                        }
                    }

                    item(key = "actions") {
                        HomeActionGroups(
                            isPrimaryUser = isPrimaryUser,
                            canStartWireless = canUseWirelessAdb && !changing,
                            grantedCount = grantedResource?.takeIf { it.status == Status.SUCCESS }?.data,
                            canManageApps = hasDetails && adbPermission,
                            onManageApps = onManageApps,
                            onOpenModules = onOpenModules,
                            onOpenComput = onOpenComput,
                            onOpenDeviceOwner = onOpenDeviceOwner,
                            onTerminal = onTerminal,
                            terminalEnabled = running,
                            onStartWirelessAdb = onStartWirelessAdb,
                            onPairWirelessAdb = onPairWirelessAdb,
                            onOpenWirelessGuide = onOpenWirelessGuide
                        )
                    }

                    if (!shizukuStubInstalled) {
                        item {
                            HomeCard(
                                icon = R.drawable.ic_warning_24,
                                title = stringResource(R.string.home_shizuku_deprecated_banner_title),
                                body = stringResource(R.string.home_shizuku_deprecated_banner_body)
                            ) {
                                HomeButtons(
                                    listOf(
                                        HomeButtonSpec(
                                            label = R.string.home_shizuku_deprecated_banner_button,
                                            icon = R.drawable.ic_system_icon,
                                            primary = true,
                                            onClick = onOpenCompatStubs
                                        )
                                    )
                                )
                            }
                        }
                    }

                    if (hasDetails && !adbPermission) {
                        item {
                            HomeCard(
                                icon = R.drawable.ic_warning_24,
                                title = stringResource(R.string.home_adb_is_limited_title),
                                body = stringResource(R.string.home_adb_is_limited_description)
                            ) {
                                HomeButtons(
                                    listOf(
                                        HomeButtonSpec(
                                            label = R.string.home_adb_button_view_help,
                                            icon = R.drawable.ic_help_outline_24dp,
                                            primary = true,
                                            onClick = onOpenAdbPermissionHelp
                                        )
                                    )
                                )
                            }
                        }
                    }

                    if (localNetworkPermissionState.required && !localNetworkPermissionState.granted) {
                        item {
                            LocalNetworkPermissionCard(
                                localNetworkPermissionState = localNetworkPermissionState,
                                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission
                            )
                        }
                    }

                    if (isPrimaryUser) {
                        item {
                            AdbCommandCard(
                                onShowAdbCommand = onShowAdbCommand,
                                onOpenAdbHelp = onOpenAdbHelp
                            )
                        }
                    }

                    item {
                        AutomationCard(
                            onViewIntents = { showAutomationSheet = true }
                        )
                    }

                    item(key = "smart-logs") {
                        SmartLogsCard(
                            onOpenLogs = { showSmartLogsDialog = true },
                            onOpenModules = onOpenModules
                        )
                    }
                }
            }
            val indicatorModifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp + topInset)
            if (LocalReducedMotion.current) {
                PullToRefreshDefaults.Indicator(pullToRefreshState, isRefreshing, indicatorModifier)
            } else {
                PullToRefreshDefaults.LoadingIndicator(pullToRefreshState, isRefreshing, indicatorModifier)
            }

            if (showAutomationSheet) {
                AutomationBottomSheet(
                    onDismiss = { showAutomationSheet = false },
                    onOpenAutomationSettings = {
                        showAutomationSheet = false
                        onOpenAutomationSettings()
                    }
                )
            }

            if (showSmartLogsDialog) {
                SheverySmartLogsDialog(
                    onDismiss = { showSmartLogsDialog = false }
                )
            }
        }
    }
}

@Composable
private fun HomeActionGroups(
    isPrimaryUser: Boolean,
    canStartWireless: Boolean,
    grantedCount: Int?,
    canManageApps: Boolean,
    onManageApps: () -> Unit,
    onOpenModules: () -> Unit,
    onOpenComput: () -> Unit,
    onOpenDeviceOwner: () -> Unit,
    onTerminal: () -> Unit,
    terminalEnabled: Boolean,
    onStartWirelessAdb: () -> Unit,
    onPairWirelessAdb: () -> Unit,
    onOpenWirelessGuide: () -> Unit
) {
    val connection: @Composable () -> Unit = {
        val pairingAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val count = if (pairingAvailable) 3 else 2
        ExpressiveSegmentedSection(stringResource(R.string.home_connection_actions)) {
            ExpressiveSegmentedItem(0, count, R.drawable.ic_wadb_24,
                stringResource(R.string.home_wireless_adb_title), enabled = canStartWireless,
                onClick = onStartWirelessAdb)
            if (pairingAvailable) {
                ExpressiveSegmentedItem(1, count, R.drawable.ic_adb_24dp, stringResource(R.string.adb_pairing),
                    onClick = onPairWirelessAdb)
            }
            ExpressiveSegmentedItem(count - 1, count, R.drawable.ic_help_outline_24dp,
                stringResource(R.string.home_wireless_adb_view_guide_button), onClick = onOpenWirelessGuide)
        }
    }
    val tools: @Composable () -> Unit = {
        ExpressiveSegmentedSection(stringResource(R.string.home_tool_actions)) {
            val apps: @Composable (Modifier, RoundedCornerShape) -> Unit = { modifier, shape ->
                HomeToolTile(
                    icon = R.drawable.ic_system_icon,
                    label = stringResource(R.string.home_manage_apps_button),
                    value = if (canManageApps && grantedCount != null)
                        stringResource(R.string.home_granted_count, grantedCount) else null,
                    enabled = canManageApps, onClick = onManageApps, modifier = modifier, shape = shape
                )
            }
            val terminal: @Composable (Modifier, RoundedCornerShape) -> Unit = { modifier, shape ->
                HomeToolTile(R.drawable.ic_terminal_24, stringResource(R.string.home_quick_terminal),
                    enabled = terminalEnabled, onClick = onTerminal, modifier = modifier, shape = shape)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 296.dp && LocalDensity.current.fontScale <= 1.35f) {
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        apps(Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(24.dp, 4.dp, 4.dp, 4.dp))
                        terminal(Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(4.dp, 24.dp, 4.dp, 4.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        apps(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp, 24.dp, 4.dp, 4.dp))
                        terminal(Modifier.fillMaxWidth(), RoundedCornerShape(4.dp))
                    }
                }
            }
            ExpressiveSegmentedItem(1, 4, R.drawable.ic_adb_24dp, stringResource(R.string.modules_title), onClick = onOpenModules)
            ExpressiveSegmentedItem(2, 4, R.drawable.ic_code_24dp, stringResource(R.string.comput_title), onClick = onOpenComput)
            ExpressiveSegmentedItem(3, 4, R.drawable.ic_device_owner_24dp,
                stringResource(R.string.settings_device_owner_title), onClick = onOpenDeviceOwner)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp && isPrimaryUser) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { connection() }
                Box(Modifier.weight(1f)) { tools() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPrimaryUser && !terminalEnabled) connection()
                tools()
                if (isPrimaryUser && terminalEnabled) connection()
            }
        }
    }
}

@Composable
private fun HomeToolTile(
    @DrawableRes icon: Int,
    label: String,
    value: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    shape: RoundedCornerShape
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        color = if (enabled) colors.secondaryContainer else colors.surfaceContainer,
        contentColor = if (enabled) colors.onSecondaryContainer else colors.onSurface.copy(alpha = 0.38f)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShizukuIcon(icon, modifier = Modifier.size(24.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (value != null) Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AdbCommandCard(
    onShowAdbCommand: () -> Unit,
    onOpenAdbHelp: () -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_adb_24dp,
        title = HtmlText(R.string.home_adb_title),
        body = HtmlText(R.string.home_adb_description, Helps.ADB.get())
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_adb_button_view_command,
                    icon = R.drawable.ic_code_24dp,
                    primary = true,
                    onClick = onShowAdbCommand
                ),
                HomeButtonSpec(
                    label = R.string.home_adb_button_view_help,
                    icon = R.drawable.ic_help_outline_24dp,
                    onClick = onOpenAdbHelp
                )
            )
        )
    }
}

@Composable
private fun LocalNetworkPermissionCard(
    localNetworkPermissionState: LocalNetworkPermissionState,
    onRequestLocalNetworkPermission: () -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_warning_24,
        title = stringResource(R.string.home_local_network_title),
        body = stringResource(
            R.string.home_local_network_description,
            localNetworkPermissionState.label
        )
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_local_network_grant,
                    icon = R.drawable.ic_settings_outline_24dp,
                    primary = true,
                    onClick = onRequestLocalNetworkPermission
                )
            )
        )
    }
}

@Composable
private fun SmartLogsCard(
    onOpenLogs: () -> Unit,
    onOpenModules: () -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_terminal_24,
        title = stringResource(R.string.home_diagnostics_smart_logs_title),
        body = stringResource(R.string.home_diagnostics_smart_logs_subtitle),
        onClick = onOpenLogs
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                ShizukuIcon(
                    icon = R.drawable.ic_warning_24,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_shizuconfig_notice_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_shizuconfig_notice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_smart_logs_open,
                    icon = R.drawable.ic_terminal_24,
                    primary = true,
                    onClick = onOpenLogs
                ),
                HomeButtonSpec(
                    label = R.string.modules_title,
                    icon = R.drawable.ic_adb_24dp,
                    primary = false,
                    onClick = onOpenModules
                )
            )
        )
    }
}

@Composable
private fun AutomationCard(
    onViewIntents: () -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_outline_play_arrow_24,
        title = stringResource(R.string.home_automation_title),
        body = stringResource(R.string.home_automation_description)
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_automation_button_view_intents,
                    icon = R.drawable.ic_code_24dp,
                    primary = true,
                    onClick = onViewIntents
                )
            )
        )
    }
}

@Composable
private fun AutomationBottomSheet(
    onDismiss: () -> Unit,
    onOpenAutomationSettings: () -> Unit
) {
    val context = LocalContext.current
    var selectedCommand by remember { mutableStateOf(Command.START) }
    var authToken by remember { mutableStateOf(ShizukuSettings.getAuthToken()) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    val action = when (selectedCommand) {
        Command.START -> PluginContract.ACTION_DIRECT_START
        Command.STOP -> PluginContract.ACTION_DIRECT_STOP
        Command.RESTART -> PluginContract.ACTION_DIRECT_RESTART
        Command.TOGGLE -> PluginContract.ACTION_DIRECT_TOGGLE
    }

    val shellCommand = "am broadcast -a $action -p ${context.packageName} -e auth $authToken"
    val isConnectorEnabled = ModuleSettings.isConnectorEnabled()

    fun copy(text: String) {
        ClipboardUtils.put(context, text)
        Toast.makeText(context, R.string.automation_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ShakeGuideHost(GuideTopic.INTENTS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.home_automation_bottom_sheet_intents),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.home_automation_bottom_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isConnectorEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.home_automation_connectors_disabled_status),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_automation_connectors_disabled_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onOpenAutomationSettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ShizukuIcon(
                                icon = R.drawable.ic_settings_outline_24dp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.home_automation_open_settings))
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.home_automation_connectors_active),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.home_automation_connectors_active_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !EnvironmentUtils.isTelevision() && !EnvironmentUtils.isRooted()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.home_automation_device_restriction, "adb tcpip 5555"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Command.START to stringResource(R.string.automation_action_start),
                    Command.STOP to stringResource(R.string.automation_action_stop),
                    Command.RESTART to stringResource(R.string.automation_action_restart),
                    Command.TOGGLE to stringResource(R.string.automation_action_toggle)
                ).forEach { (cmd, label) ->
                    FilterChip(
                        selected = selectedCommand == cmd,
                        onClick = { selectedCommand = cmd },
                        label = { Text(label) }
                    )
                }
            }

            AutomationFieldRow(
                label = stringResource(R.string.home_automation_label_action),
                value = action,
                onCopy = { copy(action) }
            )

            AutomationFieldRow(
                label = stringResource(R.string.home_automation_label_package),
                value = context.packageName,
                onCopy = { copy(context.packageName) }
            )

            AutomationFieldRow(
                label = stringResource(R.string.home_automation_label_target),
                value = "${context.packageName}/${PluginContract.MANAGER_PACKAGE}.tasker.PluginReceiver",
                onCopy = { copy("${context.packageName}/com.hamondev.shevery.tasker.PluginReceiver") }
            )

            AutomationFieldRow(
                label = stringResource(R.string.home_automation_label_extras),
                value = "auth: $authToken",
                onCopy = { copy(authToken) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showRegenerateDialog = true }) {
                            ShizukuIcon(
                                icon = R.drawable.ic_server_restart,
                                contentDescription = stringResource(R.string.home_automation_regenerate_token),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { copy(authToken) }) {
                            ShizukuIcon(
                                icon = R.drawable.ic_content_copy_24,
                                contentDescription = stringResource(R.string.automation_copied_to_clipboard),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            AutomationFieldRow(
                label = stringResource(R.string.home_automation_label_shell),
                value = shellCommand,
                onCopy = { copy(shellCommand) }
            )

            FilledTonalButton(
                onClick = onOpenAutomationSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                ShizukuIcon(
                    icon = R.drawable.ic_settings_outline_24dp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_automation_open_settings))
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text(stringResource(R.string.home_automation_regenerate_token)) },
            text = { Text(stringResource(R.string.home_automation_regenerate_token_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newToken = ShizukuSettings.generateAuthToken()
                        authToken = newToken
                        showRegenerateDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AutomationFieldRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                IconButton(onClick = onCopy) {
                    ShizukuIcon(
                        icon = R.drawable.ic_content_copy_24,
                        contentDescription = stringResource(R.string.automation_copied_to_clipboard),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleActionCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    HomeCard(
        icon = icon,
        title = title,
        body = body,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun HomeCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .alpha(if (enabled) 1f else 0.56f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShizukuIcon(icon, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun HomeButtons(buttons: List<HomeButtonSpec>) {
    if (buttons.isEmpty()) return

    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEach { button ->
            if (button.primary) {
                Button(
                    enabled = button.enabled,
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            } else if (button.enabled) {
                FilledTonalButton(
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            } else {
                OutlinedButton(
                    enabled = false,
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            }
        }
    }
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int) {
    ShizukuIcon(
        icon = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(18.dp)
    )
}

private fun formatServiceUptime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}


@Composable
private fun DialogTitleText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_security_24dp),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.security_locked_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.security_locked_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.security_unlock_button))
            }
        }
    }
}
