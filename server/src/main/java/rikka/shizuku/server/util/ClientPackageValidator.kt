package rikka.shizuku.server.util

import rikka.shizuku.server.ClientManager

/** Validates that a client-supplied package name belongs to the calling UID. */
object ClientPackageValidator {

    @JvmStatic
    fun verifyPackageOwnership(
        callingUid: Int,
        packageName: String?,
        ownedPackages: Collection<String>?
    ): Boolean {
        if (packageName.isNullOrEmpty() || ownedPackages.isNullOrEmpty()) {
            return false
        }
        return ownedPackages.contains(packageName)
    }

    @JvmStatic
    fun enforcePackageOwnership(
        callingUid: Int,
        packageName: String?,
        ownedPackages: Collection<String>?
    ) {
        ClientManager.enforcePackageOwnership(callingUid, packageName, ownedPackages)
    }
}
