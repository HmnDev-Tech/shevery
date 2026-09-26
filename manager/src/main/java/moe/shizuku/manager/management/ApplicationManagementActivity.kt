@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.management

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
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

private data class AppEntry(
    val packageInfo: PackageInfo,
    val title: String,
    val granted: Boolean
)

class ApplicationManagementActivity : AppActivity() {

    private val viewModel by appsViewModel()
    private val permissionTick = mutableIntStateOf(0)

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

            val appEntries = remember(packages, tick) {
                packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val uid = appInfo.uid
                    val userId = UserHandleCompat.getUserId(uid)
                    val label = appInfo.loadLabel(pm).toString()
                    val title = if (userId != UserHandleCompat.myUserId()) {
                        val userInfo = ShizukuSystemApis.getUserInfo(userId)
                        "$label - ${userInfo.name} ($userId)"
                    } else {
                        label
                    }
                    val granted = try {
                        AuthorizationManager.granted(pkg.packageName, uid)
                    } catch (_: SecurityException) {
                        false
                    }
                    AppEntry(pkg, title, granted)
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
                    actions = {
                        if (packages.isNotEmpty()) {
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
                                )
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
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 1.dp
                                    ) {
                                        Column {
                                            filteredEntries.forEachIndexed { index, entry ->
                                                AppPermissionRow(
                                                    entry = entry,
                                                    tick = tick,
                                                    onLimitedAdb = { showAdbLimitedDialog = true },
                                                    onPermissionChanged = {
                                                        setResult(RESULT_OK)
                                                        permissionTick.intValue++
                                                        viewModel.load(onlyCount = true)
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
        permissionTick.intValue++
    }
}

@Composable
private fun AppPermissionRow(
    entry: AppEntry,
    tick: Int,
    onLimitedAdb: () -> Unit,
    onPermissionChanged: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val packageInfo = entry.packageInfo
    val applicationInfo = packageInfo.applicationInfo ?: return
    val uid = applicationInfo.uid
    val packageName = packageInfo.packageName
    var granted by remember(packageName, uid, tick) {
        mutableStateOf(entry.granted)
    }
    val title = entry.title
    val icon = remember(packageName) {
        applicationInfo.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { toggle() })
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
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                onCheckedChange = null
            )
        }
    }
}

private fun ApplicationInfo.requiresRoot(): Boolean {
    return metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
}
