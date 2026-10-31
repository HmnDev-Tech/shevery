package com.hamondev.shevery.api;

/**
 * Identity and permission contract of the native Shevery API.
 *
 * <p>Shevery no longer declares or uses the legacy Shizuku permission
 * {@code moe.shizuku.manager.permission.API_V23}. Native clients request
 * {@link #PERMISSION_API}, which is declared by the Shevery manager itself.
 * Legacy Shizuku clients are served through the Shizuku stub APK, see
 * {@link LegacyShizukuStub}.
 */
public final class SheveryApi {

    /** Version of this API contract. Bump on any incompatible change. */
    public static final int VERSION = 1;

    /** Package name of the Shevery manager application. */
    public static final String MANAGER_PACKAGE = "com.hamondev.shevery";

    /** Runtime ("dangerous") permission declared by Shevery for native API clients. */
    public static final String PERMISSION_API = "com.hamondev.shevery.permission.API";

    /** Permission group of {@link #PERMISSION_API}. */
    public static final String PERMISSION_GROUP_API = "com.hamondev.shevery.permission-group.API";

    /** Signature permission held by the manager only. */
    public static final String PERMISSION_MANAGER = "moe.shizuku.manager.permission.MANAGER";

    /** Linux per-user uid range, identical to {@code UserHandle.PER_USER_RANGE}. */
    public static final int PER_USER_RANGE = 100000;

    public static int getUserId(int uid) {
        return uid / PER_USER_RANGE;
    }

    private SheveryApi() {
    }
}
