package moe.shizuku.manager.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.annotation.StringRes
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
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

            val scripts = arrayOf(
                ShellScript(
                    CHANNEL_SERVER,
                    "cat > ${type.remoteTmpPath} && pm install -r -d -t ${type.remoteTmpPath} && rm -f ${type.remoteTmpPath}"
                ) { output -> output.write(apkBytes) },
                ShellScript(
                    CHANNEL_ROOT,
                    "pm install -r -d -t '${privateApk.absolutePath}'"
                ),
                ShellScript(
                    CHANNEL_ADB,
                    "cp -f '${externalApkPath(context, type.assetName)}' ${type.remoteTmpPath} && pm install -r -d -t ${type.remoteTmpPath} && rm -f ${type.remoteTmpPath}"
                )
            )

            var lastFailure: Result? = null
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

            val script = "pm uninstall ${type.packageName}"

            var lastFailure: Result? = null
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
            if (exitCode == 0) Result(true, CHANNEL_SERVER)
            else Result(false, CHANNEL_SERVER, "exit code $exitCode")
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