package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
