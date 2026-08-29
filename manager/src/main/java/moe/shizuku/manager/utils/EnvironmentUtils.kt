package moe.shizuku.manager.utils

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.application
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object EnvironmentUtils {

    private const val TAG = "EnvironmentUtils"

    @JvmStatic
    fun isWatch(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_WATCH)
    }

    @JvmStatic
    fun isTV(context: Context): Boolean {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        val isLeanback = context.packageManager.hasSystemFeature("android.hardware.leanback")
        return (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
            || (isLeanback && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_NORMAL)
    }

    @JvmStatic
    fun isTelevision(): Boolean {
        return isTV(application)
    }

    fun isRooted(): Boolean {
        return Shell.getShell().isRoot
    }

    fun getAdbTcpPort(): Int {
        var port = SystemProperties.getInt("service.adb.tcp.port", -1)
        if (port == -1) port = SystemProperties.getInt("persist.adb.tcp.port", -1)
        return port
    }

    fun getLiveAdbTcpPort(): Int {
        val configuredPort = getAdbTcpPort()
        val candidates = sequenceOf(configuredPort, 5555)
            .filter { it > 0 }
            .distinct()

        return candidates.firstOrNull { isAdbPortLive(it) } ?: -1
    }

    fun isAdbPortLive(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 250)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if wireless debugging (mDNS) discovery is required to find
     * the ADB port. Returns false (use TCP directly) when:
     * - A TCP port is configured AND we're in TCP mode, OR
     * - A TCP port is configured AND the device is a TV (TVs use static TCP without mDNS)
     */
    @JvmStatic
    fun isWifiRequired(): Boolean {
        val hasTcpPort = getAdbTcpPort() > 0
        val isTv = isTelevision()
        val inTcpMode = ShizukuSettings.isTcpMode()
        // Use TCP directly if: port configured + (TCP mode OR TV device)
        return !(hasTcpPort && (inTcpMode || isTv))
    }

    @JvmStatic
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @JvmStatic
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations(context)) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct battery optimization request failed, trying settings fallback", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings", ex)
            }
        }
    }
}
