package moe.shizuku.manager.adb

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku
import java.io.IOException

object AdbStarter {

    const val TCP_MODE_PORT = 5555

    suspend fun start(
        host: String = "127.0.0.1",
        port: Int,
        context: Context? = null,
        force: Boolean = false,
        listener: ((ByteArray) -> Unit)? = null,
        log: ((String) -> Unit)? = null,
    ) {
        if (!force && Shizuku.pingBinder()) {
            ShizukuStateMachine.update()
            log?.invoke("Service is already running.")
            return
        }
        ShizukuStateMachine.onStartRequested()
        val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        val tcpMode = ShizukuSettings.isTcpMode()
        val targetPort = if (tcpMode) TCP_MODE_PORT else port
        var startedSuccessfully = false

        try {
            val isTargetAlreadyLive = EnvironmentUtils.isAdbPortLive(targetPort)
            if (tcpMode && port != targetPort && !isTargetAlreadyLive) {
                log?.invoke("Switching ADB from port $port to TCP port $targetPort...")
                switchToTcp(host, port, targetPort, key)
            }

            log?.invoke("Connecting to ADB on port $targetPort...")
            connectWithRetry(host, targetPort, key) { client ->
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
                ShizukuSettings.setLastAdbPort(targetPort)
                client.shellCommand(Starter.internalCommand, listener)
            }
            startedSuccessfully = true
        } finally {
            // Only touch wireless debugging when this starter put the device
            // in TCP mode and successfully launched the service; unconditional
            // disable would break automatic retry and recovery on failure.
            if (tcpMode && startedSuccessfully) {
                disableWirelessDebugging(context)
            }
        }
    }

    suspend fun switchToTcpMode(
        host: String = "127.0.0.1",
        currentPort: Int,
        targetPort: Int = TCP_MODE_PORT,
    ) {
        ShizukuStateMachine.onStartRequested()
        val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        switchToTcp(host, currentPort, targetPort, key)
    }

    private fun switchToTcp(host: String, currentPort: Int, targetPort: Int, key: AdbKey) {
        AdbClient(host, currentPort, key).use { client ->
            client.connect()
            try {
                client.command("tcpip:$targetPort")
            } catch (e: IOException) {
                // Expected: adbd restarts (dropping TCP/TLS socket) when switching to TCP mode.
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
        // WRITE_SECURE_SETTINGS and Device Owner may both write 0 to the
        // allowlisted key — AdbArm picks whichever this install has.
        AdbArm.disarmWifiEnabled(appContext)
    }
}
