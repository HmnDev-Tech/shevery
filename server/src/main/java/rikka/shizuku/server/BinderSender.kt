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

object BinderSender {

    private val logger = Logger("BinderSender")

    private const val PERMISSION_MANAGER = "moe.shizuku.manager.permission.MANAGER"
    private const val PERMISSION = "moe.shizuku.manager.permission.API_V23"

    private var shizukuService: ShizukuService? = null

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
        }
    }

    @Throws(RemoteException::class)
    private fun sendBinder(uid: Int, pid: Int) {
        val service = shizukuService ?: return
        val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
        if (packages.isEmpty()) {
            return
        }

        logger.d("sendBinder to uid %d: packages=%s", uid, TextUtils.join(", ", packages))

        val userId = UserHandleCompat.getUserId(uid)
        var sentManager = false
        for (packageName in packages) {
            val packageInfo = Android17Compat.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS.toLong(),
                userId
            )
            val requestedPermissions = packageInfo?.requestedPermissions ?: continue

            if (requestedPermissions.contains(PERMISSION_MANAGER)) {
                val granted = if (pid == -1) {
                    Android17Compat.checkPermission(PERMISSION_MANAGER, uid) == PackageManager.PERMISSION_GRANTED
                } else {
                    ActivityManagerApis.checkPermission(PERMISSION_MANAGER, pid, uid) == PackageManager.PERMISSION_GRANTED
                }

                if (granted && !sentManager) {
                    ShizukuService.sendBinderToManager(service, userId)
                    sentManager = true
                }
            }

            if (requestedPermissions.contains(PERMISSION)) {
                ShizukuService.sendBinderToUserApp(service, packageName, userId)
            }
        }
    }

    @JvmStatic
    fun register(shizukuService: ShizukuService) {
        this.shizukuService = shizukuService

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
