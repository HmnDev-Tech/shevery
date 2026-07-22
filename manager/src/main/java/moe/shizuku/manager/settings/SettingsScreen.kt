@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import moe.shizuku.manager.ktx.isComponentEnabled
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow
import moe.shizuku.manager.ui.compose.htmlToPlainText
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import moe.shizuku.manager.utils.BackupRestoreUtil


@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val packageManager = context.packageManager
    val componentName = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)

    val prefs = ShizukuSettings.getPreferences()

    var startOnBoot by remember {
        mutableStateOf(packageManager.isComponentEnabled(componentName))
    }
    var errorProtect by remember {
        mutableStateOf(ModuleSettings.isErrorProtectEnabled())
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
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNightDialog by remember { mutableStateOf(false) }
    var showModuleModeDialog by remember { mutableStateOf(false) }
    var showCustomPermissionsDialog by remember { mutableStateOf(false) }

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
    var computApiKey by remember {
        mutableStateOf(ModuleSettings.getComputApiKey())
    }
    var computRecommand by remember {
        mutableStateOf(ModuleSettings.isComputRecommandEnabled())
    }
    var computGeminiModel by remember {
        mutableStateOf(ModuleSettings.getComputGeminiModel())
    }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showGeminiModelDialog by remember { mutableStateOf(false) }
    var recreateTick by remember { mutableIntStateOf(0) }
    var showUpdateSettings by remember { mutableStateOf(false) }

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
                startOnBoot = packageManager.isComponentEnabled(componentName)
                errorProtect = ModuleSettings.isErrorProtectEnabled()
                languageTag = prefs.getString(LANGUAGE, "SYSTEM") ?: "SYSTEM"
                nightMode = ShizukuSettings.getNightMode()
                blackNightTheme = ThemeHelper.isBlackNightTheme(context)
                useSystemColor = ThemeHelper.isUsingSystemColor()
                moduleAccessMode = ModuleSettings.getAccessMode()
                customPermissions = ModuleSettings.getCustomPermissions()
                moduleBackground = ModuleSettings.allowBackgroundActions()
                recommandWebUi = ModuleSettings.recommandForWebUi()
                recommandAction = ModuleSettings.recommandForAction()
                computApiKey = ModuleSettings.getComputApiKey()
                computRecommand = ModuleSettings.isComputRecommandEnabled()
                computGeminiModel = ModuleSettings.getComputGeminiModel()
                recreateTick++
            }.onFailure {
                Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val localeOptions = remember(languageTag) {
        buildLocaleOptions(context, languageTag)
    }
    val languageSummary = localeOptions.firstOrNull { it.tag == languageTag }?.summary
        ?: stringResource(rikka.core.R.string.follow_system)
    val nightValues = context.resources.getIntArray(R.array.night_mode_value).toList()
    val nightLabels = stringArrayResource(R.array.night_mode).toList()
    val nightSummary = nightLabels.getOrElse(nightValues.indexOf(nightMode)) {
        stringResource(rikka.core.R.string.follow_system)
    }
    val contributors = htmlToPlainText(context.getString(R.string.translation_contributors))

    LaunchedEffect(recreateTick) {
        if (recreateTick > 0) {
            delay(260)
            activity?.recreate()
        }
    }

    if (showUpdateSettings) {
        moe.shizuku.manager.module.update.UpdateSettingsScreen(
            onNavigateUp = { showUpdateSettings = false }
        )
    } else {
        ShizukuLazyScaffold(
            title = stringResource(R.string.settings_title),
            onNavigateUp = null
        ) {
        item {
            SettingsGroup(title = stringResource(R.string.settings_startup)) {
                SwitchSettingsRow(
                    icon = R.drawable.ic_server_restart,
                    title = stringResource(R.string.settings_start_on_boot),
                    summary = stringResource(R.string.settings_start_on_boot_summary),
                    checked = startOnBoot,
                    onCheckedChange = { enabled ->
                        packageManager.setComponentEnabled(componentName, enabled)
                        startOnBoot = packageManager.isComponentEnabled(componentName)
                    }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_service_group)) {
                SwitchSettingsRow(
                    icon = R.drawable.ic_server_restart,
                    title = stringResource(R.string.error_protect_title),
                    summary = stringResource(R.string.error_protect_summary),
                    checked = errorProtect,
                    onCheckedChange = { enabled ->
                        ModuleSettings.setErrorProtectEnabled(enabled)
                        errorProtect = ModuleSettings.isErrorProtectEnabled()
                        moe.shizuku.manager.service.WatchdogManager.reconcileService(context)
                    }
                )
                SwitchSettingsRow(
                    icon = R.drawable.ic_adb_24dp,
                    title = stringResource(R.string.settings_auto_disable_usb_debugging),
                    summary = stringResource(R.string.settings_auto_disable_usb_debugging_summary),
                    checked = autoDisableUsbDebugging,
                    onCheckedChange = { enabled ->
                        ShizukuSettings.setAutoDisableUsbDebugging(enabled)
                        autoDisableUsbDebugging = ShizukuSettings.getAutoDisableUsbDebugging()
                    }
                )
                SwitchSettingsRow(
                    icon = R.drawable.ic_baseline_link_24,
                    title = stringResource(R.string.settings_tcp_mode),
                    summary = stringResource(R.string.settings_tcp_mode_summary),
                    checked = tcpMode,
                    onCheckedChange = { enabled ->
                        ShizukuSettings.setTcpMode(enabled)
                        tcpMode = ShizukuSettings.isTcpMode()
                    }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_language)) {
                SettingsRow(
                    icon = R.drawable.ic_outline_translate_24,
                    title = stringResource(R.string.settings_language),
                    summary = languageSummary,
                    onClick = { showLanguageDialog = true }
                )
                GroupDivider()
                if (contributors.isNotBlank()) {
                    SettingsRow(
                        icon = R.drawable.ic_outline_info_24,
                        title = stringResource(R.string.settings_translation_contributors),
                        summary = contributors,
                        onClick = null
                    )
                    GroupDivider()
                }
                SettingsRow(
                    icon = R.drawable.ic_baseline_link_24,
                    title = stringResource(R.string.settings_translation),
                    summary = stringResource(
                        R.string.settings_translation_summary,
                        stringResource(R.string.app_name)
                    ),
                    onClick = {
                        CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.translation_url))
                    }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_user_interface)) {
                SettingsRow(
                    icon = R.drawable.ic_outline_dark_mode_24,
                    title = stringResource(rikka.core.R.string.dark_theme),
                    summary = nightSummary,
                    onClick = { showNightDialog = true }
                )
                if (nightMode != AppCompatDelegate.MODE_NIGHT_NO) {
                    GroupDivider()
                    SwitchSettingsRow(
                        icon = R.drawable.ic_outline_dark_mode_24,
                        title = stringResource(R.string.settings_black_night_theme),
                        summary = stringResource(R.string.settings_black_night_theme_summary),
                        checked = blackNightTheme,
                        onCheckedChange = { enabled ->
                            prefs.edit().putBoolean(KEY_BLACK_NIGHT_THEME, enabled).apply()
                            blackNightTheme = enabled
                            if (ResourceUtils.isNightMode(context.resources.configuration)) {
                                recreateTick++
                            }
                        }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GroupDivider()
                    SwitchSettingsRow(
                        icon = R.drawable.ic_settings_outline_24dp,
                        title = stringResource(R.string.settings_use_system_color),
                        checked = useSystemColor,
                        onCheckedChange = { enabled ->
                            prefs.edit().putBoolean(KEY_USE_SYSTEM_COLOR, enabled).apply()
                            useSystemColor = enabled
                            recreateTick++
                        }
                    )
                }
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.lab_features_title)) {
                SettingsRow(
                    icon = R.drawable.ic_settings_outline_24dp,
                    title = stringResource(R.string.lab_features_title),
                    summary = stringResource(R.string.lab_features_summary),
                    onClick = { context.startActivity(Intent(context, LabFeaturesActivity::class.java)) }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.modules_settings_title)) {
                SettingsRow(
                    icon = R.drawable.ic_settings_outline_24dp,
                    title = stringResource(R.string.modules_access_mode),
                    summary = stringResource(moduleAccessMode.labelRes),
                    onClick = { showModuleModeDialog = true }
                )
                if (moduleAccessMode == ModuleSettings.AccessMode.CUSTOM) {
                    GroupDivider()
                    SettingsRow(
                        icon = R.drawable.ic_add_24,
                        title = stringResource(R.string.modules_custom_permissions),
                        summary = stringResource(R.string.modules_custom_permissions_summary),
                        onClick = { showCustomPermissionsDialog = true }
                    )
                }
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_outline_play_arrow_24,
                    title = stringResource(R.string.modules_background_actions),
                    summary = stringResource(R.string.modules_background_actions_summary),
                    checked = moduleBackground,
                    onCheckedChange = { enabled ->
                        ModuleSettings.setAllowBackgroundActions(enabled)
                        moduleBackground = enabled
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_warning_24,
                    title = stringResource(R.string.modules_recommand_webui),
                    summary = stringResource(R.string.modules_recommand_webui_summary),
                    checked = recommandWebUi,
                    onCheckedChange = { enabled ->
                        ModuleSettings.setRecommandForWebUi(enabled)
                        recommandWebUi = enabled
                    }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_warning_24,
                    title = stringResource(R.string.modules_recommand_action),
                    summary = stringResource(R.string.modules_recommand_action_summary),
                    checked = recommandAction,
                    onCheckedChange = { enabled ->
                        ModuleSettings.setRecommandForAction(enabled)
                        recommandAction = enabled
                    }
                )
                GroupDivider()
                SettingsRow(
                    icon = R.drawable.ic_settings_outline_24dp,
                    title = stringResource(R.string.update_settings_title),
                    summary = stringResource(R.string.update_settings_catalog_enabled_summary),
                    onClick = { showUpdateSettings = true }
                )
            }
        }

        item {
            SettingsGroup(title = "Comput Console Settings") {
                SettingsRow(
                    icon = R.drawable.ic_code_24dp,
                    title = "Google AI Studio API Key",
                    summary = if (computApiKey.isBlank()) "Not configured (Gemini AI will not work)" else "••••••••••••••••" + computApiKey.takeLast(4),
                    onClick = { showApiKeyDialog = true }
                )
                GroupDivider()
                SettingsRow(
                    icon = R.drawable.ic_outline_info_24,
                    title = "Gemini AI Model",
                    summary = computGeminiModel,
                    onClick = { showGeminiModelDialog = true }
                )
                GroupDivider()
                SwitchSettingsRow(
                    icon = R.drawable.ic_warning_24,
                    title = "ReCommand for Comput",
                    summary = "Show a confirmation dialog before running commands in Comput console",
                    checked = computRecommand,
                    onCheckedChange = { enabled ->
                        ModuleSettings.setComputRecommandEnabled(enabled)
                        computRecommand = enabled
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Backup & Restore") {
                SettingsRow(
                    icon = R.drawable.ic_outline_arrow_upward_24,
                    title = "Backup Settings & Modules",
                    summary = "Export configuration and modules to a ZIP file",
                    onClick = {
                        backupLauncher.launch("shevery_backup_${System.currentTimeMillis()}.zip")
                    }
                )
                GroupDivider()
                SettingsRow(
                    icon = R.drawable.ic_server_restart,
                    title = "Restore Settings & Modules",
                    summary = "Import configuration and modules from a ZIP file",
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )
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
            selectedIndex = nightValues.indexOf(nightMode),
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

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(computApiKey) }
        var keyVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Google AI Studio API Key") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (keyVisible) R.drawable.ic_close_24 else R.drawable.ic_outline_info_24
                        androidx.compose.material3.IconButton(onClick = { keyVisible = !keyVisible }) {
                            moe.shizuku.manager.ui.compose.ShizukuIcon(
                                icon = image,
                                contentDescription = if (keyVisible) "Hide API key" else "Show API key"
                            )
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModuleSettings.setComputApiKey(tempKey)
                        computApiKey = tempKey
                        showApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (showGeminiModelDialog) {
        val modelOptions = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite")
        ChoiceDialog(
            title = "Gemini AI Model",
            choices = modelOptions.map {
                ChoiceOption(
                    title = it,
                    summary = if (it == "gemini-3.5-flash") "High performance coding & agentic (Recommended)" else "Lightweight high speed model",
                    icon = R.drawable.ic_outline_info_24
                )
            },
            selectedIndex = modelOptions.indexOf(computGeminiModel),
            onDismiss = { showGeminiModelDialog = false },
            onSelect = { index ->
                val selected = modelOptions[index]
                ModuleSettings.setComputGeminiModel(selected)
                computGeminiModel = selected
                showGeminiModelDialog = false
            }
        )
    }
}

private data class LocaleOption(
    val tag: String,
    val title: String,
    val summary: String?
)

private fun buildLocaleOptions(context: android.content.Context, currentTag: String): List<LocaleOption> {
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
