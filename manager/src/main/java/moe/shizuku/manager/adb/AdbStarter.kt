package moe.shizuku.manager.adb

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku
import java.io.IOException

object AdbStarter {

    private val launches = AdbLaunchCoordinator(
        awaitIdle = { CancellableIo.awaitIdle() },
        awaitReady = { Starter.waitForBinder() },
        isRunning = { Shizuku.pingBinder() }
    )

    const val DEFAULT_TCP_MODE_PORT = 5555

    val TCP_MODE_PORT: Int
        get() = ShizukuSettings.getTcpModePort()

    suspend fun start(
        host: String = "127.0.0.1",
        port: Int,
        context: Context? = null,
        force: Boolean = false,
        listener: ((ByteArray) -> Unit)? = null,
        log: ((String) -> Unit)? = null,
    ) {
        var entered = false
        var tcpMode = false
        launches.launch(skipIfRunning = !force, checkAllowed = {}) { markPossiblyDispatched ->
            entered = true
            ShizukuStateMachine.onStartRequested()
            val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shevery")
            tcpMode = ShizukuSettings.isTcpMode()
            var targetPort = if (tcpMode) TCP_MODE_PORT else port
            val isTargetAlreadyLive = EnvironmentUtils.isAdbPortLive(targetPort)
            if (tcpMode && port != targetPort && !isTargetAlreadyLive) {
                if (ShizukuSettings.isTcpPortRandomized()) {
                    targetPort = ShizukuSettings.randomTcpPort()
                    ShizukuSettings.setTcpModePort(targetPort)
                }
                log?.invoke("Switching ADB from port $port to TCP port $targetPort...")
                switchToTcp(host, port, targetPort, key)
            }

            log?.invoke("Connecting to ADB on port $targetPort...")
            connectWithRetry(host, targetPort, key, markPossiblyDispatched) { client, checkActive, mark ->
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
                ShizukuSettings.setLastAdbPort(targetPort)
                client.shellCommand(Starter.internalCommand, listener, checkActive, mark)
            }
        }
        if (!entered) {
            ShizukuStateMachine.update()
            log?.invoke("Service is already running.")
        } else if (tcpMode) {
            disableWirelessDebugging(context)
        }
    }

    suspend fun switchToTcpMode(
        host: String = "127.0.0.1",
        currentPort: Int,
        targetPort: Int = TCP_MODE_PORT,
    ) {
        launches.withGate({}) {
            ShizukuStateMachine.onStartRequested()
            val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shevery")
            switchToTcp(host, currentPort, targetPort, key)
        }
    }

    private suspend fun switchToTcp(host: String, currentPort: Int, targetPort: Int, key: AdbKey) {
        val client = AdbClient(host, currentPort, key)
        CancellableIo.run(client) { checkActive ->
            client.connect()
            checkActive()
            try {
                client.command("tcpip:$targetPort", null, checkActive, {})
            } catch (e: IOException) {
                // Expected: adbd restarts (dropping TCP/TLS socket) when switching to TCP mode.
            }
        }
    }

    private suspend fun connectWithRetry(
        host: String,
        port: Int,
        key: AdbKey,
        markPossiblyDispatched: () -> Unit,
        retries: Int = 8,
        block: (AdbClient, () -> Unit, () -> Unit) -> Unit,
    ) {
        repeat(retries) { attempt ->
            currentCoroutineContext().ensureActive()
            var dispatched = false
            try {
                val client = AdbClient(host, port, key)
                CancellableIo.run(client) { checkActive ->
                    client.connect()
                    checkActive()
                    block(client, checkActive) {
                        dispatched = true
                        markPossiblyDispatched()
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // After A_OPEN may have been sent, retrying can restart a live server.
                if (dispatched || attempt == retries - 1) throw e
                delay(500L * (attempt + 1))
            }
        }
    }

    private fun disableWirelessDebugging(context: Context?) {
        val appContext = context?.applicationContext ?: return
        // WRITE_SECURE_SETTINGS and Device Owner may both write 0 to the
        // allowlisted key — AdbArm picks whichever this install has.
        AdbArm.disarmWifiEnabled(appContext)
    }
}
