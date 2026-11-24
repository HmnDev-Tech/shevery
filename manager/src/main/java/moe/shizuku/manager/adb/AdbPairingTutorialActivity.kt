package moe.shizuku.manager.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.guide.GuideTopic
import moe.shizuku.manager.guide.ShakeGuideHost
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.home.AdbPairDialog
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.StepRow
import moe.shizuku.manager.utils.SettingsHelper
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppActivity() {

    private var notificationEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationEnabled = isNotificationEnabled()
        if (notificationEnabled) {
            startPairingService()
        }

        setContent {
            var showManualPairingDialog by remember { mutableStateOf(false) }

            ShizukuExpressiveTheme {
                ShakeGuideHost(GuideTopic.WIRELESS_DEBUGGING)
                ShizukuLazyScaffold(
                    title = stringResource(R.string.adb_pairing_tutorial_title),
                    onNavigateUp = { finish() }
                ) {
                    if (notificationEnabled) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_outline_notifications_active_24,
                                title = stringResource(R.string.notification_channel_adb_pairing),
                                body = stringResource(R.string.adb_pairing_tutorial_content_notification)
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_help_outline_24dp,
                                title = stringResource(R.string.home_local_network_title),
                                body = stringResource(R.string.adb_pairing_tutorial_content_network) +
                                        "\n\n" +
                                        stringResource(R.string.adb_pairing_tutorial_content_network_limation_not_foreground)
                            )
                        }
                    } else {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_outline_info_24,
                                title = stringResource(R.string.notification_settings),
                                body = stringResource(R.string.adb_pairing_tutorial_content_notification_blocked),
                                danger = true
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = ::openNotificationSettings) {
                                        Text(stringResource(R.string.notification_settings))
                                    }
                                    OutlinedButton(onClick = { showManualPairingDialog = true }) {
                                        Text(stringResource(R.string.adb_pairing_manual_input))
                                    }
                                }
                            }
                        }
                    }

                    if (DeviceCompatibility.isMiui()) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_warning_24,
                                title = "MIUI",
                                body = stringResource(R.string.adb_pairing_tutorial_content_miui) +
                                        "\n\n" +
                                        stringResource(R.string.adb_pairing_tutorial_content_miui_2),
                                danger = true
                            )
                        }
                    }

                    if (notificationEnabled) {
                        item {
                            StepRow(
                                number = 1,
                                title = stringResource(R.string.adb_pairing_tutorial_content_steps),
                                body = stringResource(R.string.adb_pairing_tutorial_content_left_is_clickable),
                                action = {
                                    Button(onClick = ::openDeveloperOptions) {
                                        Text(stringResource(R.string.development_settings))
                                    }
                                }
                            )
                        }
                        item {
                            StepRow(
                                number = 2,
                                title = stringResource(R.string.adb_pairing_tutorial_content_enter_pairing_code),
                                body = stringResource(R.string.adb_pairing_tutorial_content_notification),
                                action = {
                                    FilledTonalButton(onClick = { showManualPairingDialog = true }) {
                                        Text(stringResource(R.string.adb_pairing_manual_input))
                                    }
                                }
                            )
                        }
                        item {
                            StepRow(
                                number = 3,
                                title = stringResource(R.string.adb_pairing_tutorial_content_finish),
                                action = {
                                    FilledTonalButton(onClick = { finish() }) {
                                        Text(stringResource(android.R.string.ok))
                                    }
                                }
                            )
                        }
                    }
                }

                if (showManualPairingDialog) {
                    AdbPairDialog(
                        inPairingWindow = true,
                        allowManualInput = true,
                        onDismissRequest = { showManualPairingDialog = false },
                        onPairSuccess = {
                            showManualPairingDialog = false
                            Toast.makeText(
                                this@AdbPairingTutorialActivity,
                                R.string.notification_adb_pairing_succeed_title,
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun openDeveloperOptions() {
        SettingsHelper.launchWirelessDebugging(this)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled

            if (newNotificationEnabled) {
                startPairingService()
            }
        }
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
            }
        }
    }
}
