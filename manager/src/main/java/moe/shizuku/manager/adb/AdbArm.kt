package moe.shizuku.manager.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import moe.shizuku.manager.deviceowner.DeviceOwnerManager

/**
 * Single choke point for arming / disarming adbd.
 *
 * Two independent ways to write these Global settings:
 *
 *  - [Method.WRITE_SECURE_SETTINGS] — obtained either from
 *    `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` or,
 *    after the first successful start, granted back to this app by the server
 *    (see `ShizukuService`, which grants it to the manager on bind). This is a
 *    strict superset: it can write every key we touch, including
 *    `adb_allowed_connection_time`.
 *  - [Method.DEVICE_OWNER] — `DevicePolicyManager.setGlobalSetting` is
 *    allowlisted for exactly `Settings.Global.ADB_ENABLED` and
 *    `adb_wifi_enabled` (AOSP `DevicePolicyManagerService.GLOBAL_SETTINGS_ALLOWLIST`).
 *    It CANNOT write `adb_allowed_connection_time` — that key is not on the
 *    allowlist and the call would throw `SecurityException` — so that write is
 *    skipped on this path and pairing keys keep their default lifetime.
 *
 * Because WRITE_SECURE_SETTINGS is a superset, it always wins when both are
 * available; Device Owner is the fallback for installs that never received the
 * `pm grant` / server grant (Device-Owner-only provisioning, grant failure on
 * new Android versions, ...).
 *
 * Every previously scattered `Settings.Global.putInt(ADB_*)` call site routes
 * through here so the two paths can never drift apart again.
 */
object AdbArm {

    private const val TAG = "AdbArm"

    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    const val ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"

    enum class Method {
        WRITE_SECURE_SETTINGS,
        DEVICE_OWNER,
        NONE
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Which method [arm] would use right now. Cheap, synchronous and safe to
     * call from a [android.content.BroadcastReceiver] (only reads a permission
     * flag and asks the DevicePolicyManager whether we own the device).
     */
    fun method(context: Context): Method = when {
        hasWriteSecureSettings(context) -> Method.WRITE_SECURE_SETTINGS
        DeviceOwnerManager.isOwner(context) -> Method.DEVICE_OWNER
        else -> Method.NONE
    }

    fun canArm(context: Context): Boolean = method(context) != Method.NONE

    /**
     * Turn USB debugging + wireless debugging on. Idempotent: writing a value
     * that is already set is a no-op for both paths.
     *
     * @return the method used, or [Method.NONE] when neither path is available
     *         (in which case nothing was written).
     */
    fun arm(context: Context): Method {
        val app = context.applicationContext
        val method = method(app)
        when (method) {
            Method.WRITE_SECURE_SETTINGS -> writeWithSecureSettings(app)
            Method.DEVICE_OWNER -> DeviceOwnerManager.enableAdbViaDpm(app)
            Method.NONE -> Log.d(TAG, "cannot arm adbd: no WRITE_SECURE_SETTINGS and not device owner")
        }
        return method
    }

    /**
     * Re-arm only `adb_wifi_enabled` (0 → 1 recovery path). Used by the unlock
     * receiver, the mDNS observer and [moe.shizuku.manager.worker.WifiDebugReassert].
     * Never throws, so it is safe to call from a receiver.
     */
    fun armWifiEnabled(context: Context): Boolean {
        val app = context.applicationContext
        return when (method(app)) {
            Method.WRITE_SECURE_SETTINGS -> putInt(app, ADB_WIFI_ENABLED, 1)
            // enableAdbViaDpm writes both allowlisted keys; writing 1 → 1 again is harmless.
            Method.DEVICE_OWNER -> DeviceOwnerManager.enableAdbViaDpm(app)
            Method.NONE -> false
        }
    }

    /**
     * Turn wireless debugging off. Device Owner may write "0" to the same
     * allowlisted keys, so the auto-disable paths work without the permission too.
     */
    fun disarmWifiEnabled(context: Context): Boolean {
        val app = context.applicationContext
        return when (method(app)) {
            Method.WRITE_SECURE_SETTINGS -> putInt(app, ADB_WIFI_ENABLED, 0)
            Method.DEVICE_OWNER ->
                DeviceOwnerManager.setGlobalSetting(app, ADB_WIFI_ENABLED, "0")
            Method.NONE -> false
        }
    }

    /** Turn USB debugging off (honours the auto-disable-USB-debugging toggle). */
    fun disarmUsbDebugging(context: Context): Boolean {
        val app = context.applicationContext
        return when (method(app)) {
            Method.WRITE_SECURE_SETTINGS -> putInt(app, Settings.Global.ADB_ENABLED, 0)
            Method.DEVICE_OWNER ->
                DeviceOwnerManager.setGlobalSetting(app, Settings.Global.ADB_ENABLED, "0")
            Method.NONE -> false
        }
    }

    /**
     * Full write set, only reachable with WRITE_SECURE_SETTINGS.
     *
     * `adb_allowed_connection_time = 0` makes wireless-debugging pairing keys
     * immortal (default is 7 days). This key is deliberately NOT attempted on
     * the Device Owner path.
     */
    private fun writeWithSecureSettings(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Settings.Global.putInt(cr, ADB_WIFI_ENABLED, 1)
            }
            Settings.Global.putLong(cr, ADB_ALLOWED_CONNECTION_TIME, 0L)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "arming adbd via WRITE_SECURE_SETTINGS failed", e)
            false
        }
    }

    private fun putInt(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "writing $key=$value failed", e)
            false
        }
    }
}
