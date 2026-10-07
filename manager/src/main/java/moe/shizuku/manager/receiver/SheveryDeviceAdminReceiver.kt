package moe.shizuku.manager.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
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
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
        val isDo = dpm?.isDeviceOwnerApp(context.packageName) == true
        val isPo = dpm?.isProfileOwnerApp(context.packageName) == true
        val type = when {
            isDo -> "Device Owner"
            isPo -> "Profile Owner"
            else -> "Device Admin"
        }
        Log.i(TAG, "Shevery $type enabled")
        LOGGER.i("$type enabled")
        try {
            android.widget.Toast.makeText(
                context,
                context.getString(moe.shizuku.manager.R.string.device_owner_admin_enabled_toast, type),
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (_: Throwable) {}
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Shevery Device Admin disabled")
        LOGGER.w("Device Admin disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Profile provisioning complete")
        LOGGER.i("Profile provisioning complete")
    }

    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        super.onTransferOwnershipComplete(context, bundle)
        Log.i(TAG, "Ownership transfer complete")
        LOGGER.i("Ownership transfer complete")
    }
}
