@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.management

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import moe.shizuku.manager.ShizukuSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import rikka.html.text.HtmlCompat
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku
import java.util.Objects

private enum class AppFilter { ALL, ALLOWED, DENIED }
private enum class AddAppAccessMode { ALLOW, SESSION_ONLY, DENY }

private data class AppEntry(
    val packageInfo: PackageInfo,
    val title: String,
    val granted: Boolean,
    val isOneTime: Boolean = false,
    val isClient: Boolean = false,
    val isSystemApp: Boolean = false
)

class ApplicationManagementActivity : AppActivity() {

    private val viewModel by appsViewModel()
    private val permissionTick = mutableIntStateOf(0)

    /** Relays the detail screen's RESULT_OK so the home screen's app counter refreshes too. */
    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.PERMISSIONS) &&
            !AuthManager.isActionAuthenticatedInSession(SecuritySettings.ProtectedAction.PERMISSIONS)) {
            AuthManager.authenticate(
                activity = this,
                title = getString(R.string.security_auth_prompt_title),
                subtitle = getString(R.string.security_action_permissions),
                onResult = { authenticated ->
                    if (authenticated) {
                        AuthManager.markActionAuthenticatedInSession(SecuritySettings.ProtectedAction.PERMISSIONS)
                    } else {
                        finish()
                    }
                }
            )
        }

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        viewModel.packages.observe(this) {
            if (it.status == Status.ERROR) {
                finish()
                val tr = it.error
                Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                tr.printStackTrace()
            }
        }
        if (viewModel.packages.value == null) {
            viewModel.load()
        }

        Shizuku.addBinderDeadListener(binderDeadListener)

        setContent {
            val context = LocalContext.current
            val pm = context.packageManager
            val packagesResource by viewModel.packages.observeAsState()
            val packages = packagesResource?.data.orEmpty()
            val tick = permissionTick.intValue
            var showAdbLimitedDialog by rememberSaveable { mutableStateOf(false) }

            var searchQuery by rememberSaveable { mutableStateOf("") }
            var selectedFilter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
            var whitelistMode by remember { mutableStateOf(ShizukuSettings.isShizukuWhitelistEnabled()) }
            var showAddAppDialog by rememberSaveable { mutableStateOf(false) }
            var addAppPackage by rememberSaveable { mutableStateOf("") }
            var addAppAccessMode by rememberSaveable { mutableStateOf(AddAppAccessMode.ALLOW) }
            var addAppGrantRuntime by rememberSaveable { mutableStateOf(false) }
            var addAppOpenDetails by rememberSaveable { mutableStateOf(false) }

            val labelCache = remember { HashMap<String, String>() }
            val appEntries = remember(packages, tick) {
                packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val uid = appInfo.uid
                    val userId = UserHandleCompat.getUserId(uid)
                    val cacheKey = "${pkg.packageName}:$uid"
                    val label = labelCache.getOrPut(cacheKey) {
                        runCatching { appInfo.loadLabel(pm).toString() }
                            .getOrDefault(pkg.packageName)
                            .ifBlank { pkg.packageName }
                    }
                    val title = if (userId != UserHandleCompat.myUserId()) {
                        val userInfo = ShizukuSystemApis.getUserInfo(userId)
                        "$label - ${userInfo.name} ($userId)"
                    } else {
                        label
                    }
                    val granted = try {
                        AuthorizationManager.granted(pkg.packageName, uid)
                    } catch (_: Throwable) {
                        false
                    }
                    val isOneTime = AuthorizationManager.isOneTime(uid)
                    val isClient = AuthorizationManager.isClientPackage(pkg)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    AppEntry(pkg, title, granted, isOneTime, isClient, isSystemApp)
                }.sortedWith(
                    compareByDescending<AppEntry> { it.granted }
                        .thenByDescending { it.isClient }
                        .thenBy { it.isSystemApp }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
            }

            fun resolveTargetPackageInfo(pkgName: String): PackageInfo? {
                val trimmed = pkgName.trim()
                if (trimmed.isEmpty()) return null
                val pmFlags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
                return runCatching {
                    packageManager.getPackageInfo(trimmed, pmFlags)
                }.getOrElse {
                    runCatching {
                        ShizukuSystemApis.getPackageInfo(trimmed, pmFlags.toLong(), 0)
                    }.getOrNull()
                }
            }

            fun applyAddApp(mode: AddAppAccessMode = addAppAccessMode) {
                val pkg = addAppPackage.trim()
                if (pkg.isEmpty()) return

                val targetPi = resolveTargetPackageInfo(pkg)
                val uid = targetPi?.applicationInfo?.uid ?: runCatching {
                    packageManager.getPackageUid(pkg, 0)
                }.getOrDefault(-1)

                if (uid == -1) {
                    Toast.makeText(
                        this@ApplicationManagementActivity,
                        R.string.app_management_add_app_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val userId = UserHandleCompat.getUserId(uid)
                try {
                    AuthorizationManager.addManualPackage(pkg)
                    when (mode) {
                        AddAppAccessMode.ALLOW -> AuthorizationManager.grant(pkg, uid)
                        AddAppAccessMode.SESSION_ONLY -> AuthorizationManager.grantSessionOnly(pkg, uid, true)
                        AddAppAccessMode.DENY -> AuthorizationManager.deny(pkg, uid)
                    }

                    if (addAppGrantRuntime && mode != AddAppAccessMode.DENY && targetPi != null) {
                        targetPi.requestedPermissions.orEmpty().forEach { perm ->
                            val permInfo = runCatching { packageManager.getPermissionInfo(perm, 0) }.getOrNull()
                            if (permInfo != null && (permInfo.protectionLevel and 0xf) == PermissionInfo.PROTECTION_DANGEROUS) {
                                runCatching {
                                    ShizukuSystemApis.grantRuntimePermission(pkg, perm, userId)
                                }
                            }
                        }
                    }

                    val shouldOpenDetails = addAppOpenDetails
                    setResult(RESULT_OK)
                    permissionTick.intValue++
                    viewModel.load()
                    Toast.makeText(
                        this@ApplicationManagementActivity,
                        if (mode != AddAppAccessMode.DENY) R.string.app_management_add_app_added
                        else R.string.app_management_add_app_deny,
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddAppDialog = false
                    addAppPackage = ""
                    if (shouldOpenDetails) {
                        detailLauncher.launch(
                            Intent(this@ApplicationManagementActivity, AppDetailActivity::class.java)
                                .putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, pkg)
                                .putExtra(AppDetailActivity.EXTRA_USER_ID, userId)
                        )
                    }
                } catch (e: SecurityException) {
                    val serverUid = try {
                        Shizuku.getUid()
                    } catch (_: Throwable) {
                        return
                    }
                    if (serverUid != 0) {
                        showAdbLimitedDialog = true
                    }
                }
            }

            val totalCount = appEntries.size
            val allowedCount = remember(appEntries) { appEntries.count { it.granted } }
            val deniedCount = totalCount - allowedCount

            val filteredEntries = remember(appEntries, searchQuery, selectedFilter) {
                val query = searchQuery.trim()
                appEntries.filter { entry ->
                    val matchesFilter = when (selectedFilter) {
                        AppFilter.ALL -> true
                        AppFilter.ALLOWED -> entry.granted
                        AppFilter.DENIED -> !entry.granted
                    }
                    val matchesQuery = query.isEmpty() ||
                        entry.title.contains(query, ignoreCase = true) ||
                        entry.packageInfo.packageName.contains(query, ignoreCase = true)
                    matchesFilter && matchesQuery
                }
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.home_app_management_title),
                    onNavigateUp = { finish() },
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                ShizukuIcon(
                                    R.drawable.ic_more_vert_24,
                                    contentDescription = stringResource(R.string.accessibility_more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.app_management_add_app)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_add_24),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showAddAppDialog = true
                                    }
                                )
                                if (packages.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.app_management_select_all)) },
                                        onClick = {
                                            menuExpanded = false
                                            val targetPackages = if (filteredEntries.size < appEntries.size) {
                                                filteredEntries.map { it.packageInfo }
                                            } else {
                                                packages
                                            }
                                            selectAll(targetPackages, true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.app_management_deselect_all)) },
                                        onClick = {
                                            menuExpanded = false
                                            val targetPackages = if (filteredEntries.size < appEntries.size) {
                                                filteredEntries.map { it.packageInfo }
                                            } else {
                                                packages
                                            }
                                            selectAll(targetPackages, false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) {
                    when {
                        packagesResource == null -> {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LoadingIndicator(Modifier.size(36.dp))
                                    Text(
                                        text = stringResource(R.string.app_management_loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        packages.isEmpty() -> {
                            item {
                                ExpressiveCard(
                                    icon = R.drawable.ic_system_icon,
                                    title = stringResource(R.string.home_app_management_title),
                                    body = stringResource(R.string.home_app_management_empty)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showAddAppDialog = true },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_add_24),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.app_management_add_app))
                                    }
                                }
                            }
                        }
                        else -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.whitelist_mode_title),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = stringResource(R.string.shizuku_whitelist_summary),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Switch(
                                                checked = whitelistMode,
                                                onCheckedChange = { checked ->
                                                    whitelistMode = checked
                                                    ShizukuSettings.setShizukuWhitelistEnabled(checked)
                                                }
                                            )
                                        }
                                    }

                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(stringResource(R.string.app_management_search_hint)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuery = "" },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Clear,
                                                        contentDescription = stringResource(R.string.app_management_clear_search)
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedFilter == AppFilter.ALL,
                                            onClick = { selectedFilter = AppFilter.ALL },
                                            label = { Text(stringResource(R.string.app_management_filter_all, totalCount)) }
                                        )
                                        FilterChip(
                                            selected = selectedFilter == AppFilter.ALLOWED,
                                            onClick = { selectedFilter = AppFilter.ALLOWED },
                                            label = { Text(stringResource(R.string.app_management_filter_allowed, allowedCount)) }
                                        )
                                        FilterChip(
                                            selected = selectedFilter == AppFilter.DENIED,
                                            onClick = { selectedFilter = AppFilter.DENIED },
                                            label = { Text(stringResource(R.string.app_management_filter_denied, deniedCount)) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            if (filteredEntries.isEmpty()) {
                                item {
                                    ExpressiveCard(
                                        icon = R.drawable.ic_system_icon,
                                        title = stringResource(R.string.app_management_no_search_results),
                                        body = stringResource(R.string.app_management_no_search_results_desc)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                searchQuery = ""
                                                selectedFilter = AppFilter.ALL
                                            },
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            Text(stringResource(R.string.app_management_clear_filters))
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = filteredEntries,
                                    key = { _, entry -> "${entry.packageInfo.packageName}:${entry.packageInfo.applicationInfo?.uid}" }
                                ) { index, entry ->
                                    val shape = when {
                                        filteredEntries.size == 1 -> RoundedCornerShape(28.dp)
                                        index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                                        index == filteredEntries.lastIndex -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                                        else -> RoundedCornerShape(0.dp)
                                    }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = shape,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 1.dp
                                    ) {
                                        Column {
                                            AppPermissionRow(
                                                entry = entry,
                                                tick = tick,
                                                onLimitedAdb = { showAdbLimitedDialog = true },
                                                onPermissionChanged = {
                                                    setResult(RESULT_OK)
                                                    permissionTick.intValue++
                                                    viewModel.load(onlyCount = true)
                                                },
                                                onOpenDetail = { pkg, userId ->
                                                    detailLauncher.launch(
                                                        Intent(this@ApplicationManagementActivity, AppDetailActivity::class.java)
                                                            .putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, pkg)
                                                            .putExtra(AppDetailActivity.EXTRA_USER_ID, userId)
                                                    )
                                                }
                                            )
                                            if (index != filteredEntries.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAdbLimitedDialog) {
                    AlertDialog(
                        onDismissRequest = { showAdbLimitedDialog = false },
                        title = {
                            Text(
                                text = stringResource(R.string.app_management_dialog_adb_is_limited_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = HtmlCompat.fromHtml(
                                    getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()),
                                    HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE
                                ).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        CustomTabsHelper.launchUrlOrCopy(
                                            this@ApplicationManagementActivity,
                                            Helps.ADB.get()
                                        )
                                    }
                                ) {
                                    Text(stringResource(R.string.home_adb_button_view_help))
                                }
                                Button(onClick = { showAdbLimitedDialog = false }) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
                if (showAddAppDialog) {
                    val allInstalledApps = remember(showAddAppDialog) {
                        AuthorizationManager.getAllInstalledPackages().mapNotNull { pi ->
                            val appInfo = pi.applicationInfo ?: return@mapNotNull null
                            val cacheKey = "${pi.packageName}:${appInfo.uid}"
                            val lbl = labelCache.getOrPut(cacheKey) {
                                runCatching { appInfo.loadLabel(pm).toString() }
                                    .getOrDefault(pi.packageName)
                                    .ifBlank { pi.packageName }
                            }
                            pi to lbl
                        }
                    }

                    val trimmedInput = addAppPackage.trim()
                    val resolvedTarget = remember(trimmedInput, allInstalledApps) {
                        if (trimmedInput.isEmpty()) {
                            null
                        } else {
                            allInstalledApps.firstOrNull { it.first.packageName.equals(trimmedInput, ignoreCase = true) }
                                ?: resolveTargetPackageInfo(trimmedInput)?.let { pi ->
                                    val lbl = pi.applicationInfo?.let {
                                        runCatching { it.loadLabel(pm).toString() }.getOrNull()
                                    }.orEmpty().ifBlank { pi.packageName }
                                    pi to lbl
                                }
                        }
                    }

                    val suggestions = remember(trimmedInput, allInstalledApps, resolvedTarget) {
                        if (trimmedInput.length < 2 || resolvedTarget?.first?.packageName.equals(trimmedInput, ignoreCase = true)) {
                            emptyList()
                        } else {
                            allInstalledApps.filter { (pi, lbl) ->
                                lbl.contains(trimmedInput, ignoreCase = true) ||
                                    pi.packageName.contains(trimmedInput, ignoreCase = true)
                            }.sortedWith(
                                compareByDescending<Pair<PackageInfo, String>> { (pi, _) ->
                                    AuthorizationManager.isClientPackage(pi)
                                }.thenBy { (pi, _) ->
                                    ((pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                                }.thenBy(String.CASE_INSENSITIVE_ORDER) { (_, lbl) -> lbl }
                            ).take(5)
                        }
                    }

                    val dangerousPermCount = remember(resolvedTarget) {
                        val pi = resolvedTarget?.first ?: return@remember 0
                        pi.requestedPermissions.orEmpty().count { perm ->
                            val info = runCatching { packageManager.getPermissionInfo(perm, 0) }.getOrNull()
                            info != null && (info.protectionLevel and 0xf) == PermissionInfo.PROTECTION_DANGEROUS
                        }
                    }

                    val alreadyInList = remember(resolvedTarget, appEntries) {
                        val pkgName = resolvedTarget?.first?.packageName ?: return@remember false
                        appEntries.any { it.packageInfo.packageName == pkgName } ||
                            AuthorizationManager.isManualPackage(pkgName)
                    }

                    AlertDialog(
                        onDismissRequest = {
                            showAddAppDialog = false
                            addAppPackage = ""
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.app_management_add_app_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextField(
                                    value = addAppPackage,
                                    onValueChange = { addAppPackage = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.app_management_add_app_hint)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Label,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (addAppPackage.isNotEmpty()) {
                                            IconButton(onClick = { addAppPackage = "" }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Clear,
                                                    contentDescription = stringResource(R.string.app_management_clear_search)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { applyAddApp() }
                                    )
                                )

                                if (suggestions.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            suggestions.forEachIndexed { idx, (pi, lbl) ->
                                                val appInfo = pi.applicationInfo
                                                val iconBmp = remember(pi.packageName) {
                                                    runCatching {
                                                        appInfo?.loadIcon(pm)?.toBitmap(64, 64)?.asImageBitmap()
                                                    }.getOrNull()
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { addAppPackage = pi.packageName }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (iconBmp != null) {
                                                        Image(
                                                            bitmap = iconBmp,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = lbl,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = pi.packageName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                if (idx < suggestions.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (resolvedTarget != null) {
                                    val (targetPi, targetLabel) = resolvedTarget
                                    val targetAppInfo = targetPi.applicationInfo
                                    val targetUid = targetAppInfo?.uid ?: -1
                                    val targetIcon = remember(targetPi.packageName) {
                                        runCatching {
                                            targetAppInfo?.loadIcon(pm)?.toBitmap(80, 80)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (targetIcon != null) {
                                                Image(
                                                    bitmap = targetIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = targetLabel,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${targetPi.packageName} • UID $targetUid",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (alreadyInList && targetUid != -1) {
                                                OutlinedButton(
                                                    onClick = {
                                                        runCatching {
                                                            AuthorizationManager.revoke(targetPi.packageName, targetUid)
                                                            AuthorizationManager.removeManualPackage(targetPi.packageName)
                                                        }
                                                        setResult(RESULT_OK)
                                                        permissionTick.intValue++
                                                        viewModel.load()
                                                        Toast.makeText(
                                                            this@ApplicationManagementActivity,
                                                            R.string.app_management_add_app_removed,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        showAddAppDialog = false
                                                        addAppPackage = ""
                                                    }
                                                ) {
                                                    Text(stringResource(R.string.app_management_add_app_remove))
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.app_management_add_app_access_mode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = addAppAccessMode == AddAppAccessMode.ALLOW,
                                        onClick = { addAppAccessMode = AddAppAccessMode.ALLOW },
                                        label = { Text(stringResource(R.string.app_management_add_app_allow)) }
                                    )
                                    FilterChip(
                                        selected = addAppAccessMode == AddAppAccessMode.SESSION_ONLY,
                                        onClick = { addAppAccessMode = AddAppAccessMode.SESSION_ONLY },
                                        label = { Text(stringResource(R.string.app_management_add_app_session)) }
                                    )
                                    FilterChip(
                                        selected = addAppAccessMode == AddAppAccessMode.DENY,
                                        onClick = { addAppAccessMode = AddAppAccessMode.DENY },
                                        label = { Text(stringResource(R.string.app_management_add_app_deny)) }
                                    )
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = addAppAccessMode != AddAppAccessMode.DENY) {
                                                    addAppGrantRuntime = !addAppGrantRuntime
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.app_management_add_app_grant_runtime),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = stringResource(
                                                        R.string.app_management_add_app_grant_runtime_desc,
                                                        dangerousPermCount
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = addAppGrantRuntime && addAppAccessMode != AddAppAccessMode.DENY,
                                                onCheckedChange = { addAppGrantRuntime = it },
                                                enabled = addAppAccessMode != AddAppAccessMode.DENY
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { addAppOpenDetails = !addAppOpenDetails }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.app_management_add_app_open_details),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = addAppOpenDetails,
                                                onCheckedChange = { addAppOpenDetails = it }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { applyAddApp() },
                                enabled = trimmedInput.isNotEmpty()
                            ) {
                                Text(stringResource(R.string.app_management_add_app_apply))
                            }
                        },
                        dismissButton = {
                            FilledTonalButton(onClick = { showAddAppDialog = false; addAppPackage = "" }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
        }
    }

    private fun selectAll(packages: List<PackageInfo>, granted: Boolean) {
        var changed = false
        packages.forEach { packageInfo ->
            val applicationInfo = packageInfo.applicationInfo ?: return@forEach
            val uid = applicationInfo.uid
            val packageName = packageInfo.packageName
            try {
                if (granted) {
                    AuthorizationManager.grant(packageName, uid)
                } else {
                    AuthorizationManager.revoke(packageName, uid)
                }
                changed = true
            } catch (_: SecurityException) {
            }
        }
        if (changed) {
            setResult(RESULT_OK)
        }
        permissionTick.intValue++
        viewModel.load(onlyCount = true)
    }

    override fun onDestroy() {
        super.onDestroy()

        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
        permissionTick.intValue++
    }
}

@Composable
private fun AppPermissionRow(
    entry: AppEntry,
    tick: Int,
    onLimitedAdb: () -> Unit,
    onPermissionChanged: () -> Unit,
    onOpenDetail: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val packageInfo = entry.packageInfo
    val applicationInfo = packageInfo.applicationInfo ?: return
    val uid = applicationInfo.uid
    val userId = UserHandleCompat.getUserId(uid)
    val packageName = packageInfo.packageName
    var granted by remember(packageName, uid, tick) {
        mutableStateOf(entry.granted)
    }
    val title = entry.title
    val icon = remember(packageName) {
        runCatching {
            applicationInfo.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
    }
    val requiresRoot = applicationInfo.requiresRoot()

    fun toggle() {
        try {
            if (granted) {
                AuthorizationManager.revoke(packageName, uid)
            } else {
                AuthorizationManager.grant(packageName, uid)
            }
            granted = !granted
            onPermissionChanged()
        } catch (_: SecurityException) {
            val serverUid = try {
                Shizuku.getUid()
            } catch (_: Throwable) {
                return
            }
            if (serverUid != 0) {
                onLimitedAdb()
            }
        }
    }

    fun makePermanent() {
        try {
            AuthorizationManager.grant(packageName, uid)
            Toast.makeText(context, R.string.app_management_made_permanent, Toast.LENGTH_SHORT).show()
            onPermissionChanged()
        } catch (_: SecurityException) {
            val serverUid = try {
                Shizuku.getUid()
            } catch (_: Throwable) {
                return
            }
            if (serverUid != 0) {
                onLimitedAdb()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onOpenDetail(packageName, userId) })
            .semantics(mergeDescendants = true) {
                this.stateDescription = if (granted) "Allowed" else "Denied"
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isOneTime && granted) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .clickable(onClick = { makePermanent() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_schedule_24dp),
                            contentDescription = stringResource(R.string.app_management_onetime_badge),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Text(
                text = packageInfo.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (requiresRoot) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.app_management_item_summary_requires_root),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Box(modifier = Modifier.clearAndSetSemantics {}) {
            ExpressiveSwitch(
                checked = granted,
                onCheckedChange = { toggle() }
            )
        }
    }
}

private fun ApplicationInfo.requiresRoot(): Boolean {
    return metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
}
