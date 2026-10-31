package rikka.shizuku.server;

import androidx.annotation.Nullable;

import com.hamondev.shevery.api.SheveryApiConstants;

import kotlin.collections.ArraysKt;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String MANAGER_APPLICATION_ID = SheveryApiConstants.MANAGER_APPLICATION_ID;
    public static final String SHIZUKU_STUB_APPLICATION_ID = SheveryApiConstants.SHIZUKU_STUB_APPLICATION_ID;

    // Primary Shevery Privilege API permissions
    public static final String PERMISSION = SheveryApiConstants.PERMISSION_PRIVILEGED_API;
    public static final String PERMISSION_MANAGER = SheveryApiConstants.PERMISSION_MANAGER;

    // Legacy Shizuku permissions (isolated to Shizuku Stub moe.shizuku.privileged.api)
    public static final String LEGACY_SHIZUKU_PERMISSION = SheveryApiConstants.LEGACY_SHIZUKU_PERMISSION;
    public static final String LEGACY_PERMISSION_MANAGER = SheveryApiConstants.LEGACY_SHIZUKU_MANAGER_PERMISSION;

    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final String SHEVERY_PROVIDER_SUFFIX = SheveryApiConstants.PROVIDER_SUFFIX;
    public static final String SHIZUKU_PROVIDER_SUFFIX = SheveryApiConstants.LEGACY_PROVIDER_SUFFIX;

    public static final String EXTRA_BINDER_SHEVERY = SheveryApiConstants.EXTRA_BINDER;
    public static final String EXTRA_BINDER_SHIZUKU = SheveryApiConstants.LEGACY_EXTRA_BINDER;

    public static final String META_DATA_SHEVERY_CLIENT = SheveryApiConstants.META_DATA_CLIENT_SUPPORT;
    public static final String META_DATA_SHIZUKU_CLIENT = SheveryApiConstants.LEGACY_META_DATA_CLIENT_SUPPORT;

    public static final int BINDER_TRANSACTION_getApplications = 10001;
    public static final int BINDER_TRANSACTION_setErrorProtectEnabled = 10002;
    public static final int BINDER_TRANSACTION_getErrorProtectEnabled = 10003;

    public static boolean hasSheveryPermission(@Nullable String[] requestedPermissions) {
        return requestedPermissions != null && ArraysKt.contains(requestedPermissions, PERMISSION);
    }

    public static boolean hasLegacyShizukuPermission(@Nullable String[] requestedPermissions) {
        return requestedPermissions != null && ArraysKt.contains(requestedPermissions, LEGACY_SHIZUKU_PERMISSION);
    }

    public static boolean hasAnyClientPermission(@Nullable String[] requestedPermissions) {
        return hasSheveryPermission(requestedPermissions) || hasLegacyShizukuPermission(requestedPermissions);
    }

    @Nullable
    public static String resolveClientPermission(@Nullable String[] requestedPermissions) {
        if (hasSheveryPermission(requestedPermissions)) {
            return PERMISSION;
        }
        if (hasLegacyShizukuPermission(requestedPermissions)) {
            return LEGACY_SHIZUKU_PERMISSION;
        }
        return null;
    }

    @Nullable
    public static String resolveManagerPermission(@Nullable String[] requestedPermissions) {
        if (requestedPermissions == null) {
            return null;
        }
        if (ArraysKt.contains(requestedPermissions, PERMISSION_MANAGER)) {
            return PERMISSION_MANAGER;
        }
        if (ArraysKt.contains(requestedPermissions, LEGACY_PERMISSION_MANAGER)) {
            return LEGACY_PERMISSION_MANAGER;
        }
        return null;
    }
}
