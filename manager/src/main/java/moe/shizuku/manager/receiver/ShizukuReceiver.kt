package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.shell.ShellBinderRequestHandler

class ShizukuReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == "rikka.shizuku.intent.action.REQUEST_BINDER" ||
            action == "moe.shizuku.privileged.api.intent.action.REQUEST_BINDER" ||
            action == "moe.shizuku.manager.intent.action.REQUEST_BINDER" ||
            action == "${context.packageName}.intent.action.REQUEST_BINDER" ||
            action == "com.hamondev.shevery.intent.action.REQUEST_BINDER"
        ) {
            ShellBinderRequestHandler.handleRequest(context, intent)
        }
    }
}
