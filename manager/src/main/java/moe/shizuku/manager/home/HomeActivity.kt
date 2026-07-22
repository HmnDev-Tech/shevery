@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.about.AboutActivity
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.module.AdbModuleManager

import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.model.ServiceStatus

import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.HtmlText
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import rikka.html.text.HtmlCompat as RikkaHtmlCompat
import moe.shizuku.manager.module.ModuleSettings
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

    private var pendingLocalNetworkAction: (() -> Unit)? = null

    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRefreshTick.intValue++
        val action = pendingLocalNetworkAction
        pendingLocalNetworkAction = null
        if (buildLocalNetworkPermissionState().granted) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val serviceResource by homeModel.serviceStatus.observeAsState()
            val grantedResource by appsModel.grantedCount.observeAsState()
            val localNetworkPermissionState = remember(permissionRefreshTick.intValue) {
                buildLocalNetworkPermissionState()
            }

            LaunchedEffect(serviceResource?.status, serviceResource?.data?.uid) {
                val status = serviceResource?.data ?: return@LaunchedEffect
                if (serviceResource?.status == Status.SUCCESS && status.isRunning) {
                    val currentMode = ShizukuSettings.getLastLaunchMode()
                    if (currentMode != ShizukuSettings.LaunchMethod.DHIZUKU) {
                        ShizukuSettings.setLastLaunchMode(
                            if (status.uid == 0) {
                                ShizukuSettings.LaunchMethod.ROOT
                            } else {
                                ShizukuSettings.LaunchMethod.ADB
                            }
                        )
                    }
                    try {
                        AdbModuleManager.runEnabledServicesIfAllowed(applicationContext)
                    } catch (_: Throwable) {
                    }
                }
            }

            var selectedTab by remember { mutableIntStateOf(0) }

            ShizukuExpressiveTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { ShizukuIcon(R.drawable.ic_server_ok_24dp, contentDescription = null) },
                                label = { Text(stringResource(R.string.app_name)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { ShizukuIcon(R.drawable.ic_system_icon, contentDescription = null) },
                                label = { Text(stringResource(R.string.modules_title)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { ShizukuIcon(R.drawable.ic_terminal_24, contentDescription = null) },
                                label = { Text("Comput") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { ShizukuIcon(R.drawable.ic_action_settings_24dp, contentDescription = null) },
                                label = { Text(stringResource(R.string.settings_title)) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            },
                            label = "tab_transition"
                        ) { targetTab ->
                            when (targetTab) {
                                0 -> HomeScreen(
                                    serviceResource = serviceResource,
                                    grantedResource = grantedResource,
                                    localNetworkPermissionState = localNetworkPermissionState,
                                    isPrimaryUser = UserHandleCompat.myUserId() == 0,
                                    isRooted = EnvironmentUtils.isRooted(),
                                    onRefresh = {
                                        checkServerStatus()
                                        appsModel.load()
                                    },
                                    onAbout = ::showAboutDialog,
                                    onStop = ::showStopDialog,
                                    onManageApps = { manageAppsLauncher.launch(Intent(this@HomeActivity, ApplicationManagementActivity::class.java)) },
                                    onTerminal = { startActivity(Intent(this@HomeActivity, ShellTutorialActivity::class.java)) },
                                    onStartRoot = ::startRoot,
                                    onStartWirelessAdb = { runWithLocalNetworkAccess(::startWirelessAdb) },
                                    onPairWirelessAdb = { runWithLocalNetworkAccess(::pairWirelessAdb) },
                                    onOpenWirelessGuide = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_ANDROID11.get()) },
                                    onShowAdbCommand = ::showAdbCommandDialog,
                                    onOpenAdbHelp = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB.get()) },
                                    onOpenAdbPermissionHelp = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_PERMISSION.get()) },
                                    onLearnMore = { CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.HOME.get()) },
                                    onCopyDiagnostics = { copyDiagnostics(it) },
                                    onRequestLocalNetworkPermission = {
                                        requestLocalNetworkPermission { permissionRefreshTick.intValue++ }
                                    },
                                    onStartDhizuku = { startDhizukuMode() },
                                    dhizukuEnabled = ModuleSettings.isDhizukuEnabled(),
                                    onStartTcp5555 = { runWithLocalNetworkAccess(::bindTcp5555) }
                                )
                                1 -> moe.shizuku.manager.module.ModulesScreen(onOpenWebUi = {
                                    startActivity(
                                        Intent(this@HomeActivity, moe.shizuku.manager.module.ModuleWebViewActivity::class.java)
                                            .putExtra(moe.shizuku.manager.module.ModuleWebViewActivity.EXTRA_MODULE_ID, it)
                                    )
                                })
                                2 -> moe.shizuku.manager.logs.ComputScreen()
                                3 -> moe.shizuku.manager.settings.SettingsScreen()
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
        checkServerStatus()
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

    private fun showAboutDialog() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private fun showStopDialog() {
        if (!Shizuku.pingBinder()) return

        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_stop_message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                moe.shizuku.manager.service.WatchdogManager.stopServer(userInitiated = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startRoot() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
        startActivity(
            Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, true)
            }
        )
    }

    private fun startWirelessAdb() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AdbDialogFragment().show(supportFragmentManager, "adb")
            return
        }

        WadbNotEnabledDialogFragment().show(supportFragmentManager, "wadb_not_enabled")
    }

    private fun pairWirelessAdb() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        if ((display?.displayId ?: -1) > 0) {
            AdbPairDialogFragment().show(supportFragmentManager, "adb_pair")
        } else {
            startActivity(Intent(this, moe.shizuku.manager.adb.AdbPairingTutorialActivity::class.java))
        }
    }

    private fun showAdbCommandDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_adb_button_view_command)
            .setMessage(
                RikkaHtmlCompat.fromHtml(
                    getString(
                        R.string.home_adb_dialog_view_command_message,
                        Starter.adbCommand
                    )
                )
            )
            .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                if (ClipboardUtils.put(this, Starter.adbCommand)) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_copied_to_clipboard, Starter.adbCommand),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.home_adb_dialog_view_command_button_send) { _, _ ->
                var intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                intent = Intent.createChooser(
                    intent,
                    getString(R.string.home_adb_dialog_view_command_button_send)
                )
                startActivity(intent)
            }
            .show()
    }

    private fun runWithLocalNetworkAccess(action: () -> Unit) {
        val state = buildLocalNetworkPermissionState()
        if (!state.required || state.granted) {
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

    private fun copyDiagnostics(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.home_diagnostics_title), text))
        Toast.makeText(this, R.string.home_diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
    private fun startDhizukuMode() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
        startActivity(
            Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_IS_DHIZUKU, true)
            }
        )
    }

    private fun bindTcp5555() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var failureReason = getString(R.string.settings_tcp_5555_bind_failed_generic)

            fun recordBindFailure(route: String, reason: String, throwable: Throwable? = null) {
                val message = "$route: $reason"
                failureReason = message
                android.util.Log.d("Shevery", "Failed to bind TCP 5555 via $message", throwable)
            }

            // 1. Try via Dhizuku if enabled
            if (ModuleSettings.isDhizukuEnabled()) {
                try {
                    val initResult = com.rosan.dhizuku.api.Dhizuku.init(applicationContext)
                    if (initResult && com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                        val userServiceArgs = com.rosan.dhizuku.api.DhizukuUserServiceArgs(
                            android.content.ComponentName(applicationContext, moe.shizuku.manager.dhizuku.DhizukuService::class.java)
                        )
                        var connection: android.content.ServiceConnection? = null
                        val serviceResult = withTimeoutOrNull(5000) {
                            suspendCancellableCoroutine<android.os.IBinder?> { cont ->
                                val conn = object : android.content.ServiceConnection {
                                    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                                        if (cont.isActive) cont.resumeWith(Result.success(service))
                                    }
                                    override fun onServiceDisconnected(name: android.content.ComponentName?) {}
                                }
                                connection = conn
                                val bound = com.rosan.dhizuku.api.Dhizuku.bindUserService(userServiceArgs, conn)
                                if (!bound && cont.isActive) {
                                    cont.resumeWith(Result.success(null))
                                }
                            }
                        }
                        if (serviceResult != null) {
                            val dhizukuService = moe.shizuku.manager.dhizuku.IDhizukuService.Stub.asInterface(serviceResult)
                            success = dhizukuService.bindAdbTcp(5555) && waitForAdbTcpPort(5555)
                            if (!success) {
                                recordBindFailure("Dhizuku", "command finished but port 5555 did not become live")
                            }
                            connection?.let {
                                try { com.rosan.dhizuku.api.Dhizuku.unbindUserService(it) } catch (_: Exception) {}
                            }
                        } else {
                            recordBindFailure("Dhizuku", "service binding failed or timed out")
                        }
                    } else if (!initResult) {
                        recordBindFailure("Dhizuku", "initialization failed")
                    } else {
                        recordBindFailure("Dhizuku", "permission is not granted")
                    }
                } catch (e: Exception) {
                    recordBindFailure("Dhizuku", e.message ?: e.javaClass.simpleName, e)
                }
            }

            // 2. Try via Root if not success
            if (!success && EnvironmentUtils.isRooted()) {
                try {
                    val result = com.topjohnwu.superuser.Shell.cmd(ADB_TCP_BIND_COMMAND).exec()
                    success = result.isSuccess && waitForAdbTcpPort(5555)
                    if (!success) {
                        recordBindFailure(
                            "root",
                            "command exit code ${result.code}, port 5555 live: ${EnvironmentUtils.isAdbPortLive(5555)}"
                        )
                    }
                } catch (e: Exception) {
                    recordBindFailure("root", e.message ?: e.javaClass.simpleName, e)
                }
            } else if (!success) {
                recordBindFailure("root", "root shell is unavailable")
            }

            // 3. Try via Shizuku shell if running
            if (!success && Shizuku.pingBinder()) {
                try {
                    val binder = Shizuku.getBinder()
                    if (binder != null) {
                        val service = moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
                        val process = service.newProcess(arrayOf("sh", "-c", ADB_TCP_BIND_COMMAND), null, null)
                        val exitCode = process.waitFor()
                        success = exitCode == 0 && waitForAdbTcpPort(5555)
                        if (!success) {
                            recordBindFailure(
                                "Shevery",
                                "command exit code $exitCode, port 5555 live: ${EnvironmentUtils.isAdbPortLive(5555)}"
                            )
                        }
                    } else {
                        recordBindFailure("Shevery", "binder was null")
                    }
                } catch (e: Exception) {
                    recordBindFailure("Shevery", e.message ?: e.javaClass.simpleName, e)
                }
            } else if (!success) {
                recordBindFailure("Shevery", "binder is not active")
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@HomeActivity, R.string.settings_tcp_5555_bind_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@HomeActivity,
                        getString(R.string.settings_tcp_5555_bind_failed, failureReason),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    private fun isPermissionDefined(permission: String): Boolean {
        return try {
            packageManager.getPermissionInfo(permission, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun waitForAdbTcpPort(port: Int): Boolean {
        repeat(10) {
            if (EnvironmentUtils.isAdbPortLive(port)) return true
            delay(500)
        }
        return false
    }

    companion object {
        private const val ADB_TCP_BIND_COMMAND = "setprop service.adb.tcp.port 5555; setprop ctl.restart adbd || (stop adbd; start adbd)"
        private const val SDK_ANDROID_13 = 33
        private const val SDK_ANDROID_17 = 37
        private const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val PERMISSION_USE_LOOPBACK_INTERFACE = "android.permission.USE_LOOPBACK_INTERFACE"
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
    grantedResource: Resource<Int>?,
    localNetworkPermissionState: LocalNetworkPermissionState,
    isPrimaryUser: Boolean,
    isRooted: Boolean,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
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
    onLearnMore: () -> Unit,
    onCopyDiagnostics: (String) -> Unit,
    onRequestLocalNetworkPermission: () -> Unit,
    onStartDhizuku: () -> Unit,
    dhizukuEnabled: Boolean,
    onStartTcp5555: () -> Unit
) {
    val context = LocalContext.current
    val status = serviceResource?.data ?: ServiceStatus()
    val grantedCount = grantedResource?.data ?: 0
    val running = status.isRunning
    val adbPermission = status.permission
    val canUseWirelessAdb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var moreOpen by remember { mutableStateOf(false) }
    val diagnostics = remember(status, grantedCount, localNetworkPermissionState) {
        buildDiagnostics(context, status, grantedCount, localNetworkPermissionState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        ShizukuIcon(
                            icon = R.drawable.ic_server_restart,
                            contentDescription = stringResource(R.string.home_refresh)
                        )
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            ShizukuIcon(
                                icon = R.drawable.ic_more_vert_24,
                                contentDescription = stringResource(R.string.accessibility_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = moreOpen,
                            onDismissRequest = { moreOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_stop)) },
                                leadingIcon = {
                                    ShizukuIcon(R.drawable.ic_close_24, contentDescription = null)
                                },
                                onClick = {
                                    moreOpen = false
                                    onStop()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_about)) },
                                leadingIcon = {
                                    ShizukuIcon(R.drawable.ic_outline_info_24, contentDescription = null)
                                },
                                onClick = {
                                    moreOpen = false
                                    onAbout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StatusCard(
                    serviceResource = serviceResource,
                    status = status
                )
            }



            if (adbPermission) {
                item {
                    ManageAppsCard(
                        status = status,
                        grantedCount = grantedCount,
                        onClick = onManageApps
                    )
                }

                item {
                    SimpleActionCard(
                        icon = R.drawable.ic_terminal_24,
                        title = stringResource(R.string.home_terminal_title),
                        body = if (running) {
                            stringResource(R.string.home_terminal_description)
                        } else {
                            stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
                        },
                        enabled = running,
                        onClick = onTerminal
                    )
                }
            }

            if (running && !adbPermission) {
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

            if (isPrimaryUser) {
                val rootRestart = running && status.uid == 0
                if (isRooted) {
                    item {
                        RootCard(rootRestart, onStartRoot)
                    }
                }
                if (canUseWirelessAdb) {
                    item {
                        WirelessAdbCard(
                            localNetworkPermissionState = localNetworkPermissionState,
                            onStartWirelessAdb = onStartWirelessAdb,
                            onPairWirelessAdb = onPairWirelessAdb,
                            onOpenWirelessGuide = onOpenWirelessGuide
                        )
                    }
                }
                item {
                    AdbCommandCard(
                        onShowAdbCommand = onShowAdbCommand,
                        onOpenAdbHelp = onOpenAdbHelp
                    )
                }
                if (!isRooted) {
                    item {
                        RootCard(rootRestart, onStartRoot)
                    }
                }
                if (dhizukuEnabled) {
                    item {
                        DhizukuCard(onStartDhizuku)
                    }
                }
                item {
                    TcpModeCard(onStartTcp5555)
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

            item {
                DiagnosticsCard(
                    diagnostics = diagnostics,
                    onCopyDiagnostics = onCopyDiagnostics
                )
            }

            item {
                SimpleActionCard(
                    icon = R.drawable.ic_learn_more_24dp,
                    title = stringResource(R.string.home_learn_more_title),
                    body = stringResource(R.string.home_learn_more_description),
                    onClick = onLearnMore
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    serviceResource: Resource<ServiceStatus>?,
    status: ServiceStatus
) {
    val context = LocalContext.current
    val running = status.isRunning
    val title = if (running) {
        stringResource(R.string.home_status_service_is_running, stringResource(R.string.app_name))
    } else {
        stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
    }
    val summary = remember(status, running) {
        buildServiceSummary(context, status)
    }

    HomeCard(
        icon = if (running) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp,
        title = title,
        body = summary
    ) {
        if (serviceResource == null) {
            Spacer(Modifier.height(12.dp))
            LoadingIndicator(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ManageAppsCard(
    status: ServiceStatus,
    grantedCount: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val running = status.isRunning
    val title = if (running) {
        context.resources.getQuantityString(
            R.plurals.home_app_management_authorized_apps_count,
            grantedCount,
            grantedCount
        )
    } else {
        stringResource(R.string.home_app_management_title)
    }
    val body = if (running) {
        stringResource(R.string.home_app_management_view_authorized_apps)
    } else {
        stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
    }

    SimpleActionCard(
        icon = R.drawable.ic_system_icon,
        title = title,
        body = body,
        enabled = running,
        onClick = onClick
    )
}

@Composable
private fun RootCard(
    restart: Boolean,
    onStartRoot: () -> Unit
) {
    val buttonLabel = if (restart) R.string.home_root_button_restart else R.string.home_root_button_start
    val buttonIcon = if (restart) R.drawable.ic_server_restart else R.drawable.ic_server_start_24dp

    HomeCard(
        icon = R.drawable.ic_root_24dp,
        title = htmlStringResource(R.string.home_root_title),
        body = htmlStringResource(
            R.string.home_root_description,
            "Don't kill my app!"
        )
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = buttonLabel,
                    icon = buttonIcon,
                    primary = true,
                    onClick = onStartRoot
                )
            )
        )
    }
}

@Composable
private fun WirelessAdbCard(
    localNetworkPermissionState: LocalNetworkPermissionState,
    onStartWirelessAdb: () -> Unit,
    onPairWirelessAdb: () -> Unit,
    onOpenWirelessGuide: () -> Unit
) {
    val body = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        htmlStringResource(R.string.home_wireless_adb_description)
    } else {
        htmlStringResource(R.string.home_wireless_adb_description_pre_11)
    }
    val permissionLine = if (localNetworkPermissionState.required) {
        stringResource(
            if (localNetworkPermissionState.granted) {
                R.string.home_local_network_granted
            } else {
                R.string.home_local_network_missing
            },
            localNetworkPermissionState.label
        )
    } else {
        null
    }

    HomeCard(
        icon = R.drawable.ic_wadb_24,
        title = htmlStringResource(R.string.home_wireless_adb_title),
        body = listOfNotNull(body, permissionLine).joinToString("\n\n")
    ) {
        val buttons = mutableListOf(
            HomeButtonSpec(
                label = R.string.home_root_button_start,
                icon = R.drawable.ic_server_start_24dp,
                primary = true,
                onClick = onStartWirelessAdb
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buttons += HomeButtonSpec(
                label = R.string.adb_pairing,
                icon = R.drawable.ic_numeric_1_circle_outline_24,
                onClick = onPairWirelessAdb
            )
            buttons += HomeButtonSpec(
                label = R.string.home_wireless_adb_view_guide_button,
                icon = R.drawable.ic_help_outline_24dp,
                onClick = onOpenWirelessGuide
            )
        }
        HomeButtons(buttons)
    }
}

@Composable
private fun AdbCommandCard(
    onShowAdbCommand: () -> Unit,
    onOpenAdbHelp: () -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_adb_24dp,
        title = htmlStringResource(R.string.home_adb_title),
        body = htmlStringResource(R.string.home_adb_description, Helps.ADB.get())
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
private fun DiagnosticsCard(
    diagnostics: String,
    onCopyDiagnostics: (String) -> Unit
) {
    HomeCard(
        icon = R.drawable.ic_outline_info_24,
        title = stringResource(R.string.home_diagnostics_title),
        body = diagnostics
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_diagnostics_copy,
                    icon = R.drawable.ic_content_copy_24,
                    primary = true,
                    onClick = { onCopyDiagnostics(diagnostics) }
                )
            )
        )
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShizukuIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content()
            }
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

@Composable
private fun htmlStringResource(@StringRes id: Int, vararg formatArgs: Any): String {
    val raw = stringResource(id, *formatArgs)
    return remember(raw) { htmlToPlainText(raw) }
}

private fun htmlToPlainText(value: String): String {
    return HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun buildServiceSummary(context: android.content.Context, status: ServiceStatus): String {
    if (!status.isRunning) return ""

    val user = when {
        ShizukuSettings.getLastLaunchMode() == ShizukuSettings.LaunchMethod.DHIZUKU -> "dhizuku"
        status.uid == 0 -> "root"
        else -> "adb"
    }
    val version = "${status.apiVersion}.${status.patchVersion}"
    val latestVersion = "${Shizuku.getLatestServiceVersion()}.${ShizukuApiConstants.SERVER_PATCH_VERSION}"
    val raw = if (
        status.apiVersion != Shizuku.getLatestServiceVersion() ||
        status.patchVersion != ShizukuApiConstants.SERVER_PATCH_VERSION
    ) {
        context.getString(R.string.home_status_service_version_update, user, version, latestVersion)
    } else {
        context.getString(R.string.home_status_service_version, user, version)
    }
    return htmlToPlainText(raw)
}

private fun buildDiagnostics(
    context: android.content.Context,
    status: ServiceStatus,
    grantedCount: Int,
    localNetworkPermissionState: LocalNetworkPermissionState
): String {
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val localNetwork = if (localNetworkPermissionState.required) {
        "${localNetworkPermissionState.label}: " +
                if (localNetworkPermissionState.granted) "granted" else "missing"
    } else {
        "not required"
    }

    return buildString {
        appendLine("App: ${context.getString(R.string.app_name)} $versionName (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT} / ${Build.VERSION.CODENAME}")
        appendLine("Service: ${if (status.isRunning) "running" else "stopped"}")
        appendLine("Server uid: ${status.uid}")
        appendLine("Server API: ${status.apiVersion}.${status.patchVersion}")
        appendLine("SELinux: ${status.seContext ?: "unknown"}")
        appendLine("ADB permission: ${if (status.permission) "full" else "limited"}")
        appendLine("Authorized apps: $grantedCount")
        appendLine("Local network: $localNetwork")
    }.trim()
}


@Composable
private fun TcpModeCard(onStartTcpMode: () -> Unit) {
    HomeCard(
        icon = R.drawable.ic_baseline_link_24,
        title = stringResource(R.string.settings_tcp_5555_title),
        body = stringResource(R.string.settings_tcp_5555_summary)
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.settings_tcp_5555_button,
                    icon = R.drawable.ic_baseline_link_24,
                    primary = true,
                    onClick = onStartTcpMode
                )
            )
        )
    }
}

@Composable
private fun DhizukuCard(onStartDhizuku: () -> Unit) {
    HomeCard(
        icon = R.drawable.ic_system_icon,
        title = htmlStringResource(R.string.home_dhizuku_title),
        body = htmlStringResource(R.string.home_dhizuku_description)
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_root_button_start,
                    icon = R.drawable.ic_server_start_24dp,
                    primary = true,
                    onClick = onStartDhizuku
                )
            )
        )
    }
}

