package com.hamondev.shevery.api

/**
 * Identity and permission contract of the native Shevery API.
 *
 * Shevery no longer declares or uses the legacy Shizuku permission
 * `moe.shizuku.manager.permission.API_V23`. Native clients request
 * [PERMISSION_API], which is declared by the Shevery manager itself.
 * Legacy Shizuku clients are served through the Shizuku stub APK, see
 * [LegacyShizukuStub].
 */
object SheveryApi {

    /** Version of this API contract. Bump on any incompatible change. */
    const val VERSION = 1

    /** Package name of the Shevery manager application. */
    const val MANAGER_PACKAGE = "com.hamondev.shevery"

    /** Runtime ("dangerous") permission declared by Shevery for native API clients. */
    const val PERMISSION_API = "com.hamondev.shevery.permission.PRIVILEGED_API"

    /** Permission group of [PERMISSION_API]. */
    const val PERMISSION_GROUP_API = "com.hamondev.shevery.permission-group.API"

    /** Signature permission held by the manager only. */
    const val PERMISSION_MANAGER = "com.hamondev.shevery.permission.MANAGER"

    /** Linux per-user uid range, identical to `UserHandle.PER_USER_RANGE`. */
    const val PER_USER_RANGE = 100000

    @JvmStatic
    fun getUserId(uid: Int): Int = uid / PER_USER_RANGE
}
