package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.service.WatchdogManager

class SheveryControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_SERVER = "moe.shizuku.manager.action.START_SERVER"
        const val ACTION_STOP_SERVER = "moe.shizuku.manager.action.STOP_SERVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_SERVER -> {
                WatchdogManager.clearUserStopRequest()
                WatchdogManager.attemptRestart(context.applicationContext)
            }
            ACTION_STOP_SERVER -> {
                WatchdogManager.stopServer()
            }
        }
    }
}
