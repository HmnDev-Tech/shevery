package com.hamondev.shevery.api

/**
 * Single-pass classification of a package's requested permissions into a bit mask.
 *
 * Replaces repeated `ArraysKt.contains(requestedPermissions, ...)` scans: one walk
 * over the array answers every question the server asks about a package.
 */
object ClientPermissions {

    const val NONE = 0

    /** Requests [SheveryApi.PERMISSION_API]. */
    const val SHEVERY_API = 1

    /** Requests [LegacyShizukuStub.PERMISSION_API_V23] (legacy Shizuku client). */
    const val LEGACY_V23 = 1 shl 1

    /** Requests [SheveryApi.PERMISSION_MANAGER]. */
    const val MANAGER = 1 shl 2

    /** Any flag that makes the package an API client. */
    const val CLIENT_MASK = SHEVERY_API or LEGACY_V23

    private const val ALL = SHEVERY_API or LEGACY_V23 or MANAGER

    @JvmStatic
    fun classify(requestedPermissions: Array<String>?): Int {
        if (requestedPermissions == null) {
            return NONE
        }
        var flags = NONE
        for (permission in requestedPermissions) {
            when (permission) {
                SheveryApi.PERMISSION_API -> flags = flags or SHEVERY_API
                LegacyShizukuStub.PERMISSION_API_V23 -> flags = flags or LEGACY_V23
                SheveryApi.PERMISSION_MANAGER -> flags = flags or MANAGER
                else -> continue
            }
            if (flags == ALL) {
                break
            }
        }
        return flags
    }

    @JvmStatic
    fun isClient(flags: Int): Boolean = (flags and CLIENT_MASK) != 0

    @JvmStatic
    fun has(flags: Int, flag: Int): Boolean = (flags and flag) == flag
}
