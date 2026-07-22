package moe.shizuku.manager.management

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.html.text.HtmlCompat
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku
import java.util.Objects

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
            val packagesResource by viewModel.packages.observeAsState()
            val packages = packagesResource?.data.orEmpty()
            val tick = permissionTick.intValue

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
                                            selectAll(packages, true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.app_management_deselect_all)) },
                                        onClick = {
                                            menuExpanded = false
                                            selectAll(packages, false)
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
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(Modifier.size(36.dp))
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
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 1.dp
                                ) {
                                    Column {
                                        packages.forEachIndexed { index, packageInfo ->
                                            AppPermissionRow(
                                                packageInfo = packageInfo,
                                                tick = tick,
                                                onLimitedAdb = ::showAdbLimitedDialog,
                                                onPermissionChanged = {
                                                    setResult(RESULT_OK)
                                                    permissionTick.intValue++
                                                    viewModel.load(onlyCount = true)
                                                }
                                            )
                                            if (index != packages.lastIndex) {
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

    private fun showAdbLimitedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_management_dialog_adb_is_limited_title)
            .setMessage(
                getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get())
                    .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

@Composable
private fun AppPermissionRow(
    packageInfo: PackageInfo,
    tick: Int,
    onLimitedAdb: () -> Unit,
    onPermissionChanged: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val applicationInfo = packageInfo.applicationInfo ?: return
    val uid = applicationInfo.uid
    val packageName = packageInfo.packageName
    var granted by remember(packageName, uid, tick) {
        mutableStateOf(AuthorizationManager.granted(packageName, uid))
    }
    val userId = UserHandleCompat.getUserId(uid)
    val title = remember(packageName, userId) {
        val label = applicationInfo.loadLabel(pm).toString()
        if (userId != UserHandleCompat.myUserId()) {
            val userInfo = ShizukuSystemApis.getUserInfo(userId)
            "$label - ${userInfo.name} ($userId)"
        } else {
            label
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
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
                AssistChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.app_management_item_summary_requires_root))
                    }
                )
            }
        }
        ExpressiveSwitch(
            checked = granted,
            onCheckedChange = { toggle() }
        )
    }
}

private fun ApplicationInfo.requiresRoot(): Boolean {
    return metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
}
