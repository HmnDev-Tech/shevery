package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbArm
import moe.shizuku.manager.settings.UsbDebuggingHideManager
import moe.shizuku.manager.settings.UsbDebuggingHideService

class AutoDisableUsbDebuggingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        ShizukuSettings.initialize(context)
        UsbDebuggingHideManager.restore(context)
        UsbDebuggingHideService.refresh(context)
        if (!ShizukuSettings.getAutoDisableUsbDebugging()) return

        val bootReceiver = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
        val bootReceiverState = context.packageManager.getComponentEnabledSetting(bootReceiver)
        if (bootReceiverState == COMPONENT_ENABLED_STATE_ENABLED) return

        // Turn USB debugging off — via WRITE_SECURE_SETTINGS or, for
        // Device-Owner-only installs, setGlobalSetting. See AdbArm.
        AdbArm.disarmUsbDebugging(context)
    }
}
