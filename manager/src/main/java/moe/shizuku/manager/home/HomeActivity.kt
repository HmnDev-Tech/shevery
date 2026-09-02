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
import android.view.LayoutInflater
import android.view.Menu
import android.widget.TextView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
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
import moe.shizuku.manager.adb.AdbStarter
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ExpressiveFloatingNavigationBar
import moe.shizuku.manager.ui.compose.HtmlText
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.NavItem
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.compat.StubManager
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

        if (ModuleSettings.isCompatibilityStubEnabled() && !StubManager.isInstalled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    StubManager.install(applicationContext)
                } catch (_: Throwable) {
                }
            }
        }

        setContent {
            val serviceResource by homeModel.serviceStatus.observeAsState()
            val grantedResource by appsModel.grantedCount.observeAsState()
            val localNetworkPermissionState = remember(permissionRefreshTick.intValue) {
                buildLocalNetworkPermissionState()
            }

            var showTcpPromptDialog by rememberSaveable { mutableStateOf(false) }
            var doNotRemindChecked by rememberSaveable { mutableStateOf(false) }

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

            var selectedTab by remember { mutableIntStateOf(0) }

            ShizukuExpressiveTheme {
                Box(Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0.dp)
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
                                    dhizukuEnabled = ModuleSettings.isDhizukuEnabled()
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
                ExpressiveFloatingNavigationBar(
                    items = listOf(
                        NavItem(stringResource(R.string.app_name), Icons.Rounded.Home),
                        NavItem(stringResource(R.string.modules_title), Icons.Rounded.Apps),
                        NavItem(stringResource(R.string.comput_title), Icons.Rounded.Terminal),
                        NavItem(stringResource(R.string.settings_title), Icons.Rounded.Settings)
                    ),
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (showTcpPromptDialog && !ShizukuSettings.isTcpMode()) {
                    AlertDialog(
                        onDismissRequest = {
                            hasPromptedTcpDialogThisSession = true
                            if (doNotRemindChecked) {
                                ShizukuSettings.setTcpModePromptSuppressed(true)
                            }
                            showTcpPromptDialog = false
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.tcp_prompt_dialog_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = stringResource(R.string.tcp_prompt_dialog_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onClick = {
                                    hasPromptedTcpDialogThisSession = true
                                    if (doNotRemindChecked) {
                                        ShizukuSettings.setTcpModePromptSuppressed(true)
                                    }
                                    showTcpPromptDialog = false
                                    ShizukuSettings.setTcpMode(true)

                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val isAlreadyOn5555 = EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)
                                        if (isAlreadyOn5555) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@HomeActivity, R.string.settings_tcp_mode, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val port = EnvironmentUtils.getLiveAdbTcpPort().takeIf { it > 0 }
                                                ?: EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 }
                                                ?: ShizukuSettings.getLastAdbPort().takeIf { it > 0 }
                                            if (port != null && port > 0) {
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
        if (!Shizuku.pingBinder()) {
            checkServerStatus()
            moe.shizuku.manager.service.SheveryNotificationManager.updateNotification(this)
            Toast.makeText(this, R.string.service_already_stopped, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_stop_message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
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
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startRoot() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        startActivity(
            Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, true)
            }
        )
    }

    private fun startWirelessAdb() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AdbDialogFragment().show(supportFragmentManager, "adb")
            return
        }

        val livePort = EnvironmentUtils.getLiveAdbTcpPort().takeIf { it > 0 }
            ?: EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 && EnvironmentUtils.isAdbPortLive(it) }
            ?: if (EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)) AdbStarter.TCP_MODE_PORT else null

        if (livePort != null) {
            startActivity(
                Intent(this, StarterActivity::class.java).apply {
                    putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                    putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                    putExtra(StarterActivity.EXTRA_PORT, livePort)
                }
            )
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
        val view = LayoutInflater.from(this).inflate(R.layout.command_dialog, null)
        view.findViewById<TextView>(R.id.command_text).text = Starter.adbCommand
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_adb_button_view_command)
            .setView(view)
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
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        startActivity(
            Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_IS_DHIZUKU, true)
            }
        )
    }

    private fun bindTcp5555() {
        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@HomeActivity)
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var failureReason = getString(R.string.settings_tcp_5555_bind_failed_generic)

            fun recordBindFailure(route: String, reason: String, throwable: Throwable? = null) {
                val message = "$route: $reason"
                failureReason = message
                android.util.Log.d("Shevery", "Failed to bind TCP 5555 via $message", throwable)
            }

            // 0. Prefer the actual ADB protocol path (adb tcpip 5555).
            // Running setprop/stop/start through the Shevery shell process is not equivalent to
            // adb tcpip and can fail with exit code 1 even when the service is active.
            if (EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)) {
                success = true
            } else {
                val activePort = EnvironmentUtils.getLiveAdbTcpPort()
                    .takeIf { it > 0 && it != AdbStarter.TCP_MODE_PORT }
                    ?: EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 && it != AdbStarter.TCP_MODE_PORT }

                if (activePort != null) {
                    try {
                        AdbStarter.switchToTcpMode(currentPort = activePort)
                        success = waitForAdbTcpPort(AdbStarter.TCP_MODE_PORT)
                        if (!success) {
                            recordBindFailure("ADB", "tcpip command finished but port 5555 did not become live")
                        }
                    } catch (e: Exception) {
                        recordBindFailure("ADB", e.message ?: e.javaClass.simpleName, e)
                    }
                } else {
                    recordBindFailure("ADB", "no active local ADB port was found")
                }
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
                            success = dhizukuService.bindAdbTcp(AdbStarter.TCP_MODE_PORT) && waitForAdbTcpPort(AdbStarter.TCP_MODE_PORT)
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
                    success = result.isSuccess && waitForAdbTcpPort(AdbStarter.TCP_MODE_PORT)
                    if (!success) {
                        recordBindFailure(
                            "root",
                            "command exit code ${result.code}, port 5555 live: ${EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)}"
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
                        success = exitCode == 0 && waitForAdbTcpPort(AdbStarter.TCP_MODE_PORT)
                        if (!success) {
                            recordBindFailure(
                                "Shevery",
                                "command exit code $exitCode, port 5555 live: ${EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)}"
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
                    startActivity(
                        Intent(this@HomeActivity, StarterActivity::class.java).apply {
                            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                            putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                            putExtra(StarterActivity.EXTRA_PORT, AdbStarter.TCP_MODE_PORT)
                        }
                    )
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
        private var hasPromptedTcpDialogThisSession = false
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
    dhizukuEnabled: Boolean
) {
    val context = LocalContext.current
    val status = serviceResource?.data ?: ServiceStatus()
    val grantedCount = grantedResource?.data ?: 0
    val running = status.isRunning
    val adbPermission = status.permission
    val canUseWirelessAdb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        || EnvironmentUtils.isAdbPortLive(AdbStarter.TCP_MODE_PORT)
        || EnvironmentUtils.getLiveAdbTcpPort() > 0
        || ShizukuSettings.isTcpMode()
    val diagnostics = remember(status, grantedCount, localNetworkPermissionState) {
        buildDiagnostics(context, status, grantedCount, localNetworkPermissionState)
    }
    var moreOpen by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusHero(
                    serviceResource = serviceResource,
                    status = status,
                    running = running,
                    adbPermission = adbPermission,
                    isPrimaryUser = isPrimaryUser,
                    isRooted = isRooted,
                    canUseWirelessAdb = canUseWirelessAdb,
                    dhizukuEnabled = dhizukuEnabled,
                    onStartRoot = onStartRoot,
                    onStartWirelessAdb = onStartWirelessAdb,
                    onStartDhizuku = onStartDhizuku,
                    onManageApps = onManageApps,
                    onStop = onStop
                )
            }

            item {
                val pendingVersion = remember { mutableStateOf<String?>(null) }
                val pendingUrl = remember { mutableStateOf<String?>(null) }
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    pendingVersion.value = ModuleSettings.getPendingUpdateVersion()
                    pendingUrl.value = ModuleSettings.getPendingUpdateUrl()
                }
                val version = pendingVersion.value
                val url = pendingUrl.value
                if (!version.isNullOrEmpty() && !url.isNullOrEmpty()) {
                    HomeCard(
                        icon = R.drawable.ic_outline_info_24,
                        title = ctx.getString(R.string.home_update_available_title, version),
                        body = ctx.getString(R.string.home_update_available_body),
                        enabled = true,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            ctx.startActivity(intent)
                        }
                    )
                }
            }

            item {
                QuickActionsPills(
                    running = running,
                    isPrimaryUser = isPrimaryUser,
                    onTerminal = onTerminal,
                    onStartWirelessAdb = onStartWirelessAdb,
                    onPairWirelessAdb = onPairWirelessAdb,
                    onOpenWirelessGuide = onOpenWirelessGuide
                )
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

            if (localNetworkPermissionState.required && !localNetworkPermissionState.granted) {
                item {
                    LocalNetworkPermissionCard(
                        localNetworkPermissionState = localNetworkPermissionState,
                        onRequestLocalNetworkPermission = onRequestLocalNetworkPermission
                    )
                }
            }

            if (adbPermission) {
                item {
                    ManageAppsCard(
                        status = status,
                        grantedCount = grantedCount,
                        onClick = onManageApps
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
                if (isRooted && !running) {
                    item {
                        RootCard(onStartRoot)
                    }
                }
                if (dhizukuEnabled) {
                    item {
                        DhizukuCard(onStartDhizuku)
                    }
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
private fun StatusHero(
    serviceResource: Resource<ServiceStatus>?,
    status: ServiceStatus,
    running: Boolean,
    adbPermission: Boolean,
    isPrimaryUser: Boolean,
    isRooted: Boolean,
    canUseWirelessAdb: Boolean,
    dhizukuEnabled: Boolean,
    onStartRoot: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onStartDhizuku: () -> Unit,
    onManageApps: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val title = if (running) {
        stringResource(R.string.home_status_service_is_running, stringResource(R.string.app_name))
    } else {
        stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
    }
    val summary = remember(status, running) {
        buildServiceSummary(context, status)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (running) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = if (running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ShizukuIcon(
                            icon = if (running) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp,
                            contentDescription = null,
                            tint = if (running) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (running) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (running) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (serviceResource == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(24.dp))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (running) {
                    if (adbPermission) {
                        Button(onClick = onManageApps) {
                            ButtonIcon(R.drawable.ic_system_icon)
                            Text(stringResource(R.string.home_manage_apps_button))
                        }
                    }
                    if (status.uid == 0) {
                        FilledTonalButton(onClick = onStartRoot) {
                            ButtonIcon(R.drawable.ic_server_restart)
                            Text(stringResource(R.string.home_root_button_restart))
                        }
                    }
                    FilledTonalButton(onClick = onStop) {
                        ButtonIcon(R.drawable.ic_close_24)
                        Text(stringResource(R.string.action_stop))
                    }
                } else if (isPrimaryUser) {
                    if (isRooted) {
                        Button(onClick = onStartRoot) {
                            ButtonIcon(R.drawable.ic_server_start_24dp)
                            Text(stringResource(R.string.home_root_button_start))
                        }
                    }
                    if (canUseWirelessAdb) {
                        FilledTonalButton(onClick = onStartWirelessAdb) {
                            ButtonIcon(R.drawable.ic_wadb_24)
                            Text(stringResource(R.string.home_wireless_adb_title))
                        }
                    }
                    if (dhizukuEnabled) {
                        FilledTonalButton(onClick = onStartDhizuku) {
                            ButtonIcon(R.drawable.ic_system_icon)
                            Text(stringResource(R.string.home_dhizuku_title))
                        }
                    }
                }
            }
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
private fun QuickActionsPills(
    running: Boolean,
    isPrimaryUser: Boolean,
    onTerminal: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onPairWirelessAdb: () -> Unit,
    onOpenWirelessGuide: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionPill(
                icon = Icons.Rounded.Terminal,
                label = stringResource(R.string.home_quick_terminal),
                enabled = running,
                onClick = onTerminal
            )
            if (isPrimaryUser) {
                QuickActionPill(
                    icon = Icons.Rounded.Wifi,
                    label = stringResource(R.string.home_quick_wireless),
                    onClick = onStartWirelessAdb
                )
                QuickActionPill(
                    icon = Icons.Rounded.NearMe,
                    label = stringResource(R.string.adb_pairing),
                    onClick = onPairWirelessAdb
                )
            }
        }
        if (isPrimaryUser) {
            TextButton(onClick = onOpenWirelessGuide) {
                Text(stringResource(R.string.home_wireless_adb_view_guide_button))
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
                if (localNetworkPermissionState.granted) context.getString(R.string.diagnostics_granted) else context.getString(R.string.diagnostics_missing)
    } else {
        context.getString(R.string.diagnostics_not_required)
    }

    return buildString {
        appendLine("${context.getString(R.string.diagnostics_app)}: ${context.getString(R.string.app_name)} $versionName (${BuildConfig.VERSION_CODE})")
        appendLine("${context.getString(R.string.diagnostics_android)}: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT} / ${Build.VERSION.CODENAME}")
        appendLine("${context.getString(R.string.diagnostics_service)}: ${if (status.isRunning) context.getString(R.string.diagnostics_running) else context.getString(R.string.diagnostics_stopped)}")
        appendLine("${context.getString(R.string.diagnostics_server_uid)}: ${status.uid}")
        appendLine("${context.getString(R.string.diagnostics_server_api)}: ${status.apiVersion}.${status.patchVersion}")
        appendLine("${context.getString(R.string.diagnostics_selinux)}: ${status.seContext ?: context.getString(R.string.diagnostics_unknown)}")
        appendLine("${context.getString(R.string.diagnostics_adb_permission)}: ${if (status.permission) context.getString(R.string.diagnostics_full) else context.getString(R.string.diagnostics_limited)}")
        appendLine("${context.getString(R.string.diagnostics_authorized_apps)}: $grantedCount")
        appendLine("${context.getString(R.string.diagnostics_local_network)}: $localNetwork")
    }.trim()
}


@Composable
private fun RootCard(onStartRoot: () -> Unit) {
    HomeCard(
        icon = R.drawable.ic_server_start_24dp,
        title = htmlStringResource(R.string.home_root_title),
        body = htmlStringResource(R.string.home_root_description, Helps.SUI.get())
    ) {
        HomeButtons(
            listOf(
                HomeButtonSpec(
                    label = R.string.home_root_button_start,
                    icon = R.drawable.ic_server_start_24dp,
                    primary = true,
                    onClick = onStartRoot
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
