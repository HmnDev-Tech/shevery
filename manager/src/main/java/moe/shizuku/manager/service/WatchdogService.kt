package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.ktx.logw
import moe.shizuku.manager.module.ModuleSettings

/**
 * Minimal foreground holder for the NightDog-style [WatchdogManager].
 *
 * Old heavy methods deleted: zombie-protect coroutine loop, StateFlow
 * isRunning, ShizukuStateMachine crash listener, binder listeners,
 * HealthResult transactions, handleUnhealthy/requestStop round-trips.
 * Monitoring lives in [WatchdogManager] on a single HandlerThread (60s poll);
 * this service only keeps the foreground notification while enabled.
 */
class WatchdogService : Service() {

    override fun onCreate() {
        super.onCreate()
        WatchdogManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            logi("WatchdogService: received ACTION_STOP_SERVICE")
            ModuleSettings.setWatchdogEnabled(false)
            WatchdogManager.reconcileService(applicationContext)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!WatchdogManager.shouldRunService()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Throwable) {
            logw("WatchdogService: startForeground failed: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                stopSelf()
            }
        }
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0x7F030001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0x7F030002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_ok_24dp)
            .setContentTitle(getString(R.string.watchdog_service_title))
            .setContentText(getString(R.string.watchdog_service_text))
            .setContentIntent(launchPendingIntent)
            .addAction(
                R.drawable.ic_close_24,
                getString(R.string.watchdog_action_turn_off),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (channelCreated) return
        channelCreated = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_watchdog),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_STOP_SERVICE = "moe.shizuku.manager.action.STOP_WATCHDOG_SERVICE"
        private const val CHANNEL_ID = "service_watchdog"
        private const val NOTIFICATION_ID = 1004
        private var channelCreated = false

        fun reconcile(context: Context) {
            val appContext = context.applicationContext
            if (WatchdogManager.shouldRunService()) {
                start(appContext)
            } else {
                stop(appContext)
            }
        }

        private fun start(context: Context) {
            try {
                val intent = Intent(context, WatchdogService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                logw("Failed to start watchdog service: ${e.message}")
            }
        }

        private fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WatchdogService::class.java))
            } catch (e: Throwable) {
                logw("Failed to stop watchdog service: ${e.message}")
            }
        }
    }
}
