package rikka.shizuku.server;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.hamondev.shevery.api.ClientPermissionCache;
import com.hamondev.shevery.api.ClientPermissions;
import com.hamondev.shevery.api.LegacyShizukuStub;
import com.hamondev.shevery.api.SheveryApi;

import rikka.hidden.compat.ActivityManagerApis;
import rikka.shizuku.server.util.Android17Compat;
import rikka.shizuku.server.util.Logger;

/**
 * Server-side client permission handling on top of the Shevery API.
 *
 * <p>Native clients hold {@link SheveryApi#PERMISSION_API}. The legacy
 * {@link LegacyShizukuStub#PERMISSION_API_V23} permission is owned by the Shizuku stub; it is
 * only mirrored when the stub is installed for the target user, otherwise the permission does
 * not exist on the device and touching it would be a wasted (and failing) binder call.
 */
final class SheveryClients {

    private static final Logger LOGGER = new Logger("SheveryClients");

    static final ClientPermissionCache CACHE = new ClientPermissionCache((packageName, userId) -> {
        PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
        return pi != null ? pi.requestedPermissions : null;
    });

    private SheveryClients() {
    }

    static int flags(int uid, String packageName) {
        return CACHE.get(uid, packageName);
    }

    static boolean isLegacyStubInstalled(int userId) {
        try {
            return Android17Compat.getApplicationInfo(LegacyShizukuStub.PACKAGE, 0, userId) != null;
        } catch (Throwable tr) {
            return false;
        }
    }

    /** Mirrors the authorization decision into the runtime permissions the package requests. */
    static void setGranted(String packageName, int uid, boolean granted) {
        int flags = flags(uid, packageName);
        if (!ClientPermissions.isClient(flags)) {
            return;
        }
        int userId = SheveryApi.getUserId(uid);
        if (ClientPermissions.has(flags, ClientPermissions.SHEVERY_API)) {
            apply(packageName, SheveryApi.PERMISSION_API, userId, granted);
        }
        if (ClientPermissions.has(flags, ClientPermissions.LEGACY_V23) && isLegacyStubInstalled(userId)) {
            apply(packageName, LegacyShizukuStub.PERMISSION_API_V23, userId, granted);
        }
    }

    private static void apply(String packageName, String permission, int userId, boolean granted) {
        if (granted) {
            Android17Compat.grantRuntimePermission(packageName, permission, userId);
        } else {
            Android17Compat.revokeRuntimePermission(packageName, permission, userId);
        }
    }

    /** Whether the package holds one of the client runtime permissions it requests. */
    static boolean isGranted(String packageName, int uid) {
        int flags = flags(uid, packageName);
        if (!ClientPermissions.isClient(flags)) {
            return false;
        }
        int userId = SheveryApi.getUserId(uid);
        try {
            if (ClientPermissions.has(flags, ClientPermissions.SHEVERY_API)
                    && Android17Compat.checkPermission(SheveryApi.PERMISSION_API, packageName, userId) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            if (ClientPermissions.has(flags, ClientPermissions.LEGACY_V23)
                    && isLegacyStubInstalled(userId)
                    && Android17Compat.checkPermission(LegacyShizukuStub.PERMISSION_API_V23, packageName, userId) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        } catch (Throwable tr) {
            LOGGER.w(tr, "isGranted %s", packageName);
        }
        return false;
    }

    /** Uid-level variant of {@link #isGranted(String, int)} for already classified packages. */
    static boolean isGrantedForUid(int flags, int uid) {
        if (ClientPermissions.has(flags, ClientPermissions.SHEVERY_API)
                && Android17Compat.checkPermission(SheveryApi.PERMISSION_API, uid) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        // Unknown permissions (stub not installed) are reported as denied by the system.
        return ClientPermissions.has(flags, ClientPermissions.LEGACY_V23)
                && Android17Compat.checkPermission(LegacyShizukuStub.PERMISSION_API_V23, uid) == PackageManager.PERMISSION_GRANTED;
    }

    /** Binder-calling process check, Shevery permission first, legacy stub permission second. */
    static int checkCallingPermission(int pid, int uid) {
        try {
            if (ActivityManagerApis.checkPermission(SheveryApi.PERMISSION_API, pid, uid) == PackageManager.PERMISSION_GRANTED) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return ActivityManagerApis.checkPermission(LegacyShizukuStub.PERMISSION_API_V23, pid, uid);
        } catch (Throwable tr) {
            LOGGER.w(tr, "checkCallingPermission");
            return PackageManager.PERMISSION_DENIED;
        }
    }
}
