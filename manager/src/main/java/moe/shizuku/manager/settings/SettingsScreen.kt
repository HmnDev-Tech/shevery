@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import moe.shizuku.manager.about.AboutActivity
import moe.shizuku.manager.about.AlsoTry
import moe.shizuku.manager.about.AlsoTryApp
import android.os.Build
import android.text.TextUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LANGUAGE
import moe.shizuku.manager.ShizukuSettings.NIGHT_MODE
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.app.ThemeHelper.KEY_BLACK_NIGHT_THEME
import moe.shizuku.manager.app.ThemeHelper.KEY_USE_SYSTEM_COLOR
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.accessibility.AccessibilityManagerActivity
import moe.shizuku.manager.compat.StubManager
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.commandium.AiProviderRepository
import moe.shizuku.manager.module.update.AppUpdateSettingsGroup
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.service.WatchdogManager
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.SectionHeader
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow
import moe.shizuku.manager.ui.compose.htmlToPlainText
import rikka.core.util.ResourceUtils
import rikka.core.util.ClipboardUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import android.widget.Toast
import moe.shizuku.manager.utils.BackupRestoreUtil
import moe.shizuku.manager.utils.AiExplainUtil

private enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    APPLICATION(
        R.string.settings_application,
        R.string.settings_section_startup_summary,
        R.drawable.ic_settings_outline_24dp
    ),
    APPEARANCE(
        R.string.settings_appearance,
        R.string.settings_section_appearance_summary,
        R.drawable.ic_outline_dark_mode_24
    ),
    SECURITY(
        R.string.settings_security_title,
        R.string.settings_security_summary,
        R.drawable.ic_security_24dp
    ),
    DEVICE_OWNER(
        R.string.settings_device_owner_title,
        R.string.settings_device_owner_summary,
        R.drawable.ic_device_owner_24dp
    ),
    MODULES(
        R.string.modules_settings_title,
        R.string.settings_section_modules_summary,
        R.drawable.ic_adb_24dp
    ),
    UPDATES(
        R.string.settings_update_group_title,
        R.string.settings_section_updates_summary,
        R.drawable.ic_outline_arrow_upward_24
    ),
    AI(
        R.string.comput_settings,
        R.string.settings_section_ai_summary,
        R.drawable.ic_code_24dp
    ),
    BACKUPS(
        R.string.settings_backups_title,
        R.string.settings_section_backups_summary,
        R.drawable.ic_outline_arrow_upward_24
    ),
    AUTOMATION(
        R.string.automation_settings_title,
        R.string.automation_section_summary,
        R.drawable.ic_outline_play_arrow_24
    ),
    EXTRAS(
        R.string.settings_extras_title,
        R.string.settings_section_extras_summary,
        R.drawable.ic_system_icon
    ),
    ABOUT(
        R.string.action_about,
        R.string.settings_section_about_summary,
        R.drawable.ic_outline_info_24
    ),
}

private sealed interface SettingsNav {
    data object Hub : SettingsNav
    data class Section(val section: SettingsSection) : SettingsNav
    data object UpdateSettings : SettingsNav
}


@Composable
fun SettingsScreen(
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val packageManager = context.packageManager
    val componentName = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)

    val prefs = ShizukuSettings.getPreferences()

    var startOnBoot by remember {
        mutableStateOf(ShizukuSettings.getStartOnBoot())
    }
    var rooted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Root check + one-time stale-pref cleanup on first composition
    LaunchedEffect(Unit) {
        rooted = withContext(Dispatchers.IO) { EnvironmentUtils.isRooted() }
        if (!rooted && ShizukuSettings.getStartOnBoot()) {
            withContext(Dispatchers.IO) {
                ShizukuSettings.setStartOnBoot(false)
                startOnBoot = false
                packageManager.setComponentEnabled(
                    componentName,
                    ShizukuSettings.getStartOnBoot() || ShizukuSettings.getStartOnBootAdb()
                )
            }
        }
    }

    // Re-check root whenever the activity is in the foreground
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) {
                rooted = EnvironmentUtils.isRooted()
            }
        }
    }
    var adbStartOnBoot by remember {
        mutableStateOf(ShizukuSettings.getStartOnBootAdb())
    }
    var watchdog by remember {
        mutableStateOf(ModuleSettings.isWatchdogEnabled())
    }
    var dhizukuEnabled by remember {
        mutableStateOf(ModuleSettings.isDhizukuEnabled())
    }
    var notifyDeath by remember {
        mutableStateOf(ModuleSettings.isNotifyOnServiceDeath())
    }
    var showDhizukuDialog by remember { mutableStateOf(false) }
    var wifiReassert by remember {
        mutableStateOf(ModuleSettings.isWifiReassertEnabled())
    }
    var compatStub by remember {
        mutableStateOf(StubManager.isInstalled(context))
    }
    var autoDisableUsbDebugging by remember {
        mutableStateOf(ShizukuSettings.getAutoDisableUsbDebugging())
    }
    var tcpMode by remember {
        mutableStateOf(ShizukuSettings.isTcpMode())
    }
    var languageTag by remember {
        mutableStateOf(prefs.getString(LANGUAGE, "SYSTEM") ?: "SYSTEM")
    }
    var nightMode by remember {
        mutableIntStateOf(ShizukuSettings.getNightMode())
    }
    var blackNightTheme by remember {
        mutableStateOf(ThemeHelper.isBlackNightTheme(context))
    }
    var useSystemColor by remember {
        mutableStateOf(ThemeHelper.isUsingSystemColor())
    }
    var classicNav by remember {
        mutableStateOf(ShizukuSettings.isClassicNav())
    }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNightDialog by remember { mutableStateOf(false) }
    var showModuleModeDialog by remember { mutableStateOf(false) }
    var showCustomPermissionsDialog by remember { mutableStateOf(false) }
    var pendingTcpModeChange by remember { mutableStateOf<Boolean?>(null) }

    var moduleAccessMode by remember {
        mutableStateOf(ModuleSettings.getAccessMode())
    }
    var customPermissions by remember {
        mutableStateOf(ModuleSettings.getCustomPermissions())
    }
    var moduleBackground by remember {
        mutableStateOf(ModuleSettings.allowBackgroundActions())
    }
    var recommandWebUi by remember {
        mutableStateOf(ModuleSettings.recommandForWebUi())
    }
    var recommandAction by remember {
        mutableStateOf(ModuleSettings.recommandForAction())
    }
    var computAiName by remember {
        mutableStateOf(ModuleSettings.getComputAiName())
    }
    var computAiBaseUrl by remember {
        mutableStateOf(ModuleSettings.getComputAiBaseUrl())
    }
    var computAiModel by remember {
        mutableStateOf(ModuleSettings.getComputAiModel())
    }
    var computRecommand by remember {
        mutableStateOf(ModuleSettings.isComputRecommandEnabled())
    }
    var showAiManager by remember { mutableStateOf(false) }
    var aiProvidersVersion by remember { mutableStateOf(0) }
    var showMissingPermissionDialog by remember { mutableStateOf(false) }
    var connectorEnabled by remember { mutableStateOf(ModuleSettings.isConnectorEnabled()) }
    var verboseLogging by remember { mutableStateOf(ModuleSettings.isVerboseLogging()) }
    var notifyRecovery by remember { mutableStateOf(ModuleSettings.isNotifyOnRecovery()) }
    var autoRefresh by remember { mutableStateOf(ModuleSettings.isAutoRefreshOnResume()) }
    var aiExplain by remember { mutableStateOf(ModuleSettings.isComputAiExplainEnabled()) }
    var showUnsafeDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }
    var recreateTick by remember { mutableIntStateOf(0) }
    var nav by remember { mutableStateOf<SettingsNav>(SettingsNav.Hub) }

    // AI Provider manager replaces the whole Settings screen while open:
    // composing it after the Scaffold stacked a second TopAppBar over this
    // screen's (dead touches on its buttons); early-return keeps one top bar.
    if (showAiManager) {
        BackHandler { showAiManager = false }
        AiManagerScreen(
            onNavigateUp = { showAiManager = false },
            onChanged = {
                aiProvidersVersion++
                computAiName = ModuleSettings.getComputAiName()
                computAiBaseUrl = ModuleSettings.getComputAiBaseUrl()
                computAiModel = ModuleSettings.getComputAiModel()
            }
        )
        return
    }

    fun tcpModeNeedsRestart(enabled: Boolean): Boolean {
        val currentPort = EnvironmentUtils.getActiveAdbPort()
        return Shizuku.pingBinder() && currentPort > 0 && when {
            enabled -> currentPort != AdbStarter.TCP_MODE_PORT
            else -> currentPort == AdbStarter.TCP_MODE_PORT
        }
    }

    fun restartAdbForTcpMode() {
        val port = EnvironmentUtils.getActiveAdbPort().takeIf { it > 0 } ?: return
        WatchdogManager.clearUserStopRequest(context)
        activity?.startActivity(
            Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                putExtra(StarterActivity.EXTRA_PORT, port)
            }
        )
    }

    fun applyTcpMode(enabled: Boolean, restart: Boolean = false) {
        ShizukuSettings.setTcpMode(enabled)
        tcpMode = ShizukuSettings.isTcpMode()
        if (restart && enabled) {
            restartAdbForTcpMode()
        }
    }

    val scope = rememberCoroutineScope()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        BackupRestoreUtil.backup(context, output)
                    } ?: error("Failed to open output stream")
                }
            }.onSuccess {
                Toast.makeText(context, "Backup completed successfully", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BackupRestoreUtil.restore(context, input)
                    } ?: error("Failed to open input stream")
                }
            }.onSuccess {
                Toast.makeText(context, "Restore completed successfully", Toast.LENGTH_SHORT).show()
                startOnBoot = ShizukuSettings.getStartOnBoot()
                adbStartOnBoot = ShizukuSettings.getStartOnBootAdb()
                watchdog = ModuleSettings.isWatchdogEnabled()
                dhizukuEnabled = ModuleSettings.isDhizukuEnabled()
                notifyDeath = ModuleSettings.isNotifyOnServiceDeath()
                languageTag = prefs.getString(LANGUAGE, "SYSTEM") ?: "SYSTEM"
                nightMode = ShizukuSettings.getNightMode()
                blackNightTheme = ThemeHelper.isBlackNightTheme(context)
                useSystemColor = ThemeHelper.isUsingSystemColor()
                moduleAccessMode = ModuleSettings.getAccessMode()
                customPermissions = ModuleSettings.getCustomPermissions()
                moduleBackground = ModuleSettings.allowBackgroundActions()
                recommandWebUi = ModuleSettings.recommandForWebUi()
                recommandAction = ModuleSettings.recommandForAction()
                computAiName = ModuleSettings.getComputAiName()
                computAiBaseUrl = ModuleSettings.getComputAiBaseUrl()
                computAiModel = ModuleSettings.getComputAiModel()
                computRecommand = ModuleSettings.isComputRecommandEnabled()
                classicNav = ShizukuSettings.isClassicNav()
                connectorEnabled = ModuleSettings.isConnectorEnabled()
                verboseLogging = ModuleSettings.isVerboseLogging()
                notifyRecovery = ModuleSettings.isNotifyOnRecovery()
                autoRefresh = ModuleSettings.isAutoRefreshOnResume()
                aiExplain = ModuleSettings.isComputAiExplainEnabled()
                recreateTick++
            }.onFailure {
                Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val localeOptions = remember(languageTag) {
        buildLocaleOptions(context, languageTag)
    }
    val languageSummary = localeOptions.firstOrNull { it.tag == languageTag }
        ?.let { it.summary ?: it.title }
        ?: stringResource(rikka.core.R.string.follow_system)
    val nightValues = context.resources.getIntArray(R.array.night_mode_value).toList()
    val nightLabels = stringArrayResource(R.array.night_mode).toList()
    val nightSummary = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> nightLabels.getOrElse(0) {
            stringResource(rikka.core.R.string.follow_system)
        }
        AppCompatDelegate.MODE_NIGHT_YES -> nightLabels.getOrElse(1) {
            stringResource(rikka.core.R.string.follow_system)
        }
        else -> nightLabels.getOrElse(2) {
            stringResource(rikka.core.R.string.follow_system)
        }
    }
    LaunchedEffect(recreateTick) {
        if (recreateTick > 0) {
            delay(260)
            activity?.recreate()
        }
    }

    AnimatedContent(
        targetState = nav,
        transitionSpec = {
            val forward = when {
                targetState is SettingsNav.Hub -> false
                initialState is SettingsNav.Hub -> true
                targetState is SettingsNav.UpdateSettings -> true
                initialState is SettingsNav.UpdateSettings -> false
                else -> true
            }
            if (forward) {
                (slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } +
                    fadeIn(animationSpec = tween(300)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth / 4 } +
                            fadeOut(animationSpec = tween(300))
                    )
            } else {
                (slideInHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth / 4 } +
                    fadeIn(animationSpec = tween(300)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth } +
                            fadeOut(animationSpec = tween(300))
                    )
            }
        },
        label = "settings-nav"
    ) { current ->
        when (current) {
            SettingsNav.UpdateSettings -> {
                BackHandler { nav = SettingsNav.Section(SettingsSection.UPDATES) }
                moe.shizuku.manager.module.update.UpdateSettingsScreen(
                    onNavigateUp = { nav = SettingsNav.Section(SettingsSection.UPDATES) }
                )
            }
            is SettingsNav.Section -> {
                BackHandler { nav = SettingsNav.Hub }
                val sectionListState = remember(current.section) { LazyListState() }
                ShizukuLazyScaffold(
                    title = stringResource(current.section.titleRes),
                    onNavigateUp = { nav = SettingsNav.Hub },
                    bottomInset = 112.dp,
                    listState = sectionListState
                ) {
                    when (current.section) {
                        SettingsSection.APPLICATION -> applicationSectionContent(
                            rooted = rooted,
                            startOnBoot = startOnBoot,
                            adbStartOnBoot = adbStartOnBoot,
                            tcpMode = tcpMode,
                            watchdog = watchdog,
                            dhizukuEnabled = dhizukuEnabled,
                            notifyDeath = notifyDeath,
                            wifiReassert = wifiReassert,
                            compatStub = compatStub,
                            autoDisableUsbDebugging = autoDisableUsbDebugging,
                            onStartOnBootChange = { enabled ->
                                ShizukuSettings.setStartOnBoot(enabled)
                                startOnBoot = ShizukuSettings.getStartOnBoot()
                                packageManager.setComponentEnabled(
                                    componentName,
                                    ShizukuSettings.getStartOnBoot() || ShizukuSettings.getStartOnBootAdb()
                                )
                                if (enabled) {
                                    EnvironmentUtils.requestIgnoreBatteryOptimizations(context)
                                }
                            },
                            onAdbStartOnBootChange = { enabled ->
                                if (enabled) {
                                    val hasPermission = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                                            PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        ShizukuSettings.setStartOnBootAdb(true)
                                        adbStartOnBoot = true
                                        packageManager.setComponentEnabled(
                                            componentName,
                                            ShizukuSettings.getStartOnBoot() || ShizukuSettings.getStartOnBootAdb()
                                        )
                                        if (!tcpMode) {
                                            Toast.makeText(
                                                context,
                                                R.string.settings_start_on_boot_adb_warning_no_tcp,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        showMissingPermissionDialog = true
                                    }
                                } else {
                                    ShizukuSettings.setStartOnBootAdb(false)
                                    adbStartOnBoot = false
                                    packageManager.setComponentEnabled(
                                        componentName,
                                        ShizukuSettings.getStartOnBoot() || ShizukuSettings.getStartOnBootAdb()
                                    )
                                }
                            },
                            onWatchdogChange = { enabled ->
                                ModuleSettings.setWatchdogEnabled(enabled)
                                watchdog = ModuleSettings.isWatchdogEnabled()
                                moe.shizuku.manager.service.WatchdogManager.reconcileService(context)
                            },
                            onDhizukuToggle = { enabled ->
                                if (enabled) {
                                    showDhizukuDialog = true
                                } else {
                                    ModuleSettings.setDhizukuEnabled(false)
                                    dhizukuEnabled = false
                                }
                            },
                            onNotifyDeathChange = { enabled ->
                                ModuleSettings.setNotifyOnServiceDeath(enabled)
                                notifyDeath = ModuleSettings.isNotifyOnServiceDeath()
                            },
                            onWifiReassertChange = { enabled ->
                                ModuleSettings.setWifiReassertEnabled(enabled)
                                wifiReassert = ModuleSettings.isWifiReassertEnabled()
                            },
                            onCompatStubChange = { enabled ->
                                val performToggle = {
                                    scope.launch {
                                        val result = if (enabled) {
                                            StubManager.install(context)
                                        } else {
                                            StubManager.uninstall(context)
                                        }
                                        compatStub = StubManager.isInstalled(context)
                                        if (result.ok) {
                                            ModuleSettings.setCompatibilityStubEnabled(enabled)
                                            val message = if (enabled) {
                                                context.getString(R.string.settings_compat_stub_installed, result.channel)
                                            } else {
                                                context.getString(R.string.settings_compat_stub_uninstalled)
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val action = if (enabled) "install" else "uninstall"
                                            val message = if (result.error == "no channel available") {
                                                context.getString(R.string.settings_compat_stub_none)
                                            } else {
                                                context.getString(R.string.settings_compat_stub_failed, action, result.channel, result.error ?: "unknown")
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                val fa = context as? androidx.fragment.app.FragmentActivity
                                if (fa != null) {
                                    moe.shizuku.manager.security.AuthManager.executeWithAuth(
                                        activity = fa,
                                        action = moe.shizuku.manager.security.SecuritySettings.ProtectedAction.STUB_MANAGEMENT,
                                        title = context.getString(R.string.security_auth_prompt_title),
                                        subtitle = context.getString(R.string.security_auth_prompt_stubs)
                                    ) {
                                        performToggle()
                                    }
                                } else {
                                    performToggle()
                                }
                            },
                            onAutoDisableUsbDebuggingChange = { enabled ->
                                ShizukuSettings.setAutoDisableUsbDebugging(enabled)
                                autoDisableUsbDebugging = ShizukuSettings.getAutoDisableUsbDebugging()
                            },
                            onTcpModeChange = { enabled ->
                                if (tcpModeNeedsRestart(enabled)) {
                                    pendingTcpModeChange = enabled
                                } else {
                                    applyTcpMode(enabled)
                                }
                            }
                        )
                        SettingsSection.APPEARANCE -> appearanceSectionContent(
                            languageSummary = languageSummary,
                            nightSummary = nightSummary,
                            nightMode = nightMode,
                            blackNightTheme = blackNightTheme,
                            useSystemColor = useSystemColor,
                            classicNav = classicNav,
                            onLanguageClick = { showLanguageDialog = true },
                            onNightClick = { showNightDialog = true },
                            onBlackNightChange = { enabled ->
                                prefs.edit().putBoolean(KEY_BLACK_NIGHT_THEME, enabled).apply()
                                blackNightTheme = enabled
                                if (ResourceUtils.isNightMode(context.resources.configuration)) {
                                    recreateTick++
                                }
                            },
                            onUseSystemColorChange = { enabled ->
                                prefs.edit().putBoolean(KEY_USE_SYSTEM_COLOR, enabled).apply()
                                useSystemColor = enabled
                                recreateTick++
                            },
                            onClassicNavChange = { enabled ->
                                ShizukuSettings.setClassicNav(enabled)
                                classicNav = enabled
                            }
                        )
                        SettingsSection.SECURITY -> {
                            item {
                                SecuritySettingsContent()
                            }
                        }
                        SettingsSection.DEVICE_OWNER -> {
                            item {
                                DeviceOwnerContent()
                            }
                        }
                        SettingsSection.MODULES -> modulesSectionContent(
                            moduleAccessMode = moduleAccessMode,
                            moduleBackground = moduleBackground,
                            recommandWebUi = recommandWebUi,
                            recommandAction = recommandAction,
                            onAccessModeClick = { showModuleModeDialog = true },
                            onCustomPermissionsClick = { showCustomPermissionsDialog = true },
                            onBackgroundChange = { enabled ->
                                ModuleSettings.setAllowBackgroundActions(enabled)
                                moduleBackground = enabled
                            },
                            onRecommandWebUiChange = { enabled ->
                                ModuleSettings.setRecommandForWebUi(enabled)
                                recommandWebUi = enabled
                            },
                            onRecommandActionChange = { enabled ->
                                ModuleSettings.setRecommandForAction(enabled)
                                recommandAction = enabled
                            }
                        )
                        SettingsSection.UPDATES -> updatesSectionContent(
                            onUpdateSettingsClick = { nav = SettingsNav.UpdateSettings }
                        )
                        SettingsSection.AI -> aiSectionContent(
                            aiProvidersVersion = aiProvidersVersion,
                            computAiBaseUrl = computAiBaseUrl,
                            computRecommand = computRecommand,
                            onOpenAiManager = { showAiManager = true },
                            onComputRecommandChange = { enabled ->
                                ModuleSettings.setComputRecommandEnabled(enabled)
                                computRecommand = enabled
                            }
                        )
                        SettingsSection.BACKUPS -> backupsSectionContent(
                            onBackup = {
                                backupLauncher.launch("shevery_backup_${System.currentTimeMillis()}.zip")
                            },
                            onRestore = {
                                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        )
                        SettingsSection.AUTOMATION -> automationSectionContent(
                            context = context,
                            connectorEnabled = connectorEnabled,
                            onConnectorToggle = { enabled ->
                                if (enabled) {
                                    showUnsafeDialog = true
                                } else {
                                    connectorEnabled = false
                                    ModuleSettings.setConnectorEnabled(false)
                                }
                            },
                            onCopy = { text ->
                                ClipboardUtils.put(context, text)
                                Toast.makeText(context, R.string.automation_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            }
                        )
                        SettingsSection.EXTRAS -> extrasSectionContent(
                            context = context,
                            notifyRecovery = notifyRecovery,
                            autoRefresh = autoRefresh,
                            verboseLogging = verboseLogging,
                            aiExplain = aiExplain,
                            onNotifyRecoveryChange = { value ->
                                notifyRecovery = value
                                ModuleSettings.setNotifyOnRecovery(value)
                            },
                            onAutoRefreshChange = { value ->
                                autoRefresh = value
                                ModuleSettings.setAutoRefreshOnResume(value)
                            },
                            onVerboseLoggingChange = { value ->
                                verboseLogging = value
                                ModuleSettings.setVerboseLogging(value)
                            },
                            onAiExplainChange = { value ->
                                aiExplain = value
                                ModuleSettings.setComputAiExplainEnabled(value)
                            },
                            onOpenAccessibility = {
                                context.startActivity(Intent(context, AccessibilityManagerActivity::class.java))
                            },
                            onRestartService = {
                                WatchdogManager.attemptRestart(context)
                                Toast.makeText(context, "Restart requested", Toast.LENGTH_SHORT).show()
                            },
                            onClearUpdateBanner = {
                                ModuleSettings.clearPendingUpdate()
                                Toast.makeText(context, "Update banner dismissed", Toast.LENGTH_SHORT).show()
                            },
                            onRevokeTrusted = { showRevokeDialog = true }
                        )
                        SettingsSection.ABOUT -> aboutSectionContent(
                            onOpenAbout = {
                                context.startActivity(Intent(context, AboutActivity::class.java))
                            }
                        )
                    }
                }
            }
            SettingsNav.Hub -> {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.settings_title),
                    onNavigateUp = null,
                    bottomInset = 112.dp,
                    listState = listState
                ) {
                    item {
                        SettingsGroup(title = "") {
                            SettingsSection.entries.forEachIndexed { index, section ->
                                if (index > 0) GroupDivider()
                                SettingsRow(
                                    icon = section.iconRes,
                                    title = stringResource(section.titleRes),
                                    summary = stringResource(section.summaryRes),
                                    onClick = {
                                        val fragActivity = context as? androidx.fragment.app.FragmentActivity
                                        if (fragActivity != null && section == SettingsSection.DEVICE_OWNER) {
                                            moe.shizuku.manager.security.AuthManager.executeWithAuth(
                                                activity = fragActivity,
                                                action = moe.shizuku.manager.security.SecuritySettings.ProtectedAction.DEVICE_OWNER,
                                                title = context.getString(R.string.security_auth_prompt_title),
                                                subtitle = context.getString(R.string.security_auth_prompt_device_owner)
                                            ) {
                                                nav = SettingsNav.Section(section)
                                            }
                                        } else if (fragActivity != null && section == SettingsSection.SECURITY && moe.shizuku.manager.security.SecuritySettings.isAuthEnabled) {
                                            moe.shizuku.manager.security.AuthManager.executeWithAuth(
                                                activity = fragActivity,
                                                action = moe.shizuku.manager.security.SecuritySettings.ProtectedAction.APP_OPEN,
                                                title = context.getString(R.string.security_auth_prompt_title),
                                                subtitle = context.getString(R.string.settings_security_title)
                                            ) {
                                                nav = SettingsNav.Section(section)
                                            }
                                        } else {
                                            nav = SettingsNav.Section(section)
                                        }
                                    },
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            choices = localeOptions.map {
                ChoiceOption(
                    title = it.title,
                    summary = it.summary,
                    icon = R.drawable.ic_outline_translate_24
                )
            },
            selectedIndex = localeOptions.indexOfFirst { it.tag == languageTag },
            onDismiss = { showLanguageDialog = false },
            onSelect = { index ->
                val tag = localeOptions[index].tag
                prefs.edit().putString(LANGUAGE, tag).apply()
                languageTag = tag
                LocaleDelegate.defaultLocale = if (tag == "SYSTEM") {
                    LocaleDelegate.systemLocale
                } else {
                    Locale.forLanguageTag(tag)
                }
                showLanguageDialog = false
                activity?.recreate()
            }
        )
    }

    if (showNightDialog) {
        ChoiceDialog(
            title = stringResource(rikka.core.R.string.dark_theme),
            choices = nightValues.mapIndexed { index, _ ->
                ChoiceOption(
                    title = nightLabels[index],
                    icon = when (nightValues[index]) {
                        AppCompatDelegate.MODE_NIGHT_NO -> R.drawable.ic_outline_light_mode_24
                        AppCompatDelegate.MODE_NIGHT_YES -> R.drawable.ic_outline_dark_mode_24
                        else -> R.drawable.ic_settings_outline_24dp
                    }
                )
            },
            selectedIndex = when (nightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            },
            onDismiss = { showNightDialog = false },
            onSelect = { index ->
                val value = nightValues[index]
                prefs.edit().putInt(NIGHT_MODE, value).apply()
                nightMode = value
                AppCompatDelegate.setDefaultNightMode(value)
                showNightDialog = false
                activity?.recreate()
            }
        )
    }

    pendingTcpModeChange?.let { enabled ->
        AlertDialog(
            onDismissRequest = { pendingTcpModeChange = null },
            title = { Text(stringResource(R.string.settings_restart_dialog_title)) },
            text = {
                Text(
                    htmlToPlainText(
                        context.getString(R.string.settings_restart_dialog_message) +
                            if (enabled) context.getString(R.string.settings_restart_dialog_message_wifi_required) else ""
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTcpModeChange = null
                        applyTcpMode(enabled, restart = true)
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTcpModeChange = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showDhizukuDialog) {
        AlertDialog(
            onDismissRequest = { showDhizukuDialog = false },
            title = { Text(stringResource(R.string.dhizuku_warning_title)) },
            text = { Text(stringResource(R.string.dhizuku_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModuleSettings.setDhizukuEnabled(true)
                        dhizukuEnabled = true
                        showDhizukuDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDhizukuDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showModuleModeDialog) {
        val moduleModes = listOf(
            ModuleSettings.AccessMode.SAFE,
            ModuleSettings.AccessMode.CUSTOM,
            ModuleSettings.AccessMode.FULL
        )
        ChoiceDialog(
            title = stringResource(R.string.modules_access_mode),
            choices = moduleModes.map {
                ChoiceOption(
                    title = stringResource(it.labelRes),
                    summary = stringResource(it.summaryRes),
                    icon = R.drawable.ic_adb_24dp
                )
            },
            selectedIndex = moduleModes.indexOf(moduleAccessMode),
            onDismiss = { showModuleModeDialog = false },
            onSelect = { index ->
                val mode = moduleModes[index]
                ModuleSettings.setAccessMode(mode)
                moduleAccessMode = mode
                showModuleModeDialog = false
            }
        )
    }

    if (showCustomPermissionsDialog) {
        CustomPermissionsDialog(
            value = customPermissions,
            onDismiss = { showCustomPermissionsDialog = false },
            onSave = { value ->
                ModuleSettings.setCustomPermissions(value)
                customPermissions = value
                showCustomPermissionsDialog = false
            }
        )
    }

    // AI Provider manager - early-returned at the top of this composable.

    if (showMissingPermissionDialog) {
        val serviceRunning = Shizuku.pingBinder()
        val grantCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog(
            onDismissRequest = { showMissingPermissionDialog = false },
            title = { Text(stringResource(R.string.settings_start_on_boot_adb_missing_permission_title)) },
            text = {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    if (!serviceRunning) {
                        Text(stringResource(R.string.settings_start_on_boot_adb_not_running))
                    } else {
                        Text(stringResource(R.string.settings_start_on_boot_adb_grant_failed))
                        Spacer(Modifier.height(12.dp))
                        MonospaceLog(text = grantCommand)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_start_on_boot_adb_missing_permission_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!tcpMode) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.settings_start_on_boot_adb_warning_no_tcp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (serviceRunning) {
                    TextButton(
                        onClick = {
                            ClipboardUtils.put(context, grantCommand)
                            showMissingPermissionDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.settings_start_on_boot_adb_missing_permission_copy))
                    }
                } else {
                    TextButton(
                        onClick = { showMissingPermissionDialog = false }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (showUnsafeDialog) {
        AlertDialog(
            onDismissRequest = { showUnsafeDialog = false },
            title = { Text(stringResource(R.string.unsafe_warning_title)) },
            text = { Text(stringResource(R.string.unsafe_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsafeDialog = false
                        connectorEnabled = true
                        ModuleSettings.setConnectorEnabled(true)
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsafeDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.lab_revoke_trusted_warning_title)) },
            text = { Text(stringResource(R.string.lab_revoke_trusted_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        ModuleSettings.clearTrustedModules()
                        Toast.makeText(context, "Trusted modules revoked", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

private fun LazyListScope.applicationSectionContent(
    rooted: Boolean,
    startOnBoot: Boolean,
    adbStartOnBoot: Boolean,
    tcpMode: Boolean,
    watchdog: Boolean,
    dhizukuEnabled: Boolean,
    notifyDeath: Boolean,
    wifiReassert: Boolean,
    compatStub: Boolean,
    autoDisableUsbDebugging: Boolean,
    onStartOnBootChange: (Boolean) -> Unit,
    onAdbStartOnBootChange: (Boolean) -> Unit,
    onWatchdogChange: (Boolean) -> Unit,
    onDhizukuToggle: (Boolean) -> Unit,
    onNotifyDeathChange: (Boolean) -> Unit,
    onWifiReassertChange: (Boolean) -> Unit,
    onCompatStubChange: (Boolean) -> Unit,
    onAutoDisableUsbDebuggingChange: (Boolean) -> Unit,
    onTcpModeChange: (Boolean) -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.settings_application)) {
            SectionHeader(stringResource(R.string.settings_startup))
            if (rooted) {
                SwitchSettingsRow(
                    icon = R.drawable.ic_server_restart,
                    title = stringResource(R.string.settings_start_on_boot),
                    summary = stringResource(R.string.settings_start_on_boot_summary),
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SwitchSettingsRow(
                    icon = R.drawable.ic_wadb_24,
                    title = stringResource(R.string.settings_start_on_boot_adb),
                    summary = stringResource(
                        if (tcpMode) R.string.settings_start_on_boot_adb_summary
                        else R.string.settings_start_on_boot_adb_summary_no_tcp
                    ),
                    checked = adbStartOnBoot,
                    onCheckedChange = onAdbStartOnBootChange
                )
            }
            GroupDivider()
            SectionHeader(stringResource(R.string.settings_service_group))
            SwitchSettingsRow(
                icon = R.drawable.ic_server_restart,
                title = stringResource(R.string.error_protect_title),
                summary = stringResource(R.string.error_protect_summary),
                checked = watchdog,
                onCheckedChange = onWatchdogChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = stringResource(R.string.dhizuku_mode_title),
                summary = stringResource(R.string.dhizuku_mode_summary),
                checked = dhizukuEnabled,
                onCheckedChange = onDhizukuToggle
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_outline_notifications_active_24,
                title = stringResource(R.string.lab_notify_death_title),
                summary = stringResource(R.string.lab_notify_death_summary),
                checked = notifyDeath,
                onCheckedChange = onNotifyDeathChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_adb_24dp,
                title = stringResource(R.string.settings_wifi_reassert_title),
                summary = stringResource(R.string.settings_wifi_reassert_summary),
                checked = wifiReassert,
                onCheckedChange = onWifiReassertChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_server_restart,
                title = stringResource(R.string.settings_compat_stub),
                summary = stringResource(R.string.settings_compat_stub_summary),
                checked = compatStub,
                onCheckedChange = onCompatStubChange
            )
            SwitchSettingsRow(
                icon = R.drawable.ic_adb_24dp,
                title = stringResource(R.string.settings_auto_disable_usb_debugging),
                summary = stringResource(R.string.settings_auto_disable_usb_debugging_summary),
                checked = autoDisableUsbDebugging,
                onCheckedChange = onAutoDisableUsbDebuggingChange
            )
            SwitchSettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = stringResource(R.string.settings_tcp_mode),
                summary = stringResource(R.string.settings_tcp_mode_summary),
                checked = tcpMode,
                onCheckedChange = onTcpModeChange
            )
        }
    }
}

private fun LazyListScope.appearanceSectionContent(
    languageSummary: String,
    nightSummary: String,
    nightMode: Int,
    blackNightTheme: Boolean,
    useSystemColor: Boolean,
    classicNav: Boolean,
    onLanguageClick: () -> Unit,
    onNightClick: () -> Unit,
    onBlackNightChange: (Boolean) -> Unit,
    onUseSystemColorChange: (Boolean) -> Unit,
    onClassicNavChange: (Boolean) -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.settings_language)) {
            SettingsRow(
                icon = R.drawable.ic_outline_translate_24,
                title = stringResource(R.string.settings_language),
                summary = languageSummary,
                onClick = onLanguageClick
            )
        }
    }
    item {
        SettingsGroup(title = stringResource(R.string.settings_appearance)) {
            SettingsRow(
                icon = R.drawable.ic_outline_dark_mode_24,
                title = stringResource(rikka.core.R.string.dark_theme),
                summary = nightSummary,
                onClick = onNightClick
            )
            if (nightMode != AppCompatDelegate.MODE_NIGHT_NO) {
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_outline_dark_mode_24,
                    title = stringResource(R.string.settings_black_night_theme),
                    summary = stringResource(R.string.settings_black_night_theme_summary),
                    checked = blackNightTheme,
                    onCheckedChange = onBlackNightChange
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_settings_outline_24dp,
                    title = stringResource(R.string.settings_use_system_color),
                    checked = useSystemColor,
                    onCheckedChange = onUseSystemColorChange
                )
            }
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_system_icon,
                title = stringResource(R.string.settings_classic_nav),
                summary = stringResource(R.string.settings_classic_nav_summary),
                checked = classicNav,
                onCheckedChange = onClassicNavChange
            )
        }
    }
}

private fun LazyListScope.modulesSectionContent(
    moduleAccessMode: ModuleSettings.AccessMode,
    moduleBackground: Boolean,
    recommandWebUi: Boolean,
    recommandAction: Boolean,
    onAccessModeClick: () -> Unit,
    onCustomPermissionsClick: () -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    onRecommandWebUiChange: (Boolean) -> Unit,
    onRecommandActionChange: (Boolean) -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.modules_settings_title)) {
            SettingsRow(
                icon = R.drawable.ic_settings_outline_24dp,
                title = stringResource(R.string.modules_access_mode),
                summary = stringResource(moduleAccessMode.labelRes),
                onClick = onAccessModeClick
            )
            if (moduleAccessMode == ModuleSettings.AccessMode.CUSTOM) {
                GroupDivider()
                SettingsRow(
                    icon = R.drawable.ic_add_24,
                    title = stringResource(R.string.modules_custom_permissions),
                    summary = stringResource(R.string.modules_custom_permissions_summary),
                    onClick = onCustomPermissionsClick
                )
            }
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_outline_play_arrow_24,
                title = stringResource(R.string.modules_background_actions),
                summary = stringResource(R.string.modules_background_actions_summary),
                checked = moduleBackground,
                onCheckedChange = onBackgroundChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_warning_24,
                title = stringResource(R.string.modules_recommand_webui),
                summary = stringResource(R.string.modules_recommand_webui_summary),
                checked = recommandWebUi,
                onCheckedChange = onRecommandWebUiChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_warning_24,
                title = stringResource(R.string.modules_recommand_action),
                summary = stringResource(R.string.modules_recommand_action_summary),
                checked = recommandAction,
                onCheckedChange = onRecommandActionChange
            )
        }
    }
}

private fun LazyListScope.updatesSectionContent(
    onUpdateSettingsClick: () -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.settings_update_group_title)) {
            SettingsRow(
                icon = R.drawable.ic_settings_outline_24dp,
                title = stringResource(R.string.update_settings_title),
                summary = stringResource(R.string.update_settings_catalog_enabled_summary),
                onClick = onUpdateSettingsClick
            )
        }
    }
    item {
        AppUpdateSettingsGroup()
    }
}

private fun LazyListScope.aiSectionContent(
    aiProvidersVersion: Int,
    computAiBaseUrl: String?,
    computRecommand: Boolean,
    onOpenAiManager: () -> Unit,
    onComputRecommandChange: (Boolean) -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.comput_settings)) {
            SettingsRow(
                icon = R.drawable.ic_code_24dp,
                title = stringResource(R.string.comput_ai_provider_title),
                summary = aiProvidersVersion.let {
                    AiProviderRepository.getActive()?.let { active ->
                        computProviderSummary(
                            active.name,
                            active.model.ifBlank { AiExplainUtil.resolveModel(active.baseUrl) },
                        )
                    } ?: computAiBaseUrl
                },
                onClick = onOpenAiManager
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_warning_24,
                title = stringResource(R.string.comput_recommand_title),
                summary = stringResource(R.string.comput_recommand_summary),
                checked = computRecommand,
                onCheckedChange = onComputRecommandChange
            )
        }
    }
}

private fun LazyListScope.backupsSectionContent(
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.backup_section_title)) {
            SettingsRow(
                icon = R.drawable.ic_outline_arrow_upward_24,
                title = stringResource(R.string.backup_title),
                summary = stringResource(R.string.backup_summary),
                onClick = onBackup
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_server_restart,
                title = stringResource(R.string.restore_title),
                summary = stringResource(R.string.restore_summary),
                onClick = onRestore
            )
        }
    }
}

private fun LazyListScope.automationSectionContent(
    context: Context,
    connectorEnabled: Boolean,
    onConnectorToggle: (Boolean) -> Unit,
    onCopy: (String) -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.shizuku_connectors_title)) {
            SwitchSettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = stringResource(R.string.shizuku_connectors_title),
                summary = stringResource(R.string.shizuku_connectors_summary),
                checked = connectorEnabled,
                onCheckedChange = onConnectorToggle
            )
        }
    }
    item {
        SettingsGroup(title = stringResource(R.string.automation_tasker_macrodroid_title)) {
            Text(
                text = stringResource(R.string.automation_dialog_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = stringResource(R.string.automation_plugin_method_title),
                summary = stringResource(R.string.automation_plugin_method_summary)
            )
            GroupDivider()
            SectionHeader(stringResource(R.string.automation_dialog_actions_header))
            listOf(
                "com.hamondev.shevery.action.START_SERVER" to stringResource(R.string.automation_action_start),
                "com.hamondev.shevery.action.STOP_SERVER" to stringResource(R.string.automation_action_stop),
                "com.hamondev.shevery.action.RESTART_SERVER" to stringResource(R.string.automation_action_restart),
                "com.hamondev.shevery.action.TOGGLE_SERVER" to stringResource(R.string.automation_action_toggle),
            ).forEach { (action, label) ->
                SettingsRow(
                    icon = R.drawable.ic_outline_play_arrow_24,
                    title = label,
                    summary = action,
                    onClick = { onCopy(action) }
                )
                GroupDivider()
            }
            SectionHeader(stringResource(R.string.automation_dialog_target_header))
            SettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = stringResource(R.string.automation_package_label),
                summary = context.packageName,
                onClick = { onCopy(context.packageName) }
            )
        }
    }
}

private fun LazyListScope.extrasSectionContent(
    context: Context,
    notifyRecovery: Boolean,
    autoRefresh: Boolean,
    verboseLogging: Boolean,
    aiExplain: Boolean,
    onNotifyRecoveryChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onAiExplainChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRestartService: () -> Unit,
    onClearUpdateBanner: () -> Unit,
    onRevokeTrusted: () -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.accessibility_manager_lab_group)) {
            SettingsRow(
                icon = R.drawable.ic_system_icon,
                title = stringResource(R.string.accessibility_manager_lab_title),
                summary = stringResource(R.string.accessibility_manager_lab_summary),
                onClick = onOpenAccessibility
            )
        }
    }

    item {
        SettingsGroup(title = stringResource(R.string.lab_service_behavior_title)) {
            SwitchSettingsRow(
                icon = R.drawable.ic_outline_notifications_active_24,
                title = stringResource(R.string.lab_notify_recovery_title),
                summary = stringResource(R.string.lab_notify_recovery_summary),
                checked = notifyRecovery,
                onCheckedChange = onNotifyRecoveryChange
            )
            GroupDivider()
            SwitchSettingsRow(
                icon = R.drawable.ic_server_restart,
                title = stringResource(R.string.lab_auto_refresh_title),
                summary = stringResource(R.string.lab_auto_refresh_summary),
                checked = autoRefresh,
                onCheckedChange = onAutoRefreshChange
            )
        }
    }

    item {
        SettingsGroup(title = stringResource(R.string.lab_debugging_title)) {
            SwitchSettingsRow(
                icon = R.drawable.ic_adb_24dp,
                title = stringResource(R.string.lab_verbose_logging_title),
                summary = stringResource(R.string.lab_verbose_logging_summary),
                checked = verboseLogging,
                onCheckedChange = onVerboseLoggingChange
            )
        }
    }

    item {
        SettingsGroup(title = stringResource(R.string.lab_ai_title)) {
            SwitchSettingsRow(
                icon = R.drawable.ic_code_24dp,
                title = stringResource(R.string.lab_ai_explain_title),
                summary = stringResource(R.string.lab_ai_explain_summary),
                checked = aiExplain,
                onCheckedChange = onAiExplainChange
            )
        }
    }

    item {
        SettingsGroup(title = stringResource(R.string.lab_maintenance_title)) {
            SettingsRow(
                icon = R.drawable.ic_server_restart,
                title = stringResource(R.string.lab_restart_service_title),
                summary = stringResource(R.string.lab_restart_service_summary),
                onClick = onRestartService
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_notifications_active_24,
                title = stringResource(R.string.lab_clear_update_title),
                summary = stringResource(R.string.lab_clear_update_summary),
                onClick = onClearUpdateBanner
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_warning_24,
                title = stringResource(R.string.lab_revoke_trusted_title),
                summary = stringResource(R.string.lab_revoke_trusted_summary),
                onClick = onRevokeTrusted
            )
        }
    }
}

private fun LazyListScope.aboutSectionContent(
    onOpenAbout: () -> Unit
) {
    item {
        SettingsGroup(title = stringResource(R.string.action_about)) {
            val context = LocalContext.current
            val versionName = remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                } catch (e: Exception) { "" }
            }
            SettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = stringResource(R.string.app_name),
                summary = if (versionName.isNotBlank()) "v$versionName" else null,
                onClick = onOpenAbout
            )
        }
    }

    item {
        AlsoTryGroup()
    }
}

@Composable
private fun AlsoTryGroup() {
    val context = LocalContext.current
    val dayStamp = remember { AlsoTry.dayStamp() }
    val githubPicks = remember { AlsoTry.dailyPick(AlsoTry.githubDaily) }
    val shizukuPicks = remember { AlsoTry.dailyPick(AlsoTry.shizukuDaily) }

    SettingsGroup(title = "Also Try...") {
        Text(
            text = "Hand-picked open source Android apps that pair well with Shevery. The two lists below refresh every day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SectionHeader("Featured")
        AlsoTryRows(apps = AlsoTry.featured, context = context)

        GroupDivider()
        SectionHeader("Kotlin apps on GitHub · $dayStamp")
        AlsoTryRows(apps = githubPicks, context = context)

        GroupDivider()
        SectionHeader("Apps that need Shizuku · $dayStamp")
        AlsoTryRows(apps = shizukuPicks, context = context)
    }
}

@Composable
private fun AlsoTryRows(
    apps: List<AlsoTryApp>,
    context: Context
) {
    apps.forEachIndexed { index, app ->
        if (index > 0) GroupDivider()
        SettingsRow(
            icon = null,
            title = app.name,
            summary = app.summary,
            onClick = { CustomTabsHelper.launchUrlOrCopy(context, app.url) }
        )
    }
}

private data class LocaleOption(
    val tag: String,
    val title: String,
    val summary: String?
)private fun buildLocaleOptions(context: android.content.Context, currentTag: String): List<LocaleOption> {
    val localeTags = ShizukuLocales.LOCALES
    val displayLocaleTags = ShizukuLocales.DISPLAY_LOCALES
    val currentLocale = ShizukuSettings.getLocale()

    return localeTags.mapIndexed { index, tag ->
        if (index == 0) {
            LocaleOption(tag.toString(), context.getString(rikka.core.R.string.follow_system), null)
        } else {
            val locale = Locale.forLanguageTag(displayLocaleTags[index].toString())
            val localeName = if (!TextUtils.isEmpty(locale.script)) {
                locale.getDisplayScript(locale)
            } else {
                locale.getDisplayName(locale)
            }
            val localizedLocaleName = if (!TextUtils.isEmpty(locale.script)) {
                locale.getDisplayScript(currentLocale)
            } else {
                locale.getDisplayName(currentLocale)
            }
            LocaleOption(
                tag = tag.toString(),
                title = if (tag.toString() == currentTag) localizedLocaleName else localeName,
                summary = if (tag.toString() == currentTag || localeName == localizedLocaleName) {
                    null
                } else {
                    localizedLocaleName
                }
            )
        }
    }
}

@Composable
private fun CustomPermissionsDialog(
    value: ModuleSettings.CustomPermissions,
    onDismiss: () -> Unit,
    onSave: (ModuleSettings.CustomPermissions) -> Unit
) {
    var draft by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modules_custom_permissions)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SwitchSettingsRow(
                    icon = R.drawable.ic_outline_play_arrow_24,
                    title = stringResource(R.string.modules_permission_action),
                    summary = stringResource(R.string.modules_permission_action_summary),
                    checked = draft.action,
                    onCheckedChange = { draft = draft.copy(action = it) }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_terminal_24,
                    title = stringResource(R.string.modules_permission_service),
                    summary = stringResource(R.string.modules_permission_service_summary),
                    checked = draft.service,
                    onCheckedChange = { draft = draft.copy(service = it) }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_code_24dp,
                    title = stringResource(R.string.modules_permission_web_bridge),
                    summary = stringResource(R.string.modules_permission_web_bridge_summary),
                    checked = draft.webBridge,
                    onCheckedChange = { enabled ->
                        draft = if (enabled) {
                            draft.copy(webBridge = true, webNetwork = false)
                        } else {
                            draft.copy(webBridge = false)
                        }
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_baseline_link_24,
                    title = stringResource(R.string.modules_permission_web_network),
                    summary = stringResource(R.string.modules_permission_web_network_summary),
                    checked = draft.webNetwork,
                    onCheckedChange = { enabled ->
                        draft = if (enabled) {
                            draft.copy(webNetwork = true, webBridge = false)
                        } else {
                            draft.copy(webNetwork = false)
                        }
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_outline_arrow_upward_24,
                    title = stringResource(R.string.modules_permission_web_download),
                    summary = stringResource(R.string.modules_permission_web_download_summary),
                    checked = draft.webDownload,
                    onCheckedChange = { draft = draft.copy(webDownload = it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}

private data class ChoiceOption(
    val title: String,
    val summary: String? = null,
    @param:androidx.annotation.DrawableRes val icon: Int? = null
)

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<ChoiceOption>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                choices.forEachIndexed { index, choice ->
                    SettingsRow(
                        icon = choice.icon,
                        title = choice.title,
                        summary = choice.summary,
                        onClick = { onSelect(index) },
                        trailing = {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}
