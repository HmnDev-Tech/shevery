package rikka.shizuku.server.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object Android17Compat {

    private const val TAG = "ShizukuAndroid17Compat"
    private const val PERSISTENT_DEVICE_ID = "default:0"
    private const val DEVICE_ID_DEFAULT = 0
    private const val REVOKE_REASON = "shizuku"

    private val LOGGER = Logger(TAG)

    private val INT: Class<*> = Int::class.javaPrimitiveType!!
    private val LONG: Class<*> = Long::class.javaPrimitiveType!!
    private val STRING: Class<*> = String::class.java

    private enum class Backend(val serviceName: String, val interfaceName: String) {
        PACKAGE("package", "android.content.pm.IPackageManager"),
        PERMISSION("permissionmgr", "android.permission.IPermissionManager")
    }

    private enum class Operation {
        PACKAGE_INFO,
        APPLICATION_INFO,
        CHECK_PERMISSION_PACKAGE,
        CHECK_PERMISSION_UID,
        GRANT_RUNTIME_PERMISSION,
        REVOKE_RUNTIME_PERMISSION
    }

    private class ManagerHolder {
        @Volatile
        var value: Any? = null

        @Volatile
        var failure: Throwable? = null
    }

    private class Candidate(
        val backend: Backend,
        val methodName: String,
        val parameterTypes: List<Class<*>>,
        val arguments: List<Any?>
    )

    private class Resolution(
        val backend: Backend,
        val method: Method,
        val candidateIndex: Int
    )

    private class Outcome(
        val reached: Boolean,
        val value: Any?,
        val failure: Throwable?
    )

    private val packageManagerHolder = ManagerHolder()
    private val permissionManagerHolder = ManagerHolder()

    private val resolutions = HashMap<Operation, Resolution>()
    private val unresolved = HashSet<Operation>()

    @JvmStatic
    @Synchronized
    fun invalidate(serviceName: String) {
        when (serviceName) {
            Backend.PACKAGE.serviceName -> {
                packageManagerHolder.value = null
                packageManagerHolder.failure = null
                resolutions.entries.removeAll { it.value.backend == Backend.PACKAGE }
                unresolved.clear()
            }
            Backend.PERMISSION.serviceName -> {
                permissionManagerHolder.value = null
                permissionManagerHolder.failure = null
                resolutions.entries.removeAll { it.value.backend == Backend.PERMISSION }
                unresolved.clear()
            }
        }
    }

    @Synchronized
    private fun managerHolder(backend: Backend): ManagerHolder =
        if (backend == Backend.PACKAGE) packageManagerHolder else permissionManagerHolder

    @Synchronized
    private fun obtain(backend: Backend): Any? {
        val holder = managerHolder(backend)
        holder.value?.let { return it }
        if (holder.failure != null) {
            return null
        }
        return try {
            val binder = ServiceManager.getService(backend.serviceName)
                ?: throw IllegalStateException("${backend.serviceName} service is not available")
            val stub = Class.forName(backend.interfaceName + "\$Stub")
            val asInterface = stub.getDeclaredMethod("asInterface", IBinder::class.java)
            val value = asInterface.invoke(null, binder)
                ?: throw IllegalStateException("asInterface of ${backend.interfaceName} returned null")
            holder.value = value
            value
        } catch (t: Throwable) {
            holder.failure = t
            LOGGER.w(t, "%s: resolving %s failed", backend.serviceName, backend.interfaceName)
            null
        }
    }

    @Synchronized
    private fun lookupFailure(backend: Backend): Throwable? = managerHolder(backend).failure

    @Synchronized
    private fun resolution(operation: Operation): Resolution? = resolutions[operation]

    @Synchronized
    private fun isUnresolved(operation: Operation): Boolean = unresolved.contains(operation)

    @Synchronized
    private fun rememberResolution(operation: Operation, resolution: Resolution) {
        resolutions[operation] = resolution
        unresolved.remove(operation)
    }

    @Synchronized
    private fun forgetResolution(operation: Operation) {
        resolutions.remove(operation)
        unresolved.remove(operation)
    }

    @Synchronized
    private fun markUnresolved(operation: Operation) {
        unresolved.add(operation)
    }

    @JvmStatic
    fun getPackageInfo(packageName: String, flags: Long, userId: Int): PackageInfo? {
        return try {
            getPackageInfoOrThrow(packageName, flags, userId)
        } catch (t: Throwable) {
            LOGGER.w(t, "getPackageInfo(%s, %d) failed", packageName, userId)
            null
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getPackageInfoOrThrow(packageName: String, flags: Long, userId: Int): PackageInfo? {
        return value(
            Operation.PACKAGE_INFO,
            listOf(
                Candidate(
                    Backend.PACKAGE,
                    "getPackageInfo",
                    listOf(STRING, LONG, INT),
                    listOf(packageName, flags, userId)
                ),
                // API 32 and older expose int flags; long flags arrived with
                // PackageInfoFlags in API 33.
                Candidate(
                    Backend.PACKAGE,
                    "getPackageInfo",
                    listOf(STRING, INT, INT),
                    listOf(packageName, flags.toInt(), userId)
                )
            )
        ) as PackageInfo?
    }

    @JvmStatic
    fun getApplicationInfo(packageName: String, flags: Long, userId: Int): ApplicationInfo? {
        return try {
            value(
                Operation.APPLICATION_INFO,
                listOf(
                    Candidate(
                        Backend.PACKAGE,
                        "getApplicationInfo",
                        listOf(STRING, LONG, INT),
                        listOf(packageName, flags, userId)
                    ),
                    // API 32 and older expose int flags; long flags arrived with
                    // ApplicationInfoFlags in API 33.
                    Candidate(
                        Backend.PACKAGE,
                        "getApplicationInfo",
                        listOf(STRING, INT, INT),
                        listOf(packageName, flags.toInt(), userId)
                    )
                )
            ) as ApplicationInfo?
        } catch (t: Throwable) {
            LOGGER.w(t, "getApplicationInfo(%s, %d) failed", packageName, userId)
            null
        }
    }

    @JvmStatic
    fun checkPermission(permissionName: String, packageName: String, userId: Int): Int {
        val outcome = invoke(
            Operation.CHECK_PERMISSION_PACKAGE,
            listOf(
                Candidate(
                    Backend.PERMISSION,
                    "checkPermission",
                    listOf(STRING, STRING, STRING, INT),
                    listOf(packageName, permissionName, PERSISTENT_DEVICE_ID, userId)
                ),
                Candidate(
                    Backend.PERMISSION,
                    "checkPermission",
                    listOf(STRING, STRING, INT),
                    listOf(permissionName, packageName, userId)
                )
            )
        )
        if (outcome.reached && outcome.failure == null) {
            val result = outcome.value
            if (result is Int) {
                return result
            }
        }
        return PackageManager.PERMISSION_DENIED
    }

    @JvmStatic
    fun checkPermission(permissionName: String, uid: Int): Int {
        val outcome = invoke(
            Operation.CHECK_PERMISSION_UID,
            listOf(
                Candidate(
                    Backend.PERMISSION,
                    "checkUidPermission",
                    listOf(INT, STRING, INT),
                    listOf(uid, permissionName, DEVICE_ID_DEFAULT)
                ),
                Candidate(
                    Backend.PACKAGE,
                    "checkUidPermission",
                    listOf(STRING, INT),
                    listOf(permissionName, uid)
                )
            )
        )
        if (outcome.reached && outcome.failure == null) {
            val result = outcome.value
            if (result is Int) {
                return result
            }
        }
        return PackageManager.PERMISSION_DENIED
    }

    @JvmStatic
    fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int): Boolean {
        val outcome = invoke(
            Operation.GRANT_RUNTIME_PERMISSION,
            listOf(
                Candidate(
                    Backend.PERMISSION,
                    "grantRuntimePermission",
                    listOf(STRING, STRING, STRING, INT),
                    listOf(packageName, permissionName, PERSISTENT_DEVICE_ID, userId)
                ),
                Candidate(
                    Backend.PERMISSION,
                    "grantRuntimePermission",
                    listOf(STRING, STRING, INT),
                    listOf(packageName, permissionName, userId)
                )
            )
        )
        return outcome.reached
    }

    @JvmStatic
    fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int): Boolean {
        val outcome = invoke(
            Operation.REVOKE_RUNTIME_PERMISSION,
            listOf(
                Candidate(
                    Backend.PERMISSION,
                    "revokeRuntimePermission",
                    listOf(STRING, STRING, STRING, INT, STRING),
                    listOf(packageName, permissionName, PERSISTENT_DEVICE_ID, userId, REVOKE_REASON)
                ),
                Candidate(
                    Backend.PERMISSION,
                    "revokeRuntimePermission",
                    listOf(STRING, STRING, STRING, INT),
                    listOf(packageName, permissionName, PERSISTENT_DEVICE_ID, userId)
                ),
                Candidate(
                    Backend.PERMISSION,
                    "revokeRuntimePermission",
                    listOf(STRING, STRING, INT),
                    listOf(packageName, permissionName, userId)
                )
            )
        )
        return outcome.reached
    }

    private fun value(operation: Operation, candidates: List<Candidate>): Any? {
        val outcome = invoke(operation, candidates)
        if (!outcome.reached) {
            throw outcome.failure ?: IllegalStateException("${operation.name} was not reached")
        }
        outcome.failure?.let { throw it }
        return outcome.value
    }

    private fun invoke(operation: Operation, candidates: List<Candidate>): Outcome {
        if (isUnresolved(operation)) {
            return Outcome(false, null, null)
        }
        val cached = resolution(operation)
        if (cached != null) {
            invokeResolved(operation, cached, candidates)?.let { return it }
        }
        return search(operation, candidates)
    }

    private fun invokeResolved(operation: Operation, cached: Resolution, candidates: List<Candidate>): Outcome? {
        val candidate = candidates.getOrNull(cached.candidateIndex)
        if (candidate == null) {
            forgetResolution(operation)
            return null
        }
        val target = obtain(cached.backend)
        if (target == null) {
            return Outcome(false, null, lookupFailure(cached.backend))
        }
        val outcome = attempt(operation, candidate, target, cached.method)
        if (outcome.reached) {
            return outcome
        }
        if (outcome.failure is IllegalArgumentException) {
            forgetResolution(operation)
            return null
        }
        markUnresolved(operation)
        return outcome
    }

    private fun search(operation: Operation, candidates: List<Candidate>): Outcome {
        val triedShapes = LinkedHashSet<List<Class<*>>>()
        var rejection: Throwable? = null
        var lookup: Throwable? = null

        for ((index, candidate) in candidates.withIndex()) {
            if (!triedShapes.add(candidate.parameterTypes)) {
                LOGGER.w(
                    "%s: candidate %d rejected, parameter shape (%s) is a duplicate",
                    operation.name,
                    index,
                    shape(candidate.parameterTypes)
                )
                continue
            }
            if (!isBound(candidate)) {
                continue
            }
            val target = obtain(candidate.backend)
            if (target == null) {
                lookup = lookupFailure(candidate.backend)
                continue
            }
            val method = findMethod(target, candidate) ?: continue
            val outcome = attempt(operation, candidate, target, method)
            if (outcome.reached) {
                rememberResolution(operation, Resolution(candidate.backend, method, index))
                return outcome
            }
            if (outcome.failure is IllegalArgumentException) {
                rejection = outcome.failure
                continue
            }
            markUnresolved(operation)
            return outcome
        }

        markUnresolved(operation)
        LOGGER.w(
            "%s: no candidate shape was accepted, candidates were (%s)",
            operation.name,
            candidates.joinToString("; ") { shape(it.parameterTypes) }
        )
        return Outcome(false, null, rejection ?: lookup)
    }

    private fun attempt(operation: Operation, candidate: Candidate, target: Any, method: Method): Outcome {
        return try {
            Outcome(true, method.invoke(target, *candidate.arguments.toTypedArray()), null)
        } catch (e: IllegalArgumentException) {
            Outcome(false, null, e)
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            LOGGER.w(cause, "%s: %s refused the request", operation.name, candidate.methodName)
            Outcome(true, null, cause)
        } catch (t: Throwable) {
            LOGGER.w(t, "%s: calling %s failed", operation.name, candidate.methodName)
            Outcome(false, null, t)
        }
    }

    private fun findMethod(target: Any, candidate: Candidate): Method? {
        for (method in target.javaClass.methods) {
            if (method.name != candidate.methodName) {
                continue
            }
            if (method.parameterTypes.toList() != candidate.parameterTypes) {
                continue
            }
            return method
        }
        return null
    }

    private fun isBound(candidate: Candidate): Boolean {
        if (candidate.parameterTypes.size != candidate.arguments.size) {
            return false
        }
        for (i in candidate.parameterTypes.indices) {
            if (!isAssignable(candidate.parameterTypes[i], candidate.arguments[i])) {
                return false
            }
        }
        return true
    }

    private fun isAssignable(parameterType: Class<*>, argument: Any?): Boolean {
        if (argument == null) {
            return !parameterType.isPrimitive
        }
        if (parameterType.isInstance(argument)) {
            return true
        }
        return when (parameterType) {
            INT -> argument is Int
            LONG -> argument is Long
            else -> false
        }
    }

    private fun shape(parameterTypes: List<Class<*>>): String =
        parameterTypes.joinToString(",") { it.simpleName }
}
