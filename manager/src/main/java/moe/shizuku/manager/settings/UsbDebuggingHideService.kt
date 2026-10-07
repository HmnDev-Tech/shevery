package moe.shizuku.manager.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.shizuku.manager.R

class UsbDebuggingHideService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        watchJob = serviceScope.launch { watch() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watchJob?.cancel()
        UsbDebuggingHideManager.restore(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        while (serviceScope.isActive) {
            val targets = runCatching { UsbDebuggingHideManager.targetPackages() }.getOrDefault(emptySet())
            if (targets.isEmpty()) {
                if (UsbDebuggingHideManager.restore(this)) {
                    stopSelf()
                    return
                }
                delay(5_000)
                continue
            }

            if (!runCatching { UsbDebuggingHideManager.hasUsageAccess(this) }.getOrDefault(false)) {
                if (UsbDebuggingHideManager.restore(this)) {
                    stopSelf()
                    return
                }
                delay(5_000)
                continue
            }
            val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            val foreground = if (!locked) {
                runCatching { foregroundPackage() }.getOrNull()
            } else {
                null
            }

            runCatching {
                if (foreground != null && foreground in targets) {
                    UsbDebuggingHideManager.hide(this)
                } else {
                    UsbDebuggingHideManager.restore(this)
                }
            }
            delay(1_000)
        }
    }

    private fun foregroundPackage(): String? {
        val now = System.currentTimeMillis()
        val stats = getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 60 * 60 * 1000L, now)
        return stats?.maxByOrNull { it.lastTimeUsed }
            ?.takeIf { now - it.lastTimeUsed < 24 * 60 * 60 * 1000L }
            ?.packageName
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.usb_debugging_hide_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_adb_24dp)
            .setContentTitle(getString(R.string.usb_debugging_hide_notification_title))
            .setContentText(getString(R.string.usb_debugging_hide_notification_summary))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, UsbDebuggingHideActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "usb_debugging_hide"
        private const val NOTIFICATION_ID = 1453

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            if (UsbDebuggingHideManager.targetPackages().isEmpty() &&
                !UsbDebuggingHideManager.hasPendingRestore()
            ) {
                appContext.stopService(Intent(appContext, UsbDebuggingHideService::class.java))
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, UsbDebuggingHideService::class.java)
                )
            }
        }
    }
}
