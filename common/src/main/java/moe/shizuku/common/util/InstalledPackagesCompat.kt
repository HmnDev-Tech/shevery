package moe.shizuku.common.util

import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Reflection-based compatibility helper for querying installed packages across Android versions. */
object InstalledPackagesCompat {

    private const val TAG = "InstalledPackagesCompat"
    private const val ANDROID_13 = 33
    private const val PARCELED_LIST_SLICE = "android.content.pm.ParceledListSlice"

    @JvmStatic
    fun getInstalledPackagesNoThrow(flags: Long, userId: Int): List<PackageInfo> {
        return try {
            getInstalledPackages(flags, userId)
        } catch (e: Throwable) {
            Log.w(TAG, "getInstalledPackages failed", e)
            emptyList()
        }
    }

    @JvmStatic
    @Throws(ReflectiveOperationException::class)
    @Suppress("UNCHECKED_CAST")
    fun getInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        try {
            val packages = getInstalledPackagesViaBinder(flags, userId)
            if (packages.isNotEmpty()) {
                return packages
            }
        } catch (_: NoSuchMethodException) {
        } catch (e: Exception) {
            Log.d(
                TAG,
                "getInstalledPackages via hidden IPackageManager failed, falling back to context PackageManager",
                e
            )
        }

        try {
            val packageManager = getContextPackageManager()
            val method = packageManager.javaClass.getMethod(
                "getInstalledPackagesAsUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val result = invoke(method, packageManager, flags.toInt(), userId)
            return (result as? List<PackageInfo>) ?: emptyList()
        } catch (_: NoSuchMethodException) {
        } catch (e: Exception) {
            Log.d(TAG, "getInstalledPackagesAsUser fallback failed", e)
        }

        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(ReflectiveOperationException::class)
    private fun getInstalledPackagesViaBinder(flags: Long, userId: Int): List<PackageInfo> {
        val packageManager = getPackageManager()
        val method: Method
        val result: Any?

        if (Build.VERSION.SDK_INT >= ANDROID_13) {
            method = packageManager.javaClass.getMethod(
                "getInstalledPackages",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            result = invoke(method, packageManager, flags, userId)
        } else {
            method = packageManager.javaClass.getMethod(
                "getInstalledPackages",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            result = invoke(method, packageManager, flags.toInt(), userId)
        }

        if (result == null) {
            return emptyList()
        }

        val resultClassName = result.javaClass.name
        if (resultClassName.startsWith(PARCELED_LIST_SLICE) || resultClassName.contains("PackageInfoList")) {
            val list = result.javaClass.getMethod("getList").invoke(result)
            return (list as? List<PackageInfo>) ?: emptyList()
        }

        throw IllegalStateException("Unsupported getInstalledPackages return type: $resultClassName")
    }

    @Throws(ReflectiveOperationException::class)
    private fun getPackageManager(): Any {
        val servicesClass = Class.forName("rikka.hidden.compat.Services")
        val field = servicesClass.getDeclaredField("packageManager").apply {
            isAccessible = true
        }
        val service = field.get(null)
            ?: throw IllegalStateException("Services.packageManager is null")
        return service.javaClass.getMethod("get").invoke(service)
            ?: throw IllegalStateException("Services.packageManager.get() returned null")
    }

    @Throws(ReflectiveOperationException::class)
    private fun getContextPackageManager(): Any {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        var activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
        if (activityThread != null) {
            val application = activityThreadClass.getMethod("getApplication").invoke(activityThread)
            if (application != null) {
                return application.javaClass.getMethod("getPackageManager").invoke(application)!!
            }
        }

        activityThread = activityThreadClass.getMethod("systemMain").invoke(null)
        val systemContext = activityThreadClass.getMethod("getSystemContext").invoke(activityThread)!!
        return systemContext.javaClass.getMethod("getPackageManager").invoke(systemContext)!!
    }

    @Throws(ReflectiveOperationException::class)
    private fun invoke(method: Method, receiver: Any, vararg args: Any?): Any? {
        return try {
            method.invoke(receiver, *args)
        } catch (e: InvocationTargetException) {
            when (val cause = e.cause) {
                is ReflectiveOperationException -> throw cause
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw e
            }
        }
    }
}
