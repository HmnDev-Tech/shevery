package rikka.shizuku.server.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.reflect.Method;

import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;

public class Android17Compat {

    private static final String TAG = "ShizukuAndroid17Compat";

    private static volatile Object sPackageManager;
    private static volatile Method sGetPackageInfoMethod;
    private static volatile Method sGetApplicationInfoMethod;

    private static volatile Object sPermissionManager;
    private static volatile Method sGrantRuntimePermissionMethod;
    private static volatile Method sRevokeRuntimePermissionMethod;
    private static volatile Method sCheckPermissionMethod;
    private static volatile Method sCheckPermissionUidMethod;

    private static synchronized Object getPackageManager() throws Exception {
        if (sPackageManager == null) {
            IBinder binder = ServiceManager.getService("package");
            Class<?> stubClass = Class.forName("android.content.pm.IPackageManager$Stub");
            sPackageManager = stubClass.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
        }
        return sPackageManager;
    }

    private static synchronized Object getPermissionManager() throws Exception {
        if (sPermissionManager == null) {
            IBinder binder = ServiceManager.getService("permissionmgr");
            if (binder != null) {
                Class<?> stubClass = Class.forName("android.permission.IPermissionManager$Stub");
                sPermissionManager = stubClass.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
            }
        }
        return sPermissionManager;
    }

    public static PackageInfo getPackageInfo(String packageName, long flags, int userId) {
        try {
            PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, flags, userId);
            if (pi != null) return pi;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPackageManager();
            if (pm != null) {
                if (sGetPackageInfoMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sGetPackageInfoMethod == null) {
                            sGetPackageInfoMethod = findMatchingMethod(pm, "getPackageInfo", String.class);
                        }
                    }
                }
                if (sGetPackageInfoMethod != null) {
                    return (PackageInfo) invokeDynamic(pm, sGetPackageInfoMethod, packageName, flags, userId);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for getPackageInfo failed for " + packageName, ex);
        }
        return null;
    }

    public static ApplicationInfo getApplicationInfo(String packageName, long flags, int userId) {
        try {
            ApplicationInfo ai = PackageManagerApis.getApplicationInfoNoThrow(packageName, flags, userId);
            if (ai != null) return ai;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPackageManager();
            if (pm != null) {
                if (sGetApplicationInfoMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sGetApplicationInfoMethod == null) {
                            sGetApplicationInfoMethod = findMatchingMethod(pm, "getApplicationInfo", String.class);
                        }
                    }
                }
                if (sGetApplicationInfoMethod != null) {
                    return (ApplicationInfo) invokeDynamic(pm, sGetApplicationInfoMethod, packageName, flags, userId);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for getApplicationInfo failed for " + packageName, ex);
        }
        return null;
    }

    public static int checkPermission(String permissionName, String packageName, int userId) {
        try {
            int res = PermissionManagerApis.checkPermission(permissionName, packageName, userId);
            if (res == android.content.pm.PackageManager.PERMISSION_GRANTED) return res;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPermissionManager();
            if (pm != null) {
                if (sCheckPermissionMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sCheckPermissionMethod == null) {
                            sCheckPermissionMethod = findMatchingMethod(pm, "checkPermission", String.class, String.class);
                        }
                    }
                }
                if (sCheckPermissionMethod != null) {
                    return (int) invokeDynamic(pm, sCheckPermissionMethod, packageName, permissionName, userId);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for checkPermission failed", ex);
        }
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }

    public static int checkPermission(String permissionName, int uid) {
        try {
            int res = PermissionManagerApis.checkPermission(permissionName, uid);
            if (res == android.content.pm.PackageManager.PERMISSION_GRANTED) return res;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPermissionManager();
            if (pm != null) {
                if (sCheckPermissionUidMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sCheckPermissionUidMethod == null) {
                            sCheckPermissionUidMethod = findMatchingMethod(pm, "checkUidPermission", int.class, String.class);
                        }
                    }
                }
                if (sCheckPermissionUidMethod != null) {
                    return (int) invokeDynamic(pm, sCheckPermissionUidMethod, uid, permissionName);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for checkPermission(uid) failed", ex);
        }
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }

    public static void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException {
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPermissionManager();
            if (pm != null) {
                if (sGrantRuntimePermissionMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sGrantRuntimePermissionMethod == null) {
                            sGrantRuntimePermissionMethod = findMatchingMethod(pm, "grantRuntimePermission", String.class, String.class);
                        }
                    }
                }
                if (sGrantRuntimePermissionMethod != null) {
                    invokeDynamic(pm, sGrantRuntimePermissionMethod, packageName, permissionName, userId);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for grantRuntimePermission failed", ex);
        }
    }

    public static void revokeRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException {
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Object pm = getPermissionManager();
            if (pm != null) {
                if (sRevokeRuntimePermissionMethod == null) {
                    synchronized (Android17Compat.class) {
                        if (sRevokeRuntimePermissionMethod == null) {
                            sRevokeRuntimePermissionMethod = findMatchingMethod(pm, "revokeRuntimePermission", String.class, String.class);
                        }
                    }
                }
                if (sRevokeRuntimePermissionMethod != null) {
                    invokeDynamic(pm, sRevokeRuntimePermissionMethod, packageName, permissionName, userId);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Android 17 fallback for revokeRuntimePermission failed", ex);
        }
    }

    private static Method findMatchingMethod(Object obj, String name, Class<?>... prefixTypes) {
        Method bestMethod = null;
        for (Method method : obj.getClass().getMethods()) {
            if (name.equals(method.getName())) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length >= prefixTypes.length) {
                    boolean match = true;
                    for (int i = 0; i < prefixTypes.length; i++) {
                        Class<?> expected = prefixTypes[i];
                        Class<?> actual = paramTypes[i];
                        if (expected == String.class && actual != String.class) {
                            match = false;
                            break;
                        }
                        if ((expected == int.class || expected == long.class) && (actual != int.class && actual != long.class)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        if (bestMethod == null || paramTypes.length > bestMethod.getParameterTypes().length) {
                            bestMethod = method;
                        }
                    }
                }
            }
        }
        return bestMethod;
    }

    private static Object invokeDynamic(Object target, Method method, Object... args) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] invokeArgs = new Object[paramTypes.length];

        if (paramTypes.length == args.length) {
            for (int i = 0; i < paramTypes.length; i++) {
                invokeArgs[i] = coerce(args[i], paramTypes[i]);
            }
        } else if (paramTypes.length == args.length + 1) {
            int lastArgIdx = args.length - 1;
            for (int i = 0; i < lastArgIdx; i++) {
                invokeArgs[i] = coerce(args[i], paramTypes[i]);
            }
            if (paramTypes[lastArgIdx] == String.class) {
                invokeArgs[lastArgIdx] = "default";
            } else {
                invokeArgs[lastArgIdx] = coerce(0, paramTypes[lastArgIdx]);
            }
            invokeArgs[lastArgIdx + 1] = coerce(args[lastArgIdx], paramTypes[lastArgIdx + 1]);
        } else if (paramTypes.length > args.length + 1) {
            int lastArgIdx = args.length - 1;
            for (int i = 0; i < lastArgIdx; i++) {
                invokeArgs[i] = coerce(args[i], paramTypes[i]);
            }
            invokeArgs[lastArgIdx] = (paramTypes[lastArgIdx] == String.class) ? "default" : coerce(0, paramTypes[lastArgIdx]);
            invokeArgs[lastArgIdx + 1] = coerce(args[lastArgIdx], paramTypes[lastArgIdx + 1]);
            for (int i = lastArgIdx + 2; i < paramTypes.length; i++) {
                if (paramTypes[i] == String.class) {
                    invokeArgs[i] = "shizuku";
                } else {
                    invokeArgs[i] = coerce(0, paramTypes[i]);
                }
            }
        } else {
            for (int i = 0; i < paramTypes.length; i++) {
                invokeArgs[i] = (i < args.length) ? coerce(args[i], paramTypes[i]) : coerce(null, paramTypes[i]);
            }
        }
        return method.invoke(target, invokeArgs);
    }

    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType == int.class) return 0;
            if (targetType == long.class) return 0L;
            if (targetType == boolean.class) return false;
            return null;
        }
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) return value;
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }
        return value;
    }
}
