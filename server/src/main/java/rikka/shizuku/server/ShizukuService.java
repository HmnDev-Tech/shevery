package rikka.shizuku.server;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static rikka.shizuku.ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION;
import static rikka.shizuku.ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_PERMISSION_GRANTED;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_SECONTEXT;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_UID;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_VERSION;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE;
import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;
import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME;
import static rikka.shizuku.server.ServerConstants.MANAGER_APPLICATION_ID;
import static rikka.shizuku.server.ServerConstants.PERMISSION;

import android.content.ComponentName;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.ddm.DdmHandleAppName;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.collections.ArraysKt;
import moe.shizuku.api.BinderContainer;
import moe.shizuku.common.util.BuildUtils;
import moe.shizuku.common.util.InstalledPackagesCompat;
import moe.shizuku.common.util.OsUtils;
import moe.shizuku.server.IShizukuApplication;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.DeviceIdleControllerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.parcelablelist.ParcelableListSlice;
import rikka.rish.RishConfig;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.errorprotect.ErrorProtect;
import rikka.shizuku.server.api.IContentProviderUtils;
import rikka.shizuku.server.util.Android17Compat;
import rikka.shizuku.server.util.HandlerUtil;
import rikka.shizuku.server.util.UserHandleCompat;

public class ShizukuService extends Service<ShizukuUserServiceManager, ShizukuClientManager, ShizukuConfigManager> {

    private static final String TAG = "Shizuku";
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;
    private static final ExecutorService BINDER_DISPATCH_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "shevery-binder-dispatch");
        t.setDaemon(true);
        return t;
    });

    public static void main(String[] args) {
        try {
            Log.i(TAG, "Starting Shizuku server (pid=" + android.os.Process.myPid() + ", uid=" + android.os.Process.myUid() + ")...");
            DdmHandleAppName.setAppName("shizuku_server", 0);
            RishConfig.setLibraryPath(System.getProperty("shizuku.library.path"));

            Looper.prepareMainLooper();
            new ShizukuService();
            Looper.loop();
        } catch (Throwable tr) {
            Log.e(TAG, "Fatal exception in Shizuku server main", tr);
            System.exit(1);
        }
    }

    private static void waitSystemService(String name) {
        while (ServiceManager.getService(name) == null) {
            try {
                LOGGER.i("service " + name + " is not started, wait 200ms.");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                LOGGER.w(e.getMessage(), e);
            }
        }
    }

    public static ApplicationInfo getManagerApplicationInfo() {
        return Android17Compat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, 0);
    }

    @SuppressWarnings({"FieldCanBeLocal"})
    private final Handler mainHandler = new Handler(Looper.myLooper());
    private final ShizukuClientManager clientManager;
    private final ShizukuConfigManager configManager;
    private final int managerAppId;
    private Runnable heartbeatRunnable;

    public ShizukuService() {
        super();

        HandlerUtil.setMainHandler(mainHandler);

        LOGGER.i("starting server...");

        waitSystemService("package");
        waitSystemService(Context.ACTIVITY_SERVICE);
        waitSystemService(Context.USER_SERVICE);
        waitSystemService(Context.APP_OPS_SERVICE);

        ApplicationInfo ai = getManagerApplicationInfo();
        if (ai == null) {
            Log.e(TAG, "Manager application " + MANAGER_APPLICATION_ID + " not found!");
            System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
        }

        assert ai != null;
        managerAppId = ai.uid;

        configManager = getConfigManager();
        clientManager = getClientManager();

        ApkChangedObservers.start(ai.sourceDir, () -> {
            if (getManagerApplicationInfo() == null) {
                LOGGER.w("manager app is uninstalled in user 0, exiting...");
                System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
            }
        });

        BinderSender.register(this);

        if (configManager.getErrorProtectEnabled()) {
            applyErrorProtectState(true);
        }

        mainHandler.post(() -> {
            sendBinderToManager();
            sendBinderToClient();
        });
    }

    private void applyErrorProtectState(boolean enabled) {
        if (heartbeatRunnable != null) {
            mainHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }

        if (enabled) {
            if (!ErrorProtect.isStarted()) {
                LOGGER.i("Starting ErrorProtect watchdog...");
                ErrorProtect.start(
                        ErrorProtect.DEFAULT_TIMEOUT_MS,
                        ErrorProtect.DEFAULT_POLL_INTERVAL_MS,
                        new ErrorProtect.Listener() {
                            @Override
                            public void onServiceRecovered(@NonNull String name) {
                                LOGGER.i("ErrorProtect: system service '%s' recovered, refreshing bindings", name);
                                Android17Compat.invalidate(name);
                                mainHandler.post(() -> {
                                    if (Context.ACTIVITY_SERVICE.equals(name)) {
                                        BinderSender.register(ShizukuService.this);
                                    }
                                    if (Context.ACTIVITY_SERVICE.equals(name)
                                            || "package".equals(name)
                                            || Context.USER_SERVICE.equals(name)) {
                                        sendBinderToManager();
                                        sendBinderToClient();
                                    }
                                });
                            }

                            @Override
                            public void onFatalFault(@NonNull String reason) {
                                LOGGER.e("ErrorProtect detected fatal fault (%s), terminating server for recovery", reason);
                            }
                        }
                );
                LOGGER.i("ErrorProtect watchdog started");
            }

            Runnable heartbeat = new Runnable() {
                @Override
                public void run() {
                    if (!ErrorProtect.isStarted()) return;
                    ErrorProtect.beat();
                    mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
                }
            };
            heartbeatRunnable = heartbeat;
            ErrorProtect.beat();
            mainHandler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS);
        } else {
            if (ErrorProtect.isStarted()) {
                ErrorProtect.stop();
                LOGGER.i("ErrorProtect watchdog stopped");
            }
        }
    }

    @Override
    public ShizukuUserServiceManager onCreateUserServiceManager() {
        return new ShizukuUserServiceManager();
    }

    @Override
    public ShizukuClientManager onCreateClientManager() {
        return new ShizukuClientManager(getConfigManager());
    }

    @Override
    public ShizukuConfigManager onCreateConfigManager() {
        return new ShizukuConfigManager();
    }

    @Override
    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return UserHandleCompat.getAppId(callingUid) == managerAppId;
    }

    public static boolean isShizukuStubActive(int userId) {
        return Android17Compat.getApplicationInfo(ServerConstants.SHIZUKU_STUB_APPLICATION_ID, 0, userId) != null;
    }

    private int checkCallingPermission() {
        try {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            if (ActivityManagerApis.checkPermission(ServerConstants.PERMISSION, pid, uid) == PackageManager.PERMISSION_GRANTED) {
                return PackageManager.PERMISSION_GRANTED;
            }
            int userId = UserHandleCompat.getUserId(uid);
            if (isShizukuStubActive(userId)) {
                return ActivityManagerApis.checkPermission(ServerConstants.LEGACY_SHIZUKU_PERMISSION, pid, uid);
            }
            return PackageManager.PERMISSION_DENIED;
        } catch (Throwable tr) {
            LOGGER.w(tr, "checkCallingPermission");
            return PackageManager.PERMISSION_DENIED;
        }
    }

    @Override
    public boolean checkCallerPermission(String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        if (UserHandleCompat.getAppId(callingUid) == managerAppId) {
            return true;
        }
        if (clientRecord == null && checkCallingPermission() == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    @Override
    public void exit() {
        enforceManagerPermission("exit");
        LOGGER.i("exit");
        if (heartbeatRunnable != null) {
            mainHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
        ErrorProtect.stop();
        System.exit(0);
    }

    @Override
    public void attachUserService(IBinder binder, Bundle options) {
        enforceManagerPermission("func");
        super.attachUserService(binder, options);
    }

    @Override
    public void attachApplication(IShizukuApplication application, Bundle args) {
        if (application == null || args == null) {
            return;
        }

        String requestPackageName = args.getString(ATTACH_APPLICATION_PACKAGE_NAME);
        if (requestPackageName == null) {
            return;
        }
        int apiVersion = args.getInt(ATTACH_APPLICATION_API_VERSION, -1);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        boolean isManager;
        ClientRecord clientRecord = null;

        List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(callingUid);
        if (!packages.contains(requestPackageName)) {
            LOGGER.w("Request package " + requestPackageName + "does not belong to uid " + callingUid);
            throw new SecurityException("Request package " + requestPackageName + "does not belong to uid " + callingUid);
        }

        isManager = MANAGER_APPLICATION_ID.equals(requestPackageName);

        clientRecord = clientManager.findClient(callingUid, callingPid);
        if (clientRecord == null) {
            synchronized (this) {
                clientRecord = clientManager.addClient(callingUid, callingPid, application, requestPackageName, apiVersion);
            }
            if (clientRecord == null) {
                LOGGER.w("Add client failed");
                return;
            }
        }

        LOGGER.d("attachApplication: %s %d %d", requestPackageName, callingUid, callingPid);

        int replyServerVersion = ShizukuApiConstants.SERVER_VERSION;
        if (apiVersion == -1) {
            replyServerVersion = 12;
        }

        Bundle reply = new Bundle();
        reply.putInt(BIND_APPLICATION_SERVER_UID, OsUtils.getUid());
        reply.putInt(BIND_APPLICATION_SERVER_VERSION, replyServerVersion);
        reply.putString(BIND_APPLICATION_SERVER_SECONTEXT, OsUtils.getSELinuxContext());
        reply.putInt(BIND_APPLICATION_SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION);
        if (!isManager) {
            reply.putBoolean(BIND_APPLICATION_PERMISSION_GRANTED, Objects.requireNonNull(clientRecord).allowed);
            reply.putBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false);
        } else {
            try {
                Android17Compat.grantRuntimePermission(MANAGER_APPLICATION_ID,
                        WRITE_SECURE_SETTINGS, UserHandleCompat.getUserId(callingUid));
            } catch (Throwable e) {
                LOGGER.w(e, "grant WRITE_SECURE_SETTINGS");
            }
        }
        try {
            application.bindApplication(reply);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to call bindApplication for " + requestPackageName, e);
            LOGGER.w(e, "attachApplication");
        }
    }

    @Override
    public void showPermissionConfirmation(int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId) {
        ApplicationInfo ai = Android17Compat.getApplicationInfo(clientRecord.packageName, 0, userId);
        if (ai == null) {
            ai = new ApplicationInfo();
            ai.packageName = clientRecord.packageName;
            ai.uid = callingUid;
        }

        PackageInfo pi = Android17Compat.getPackageInfo(MANAGER_APPLICATION_ID, 0, userId);
        UserInfo userInfo = UserManagerApis.getUserInfo(userId);
        boolean isWorkProfileUser = BuildUtils.atLeast30() ?
                (userInfo != null && "android.os.usertype.profile.MANAGED".equals(userInfo.userType)) :
                (userInfo != null && (userInfo.flags & UserInfo.FLAG_MANAGED_PROFILE) != 0);
        if (pi == null && !isWorkProfileUser) {
            LOGGER.w("Manager package info not found for user %d via reflection, proceeding with start activity anyway", userId);
        }

        Intent intent = new Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
                .setComponent(new ComponentName(MANAGER_APPLICATION_ID, "moe.shizuku.manager.authorization.RequestPermissionActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra("uid", callingUid)
                .putExtra("pid", callingPid)
                .putExtra("requestCode", requestCode)
                .putExtra("packageName", clientRecord.packageName)
                .putExtra("applicationInfo", ai);
        ActivityManagerApis.startActivityNoThrow(intent, null, isWorkProfileUser ? 0 : userId);
    }

    @Override
    public void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, Bundle data) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult called not from the manager package");
            return;
        }

        if (data == null) {
            return;
        }

        boolean allowed = data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED);
        boolean onetime = data.getBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME);

        LOGGER.i("dispatchPermissionConfirmationResult: uid=%d, pid=%d, requestCode=%d, allowed=%s, onetime=%s",
                requestUid, requestPid, requestCode, Boolean.toString(allowed), Boolean.toString(onetime));

        List<ClientRecord> records = clientManager.findClients(requestUid);
        List<String> packages = new ArrayList<>();
        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionConfirmationResult: no client for uid %d was found", requestUid);
        } else {
            for (ClientRecord record : records) {
                packages.add(record.packageName);
                record.allowed = allowed;
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(requestCode, allowed);
                }
            }
        }

        if (!onetime) {
            configManager.update(requestUid, packages, ConfigManager.MASK_PERMISSION, allowed ? ConfigManager.FLAG_ALLOWED : ConfigManager.FLAG_DENIED);
        }

        if (!onetime && allowed) {
            int userId = UserHandleCompat.getUserId(requestUid);
            BinderSender.invalidateUid(requestUid);

            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(requestUid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null) continue;
                String targetPermission = ServerConstants.resolveClientPermission(pi.requestedPermissions);
                if (targetPermission == null) {
                    continue;
                }

                if (allowed) {
                    Android17Compat.grantRuntimePermission(packageName, targetPermission, userId);
                } else {
                    Android17Compat.revokeRuntimePermission(packageName, targetPermission, userId);
                }
            }
        }
    }

    private int getFlagsForUidInternal(int uid, int mask, boolean allowRuntimePermission) {
        ShizukuConfig.PackageEntry entry = (ShizukuConfig.PackageEntry) configManager.find(uid);
        if (entry != null) {
            return entry.flags & mask;
        }

        if (allowRuntimePermission && (mask & ConfigManager.MASK_PERMISSION) != 0) {
            int userId = UserHandleCompat.getUserId(uid);
            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null) continue;
                String targetPermission = ServerConstants.resolveClientPermission(pi.requestedPermissions);
                if (targetPermission == null) {
                    continue;
                }

                try {
                    if (Android17Compat.checkPermission(targetPermission, packageName, userId) == PackageManager.PERMISSION_GRANTED) {
                        return ConfigManager.FLAG_ALLOWED;
                    }
                } catch (Throwable e) {
                    LOGGER.w("getFlagsForUid");
                }
            }
        }
        return 0;
    }

    @Override
    public int getFlagsForUid(int uid, int mask) {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid is allowed to be called only from the manager");
            return 0;
        }
        return getFlagsForUidInternal(uid, mask, true);
    }

    @Override
    public void updateFlagsForUid(int uid, int mask, int value) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid is allowed to be called only from the manager");
            return;
        }

        int userId = UserHandleCompat.getUserId(uid);

        if ((mask & ConfigManager.MASK_PERMISSION) != 0) {
            boolean allowed = (value & ConfigManager.FLAG_ALLOWED) != 0;

            List<ClientRecord> records = clientManager.findClients(uid);
            for (ClientRecord record : records) {
                if (allowed) {
                    record.allowed = true;
                } else {
                    record.allowed = false;
                    ActivityManagerApis.forceStopPackageNoThrow(record.packageName, UserHandleCompat.getUserId(record.uid));
                    onPermissionRevoked(record.packageName);
                }
            }

            BinderSender.invalidateUid(uid);
            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null) continue;
                String targetPermission = ServerConstants.resolveClientPermission(pi.requestedPermissions);
                if (targetPermission == null) {
                    continue;
                }

                if (allowed) {
                    Android17Compat.grantRuntimePermission(packageName, targetPermission, userId);
                } else {
                    Android17Compat.revokeRuntimePermission(packageName, targetPermission, userId);
                }
            }
        }

        configManager.update(uid, PackageManagerApis.getPackagesForUidNoThrow(uid), mask, value);
    }

    private void onPermissionRevoked(String packageName) {
        getUserServiceManager().removeUserServicesForPackage(packageName);
    }

    private ParcelableListSlice<PackageInfo> getApplications(int userId) {
        List<PackageInfo> list = new ArrayList<>();
        List<Integer> users = new ArrayList<>();
        if (userId == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow());
        } else {
            users.add(userId);
        }

        for (int user : users) {
            boolean stubActive = isShizukuStubActive(user);
            for (PackageInfo pi : InstalledPackagesCompat.getInstalledPackagesNoThrow(PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS, user)) {
                if (Objects.equals(MANAGER_APPLICATION_ID, pi.packageName)) continue;
                if (Objects.equals(ServerConstants.SHIZUKU_STUB_APPLICATION_ID, pi.packageName)) continue;
                if (pi.applicationInfo == null) continue;

                int uid = pi.applicationInfo.uid;
                int flags = 0;
                ShizukuConfig.PackageEntry entry = (ShizukuConfig.PackageEntry) configManager.find(uid);
                if (entry != null) {
                    if (entry.packages != null && !entry.packages.contains(pi.packageName))
                        continue;
                    flags = entry.flags & ConfigManager.MASK_PERMISSION;
                }

                if (flags != 0) {
                    list.add(pi);
                } else if (ServerConstants.hasSheveryPermission(pi.requestedPermissions)) {
                    list.add(pi);
                } else if (stubActive
                        && pi.applicationInfo.metaData != null
                        && pi.applicationInfo.metaData.getBoolean(ServerConstants.META_DATA_SHIZUKU_CLIENT, false)
                        && ServerConstants.hasLegacyShizukuPermission(pi.requestedPermissions)) {
                    list.add(pi);
                }
            }
        }
        return new ParcelableListSlice<>(list);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId
                    && checkCallingPermission() != PackageManager.PERMISSION_GRANTED) {
                reply.writeException(new SecurityException("Permission denied"));
                return true;
            }
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            int userId = data.readInt();
            ParcelableListSlice<PackageInfo> result = getApplications(userId);
            reply.writeNoException();
            result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            return true;
        } else if (code == ServerConstants.BINDER_TRANSACTION_setErrorProtectEnabled) {
            if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
                reply.writeException(new SecurityException("Permission denied"));
                return true;
            }
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            boolean enabled = data.readInt() != 0;
            LOGGER.i("setErrorProtectEnabled: %s", Boolean.toString(enabled));
            configManager.setErrorProtectEnabled(enabled);
            applyErrorProtectState(enabled);
            reply.writeNoException();
            return true;
        } else if (code == ServerConstants.BINDER_TRANSACTION_getErrorProtectEnabled) {
            if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
                reply.writeException(new SecurityException("Permission denied"));
                return true;
            }
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            reply.writeNoException();
            reply.writeInt(configManager.getErrorProtectEnabled() ? 1 : 0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private static boolean isPackageRunning(String packageName, int userId) {
        try {
            int state = ActivityManagerApis.getPackageProcessState(packageName, userId, "shell");
            // In Android/AOSP, process states:
            // 0..19: Active / running / cached process in memory
            // 20+: PROCESS_STATE_NONEXISTENT (does not exist)
            // < 0: PROCESS_STATE_UNKNOWN
            return state >= 0 && state < 20;
        } catch (Throwable tr) {
            return false;
        }
    }

    void sendBinderToClient() {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId);
        }
    }

    private static void sendBinderToClient(Binder binder, int userId) {
        try {
            boolean stubActive = isShizukuStubActive(userId);
            for (PackageInfo pi : InstalledPackagesCompat.getInstalledPackagesNoThrow(PackageManager.GET_PERMISSIONS, userId)) {
                if (pi == null || pi.requestedPermissions == null)
                    continue;

                if (Objects.equals(MANAGER_APPLICATION_ID, pi.packageName)
                        || Objects.equals(ServerConstants.SHIZUKU_STUB_APPLICATION_ID, pi.packageName))
                    continue;

                boolean isShevery = ServerConstants.hasSheveryPermission(pi.requestedPermissions);
                boolean isLegacy = stubActive && ServerConstants.hasLegacyShizukuPermission(pi.requestedPermissions);
                if (!isShevery && !isLegacy) {
                    continue;
                }

                // Strictly avoid waking up inactive or stopped applications on server startup (issue #348).
                // Any application launched by the user later receives the binder on-demand via BinderSender.
                if (!isPackageRunning(pi.packageName, userId)) {
                    continue;
                }

                if (isShevery) {
                    sendBinderToUserAppAsync(binder, pi.packageName, userId, true);
                } else {
                    sendBinderToUserAppAsync(binder, pi.packageName, userId, false);
                }
            }
        } catch (Throwable tr) {
            LOGGER.e("exception when call getInstalledPackages", tr);
        }
    }

    void sendBinderToManager() {
        sendBinderToManager(this);
    }

    private static void sendBinderToManager(Binder binder) {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToManager(binder, userId);
        }
    }

    static void sendBinderToManager(Binder binder, int userId) {
        if (userId != 0 && Android17Compat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, userId) == null) {
            LOGGER.d("Manager app not installed in user %d, skipping binder dispatch", userId);
            return;
        }
        sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId, true, true);
    }

    @Deprecated
    static void sendBinderToManger(Binder binder, int userId) {
        sendBinderToManager(binder, userId);
    }

    static void sendBinderToUserAppAsync(Binder binder, String packageName, int userId, boolean preferSheveryProvider) {
        BINDER_DISPATCH_EXECUTOR.execute(() -> sendBinderToUserApp(binder, packageName, userId, true, preferSheveryProvider));
    }

    static void sendBinderToUserApp(Binder binder, String packageName, int userId) {
        sendBinderToUserApp(binder, packageName, userId, true, true);
    }

    static void sendBinderToUserApp(Binder binder, String packageName, int userId, boolean retry) {
        sendBinderToUserApp(binder, packageName, userId, retry, true);
    }

    private static final long[] FAST_PROVIDER_RETRY_BACKOFF_MS = {50L, 150L, 400L};

    static void sendBinderToUserApp(Binder binder, String packageName, int userId, boolean retry, boolean preferSheveryProvider) {
        if (!Objects.equals(MANAGER_APPLICATION_ID, packageName)) {
            try {
                DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(packageName, 30 * 1000, userId,
                        316, "shell");
            } catch (Throwable tr) {
                LOGGER.w(tr, "Failed to add %d:%s to power save temp whitelist", userId, packageName);
            }
        }

        String primarySuffix = preferSheveryProvider
                ? ServerConstants.SHEVERY_PROVIDER_SUFFIX
                : ServerConstants.SHIZUKU_PROVIDER_SUFFIX;
        String fallbackSuffix = preferSheveryProvider
                ? ServerConstants.SHIZUKU_PROVIDER_SUFFIX
                : ServerConstants.SHEVERY_PROVIDER_SUFFIX;

        if (trySendBinderViaAuthority(binder, packageName, packageName + primarySuffix, userId, retry, false)) {
            return;
        }
        trySendBinderViaAuthority(binder, packageName, packageName + fallbackSuffix, userId, retry, true);
    }

    private static boolean trySendBinderViaAuthority(
            Binder binder,
            String packageName,
            String authority,
            int userId,
            boolean retry,
            boolean isFallback
    ) {
        IContentProvider provider = null;
        IBinder token = null;

        try {
            int maxAttempts = isFallback ? FAST_PROVIDER_RETRY_BACKOFF_MS.length : 1;
            for (int attempt = 0; ; attempt++) {
                provider = ActivityManagerApis.getContentProviderExternal(authority, userId, token, authority);
                if (provider != null) {
                    break;
                }
                if (attempt >= maxAttempts) {
                    if (isFallback) {
                        LOGGER.w("provider is null %s %d", authority, userId);
                    }
                    return false;
                }
                Thread.sleep(FAST_PROVIDER_RETRY_BACKOFF_MS[attempt]);
            }

            boolean isAlive = provider.asBinder().pingBinder();
            if (!isAlive) {
                LOGGER.e("provider is dead %s %d", authority, userId);
                return false;
            }

            BinderContainer container = new BinderContainer(binder);
            Bundle extra = new Bundle();
            extra.putParcelable(ServerConstants.EXTRA_BINDER_SHEVERY, container);
            extra.putParcelable(ServerConstants.EXTRA_BINDER_SHIZUKU, container);

            Bundle reply = IContentProviderUtils.callCompat(provider, null, authority, "sendBinder", null, extra);
            if (reply != null) {
                if (Objects.equals(MANAGER_APPLICATION_ID, packageName)) {
                    Log.i(TAG, "Dispatched server binder to manager (" + authority + ")");
                }
                LOGGER.i("send binder to %s via %s in user %d", packageName, authority, userId);
                return true;
            }
            return false;
        } catch (Throwable tr) {
            LOGGER.w(tr, "failed send binder to %s via %s in user %d", packageName, authority, userId);
            return false;
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(authority, token);
                } catch (Throwable tr) {
                    LOGGER.w(tr, "removeContentProviderExternal");
                }
            }
        }
    }

    @Override
    public void dispatchPackageChanged(Intent intent) throws RemoteException {
    }

    @Override
    public boolean isHidden(int uid) throws RemoteException {
        return false;
    }
}
