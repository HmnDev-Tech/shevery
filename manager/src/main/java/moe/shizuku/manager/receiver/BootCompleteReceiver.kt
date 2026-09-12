package moe.shizuku.manager.receiver

import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val KEYGUARD_WAIT_TIMEOUT_MS = 120_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }

        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) return

        // Schedule app auto-update check (cheap, no-op if already scheduled)
        moe.shizuku.manager.module.update.SheveryAutoUpdateWorker.maybeSchedule(context)

        val isAdbCandidate = ShizukuSettings.getStartOnBootAdb() && (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            EnvironmentUtils.getAdbTcpPort() > 0 ||
            ShizukuSettings.isTcpMode() ||
            EnvironmentUtils.isTV(context)
        )

        if (isAdbCandidate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                    moe.shizuku.manager.service.StartupNotificationManager.showFailed(
                        context,
                        context.getString(moe.shizuku.manager.R.string.notification_startup_no_permission)
                    )
                    return
                }
                // On API 33+ (Android 13+), Wi-Fi/mDNS discovery requires NEARBY_WIFI_DEVICES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && context.checkSelfPermission(NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    moe.shizuku.manager.service.StartupNotificationManager.showFailed(
                        context,
                        context.getString(moe.shizuku.manager.R.string.notification_startup_no_permission)
                    )
                    return
                }
                // On API 36+ (Android 16+), local network access requires ACCESS_LOCAL_NETWORK
                val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
                if (Build.VERSION.SDK_INT >= 36
                    && context.checkSelfPermission(ACCESS_LOCAL_NETWORK_PERMISSION)
                            != PackageManager.PERMISSION_GRANTED) {
                    moe.shizuku.manager.service.StartupNotificationManager.showFailed(
                        context,
                        context.getString(moe.shizuku.manager.R.string.notification_startup_no_permission)
                    )
                    return
                }
            }
            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            if (tcpPort > 0 && (EnvironmentUtils.isTV(context) || ShizukuSettings.isTcpMode())) {
                ShizukuReceiverStarter.start(context)
            } else {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                if (km != null && km.isKeyguardLocked && isPreS) {
                    handlePreSKeyguard(context, km)
                } else {
                    ShizukuReceiverStarter.start(context)
                }
            }
        } else if (ShizukuSettings.getStartOnBoot()
            && ShizukuSettings.getLastLaunchMode() == LaunchMethod.ROOT) {
            rootStart(context)
        } else {
            Log.w(AppConstants.TAG, "No support start on boot")
        }
    }

    private fun handlePreSKeyguard(context: Context, km: KeyguardManager) {
        Log.i(AppConstants.TAG, "Device locked at boot (pre-S), deferring ADB start until unlock")
        moe.shizuku.manager.service.StartupNotificationManager.showProgress(
            context,
            context.getString(moe.shizuku.manager.R.string.notification_startup_waiting_unlock)
        )

        val appContext = context.applicationContext
        val timeoutHandler = Handler(Looper.getMainLooper())
        var unlockReceiver: BroadcastReceiver? = null
        val timeoutRunnable = Runnable {
            unlockReceiver?.let { r ->
                try { appContext.unregisterReceiver(r) } catch (_: Exception) {}
            }
            moe.shizuku.manager.service.StartupNotificationManager.showFailed(
                appContext,
                appContext.getString(moe.shizuku.manager.R.string.notification_startup_failed)
            )
            Log.w(AppConstants.TAG, "Keyguard timeout on pre-S — aborting ADB boot start")
        }

        timeoutHandler.postDelayed(timeoutRunnable, KEYGUARD_WAIT_TIMEOUT_MS)

        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                    ShizukuReceiverStarter.start(ctx)
                }
            }
        }

        try {
            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.RECEIVER_EXPORTED
            } else {
                0 // No flag needed on pre-S
            }
            ContextCompat.registerReceiver(
                appContext,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                receiverFlags
            )
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "Failed to register unlock receiver", e)
            moe.shizuku.manager.service.StartupNotificationManager.showFailed(
                context,
                context.getString(moe.shizuku.manager.R.string.notification_startup_failed)
            )
            return
        }

        // Re-check keyguard after registration: if user unlocked between
        // the isKeyguardLocked check and the registerReceiver call,
        // the USER_PRESENT broadcast may have already fired and been missed.
        if (!km.isKeyguardLocked) {
            try { appContext.unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
            timeoutHandler.removeCallbacks(timeoutRunnable)
            ShizukuReceiverStarter.start(context)
            return
        }
        // Timeout handles the case where user never unlocks
    }

    private fun rootStart(context: Context) {
        if (!com.topjohnwu.superuser.Shell.getShell().isRoot) {
            com.topjohnwu.superuser.Shell.getCachedShell()?.close()
            return
        }
        try {
            moe.shizuku.manager.utils.ShizukuStateMachine.set(moe.shizuku.manager.utils.ShizukuStateMachine.State.STARTING)
            com.topjohnwu.superuser.Shell.cmd(moe.shizuku.manager.starter.Starter.internalCommand).exec()
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Failed to start Shizuku with root on boot", e)
            moe.shizuku.manager.utils.ShizukuStateMachine.update()
        }
    }
}
