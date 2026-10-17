package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.ktx.logi
import moe.shizuku.manager.ktx.logw
import moe.shizuku.manager.service.SheveryNotificationManager
import moe.shizuku.manager.service.WatchdogManager
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.shizuku.Shizuku

class SheveryControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_SERVER = "moe.shizuku.manager.action.START_SERVER"
        const val ACTION_STOP_SERVER = "moe.shizuku.manager.action.STOP_SERVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_SERVER -> {
                val appCtx = context.applicationContext
                if (Shizuku.pingBinder() || ShizukuStateMachine.isRunning()) {
                    logi("Server already running; ignoring redundant START_SERVER broadcast")
                    return
                }
                if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
                    logi("Server startup already in progress; ignoring duplicate start request")
                    return
                }
                WatchdogManager.clearUserStopRequest(appCtx)
                if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.ADB) {
                    AdbStartWorker.enqueueIfIdle(appCtx)
                    ShizukuReceiverStarter.updateNotification(appCtx,
                        AdbStartWorker.bannerStateFor(appCtx)
                    )
                } else {
                    WatchdogManager.attemptRestart(appCtx)
                }
            }
            ACTION_STOP_SERVER -> {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(AdbStartWorker.UNIQUE_WORK_NAME)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WatchdogManager.stopServerAndWait(context.applicationContext, userInitiated = true)
                        SheveryNotificationManager.updateNotification(context.applicationContext)
                    } catch (e: Exception) {
                        logw("Stop server failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
