package moe.shizuku.manager.settings

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SwitchSettingsRow
import rikka.shizuku.Shizuku

private data class FirewallApp(val packageName: String, val label: String)

class NetworkFirewallActivity : AppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var refresh by remember { mutableStateOf(0) }
            var apps by remember { mutableStateOf(emptyList<FirewallApp>()) }
            var blocked by remember { mutableStateOf(NetworkFirewallManager.blockedPackages()) }
            var changing by remember { mutableStateOf(emptySet<String>()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                blocked = NetworkFirewallManager.blockedPackages()
                refresh++
            }
            androidx.compose.runtime.LaunchedEffect(refresh) {
                apps = withContext(Dispatchers.IO) { loadApps() }
                NetworkFirewallManager.retainInstalledPackages(apps.map { it.packageName }.toSet())
                blocked = NetworkFirewallManager.blockedPackages()
            }
            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val serverRunning = Shizuku.pingBinder()

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.network_firewall_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        SettingsGroup(title = stringResource(R.string.network_firewall_title)) {
                            Text(
                                stringResource(
                                    if (!supported) R.string.network_firewall_unsupported
                                    else R.string.network_firewall_summary
                                ),
                                modifier = Modifier.padding(16.dp)
                            )
                            if (supported && !serverRunning) {
                                GroupDivider()
                                Text(
                                    stringResource(R.string.network_firewall_server_required),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    item {
                        SettingsGroup(title = stringResource(R.string.network_firewall_apps)) {}
                    }
                    items(apps, key = { it.packageName }) { app ->
                        SwitchSettingsRow(
                            icon = R.drawable.ic_security_24dp,
                            title = app.label,
                            summary = app.packageName,
                            checked = app.packageName in blocked,
                            enabled = supported && serverRunning && app.packageName !in changing,
                            onCheckedChange = { enabled ->
                                changing = changing + app.packageName
                                scope.launch {
                                    val applied = withContext(Dispatchers.IO) {
                                        NetworkFirewallManager.setBlocked(app.packageName, enabled)
                                    }
                                    changing = changing - app.packageName
                                    if (applied) {
                                        blocked = NetworkFirewallManager.blockedPackages()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.network_firewall_change_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<FirewallApp> =
        packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .map { FirewallApp(it.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
}
