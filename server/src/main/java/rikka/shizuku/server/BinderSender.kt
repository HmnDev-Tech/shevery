package rikka.shizuku.server

import android.app.ActivityManagerHidden
import android.app.ActivityManagerHidden.UID_OBSERVER_ACTIVE
import android.app.ActivityManagerHidden.UID_OBSERVER_CACHED
import android.app.ActivityManagerHidden.UID_OBSERVER_GONE
import android.app.ActivityManagerHidden.UID_OBSERVER_IDLE
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.RequiresApi
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.adapter.ProcessObserverAdapter
import rikka.hidden.compat.adapter.UidObserverAdapter
import rikka.shizuku.server.util.Android17Compat
import rikka.shizuku.server.util.Logger
import rikka.shizuku.server.util.UserHandleCompat
import java.util.concurrent.ConcurrentHashMap

object BinderSender {

    private val logger = Logger("BinderSender")

    private data class PackageDispatchEntry(
        val packageName: String,
        val managerPermission: String?,
        val isNativeSheveryClient: Boolean,
        val isLegacyShizukuStubClient: Boolean
    )

    private val uidDispatchCache = ConcurrentHashMap<Int, List<PackageDispatchEntry>>()

    private var shizukuService: ShizukuService? = null

    @JvmStatic
    fun invalidateCache() {
        uidDispatchCache.clear()
    }

    @JvmStatic
    fun invalidateUid(uid: Int) {
        uidDispatchCache.remove(uid)
    }

    private class ProcessObserver : ProcessObserverAdapter() {

        @Throws(RemoteException::class)
        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foregroundActivities: Boolean) {
            logger.d(
                "onForegroundActivitiesChanged: pid=%d, uid=%d, foregroundActivities=%s",
                pid,
                uid,
                foregroundActivities.toString()
            )

            synchronized(startedPids) {
                if (!foregroundActivities || !startedPids.add(pid)) {
                    return
                }
            }

            sendBinder(uid, pid)
        }

        override fun onProcessDied(pid: Int, uid: Int) {
            logger.d("onProcessDied: pid=%d, uid=%d", pid, uid)

            synchronized(startedPids) {
                if (startedPids.remove(pid)) {
                    logger.v("Pid %d dead", pid)
                }
            }
        }

        @Throws(RemoteException::class)
        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {
            logger.d("onProcessStateChanged: pid=%d, uid=%d, procState=%d", pid, uid, procState)

            synchronized(startedPids) {
                if (!startedPids.add(pid)) {
                    return
                }
            }

            sendBinder(uid, pid)
        }

        companion object {
            private val startedPids = HashSet<Int>()

            fun clear() {
                synchronized(startedPids) {
                    startedPids.clear()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private class UidObserver : UidObserverAdapter() {

        @Throws(RemoteException::class)
        override fun onUidActive(uid: Int) {
            logger.d("onUidActive: uid=%d", uid)
            uidStarts(uid)
        }

        @Throws(RemoteException::class)
        override fun onUidCachedChanged(uid: Int, cached: Boolean) {
            logger.d("onUidCachedChanged: uid=%d, cached=%s", uid, cached.toString())

            if (!cached) {
                uidStarts(uid)
            }
        }

        @Throws(RemoteException::class)
        override fun onUidIdle(uid: Int, disabled: Boolean) {
            logger.d("onUidIdle: uid=%d, disabled=%s", uid, disabled.toString())
            uidStarts(uid)
        }

        override fun onUidGone(uid: Int, disabled: Boolean) {
            logger.d("onUidGone: uid=%d, disabled=%s", uid, disabled.toString())
            uidGone(uid)
        }

        @Throws(RemoteException::class)
        private fun uidStarts(uid: Int) {
            synchronized(startedUids) {
                if (!startedUids.add(uid)) {
                    logger.v("Uid %d already starts", uid)
                    return
                }
                logger.v("Uid %d starts", uid)
            }

            sendBinder(uid, -1)
        }

        private fun uidGone(uid: Int) {
            synchronized(startedUids) {
                if (startedUids.remove(uid)) {
                    logger.v("Uid %d dead", uid)
                }
            }
        }

        companion object {
            private val startedUids = HashSet<Int>()

            fun clear() {
                synchronized(startedUids) {
                    startedUids.clear()
                }
            }
        }
    }

    private fun resolveDispatchEntries(uid: Int, userId: Int): List<PackageDispatchEntry> {
        uidDispatchCache[uid]?.let { return it }

        val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
        if (packages.isEmpty()) {
            return emptyList()
        }

        val entries = ArrayList<PackageDispatchEntry>(packages.size)
        for (packageName in packages) {
            if (packageName == ServerConstants.SHIZUKU_STUB_APPLICATION_ID) {
                continue
            }
            val packageInfo = Android17Compat.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS.toLong(),
                userId
            )
            val requestedPermissions = packageInfo?.requestedPermissions ?: continue
            val managerPerm = ServerConstants.resolveManagerPermission(requestedPermissions)
            val isNativeShevery = ServerConstants.hasSheveryPermission(requestedPermissions)
            val isLegacyShizuku = !isNativeShevery && ServerConstants.hasLegacyShizukuPermission(requestedPermissions)

            if (managerPerm != null || isNativeShevery || isLegacyShizuku) {
                entries.add(
                    PackageDispatchEntry(
                        packageName = packageName,
                        managerPermission = managerPerm,
                        isNativeSheveryClient = isNativeShevery,
                        isLegacyShizukuStubClient = isLegacyShizuku
                    )
                )
            }
        }

        uidDispatchCache[uid] = entries
        return entries
    }

    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
        val service = shizukuService ?: return
        val userId = UserHandleCompat.getUserId(uid)
        val entries = resolveDispatchEntries(uid, userId)
        if (entries.isEmpty()) {
            return
        }

        logger.d(
            "sendBinder to uid %d: packages=%s",
            uid,
            TextUtils.join(", ", entries.map { it.packageName })
        )

        var sentManager = false
        for (entry in entries) {
            val managerPerm = entry.managerPermission
            if (managerPerm != null && !sentManager) {
                val granted = if (pid == -1) {
                    Android17Compat.checkPermission(managerPerm, uid) == PackageManager.PERMISSION_GRANTED
                } else {
                    ActivityManagerApis.checkPermission(managerPerm, pid, uid) == PackageManager.PERMISSION_GRANTED
                }

                if (granted) {
                    ShizukuService.sendBinderToManager(service, userId)
                    sentManager = true
                    continue
                }
            }

            if (entry.isNativeSheveryClient) {
                // Fast native Shevery Privilege API path
                ShizukuService.sendBinderToUserAppAsync(service, entry.packageName, userId, true)
            } else if (entry.isLegacyShizukuStubClient && ShizukuService.isShizukuStubActive(userId)) {
                // Legacy API_V23 path offloaded via Shizuku Stub
                ShizukuService.sendBinderToUserAppAsync(service, entry.packageName, userId, false)
            }
        }
    }

    @JvmStatic
    fun register(shizukuService: ShizukuService) {
        this.shizukuService = shizukuService
        invalidateCache()
        ProcessObserver.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            UidObserver.clear()
        }

        try {
            ActivityManagerApis.registerProcessObserver(ProcessObserver())
        } catch (tr: Throwable) {
            logger.e(tr, "registerProcessObserver")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = UID_OBSERVER_GONE or UID_OBSERVER_IDLE or UID_OBSERVER_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                flags = flags or UID_OBSERVER_CACHED
            }
            try {
                ActivityManagerApis.registerUidObserver(
                    UidObserver(),
                    flags,
                    ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
                    null
                )
            } catch (tr: Throwable) {
                logger.e(tr, "registerUidObserver")
            }
        }
    }
}
