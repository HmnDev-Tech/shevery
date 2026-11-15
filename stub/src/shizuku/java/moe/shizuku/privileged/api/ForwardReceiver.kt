package moe.shizuku.privileged.api

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class ForwardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (!isSheveryInstalled(context)) {
                return
            }

            val forward = Intent(intent).apply {
                setPackage(SHEVERY_PACKAGE)
                component = ComponentName(SHEVERY_PACKAGE, SHEVERY_RECEIVER)
                putExtra(EXTRA_PERMISSION_CHANNEL, "shizuku_stub")
                putExtra(EXTRA_REQUESTED_PERMISSION, "moe.shizuku.manager.permission.API_V23")
            }

            context.sendBroadcast(forward)
        } catch (_: Throwable) {
        }
    }

    private fun isSheveryInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHEVERY_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        const val SHEVERY_PACKAGE = "com.hamondev.shevery"
        const val SHEVERY_RECEIVER = "moe.shizuku.manager.receiver.ShizukuReceiver"
        const val EXTRA_PERMISSION_CHANNEL = "com.hamondev.shevery.intent.extra.PERMISSION_CHANNEL"
        const val EXTRA_REQUESTED_PERMISSION = "com.hamondev.shevery.intent.extra.REQUESTED_PERMISSION"
    }
}
