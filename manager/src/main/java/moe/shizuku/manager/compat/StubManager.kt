package moe.shizuku.manager.compat

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File

object StubManager {

    enum class StubType(
        val id: String,
        val packageName: String,
        val assetName: String,
        val remoteTmpPath: String,
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int
    ) {
        SHIZUKU(
            id = "shizuku",
            packageName = "moe.shizuku.privileged.api",
            assetName = "shevery-stub.apk",
            remoteTmpPath = "/data/local/tmp/shevery-stub.apk",
            titleRes = R.string.stub_shizuku_title,
            summaryRes = R.string.stub_shizuku_summary
        ),
        DHIZUKU(
            id = "dhizuku",
            packageName = "com.rosan.dhizuku",
            assetName = "shevery-dhizuku-stub.apk",
            remoteTmpPath = "/data/local/tmp/shevery-dhizuku-stub.apk",
            titleRes = R.string.stub_dhizuku_title,
            summaryRes = R.string.stub_dhizuku_summary
        )
    }

    const val STUB_PACKAGE = "moe.shizuku.privileged.api"
    const val DHIZUKU_STUB_PACKAGE = "com.rosan.dhizuku"

    private const val CHANNEL_DEVICE_OWNER = "Device Owner"
    private const val CHANNEL_SERVER = "Shevery"
    private const val CHANNEL_ROOT = "root"
    private const val CHANNEL_ADB = "ADB"

    data class Result(val ok: Boolean, val channel: String, val error: String? = null) {
        val failed: Boolean get() = !ok
    }

    fun isInstalled(context: Context, type: StubType = StubType.SHIZUKU): Boolean {
        return try {
            context.packageManager.getPackageInfo(type.packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledVersion(context: Context, type: StubType): String? {
        return try {
            val pi = context.packageManager.getPackageInfo(type.packageName, 0)
            pi.versionName ?: pi.versionCode.toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun install(context: Context, type: StubType = StubType.SHIZUKU): Result {
        return withContext(Dispatchers.IO) {
            val privateApk = extractTo(context.filesDir, context, type.assetName)
            if (privateApk == null) {
                return@withContext Result(false, "none", "failed to extract ${type.assetName}")
            }
            val apkBytes = privateApk.readBytes()

            var lastFailure: Result? = null

            if (DeviceOwnerManager.isDeviceOwner(context)) {
                val doResult = runViaDeviceOwner(context, privateApk)
                if (doResult.ok && pollInstalled(context, type, wantInstalled = true)) {
                    return@withContext doResult
                }
                lastFailure = doResult
            }

            val bypassFlag = if (Build.VERSION.SDK_INT >= 34) " --bypass-low-target-sdk-block" else ""
            val installFlags = "-r -d -t -g$bypassFlag"

            val scripts = arrayOf(
                ShellScript(
                    CHANNEL_SERVER,
                    "cat > ${type.remoteTmpPath} && pm install $installFlags ${type.remoteTmpPath} && rm -f ${type.remoteTmpPath}"
                ) { output -> output.write(apkBytes) },
                ShellScript(
                    CHANNEL_ROOT,
                    "pm install $installFlags '${privateApk.absolutePath}'"
                ),
                ShellScript(
                    CHANNEL_ADB,
                    "cp -f '${externalApkPath(context, type.assetName)}' ${type.remoteTmpPath} && pm install $installFlags ${type.remoteTmpPath} && rm -f ${type.remoteTmpPath}"
                )
            )

            for (script in scripts) {
                val result = when (script.channel) {
                    CHANNEL_SERVER -> runViaServer(script)
                    CHANNEL_ROOT -> runViaRoot(script)
                    CHANNEL_ADB -> runViaAdb(script)
                    else -> Result(false, script.channel, "unknown channel")
                }
                if (result.ok && pollInstalled(context, type, wantInstalled = true)) {
                    return@withContext Result(true, result.channel)
                }
                if (!result.ok) {
                    lastFailure = result
                }
            }
            lastFailure ?: Result(false, "none", "no channel available")
        }
    }

    suspend fun uninstall(context: Context, type: StubType = StubType.SHIZUKU): Result {
        return withContext(Dispatchers.IO) {
            if (!isInstalled(context, type)) {
                return@withContext Result(true, "none")
            }

            var lastFailure: Result? = null

            if (DeviceOwnerManager.isDeviceOwner(context)) {
                val doResult = uninstallViaDeviceOwner(context, type)
                if (doResult.ok && pollInstalled(context, type, wantInstalled = false)) {
                    return@withContext doResult
                }
                lastFailure = doResult
            }

            val script = "pm uninstall ${type.packageName}"

            val channels = sequenceOf(CHANNEL_SERVER, CHANNEL_ROOT, CHANNEL_ADB)
            for (channel in channels) {
                val result = when (channel) {
                    CHANNEL_SERVER -> runViaServer(ShellScript(channel, script))
                    CHANNEL_ROOT -> runViaRoot(ShellScript(channel, script))
                    else -> runViaAdb(ShellScript(channel, script))
                }
                if (result.ok && pollInstalled(context, type, wantInstalled = false)) {
                    return@withContext Result(true, result.channel)
                }
                lastFailure = result
            }
            lastFailure ?: Result(false, "none", "no channel available")
        }
    }

    private suspend fun runViaDeviceOwner(context: Context, apkFile: File): Result {
        return withContext(Dispatchers.IO) {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val method = params.javaClass.getMethod("setInstallFlags", Int::class.javaPrimitiveType)
                        method.invoke(params, 0x01000000 or 0x00000002)
                    } catch (_: Throwable) {}
                }
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                try {
                    apkFile.inputStream().use { input ->
                        val output = session.openWrite("package", 0, apkFile.length())
                        input.copyTo(output)
                        session.fsync(output)
                        output.close()
                    }

                    val action = "${context.packageName}.STUB_INSTALL_STATUS_${SystemClock.elapsedRealtime()}"
                    val intent = Intent(action).setPackage(context.packageName)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    var installResult: Result? = null
                    val lock = Object()
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) {
                            val status = i?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                            val msg = i?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            synchronized(lock) {
                                installResult = if (status == PackageInstaller.STATUS_SUCCESS) {
                                    Result(true, CHANNEL_DEVICE_OWNER)
                                } else {
                                    Result(false, CHANNEL_DEVICE_OWNER, msg ?: "status $status")
                                }
                                lock.notifyAll()
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(action),
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    try {
                        session.commit(pendingIntent.intentSender)
                        session.close()
                        synchronized(lock) {
                            if (installResult == null) {
                                lock.wait(15000L)
                            }
                        }
                    } finally {
                        try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
                    }
                    installResult ?: Result(false, CHANNEL_DEVICE_OWNER, "install timed out")
                } catch (e: Throwable) {
                    try { session.abandon() } catch (_: Throwable) {}
                    throw e
                }
            } catch (e: Throwable) {
                Result(false, CHANNEL_DEVICE_OWNER, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun uninstallViaDeviceOwner(context: Context, type: StubType): Result {
        return withContext(Dispatchers.IO) {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val action = "${context.packageName}.STUB_UNINSTALL_STATUS_${SystemClock.elapsedRealtime()}"
                val intent = Intent(action).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                var uninstallResult: Result? = null
                val lock = Object()
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        val status = i?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        val msg = i?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        synchronized(lock) {
                            uninstallResult = if (status == PackageInstaller.STATUS_SUCCESS) {
                                Result(true, CHANNEL_DEVICE_OWNER)
                            } else {
                                Result(false, CHANNEL_DEVICE_OWNER, msg ?: "status $status")
                            }
                            lock.notifyAll()
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(action),
                    ContextCompat.RECEIVER_EXPORTED
                )
                try {
                    packageInstaller.uninstall(type.packageName, pendingIntent.intentSender)
                    synchronized(lock) {
                        if (uninstallResult == null) {
                            lock.wait(10000L)
                        }
                    }
                } finally {
                    try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
                }
                uninstallResult ?: Result(false, CHANNEL_DEVICE_OWNER, "uninstall timed out")
            } catch (e: Throwable) {
                Result(false, CHANNEL_DEVICE_OWNER, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private data class ShellScript(
        val channel: String,
        val command: String,
        val stdin: ((java.io.OutputStream) -> Unit)? = null
    )

    private fun extractTo(dir: File, context: Context, assetName: String): File? {
        return try {
            val file = File(dir, assetName)
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Throwable) {
            logd("Failed to extract $assetName: ${e.message}")
            null
        }
    }

    private fun externalApkPath(context: Context, assetName: String): String {
        val externalDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("external storage unavailable")
        val file = File(externalDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return file.absolutePath
    }

    private fun runViaServer(script: ShellScript): Result {
        return try {
            val binder = Shizuku.getBinder()
                ?: return Result(false, CHANNEL_SERVER, "binder is null")
            val service = IShizukuService.Stub.asInterface(binder)
            val process = service.newProcess(arrayOf("sh", "-c", script.command), null, null)
            script.stdin?.let { writer ->
                ParcelFileDescriptor.AutoCloseOutputStream(process.getOutputStream()).use { output -> writer(output) }
            }
            val finished = process.waitForTimeout(60, "SECONDS")
            if (!finished) {
                process.destroy()
                return Result(false, CHANNEL_SERVER, "command timed out")
            }
            val exitCode = process.exitValue()
            val errText = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(process.errorStream).bufferedReader().readText().trim()
            }.getOrDefault("")
            val outText = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(process.inputStream).bufferedReader().readText().trim()
            }.getOrDefault("")
            val errorMsg = listOf(errText, outText).filter { it.isNotBlank() }.joinToString(" | ")

            if (exitCode == 0) Result(true, CHANNEL_SERVER)
            else Result(false, CHANNEL_SERVER, if (errorMsg.isNotBlank()) errorMsg else "exit code $exitCode")
        } catch (e: Throwable) {
            Result(false, CHANNEL_SERVER, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun runViaRoot(script: ShellScript): Result {
        return try {
            if (!Shell.getShell().isRoot) {
                return Result(false, CHANNEL_ROOT, "not root")
            }
            val result = Shell.cmd(script.command).exec()
            if (result.isSuccess) {
                Result(true, CHANNEL_ROOT)
            } else {
                Result(false, CHANNEL_ROOT, (result.err?.firstOrNull()) ?: "command failed")
            }
        } catch (e: Throwable) {
            Result(false, CHANNEL_ROOT, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun runViaAdb(script: ShellScript): Result {
        val candidatePorts = sequenceOf(
            EnvironmentUtils.getLiveAdbTcpPort(),
            EnvironmentUtils.getAdbTcpPort(),
            5555
        )
            .filter { it > 0 }
            .distinct()
            .toList()

        if (candidatePorts.isEmpty()) {
            return Result(false, CHANNEL_ADB, "no ADB port")
        }

        val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
        for (port in candidatePorts) {
            try {
                val output = StringBuilder()
                AdbClient("127.0.0.1", port, key).use { client ->
                    client.connect()
                    client.shellCommand(script.command) { data ->
                        synchronized(output) { output.append(String(data)) }
                    }
                }
                val text = output.toString()
                if (text.contains("Success")) {
                    return Result(true, CHANNEL_ADB)
                }
                return Result(false, CHANNEL_ADB, text.ifBlank { "no output on port $port" })
            } catch (e: Throwable) {
                logd("Stub command failed via ADB on port $port: ${e.message}")
            }
        }
        return Result(false, CHANNEL_ADB, "all ADB ports failed")
    }

    private suspend fun pollInstalled(context: Context, type: StubType, wantInstalled: Boolean, timeoutMs: Long = 5_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isInstalled(context, type) == wantInstalled) return true
            delay(200L)
        }
        return isInstalled(context, type) == wantInstalled
    }
}