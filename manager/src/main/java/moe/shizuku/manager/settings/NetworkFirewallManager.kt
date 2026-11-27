package moe.shizuku.manager.settings

import android.os.ParcelFileDescriptor
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object NetworkFirewallManager {

    private const val KEY_BLOCKED_PACKAGES = "network_firewall_blocked_packages"
    private const val KEY_AUTO_FIREWALL_ON_INSTALL = "network_firewall_auto_on_install"
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun isAutoFirewallOnInstall(): Boolean =
        ShizukuSettings.getPreferences()
            .getBoolean(KEY_AUTO_FIREWALL_ON_INSTALL, false)

    fun setAutoFirewallOnInstall(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit()
            .putBoolean(KEY_AUTO_FIREWALL_ON_INSTALL, enabled)
            .apply()
    }

    private fun blockedPackages(): Set<String> =
        ShizukuSettings.getPreferences()
            .getStringSet(KEY_BLOCKED_PACKAGES, emptySet())
            .orEmpty()
            .toSet()

    @Synchronized
    private fun retainInstalledPackages(installedPackages: Set<String>): Set<String> {
        val blocked = blockedPackages()
        val retained = blocked intersect installedPackages
        if (blocked != retained) {
            ShizukuSettings.getPreferences().edit()
                .putStringSet(KEY_BLOCKED_PACKAGES, retained)
                .apply()
        }
        return retained
    }

    fun effectiveBlockedPackages(installedPackages: Set<String>): Set<String> {
        val tracked = retainInstalledPackages(installedPackages)
        if (tracked.isEmpty() || !isChainEnabled()) return emptySet()
        return tracked.filterTo(mutableSetOf()) {
            packageNamePattern.matches(it) && isPackageBlocked(it) == true
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean): Boolean {
        if (!packageNamePattern.matches(packageName)) return false
        // Older builds may accept the setter without supporting the verification command.
        if (isPackageBlocked(packageName) == null) return false

        if (blocked && !isChainEnabled()) {
            if (runCommand("cmd connectivity set-chain3-enabled true") == null || !isChainEnabled()) {
                return false
            }
        }

        val enabled = if (blocked) "false" else "true"
        if (runCommand("cmd connectivity set-package-networking-enabled $enabled $packageName") == null) {
            return false
        }
        if (isPackageBlocked(packageName) != blocked) return false

        synchronized(this) {
            val packages = blockedPackages().toMutableSet()
            if (blocked) packages.add(packageName) else packages.remove(packageName)
            ShizukuSettings.getPreferences().edit()
                .putStringSet(KEY_BLOCKED_PACKAGES, packages)
                .apply()
        }
        return true
    }

    private fun isChainEnabled(): Boolean =
        runCommand("cmd connectivity get-chain3-enabled")?.trim() == "chain:enabled"

    private fun isPackageBlocked(packageName: String): Boolean? =
        when (runCommand("cmd connectivity get-package-networking-enabled $packageName")?.trim()) {
            "$packageName:deny" -> true
            "$packageName:allow" -> false
            else -> null
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
