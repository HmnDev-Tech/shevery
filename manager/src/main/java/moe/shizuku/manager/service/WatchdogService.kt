package moe.shizuku.manager.service

import android.app.KeyguardManager
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
import moe.shizuku.manager.utils.ShizukuStateMachine

class WatchdogService : Service() {

    private var watchdogJob: Job? = null
    private val watchdogScope = CoroutineScope(Dispatchers.Default)

    // Watchdog restart backoff: doubles per failure, from 30s up to a 5min cap.

    // The 10s base poll stays for health checks; only RESTART actions back off, so the loop cannot
    // hammer adbd behind the lockscreen or in a permanently-dead state.
    private val retryBackoffBaseMs = 30_000L
    private val retryBackoffMaxMs = 300_000L

    private val binderReceivedListener = object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            startAsForeground()
        }
    }
    private val binderDeadListener = object : rikka.shizuku.Shizuku.OnBinderDeadListener {
        override fun onBinderDead() {
            startAsForeground()
        }
    }

    override fun onCreate() {
        super.onCreate()
        WatchdogManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!WatchdogManager.shouldRunService()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        startWatchdogLoop()
        rikka.shizuku.Shizuku.removeBinderReceivedListener(binderReceivedListener)
        rikka.shizuku.Shizuku.removeBinderDeadListener(binderDeadListener)
        rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        rikka.shizuku.Shizuku.addBinderDeadListener(binderDeadListener)
        return START_STICKY
    }

    override fun onDestroy() {
        rikka.shizuku.Shizuku.removeBinderReceivedListener(binderReceivedListener)
        rikka.shizuku.Shizuku.removeBinderDeadListener(binderDeadListener)
        stopWatchdogLoop()
        super.onDestroy()
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        if (!moe.shizuku.manager.module.ModuleSettings.isWatchdogEnabled()) return

        watchdogJob = watchdogScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(10_000)
                if (!moe.shizuku.manager.module.ModuleSettings.isWatchdogEnabled()) break

                var healthy = false
                try {
                    if (rikka.shizuku.Shizuku.pingBinder()) {
                        val version = rikka.shizuku.Shizuku.getVersion()
                        if (version > 0) {
                            healthy = true
                        }
                    }
                } catch (e: Throwable) {
                    logd("Watchdog: Binder check threw exception: ${e.message}")
                }

                if (!healthy && !WatchdogManager.isExpectingDeathActive() && !WatchdogManager.isUserStopRequested() && WatchdogManager.shouldRunService() && ShizukuStateMachine.get() != ShizukuStateMachine.State.STARTING) {

                    // While the keyguard is up, Android tears down plain-TCP adb and kills
                    // the server. Restarting behind the lockscreen just re-kicks the same doomed
                    // transport every 10s (old behavior), which wedges 25-45s. Defer until unlock.
                    // the keyguard-aware AdbStartWorker waits for USER_PRESENT and re-runs full
                    // discovery + adbd rebind itself.
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (km?.isKeyguardLocked == true) {
                        logd("Watchdog: device locked -- deferring restart until unlock")
                        continue
                    }
                    logd("Watchdog: service check failed. Stopping and restarting...")
                    withContext(Dispatchers.IO) {
                        moe.shizuku.manager.service.WatchdogManager.stopServer(applicationContext, userInitiated = false)
                        moe.shizuku.manager.service.WatchdogManager.attemptRestart(applicationContext)
                    }
                    consecutiveFailures += 1
                    val shiftCount = (consecutiveFailures.minus(1)).coerceAtMost(4)
                    val backoffMs = (retryBackoffBaseMs * (1L shl shiftCount)).coerceAtMost(retryBackoffMaxMs)
                    logd("Watchdog: restart scheduled; backing off ${backoffMs} ms before next check")
                    delay(backoffMs)
                } else if (healthy) {
                    consecutiveFailures = 0
                }
            }
        }
    }

    private fun stopWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = null
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
