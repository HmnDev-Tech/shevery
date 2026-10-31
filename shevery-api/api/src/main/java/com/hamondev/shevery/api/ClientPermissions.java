package com.hamondev.shevery.api;

/**
 * Single-pass classification of a package's requested permissions into a bit mask.
 *
 * <p>Replaces repeated {@code ArraysKt.contains(requestedPermissions, ...)} scans: one walk
 * over the array answers every question the server asks about a package.
 */
public final class ClientPermissions {

    public static final int NONE = 0;
    /** Requests {@link SheveryApi#PERMISSION_API}. */
    public static final int SHEVERY_API = 1;
    /** Requests {@link LegacyShizukuStub#PERMISSION_API_V23} (legacy Shizuku client). */
    public static final int LEGACY_V23 = 1 << 1;
    /** Requests {@link SheveryApi#PERMISSION_MANAGER}. */
    public static final int MANAGER = 1 << 2;

    /** Any flag that makes the package an API client. */
    public static final int CLIENT_MASK = SHEVERY_API | LEGACY_V23;

    private static final int ALL = SHEVERY_API | LEGACY_V23 | MANAGER;

    public static int classify(String[] requestedPermissions) {
        if (requestedPermissions == null) {
            return NONE;
        }
        int flags = NONE;
        for (String permission : requestedPermissions) {
            if (permission == null) {
                continue;
            }
            switch (permission) {
                case SheveryApi.PERMISSION_API:
                    flags |= SHEVERY_API;
                    break;
                case LegacyShizukuStub.PERMISSION_API_V23:
                    flags |= LEGACY_V23;
                    break;
                case SheveryApi.PERMISSION_MANAGER:
                    flags |= MANAGER;
                    break;
                default:
                    continue;
            }
            if (flags == ALL) {
                break;
            }
        }
        return flags;
    }

    public static boolean isClient(int flags) {
        return (flags & CLIENT_MASK) != 0;
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) == flag;
    }

    private ClientPermissions() {
    }
}
