@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package moe.shizuku.manager.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.utils.ShizukuStateMachine.State
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

@Composable
internal fun HomeServicePanel(
    state: State,
    checking: Boolean,
    readFailed: Boolean,
    status: ServiceStatus,
    launchMode: Int,
    uptime: String?,
    isPrimaryUser: Boolean,
    isRooted: Boolean,
    canUseWirelessAdb: Boolean,
    onStartRoot: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onShowAdbCommand: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit
) {
    val running = state == State.RUNNING
    val hasDetails = running && status.uid >= 0
    val pending = checking || state == State.STARTING || state == State.STOPPING
    val colors = MaterialTheme.colorScheme
    val title = when (state) {
        State.STARTING -> R.string.home_server_starting
        State.STOPPING -> R.string.home_server_stopping
        State.CRASHED -> R.string.home_server_crashed
        State.RUNNING -> R.string.home_server_running
        State.STOPPED -> when {
            checking -> R.string.home_server_checking
            readFailed -> R.string.home_status_unavailable
            else -> R.string.home_server_stopped
        }
    }
    val fault = state == State.CRASHED || (readFailed && !running && !pending)
    val ink = when {
        fault -> colors.onErrorContainer
        running -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomEnd = 32.dp, bottomStart = 8.dp),
        color = when {
            fault -> colors.errorContainer
            running -> colors.primaryContainer
            else -> colors.surfaceContainerHigh
        },
        contentColor = ink
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(if (hasDetails) launchMode else R.string.home_service),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(title),
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)

            if (hasDetails) {
                HorizontalDivider(color = ink.copy(alpha = 0.24f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceMetric(stringResource(R.string.home_metric_api), "${status.apiVersion}.${status.patchVersion}")
                    ServiceMetric(stringResource(R.string.home_metric_uid), status.uid.toString())
                    ServiceMetric(stringResource(R.string.home_metric_pid),
                        status.pid?.toString() ?: stringResource(R.string.home_pid_unavailable))
                }
                if (uptime != null) {
                    Text(stringResource(R.string.home_status_uptime, uptime), style = MaterialTheme.typography.bodySmall)
                }
                if (status.apiVersion != Shizuku.getLatestServiceVersion() ||
                    status.patchVersion != ShizukuApiConstants.SERVER_PATCH_VERSION) {
                    Text(stringResource(R.string.home_server_restart_to_update), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (readFailed && running) {
                Text(stringResource(R.string.home_status_unavailable), style = MaterialTheme.typography.bodyMedium)
            }
            if (!pending && (running || isPrimaryUser || readFailed)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        running -> {
                            FilledTonalButton(onClick = onStop) {
                                ShizukuIcon(R.drawable.ic_symbol_stop_24, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_stop))
                            }
                            if (hasDetails && status.uid == 0) {
                                OutlinedButton(onClick = onStartRoot) { Text(stringResource(R.string.home_root_button_restart)) }
                            }
                        }
                        isPrimaryUser && (!readFailed || state == State.CRASHED) -> {
                            val (label, action) = when {
                                isRooted -> R.string.home_start_root to onStartRoot
                                canUseWirelessAdb -> R.string.home_wireless_adb_title to onStartWirelessAdb
                                else -> R.string.home_adb_button_view_command to onShowAdbCommand
                            }
                            Button(onClick = action) { Text(stringResource(label)) }
                        }
                    }
                    if (readFailed) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.shevery_update_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceMetric(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
    }
}
