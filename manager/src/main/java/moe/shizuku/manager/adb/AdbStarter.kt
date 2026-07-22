package moe.shizuku.manager.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.Starter
import java.io.EOFException
import java.net.SocketException

object AdbStarter {

    const val TCP_MODE_PORT = 5555

    suspend fun start(
        host: String = "127.0.0.1",
        port: Int,
        context: Context? = null,
        listener: ((ByteArray) -> Unit)? = null,
        log: ((String) -> Unit)? = null,
    ) {
        val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        val tcpMode = ShizukuSettings.isTcpMode()
        val targetPort = if (tcpMode) TCP_MODE_PORT else port

        try {
            if (tcpMode && port != targetPort) {
                log?.invoke("Switching ADB from port $port to TCP port $targetPort...")
                switchToTcp(host, port, targetPort, key)
            }

            log?.invoke("Connecting to ADB on port $targetPort...")
            connectWithRetry(host, targetPort, key) { client ->
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
                client.shellCommand(Starter.internalCommand, listener)
            }
        } finally {
            if (tcpMode) disableWirelessDebugging(context)
        }
    }

    private fun switchToTcp(host: String, currentPort: Int, targetPort: Int, key: AdbKey) {
        AdbClient(host, currentPort, key).use { client ->
            client.connect()
            try {
                client.command("tcpip:$targetPort")
            } catch (e: EOFException) {
                // Expected: adbd restarts when switching from wireless debugging to TCP mode.
            } catch (e: SocketException) {
                // Expected: adbd restarts when switching from wireless debugging to TCP mode.
            }
        }
    }

    private suspend fun connectWithRetry(
        host: String,
        port: Int,
        key: AdbKey,
        retries: Int = 8,
        block: (AdbClient) -> Unit,
    ) {
        var lastError: Throwable? = null
        repeat(retries) { attempt ->
            try {
                AdbClient(host, port, key).use { client ->
                    client.connect()
                    block(client)
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt == retries - 1) throw e
                delay(500L * (attempt + 1))
            }
        }
        lastError?.let { throw it }
    }

    private fun disableWirelessDebugging(context: Context?) {
        val appContext = context?.applicationContext ?: return
        if (appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        runCatching {
            Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 0)
        }
    }
}
