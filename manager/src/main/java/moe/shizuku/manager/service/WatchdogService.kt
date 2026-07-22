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
import kotlinx.coroutines.*
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.logd

class WatchdogService : Service() {

    private var errorProtectJob: Job? = null
    private val errorProtectScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        WatchdogManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!WatchdogManager.isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        startErrorProtectLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        stopErrorProtectLoop()
        super.onDestroy()
    }

    private fun startErrorProtectLoop() {
        errorProtectJob?.cancel()
        if (!moe.shizuku.manager.module.ModuleSettings.isErrorProtectEnabled()) return

        errorProtectJob = errorProtectScope.launch {
            while (isActive) {
                delay(10_000)
                if (!moe.shizuku.manager.module.ModuleSettings.isErrorProtectEnabled()) break

                var healthy = false
                try {
                    if (rikka.shizuku.Shizuku.pingBinder()) {
                        val version = rikka.shizuku.Shizuku.getVersion()
                        if (version > 0) {
                            healthy = true
                        }
                    }
                } catch (e: Throwable) {
                    logd("ErrorProtect: Binder check threw exception: ${e.message}")
                }

                if (!healthy) {
                    logd("ErrorProtect: Service check failed. Stopping and restarting...")
                    withContext(Dispatchers.IO) {
                        moe.shizuku.manager.service.WatchdogManager.stopServer(userInitiated = false)
                        moe.shizuku.manager.service.WatchdogManager.attemptRestart(applicationContext)
                    }
                }
            }
        }
    }

    private fun stopErrorProtectLoop() {
        errorProtectJob?.cancel()
        errorProtectJob = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_watchdog),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_ok_24dp)
            .setContentTitle(getString(R.string.watchdog_service_title))
            .setContentText(getString(R.string.watchdog_service_text))
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "service_watchdog"
        private const val NOTIFICATION_ID = 1003

        fun reconcile(context: Context) {
            val appContext = context.applicationContext
            if (WatchdogManager.isEnabled()) {
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
            } catch (e: Exception) {
                logd("Failed to start watchdog service: ${e.message}")
            }
        }

        private fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WatchdogService::class.java))
            } catch (e: Exception) {
                logd("Failed to stop watchdog service: ${e.message}")
            }
        }
    }
}
