package moe.shizuku.manager.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.utils.Logger.LOGGER

class SheveryDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SheveryDAReceiver"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, SheveryDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Shevery Device Admin enabled")
        LOGGER.i("Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Shevery Device Admin disabled")
        LOGGER.w("Device Admin disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Device provisioning complete")
        LOGGER.i("Device provisioning complete")
    }
}
