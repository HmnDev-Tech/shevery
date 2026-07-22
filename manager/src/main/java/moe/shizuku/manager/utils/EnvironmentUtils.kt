package moe.shizuku.manager.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemProperties
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object EnvironmentUtils {

    @JvmStatic
    fun isWatch(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_WATCH)
    }

    @JvmStatic
    fun isTV(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_TELEVISION)
    }

    fun isRooted(): Boolean {
        return System.getenv("PATH")?.split(File.pathSeparatorChar)?.find { File("$it/su").exists() } != null
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
}
