package moe.shizuku.manager.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ParceledListSlice
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.PermissionManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.hidden.compat.util.SystemServiceBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import moe.shizuku.common.util.InstalledPackagesCompat
import java.lang.reflect.Method

object ShizukuSystemApis {

    @Volatile
    private var sPackageManager: Any? = null
    @Volatile
    private var getInstalledPackagesMethod: Method? = null
    @Volatile
    private var getPackageInfoMethod: Method? = null
    @Volatile
    private var getApplicationInfoMethod: Method? = null
    @Volatile
    private var getListMethod: Method? = null

    @Volatile
    private var sPermissionManager: Any? = null
    @Volatile
    private var grantRuntimePermissionMethod: Method? = null
    @Volatile
    private var revokeRuntimePermissionMethod: Method? = null
    @Volatile
    private var checkPermissionMethod: Method? = null

    init {
        SystemServiceBinder.setOnGetBinderListener {
            return@setOnGetBinderListener ShizukuBinderWrapper(it)
        }
    }

    @Synchronized
    private fun getPackageManager(): Any? {
        if (sPackageManager == null) {
            try {
                val packageManagerStub = Class.forName("android.content.pm.IPackageManager\$Stub")
                val pmBinder = ShizukuBinderWrapper(ServiceManager.getService("package"))
                sPackageManager = packageManagerStub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, pmBinder)
                sPackageManager?.let { pm ->
                    for (method in pm.javaClass.methods) {
                        when (method.name) {
                            "getInstalledPackages" -> {
                                if (method.parameterTypes.isNotEmpty() && method.parameterTypes[0] == Long::class.javaPrimitiveType) {
                                    val current = getInstalledPackagesMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        getInstalledPackagesMethod = method
                                    }
                                }
                            }
                            "getPackageInfo" -> {
                                if (method.parameterTypes.size >= 2 && method.parameterTypes[0] == String::class.java && method.parameterTypes[1] == Long::class.javaPrimitiveType) {
                                    val current = getPackageInfoMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        getPackageInfoMethod = method
                                    }
                                }
                            }
                            "getApplicationInfo" -> {
                                if (method.parameterTypes.size >= 2 && method.parameterTypes[0] == String::class.java && method.parameterTypes[1] == Long::class.javaPrimitiveType) {
                                    val current = getApplicationInfoMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        getApplicationInfoMethod = method
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
            }
        }
        return sPackageManager
    }

    @Synchronized
    private fun getPermissionManager(): Any? {
        if (sPermissionManager == null) {
            try {
                val permManagerStub = Class.forName("android.permission.IPermissionManager\$Stub")
                val permBinder = ShizukuBinderWrapper(ServiceManager.getService("permissionmgr"))
                sPermissionManager = permManagerStub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, permBinder)
                sPermissionManager?.let { pm ->
                    for (method in pm.javaClass.methods) {
                        when (method.name) {
                            "grantRuntimePermission" -> {
                                if (method.parameterTypes.size >= 3 && method.parameterTypes[0] == String::class.java) {
                                    val current = grantRuntimePermissionMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        grantRuntimePermissionMethod = method
                                    }
                                }
                            }
                            "revokeRuntimePermission" -> {
                                if (method.parameterTypes.size >= 3 && method.parameterTypes[0] == String::class.java) {
                                    val current = revokeRuntimePermissionMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        revokeRuntimePermissionMethod = method
                                    }
                                }
                            }
                            "checkPermission" -> {
                                if (method.parameterTypes.size >= 3 && method.parameterTypes[0] == String::class.java) {
                                    val current = checkPermissionMethod
                                    if (current == null || method.parameterTypes.size > current.parameterTypes.size) {
                                        checkPermissionMethod = method
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
            }
        }
        return sPermissionManager
    }

    private fun invokeCompat(method: Method?, target: Any?, vararg args: Any?): Any? {
        if (method == null || target == null) return null
        val paramTypes = method.parameterTypes
        val finalArgs = arrayOfNulls<Any>(paramTypes.size)

        if (paramTypes.size == args.size + 1) {
            val lastIdx = args.size - 1
            System.arraycopy(args, 0, finalArgs, 0, lastIdx)
            finalArgs[lastIdx] = 0
            finalArgs[lastIdx + 1] = args[lastIdx]

            for (i in (lastIdx + 2) until paramTypes.size) {
                if (paramTypes[i] == Int::class.javaPrimitiveType) finalArgs[i] = 0
                else if (paramTypes[i] == String::class.java) finalArgs[i] = null
            }
        } else {
            System.arraycopy(args, 0, finalArgs, 0, Math.min(args.size, paramTypes.size))
            for (i in args.size until paramTypes.size) {
                if (paramTypes[i] == Int::class.javaPrimitiveType) finalArgs[i] = args.lastOrNull()
            }
        }
        return method.invoke(target, *finalArgs)
    }

    private val users = arrayListOf<UserInfoCompat>()

    private fun getUsers(): List<UserInfoCompat> {
        return if (!Shizuku.pingBinder()) {
            arrayListOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        } else try {
            val list = UserManagerApis.getUsers(true, true, true)
            val users: MutableList<UserInfoCompat> = ArrayList<UserInfoCompat>()
            for (ui in list) {
                users.add(UserInfoCompat(ui.id, ui.name))
            }
            return users
        } catch (tr: Throwable) {
            arrayListOf(UserInfoCompat(UserHandleCompat.myUserId(), "Owner"))
        }
    }

    fun getUsers(useCache: Boolean = true): List<UserInfoCompat> {
        synchronized(users) {
            if (!useCache || users.isEmpty()) {
                users.clear()
                users.addAll(getUsers())
            }
            return users
        }
    }

    fun getUserInfo(userId: Int): UserInfoCompat {
        return getUsers(useCache = true).firstOrNull { it.id == userId } ?: UserInfoCompat(
            UserHandleCompat.myUserId(),
            "Unknown"
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        if (!Shizuku.pingBinder()) {
            return ArrayList()
        }
        try {
            return InstalledPackagesCompat.getInstalledPackages(flags, userId)
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        } catch (tr: ReflectiveOperationException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun getPackageInfo(packageName: String, flags: Long, userId: Int): PackageInfo? {
        if (!Shizuku.pingBinder()) return null
        try {
            return PackageManagerApis.getPackageInfo(packageName, flags, userId)
        } catch (e: NoSuchMethodError) {
            try {
                val pm = getPackageManager()
                return invokeCompat(getPackageInfoMethod, pm, packageName, flags, userId) as? PackageInfo
            } catch (t: Throwable) {}
            return null
        }
    }

    fun getApplicationInfo(packageName: String, flags: Long, userId: Int): ApplicationInfo? {
        if (!Shizuku.pingBinder()) return null
        try {
            return PackageManagerApis.getApplicationInfo(packageName, flags, userId)
        } catch (e: NoSuchMethodError) {
            try {
                val pm = getPackageManager()
                return invokeCompat(getApplicationInfoMethod, pm, packageName, flags, userId) as? ApplicationInfo
            } catch (t: Throwable) {}
            return null
        }
    }

    fun checkPermission(permName: String, pkgName: String, userId: Int): Int {
        if (!Shizuku.pingBinder()) {
            return PackageManager.PERMISSION_DENIED
        }
        try {
            return PermissionManagerApis.checkPermission(permName, pkgName, userId)
        } catch (e: NoSuchMethodError) {
            try {
                val pm = getPermissionManager()
                return invokeCompat(checkPermissionMethod, pm, permName, pkgName, userId) as? Int ?: PackageManager.PERMISSION_DENIED
            } catch (t: Throwable) {}
            return PackageManager.PERMISSION_DENIED
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Shizuku.pingBinder()) {
            return
        }
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId)
        } catch (e: NoSuchMethodError) {
            try {
                val pm = getPermissionManager()
                invokeCompat(grantRuntimePermissionMethod, pm, packageName, permissionName, userId)
            } catch (ex: Throwable) {
            }
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }

    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        if (!Shizuku.pingBinder()) {
            return
        }
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId)
        } catch (e: NoSuchMethodError) {
            try {
                val pm = getPermissionManager()
                val method = revokeRuntimePermissionMethod
                if (method != null && method.parameterTypes.size == 5 && method.parameterTypes[4] == String::class.java) {
                    method.invoke(pm, packageName, permissionName, 0, userId, "shizuku")
                } else {
                    invokeCompat(method, pm, packageName, permissionName, userId)
                }
            } catch (ex: Throwable) {
            }
        } catch (tr: RemoteException) {
            throw RuntimeException(tr.message, tr)
        }
    }
}
