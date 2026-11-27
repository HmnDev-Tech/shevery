package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.settings.NetworkFirewallManager
import rikka.shizuku.Shizuku

class FirewallPackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!NetworkFirewallManager.isAutoFirewallOnInstall()) return
        if (!Shizuku.pingBinder()) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        if (uid >= 0 && (uid % 100000) < Process.FIRST_APPLICATION_UID) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NetworkFirewallManager.setBlocked(packageName, true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
