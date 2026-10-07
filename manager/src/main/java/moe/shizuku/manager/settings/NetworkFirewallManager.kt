package moe.shizuku.manager.settings

import android.os.ParcelFileDescriptor
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object NetworkFirewallManager {

    private const val KEY_BLOCKED_PACKAGES = "network_firewall_blocked_packages"
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun blockedPackages(): Set<String> =
        ShizukuSettings.getPreferences()
            .getStringSet(KEY_BLOCKED_PACKAGES, emptySet())
            .orEmpty()
            .toSet()

    fun retainInstalledPackages(installedPackages: Set<String>) {
        val blocked = blockedPackages()
        val retained = blocked intersect installedPackages
        if (blocked == retained) return
        ShizukuSettings.getPreferences().edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, retained)
            .apply()
    }

    fun setBlocked(packageName: String, blocked: Boolean): Boolean {
        if (!packageNamePattern.matches(packageName)) return false

        val chain = runCommand("cmd connectivity get-chain3-enabled")?.trim()
        if (chain?.endsWith(":enabled") != true) {
            if (runCommand("cmd connectivity set-chain3-enabled true") == null) return false
        }

        val enabled = if (blocked) "false" else "true"
        if (runCommand("cmd connectivity set-package-networking-enabled $enabled $packageName") == null) {
            return false
        }
        val state = runCommand("cmd connectivity get-package-networking-enabled $packageName")
            ?.trim()
            ?: return false
        val applied = if (blocked) state.endsWith(":deny") else state.endsWith(":allow")
        if (!applied) return false

        val packages = blockedPackages().toMutableSet()
        if (blocked) packages.add(packageName) else packages.remove(packageName)
        ShizukuSettings.getPreferences().edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, packages)
            .apply()
        return true
    }

    private fun runCommand(command: String): String? = runCatching {
        val binder = Shizuku.getBinder() ?: return null
        val service = IShizukuService.Stub.asInterface(binder)
        val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
        try {
            if (!process.waitForTimeout(5L, TimeUnit.SECONDS.name) || process.exitValue() != 0) {
                return null
            }
            ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader()
                .use { it.readText() }
        } finally {
            process.destroy()
        }
    }.getOrNull()
}
