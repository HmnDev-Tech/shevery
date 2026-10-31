package com.hamondev.shevery.api;

/**
 * Contract of the Shizuku compatibility stub ({@code moe.shizuku.privileged.api}).
 *
 * <p>The legacy permission {@link #PERMISSION_API_V23} is declared <b>only</b> by the
 * stub APK. Shevery itself neither declares nor requests it; the stub carries the
 * whole legacy Shizuku surface (permission, request activity, binder request receiver).
 * When the stub is not installed the permission does not exist on the device and the
 * server must not try to grant, revoke or check it.
 */
public final class LegacyShizukuStub {

    /** Application id of the Shizuku stub flavor. */
    public static final String PACKAGE = "moe.shizuku.privileged.api";

    /** Legacy Shizuku client permission, owned by the stub. */
    public static final String PERMISSION_API_V23 = "moe.shizuku.manager.permission.API_V23";

    private LegacyShizukuStub() {
    }
}
