package moe.shizuku.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.provider.Settings
import moe.shizuku.manager.ShizukuSettings

class AutoDisableUsbDebuggingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        ShizukuSettings.initialize(context)
        if (!ShizukuSettings.getAutoDisableUsbDebugging()) return

        val bootReceiver = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
        val bootReceiverState = context.packageManager.getComponentEnabledSetting(bootReceiver)
        if (bootReceiverState == COMPONENT_ENABLED_STATE_ENABLED || bootReceiverState == COMPONENT_ENABLED_STATE_DEFAULT) return
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return

        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
    }
}
