# Android 15+ Hidden API Changes and the Android17Compat Layer

This document records what actually changed in the platform's hidden package and
permission APIs, how that was established, and what `Android17Compat` does about
it. Every signature below was read out of the platform, not recalled.

## How this was established

- The compiled Android 17 framework,
  `org.robolectric:android-all:17-robolectric-15733970`, which is built from AOSP
  and contains the hidden `android.permission.IPermissionManager$Stub` and
  `android.content.pm.IPackageManager$Stub` that the SDK's public `android.jar`
  does not ship. `javap` on those two interfaces gives the exact descriptors.
- The AIDL sources on the `android15-release`, `android16-release` and
  `android17-release` branches of `platform/frameworks/base`.
- `dev.rikka.hidden:compat:4.4.0` itself, to see which signatures the helper
  library was compiled against.

## What changed

`IPermissionManager`, identically on 15, 16 and 17:

```
int  checkPermission(String packageName, String permissionName, String persistentDeviceId, int userId)
void grantRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId)
void revokeRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId, String reason)
int  checkUidPermission(int uid, String permissionName, int deviceId)
```

Two details matter, and both are easy to get wrong:

1. **The package name comes first, the permission second.** Passing them the other
   way round does not fail loudly: both parameters are `String`, so the call
   succeeds and answers `PERMISSION_DENIED`. A permission that is held is then
   reported as denied, which reads as a permission problem rather than a
   programming one.
2. **The device id is a `String` here, and the value for the local device is
   `"default:0"`** — that is what `android.permission.PermissionManager`
   `getPersistentDeviceId(0)` returns. It is not `"default"`. At the same time
   other methods in the *same* interface — `shouldShowRequestPermissionRationale`,
   `isPermissionRevokedByPolicy`, `getPermissionRequestState` — still take an
   `int deviceId`, so no single hardcoded shape can be correct for the whole
   interface.

`IPackageManager`:

```
PackageInfo     getPackageInfo(String, long, int)         unchanged on 17; see the flag-width note
ApplicationInfo getApplicationInfo(String, long, int)     unchanged on 17; see the flag-width note
                getInstalledPackages(long, int)           ParceledListSlice -> PackageInfoList on 17
                getInstalledApplications(long, int)       unchanged
```

`getInstalledPackages` changed by **return type**. A JVM method descriptor
includes the return type, so the old descriptor no longer resolves and the call
fails with `NoSuchMethodError`. `getPackageInfo` and `getApplicationInfo` did
*not* change in Android 17, but they are not stable across older releases.

### The API 33 flag-width change

Android 13 (API 33) introduced `PackageInfoFlags` and `ApplicationInfoFlags` and
widened the hidden flags parameter of both methods from `int` to `long`:

```
API 33+    PackageInfo     getPackageInfo(String, long, int)
API <= 32  PackageInfo     getPackageInfo(String, int, int)
API 33+    ApplicationInfo getApplicationInfo(String, long, int)
API <= 32  ApplicationInfo getApplicationInfo(String, int, int)
```

`Android17Compat` resolves methods by exact `Class` equality, so a candidate
list that only contains `(String, long, int)` matches API 33 and later, but
finds nothing on Android 12L and older. That failure is not local: on such a
device `getApplicationInfo` returns `null`, `ShizukuService` cannot resolve the
manager package and exits with `MANAGER_APP_NOT_FOUND` (50); the starter treats
exit code 50 as "the manager was uninstalled", stops restarting the server, and
the UI eventually reports that the service did not initialize.

Both methods therefore carry two candidates:

- `(String, long, int)` first, so API 33+ keeps using the modern signature and
  no narrowing happens there;
- `(String, int, int)` second, with `flags.toInt()`, for Android 12L and older.

The `int` fallback is only selected after the `long` shape has been rejected by
reflection, so a device that supports both cannot accidentally take the legacy
path. The compat layer only passes flag values that fit in 32 bits (`0` plus
permission bits), so `toInt()` is lossless for those calls; do not reuse the
fallback for APIs that need the high 32 bits of a 64-bit flag value.

The change is not specific to Android 17: `android15-release` already carries the
four-argument permission signatures, and the flag-width split above is tied to
API 33 rather than to any 15/16/17 release.

## What Android17Compat does

- Resolves the hidden interfaces through `ServiceManager` and `Class.forName`, and
  tries candidate argument shapes against the methods the platform actually
  exposes.
- Requires an **exact** parameter-type match, and never tries a candidate whose
  parameter shape duplicates one already tried. Two argument lists that bind to
  the same parameter types are not alternatives — reflection cannot tell them
  apart, so treating them as alternatives is how a wrong order gets silently
  accepted.
- Reads `IllegalArgumentException` as "this shape is not for this method" and
  tries the next, and `InvocationTargetException` as the platform answering, which
  is not retried as a wrong guess.
- Caches the resolved method together with the shape that won, so the guessing
  happens once, and reports through the server log when no candidate matched
  instead of returning a denial that looks like an answer.
- `grantRuntimePermission` and `revokeRuntimePermission` return whether the
  platform was reached, so a grant cannot be lost without a word.

## Known limits

- The 15/16/17 shapes are covered. Pre-15 shapes are kept as legacy candidates
  where they are distinguishable by arity, but they are not the target.
- This layer is compile-verified against the Android 17 SDK and reviewed against
  the platform sources. It has not been exercised on a device.
