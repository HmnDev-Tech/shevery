@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.guide.GuideTopic
import moe.shizuku.manager.guide.ShakeGuideHost
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbArm
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun AdbDiscoveryDialog(
    onDismissRequest: () -> Unit,
    onStartService: (port: Int) -> Unit
) {
    val context = LocalContext.current
    var startCommitted by remember { mutableStateOf(false) }

    var activePort by remember {
        mutableStateOf(
            if (ShizukuSettings.isTcpMode()) AdbStarter.TCP_MODE_PORT else -1
        )
    }

    LaunchedEffect(Unit) {
        // Arm adbd via WRITE_SECURE_SETTINGS or Device Owner setGlobalSetting —
        // AdbArm picks whichever this install has; no-op when neither exists.
        AdbArm.arm(context)

        withContext(Dispatchers.IO) {
            val livePort = EnvironmentUtils.getLiveAdbTcpPort()
            if (livePort in 1..65535) {
                withContext(Dispatchers.Main) {
                    activePort = livePort
                    if (ShizukuSettings.isTcpMode() && !startCommitted) {
                        startCommitted = true
                        onStartService(livePort)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { discoveredPort ->
            if (discoveredPort in 1..65535 && !startCommitted) {
                startCommitted = true
                onStartService(discoveredPort)
            }
        }
        adbMdns.start()

        onDispose {
            adbMdns.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_adb_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.dialog_adb_discovery))
        },
        text = {
            ShakeGuideHost(GuideTopic.WIRELESS_DEBUGGING)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dialog_adb_discovery_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.dialog_adb_discovery_message_toggle_wireless_debugging),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activePort in 1..65535) {
                    OutlinedButton(
                        onClick = {
                            if (!startCommitted) {
                                startCommitted = true
                                onStartService(activePort)
                            }
                        }
                    ) {
                        Text("$activePort")
                    }
                }
                Button(
                    onClick = {
                        SettingsHelper.launchWirelessDebugging(context)
                    }
                ) {
                    Text(stringResource(R.string.development_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}
