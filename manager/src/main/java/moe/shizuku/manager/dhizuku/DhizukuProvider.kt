package moe.shizuku.manager.dhizuku

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteCallbackList
import android.os.ServiceManager
import com.rosan.dhizuku.aidl.IDhizuku
import com.rosan.dhizuku.aidl.IDhizukuLockTaskModeListener
import com.rosan.dhizuku.aidl.IDhizukuRemoteProcess
import com.rosan.dhizuku.aidl.IDhizukuUserServiceConnection
import com.rosan.dhizuku.shared.DhizukuVariables
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.server.util.ParcelFileDescriptorUtil
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DhizukuProvider : ContentProvider() {

    companion object {
        /**
         * Dhizuku Server API compatibility level (Server API 8+ supports lockTaskModeListener
         * and unbindUserServiceByConnection in Dhizuku-API 2.7.0).
         */
        const val DHIZUKU_SERVER_VERSION_CODE = 8
        const val DHIZUKU_COMPAT_VERSION_NAME = "2.7.0"
        const val EXTRA_DHIZUKU_SERVER_VERSION_CODE = "dhizuku_server_version_code"
        const val EXTRA_SHEVERY_VERSION_CODE = "shevery_version_code"
        const val EXTRA_SHEVERY_VERSION_NAME = "shevery_version_name"

        private val lockTaskListeners = RemoteCallbackList<IDhizukuLockTaskModeListener>()

        fun notifyLockTaskModeChanged(entering: Boolean, packageName: String) {
            synchronized(lockTaskListeners) {
                val count = lockTaskListeners.beginBroadcast()
                try {
                    for (i in 0 until count) {
                        try {
                            lockTaskListeners.getBroadcastItem(i)?.onLockTaskModeChanged(entering, packageName)
                        } catch (t: Throwable) {
                            LOGGER.w(t, "DhizukuProvider: failed to notify lockTask listener")
                        }
                    }
                } finally {
                    lockTaskListeners.finishBroadcast()
                }
            }
        }
    }

    private data class ClientDeathRecord(
        val connection: IDhizukuUserServiceConnection,
        val recipient: IBinder.DeathRecipient
    )

    private inner class ActiveUserService(
        val key: String,
        val uid: Int,
        val pid: Int,
        val component: ComponentName,
        val bundle: Bundle,
        var serviceBinder: IBinder
    ) {
        val clients = ConcurrentHashMap<IBinder, ClientDeathRecord>()

        fun addClient(connection: IDhizukuUserServiceConnection, onClientDied: (IBinder) -> Unit) {
            val connBinder = connection.asBinder() ?: return
            clients.remove(connBinder)?.let { old ->
                runCatching { connBinder.unlinkToDeath(old.recipient, 0) }
            }
            val recipient = IBinder.DeathRecipient {
                onClientDied(connBinder)
            }
            try {
                connBinder.linkToDeath(recipient, 0)
                clients[connBinder] = ClientDeathRecord(connection, recipient)
            } catch (t: Throwable) {
                LOGGER.w(t, "DhizukuProvider: client binder already dead for $key")
                onClientDied(connBinder)
            }
        }

        fun removeClient(connBinder: IBinder): ClientDeathRecord? {
            val removed = clients.remove(connBinder)
            if (removed != null) {
                runCatching { connBinder.unlinkToDeath(removed.recipient, 0) }
            }
            return removed
        }

        fun clearAllClients(): List<IDhizukuUserServiceConnection> {
            val list = mutableListOf<IDhizukuUserServiceConnection>()
            val keys = clients.keys.toList()
            for (k in keys) {
                val rec = clients.remove(k) ?: continue
                runCatching { k.unlinkToDeath(rec.recipient, 0) }
                list.add(rec.connection)
            }
            return list
        }
    }

    private class DhizukuRemoteProcessImpl(
        private val process: java.lang.Process
    ) : IDhizukuRemoteProcess.Stub() {

        private var outPfd: ParcelFileDescriptor? = null
        private var inPfd: ParcelFileDescriptor? = null
        private var errPfd: ParcelFileDescriptor? = null

        @Synchronized
        override fun getOutputStream(): ParcelFileDescriptor {
            outPfd?.let { return it }
            try {
                return ParcelFileDescriptorUtil.pipeTo(process.outputStream).also { outPfd = it }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }

        @Synchronized
        override fun getInputStream(): ParcelFileDescriptor {
            inPfd?.let { return it }
            try {
                return ParcelFileDescriptorUtil.pipeFrom(process.inputStream).also { inPfd = it }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }

        @Synchronized
        override fun getErrorStream(): ParcelFileDescriptor {
            errPfd?.let { return it }
            try {
                return ParcelFileDescriptorUtil.pipeFrom(process.errorStream).also { errPfd = it }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }

        override fun exitValue(): Int {
            return process.exitValue()
        }

        override fun destroy() {
            process.destroy()
        }

        override fun alive(): Boolean {
            return try {
                process.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }

        override fun waitFor(): Int {
            return try {
                process.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException(e)
            }
        }

        override fun waitForTimeout(timeout: Long, unitName: String?): Boolean {
            val unit = if (!unitName.isNullOrEmpty()) {
                runCatching { TimeUnit.valueOf(unitName) }.getOrDefault(TimeUnit.MILLISECONDS)
            } else {
                TimeUnit.MILLISECONDS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return try {
                    process.waitFor(timeout, unit)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException(e)
                }
            }
            val startTime = System.nanoTime()
            var rem = unit.toNanos(timeout)
            do {
                try {
                    process.exitValue()
                    return true
                } catch (_: IllegalThreadStateException) {
                    if (rem > 0) {
                        try {
                            Thread.sleep((TimeUnit.NANOSECONDS.toMillis(rem) + 1).coerceAtMost(100L))
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw IllegalStateException(e)
                        }
                    }
                }
                rem = unit.toNanos(timeout) - (System.nanoTime() - startTime)
            } while (rem > 0)
            return false
        }
    }

    private val activeUserServices = ConcurrentHashMap<String, ActiveUserService>()
    private val serviceLock = Any()

    @Volatile
    private var remoteExecutor: IDhizukuUserServiceExecutor? = null
    private var executorConnectionLatch: CountDownLatch? = null

    private val executorServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteExecutor = IDhizukuUserServiceExecutor.Stub.asInterface(service)
            executorConnectionLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteExecutor = null
        }
    }

    private fun obtainRemoteExecutor(ctx: Context): IDhizukuUserServiceExecutor? {
        remoteExecutor?.let {
            if (it.asBinder().isBinderAlive) return it
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null
        }
        val latch: CountDownLatch
        synchronized(serviceLock) {
            remoteExecutor?.let {
                if (it.asBinder().isBinderAlive) return it
            }
            val currentLatch = executorConnectionLatch
            if (currentLatch != null && currentLatch.count > 0) {
                latch = currentLatch
            } else {
                latch = CountDownLatch(1)
                executorConnectionLatch = latch
                val appCtx = ctx.applicationContext ?: ctx
                val intent = Intent(appCtx, DhizukuUserServiceHostService::class.java)
                val bound = runCatching {
                    appCtx.bindService(intent, executorServiceConnection, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
                if (!bound) {
                    executorConnectionLatch = null
                    return null
                }
            }
        }
        return try {
            latch.await(2500L, TimeUnit.MILLISECONDS)
            remoteExecutor?.takeIf { it.asBinder().isBinderAlive }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private inner class DhizukuBinder : IDhizuku.Stub() {

        private fun isCallerAuthorized(): Boolean {
            val callingUid = Binder.getCallingUid()
            if (callingUid == Process.myUid()) return true
            val ctx = context ?: return false
            return DhizukuAuthManager.isGranted(ctx, callingUid)
        }

        private fun enforceCallerAuthorized(methodName: String) {
            if (!isCallerAuthorized()) {
                throw SecurityException("Dhizuku: Unauthorized $methodName from UID ${Binder.getCallingUid()}")
            }
        }

        private fun resolveSheveryVersionCode(): Int {
            val ctx = context
            if (ctx != null) {
                try {
                    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pi.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pi.versionCode
                    }
                } catch (_: Throwable) {}
            }
            return BuildConfig.VERSION_CODE
        }

        private fun resolveSheveryVersionName(): String {
            val ctx = context
            if (ctx != null) {
                try {
                    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                    if (!pi.versionName.isNullOrEmpty()) {
                        return pi.versionName!!
                    }
                } catch (_: Throwable) {}
            }
            return BuildConfig.VERSION_NAME
        }

        private fun extractComponentName(bundle: Bundle?): ComponentName? {
            if (bundle == null) return null
            bundle.classLoader = ComponentName::class.java.classLoader
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(DhizukuVariables.PARAM_COMPONENT, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(DhizukuVariables.PARAM_COMPONENT) as? ComponentName
            }
        }

        /**
         * Verifies that `component.packageName` belongs to `Binder.getCallingUid()`
         * via `packageManager.getPackagesForUid(callingUid)`.
         */
        private fun enforceComponentBelongsToCaller(ctx: Context, component: ComponentName?) {
            if (component == null) {
                throw SecurityException("UserService component is null")
            }
            val callingUid = Binder.getCallingUid()
            if (callingUid == Process.myUid()) return
            val packages = ctx.packageManager.getPackagesForUid(callingUid)
            if (packages == null || !packages.contains(component.packageName)) {
                throw SecurityException(
                    "Package ${component.packageName} does not belong to UID $callingUid"
                )
            }
        }

        private fun enforcePackageBelongsToCaller(ctx: Context, packageName: String?) {
            if (packageName.isNullOrEmpty()) {
                throw SecurityException("Package name is null or empty")
            }
            val callingUid = Binder.getCallingUid()
            if (callingUid == Process.myUid()) return
            val packages = ctx.packageManager.getPackagesForUid(callingUid)
            if (packages == null || !packages.contains(packageName)) {
                throw SecurityException(
                    "Package $packageName does not belong to UID $callingUid"
                )
            }
        }

        private fun buildCacheKey(uid: Int, pid: Int, component: ComponentName): String {
            return "$uid:$pid:${component.flattenToShortString()}"
        }

        private fun createOrReuseUserServiceBinder(ctx: Context, component: ComponentName): IBinder? {
            val token = Binder.clearCallingIdentity()
            return try {
                val executor = obtainRemoteExecutor(ctx)
                if (executor != null) {
                    runCatching { executor.getOrCreateService(component) }.getOrNull()?.let {
                        return it
                    }
                }
                // Direct in-process fallback when isolated host binding is not yet ready (e.g. main-thread call)
                val created = DhizukuUserServiceHostService.instantiateUserService(ctx, component)
                if (created != null) {
                    DhizukuUserServiceHostService.sendLifecycleTransaction(
                        created,
                        IBinder.FIRST_CALL_TRANSACTION + 1
                    )
                }
                created
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        private fun destroyUserServiceInstance(ctx: Context, entry: ActiveUserService) {
            val token = Binder.clearCallingIdentity()
            try {
                val executor = remoteExecutor?.takeIf { it.asBinder().isBinderAlive }
                if (executor != null && entry.serviceBinder !is Binder) {
                    runCatching { executor.destroyService(entry.component) }
                } else {
                    DhizukuUserServiceHostService.sendLifecycleTransaction(
                        entry.serviceBinder,
                        IBinder.FIRST_CALL_TRANSACTION + 2
                    )
                }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun getVersionCode(): Int = resolveSheveryVersionCode()

        override fun getVersionName(): String = resolveSheveryVersionName()

        override fun isPermissionGranted(): Boolean = isCallerAuthorized()

        override fun remoteProcess(
            cmd: Array<out String>?,
            env: Array<out String>?,
            dir: String?
        ): IDhizukuRemoteProcess {
            enforceCallerAuthorized("remoteProcess")
            if (cmd.isNullOrEmpty() || cmd[0].isNullOrEmpty()) {
                throw IllegalArgumentException("Command array must not be null or empty")
            }
            val token = Binder.clearCallingIdentity()
            try {
                val pb = ProcessBuilder(cmd.toList())
                if (!dir.isNullOrEmpty()) {
                    pb.directory(File(dir))
                }
                if (env != null) {
                    val envMap = pb.environment()
                    for (entry in env) {
                        if (entry.isNullOrEmpty()) continue
                        val idx = entry.indexOf('=')
                        if (idx > 0) {
                            envMap[entry.substring(0, idx)] = entry.substring(idx + 1)
                        }
                    }
                }
                val proc = pb.start()
                return DhizukuRemoteProcessImpl(proc)
            } catch (e: IOException) {
                throw IllegalStateException("Failed to start remoteProcess: ${e.message}", e)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun bindUserService(connection: IDhizukuUserServiceConnection?, bundle: Bundle?) {
            enforceCallerAuthorized("bindUserService")
            val ctx = context ?: return
            if (connection == null || bundle == null) return

            val component = extractComponentName(bundle)
            enforceComponentBelongsToCaller(ctx, component)
            val targetComponent = component!!

            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            val cacheKey = buildCacheKey(callingUid, callingPid, targetComponent)

            val callbackBundle = Bundle(bundle).apply {
                putParcelable(DhizukuVariables.PARAM_COMPONENT, targetComponent)
            }

            val serviceBinder: IBinder
            synchronized(serviceLock) {
                val existing = activeUserServices[cacheKey]
                val aliveBinder = existing?.serviceBinder?.takeIf {
                    it.isBinderAlive && it.pingBinder()
                }
                if (existing != null && aliveBinder != null) {
                    serviceBinder = aliveBinder
                    existing.addClient(connection) { deadConnBinder ->
                        handleClientBinderDeath(ctx, cacheKey, deadConnBinder)
                    }
                } else {
                    if (existing != null) {
                        activeUserServices.remove(cacheKey)
                        existing.clearAllClients()
                    }
                    val created = createOrReuseUserServiceBinder(ctx, targetComponent)
                        ?: throw IllegalStateException(
                            "Failed to instantiate UserService ${targetComponent.flattenToShortString()}"
                        )
                    serviceBinder = created
                    val active = ActiveUserService(
                        key = cacheKey,
                        uid = callingUid,
                        pid = callingPid,
                        component = targetComponent,
                        bundle = callbackBundle,
                        serviceBinder = created
                    )
                    active.addClient(connection) { deadConnBinder ->
                        handleClientBinderDeath(ctx, cacheKey, deadConnBinder)
                    }
                    activeUserServices[cacheKey] = active
                }
            }

            try {
                connection.connected(callbackBundle, serviceBinder)
            } catch (t: Throwable) {
                LOGGER.w(t, "DhizukuProvider: failed to deliver connected callback for $cacheKey")
            }
        }

        private fun handleClientBinderDeath(ctx: Context, cacheKey: String, deadConnBinder: IBinder) {
            var toDestroy: ActiveUserService? = null
            synchronized(serviceLock) {
                val entry = activeUserServices[cacheKey] ?: return
                entry.removeClient(deadConnBinder)
                if (entry.clients.isEmpty()) {
                    activeUserServices.remove(cacheKey)
                    toDestroy = entry
                }
            }
            toDestroy?.let { entry ->
                destroyUserServiceInstance(ctx, entry)
            }
        }

        override fun unbindUserService(bundle: Bundle?) {
            enforceCallerAuthorized("unbindUserService")
            val ctx = context ?: return
            val component = extractComponentName(bundle)
            enforceComponentBelongsToCaller(ctx, component)
            val targetComponent = component!!

            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            val exactKey = buildCacheKey(callingUid, callingPid, targetComponent)

            val removedEntries = mutableListOf<ActiveUserService>()
            synchronized(serviceLock) {
                activeUserServices.remove(exactKey)?.let { removedEntries.add(it) }
                if (removedEntries.isEmpty()) {
                    val matchingKeys = activeUserServices.entries
                        .filter { it.value.uid == callingUid && it.value.component == targetComponent }
                        .map { it.key }
                    for (k in matchingKeys) {
                        activeUserServices.remove(k)?.let { removedEntries.add(it) }
                    }
                }
            }

            for (entry in removedEntries) {
                val connections = entry.clearAllClients()
                destroyUserServiceInstance(ctx, entry)
                for (conn in connections) {
                    runCatching { conn.died(entry.bundle) }
                }
            }
        }

        override fun unbindUserServiceByConnection(
            connection: IDhizukuUserServiceConnection?,
            bundle: Bundle?
        ) {
            enforceCallerAuthorized("unbindUserServiceByConnection")
            val ctx = context ?: return
            val component = extractComponentName(bundle)
            enforceComponentBelongsToCaller(ctx, component)
            val targetComponent = component!!

            if (connection == null) {
                unbindUserService(bundle)
                return
            }

            val connBinder = connection.asBinder()
            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            val exactKey = buildCacheKey(callingUid, callingPid, targetComponent)

            val entriesToDestroy = mutableListOf<ActiveUserService>()
            val disconnectedCallbacks = mutableListOf<Pair<IDhizukuUserServiceConnection, Bundle>>()

            synchronized(serviceLock) {
                val candidates = mutableListOf<ActiveUserService>()
                activeUserServices[exactKey]?.let { candidates.add(it) }
                for (entry in activeUserServices.values) {
                    if (entry.uid == callingUid && entry.component == targetComponent && !candidates.contains(entry)) {
                        candidates.add(entry)
                    }
                }
                for (entry in candidates) {
                    val removedClient = if (connBinder != null) entry.removeClient(connBinder) else null
                    if (removedClient != null) {
                        disconnectedCallbacks.add(removedClient.connection to entry.bundle)
                    }
                    if (entry.clients.isEmpty()) {
                        activeUserServices.remove(entry.key)
                        entriesToDestroy.add(entry)
                    }
                }
            }

            for (entry in entriesToDestroy) {
                destroyUserServiceInstance(ctx, entry)
            }
            for ((conn, b) in disconnectedCallbacks) {
                runCatching { conn.died(b) }
            }
        }

        override fun getDelegatedScopes(packageName: String?): Array<String> {
            enforceCallerAuthorized("getDelegatedScopes")
            val ctx = context ?: return emptyArray()
            if (packageName.isNullOrEmpty()) return emptyArray()
            enforcePackageBelongsToCaller(ctx, packageName)
            val token = Binder.clearCallingIdentity()
            return try {
                DeviceOwnerManager.getDelegatedScopes(ctx, packageName).toTypedArray()
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun setDelegatedScopes(packageName: String?, scopes: Array<out String>?) {
            enforceCallerAuthorized("setDelegatedScopes")
            val ctx = context ?: return
            if (!packageName.isNullOrEmpty() && scopes != null) {
                enforcePackageBelongsToCaller(ctx, packageName)
                val token = Binder.clearCallingIdentity()
                try {
                    DeviceOwnerManager.setDelegatedScopes(ctx, packageName, scopes.toList())
                } finally {
                    Binder.restoreCallingIdentity(token)
                }
            }
        }

        override fun addLockTaskModeListener(listener: IDhizukuLockTaskModeListener?) {
            enforceCallerAuthorized("addLockTaskModeListener")
            if (listener == null) return
            synchronized(lockTaskListeners) {
                lockTaskListeners.register(listener, Binder.getCallingUid())
            }
        }

        override fun removeLockTaskModeListener(listener: IDhizukuLockTaskModeListener?) {
            enforceCallerAuthorized("removeLockTaskModeListener")
            if (listener == null) return
            synchronized(lockTaskListeners) {
                lockTaskListeners.unregister(listener)
            }
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == DhizukuVariables.TRANSACT_CODE_REMOTE_BINDER) {
                val callingUid = Binder.getCallingUid()
                if (!isCallerAuthorized()) {
                    LOGGER.w("DhizukuProvider: Unauthorized remoteTransact call from UID $callingUid")
                    return false
                }

                val targetData = Parcel.obtain()
                try {
                    try {
                        data.enforceInterface(DhizukuVariables.BINDER_DESCRIPTOR)
                    } catch (_: SecurityException) {
                        data.setDataPosition(0)
                        try {
                            data.enforceInterface("com.rosan.dhizuku.aidl.IDhizuku")
                        } catch (_: SecurityException) {
                            data.setDataPosition(0)
                            try {
                                data.readInt()
                                data.readString()
                            } catch (_: Throwable) {
                                data.setDataPosition(0)
                            }
                        }
                    }
                    val targetBinder = data.readStrongBinder() ?: return false
                    val targetCode = data.readInt()
                    val targetFlags = data.readInt()
                    targetData.appendFrom(data, data.dataPosition(), data.dataAvail())
                    val id = Binder.clearCallingIdentity()
                    return try {
                        targetBinder.transact(targetCode, targetData, reply, targetFlags)
                    } finally {
                        Binder.restoreCallingIdentity(id)
                    }
                } finally {
                    targetData.recycle()
                }
            }

            // Fallback for v1 / legacy Dhizuku AIDL transactions
            if (code in FIRST_CALL_TRANSACTION..(FIRST_CALL_TRANSACTION + 3)) {
                var isV2 = true
                val startPos = data.dataPosition()
                try {
                    data.enforceInterface("com.rosan.dhizuku.aidl.IDhizuku")
                } catch (_: SecurityException) {
                    data.setDataPosition(startPos)
                    try {
                        data.enforceInterface("com.rosan.dhizuku.IDhizuku")
                        isV2 = false
                    } catch (_: SecurityException) {}
                }
                if (!isV2) {
                    when (code) {
                        FIRST_CALL_TRANSACTION + 0 -> { // legacy v1 getVersion
                            reply?.writeNoException()
                            reply?.writeInt(1)
                            return true
                        }
                        FIRST_CALL_TRANSACTION + 1 -> { // legacy v1 getBinder
                            reply?.writeNoException()
                            val binder = if (isCallerAuthorized()) {
                                try {
                                    ServiceManager.getService(Context.DEVICE_POLICY_SERVICE)
                                } catch (_: Throwable) {
                                    null
                                }
                            } else null
                            reply?.writeStrongBinder(binder)
                            return true
                        }
                        FIRST_CALL_TRANSACTION + 2 -> { // legacy v1 isPermissionGranted
                            reply?.writeNoException()
                            reply?.writeInt(if (isCallerAuthorized()) 1 else 0)
                            return true
                        }
                        FIRST_CALL_TRANSACTION + 3 -> {
                            reply?.writeNoException()
                            reply?.writeBundle(Bundle())
                            return true
                        }
                    }
                } else {
                    data.setDataPosition(startPos)
                    if (code == FIRST_CALL_TRANSACTION + 3) {
                        reply?.writeNoException()
                        reply?.writeBundle(Bundle())
                        return true
                    }
                }
            }

            return super.onTransact(code, data, reply, flags)
        }
    }

    private val binder = DhizukuBinder()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context
        if (DhizukuVariables.PROVIDER_METHOD_CLIENT == method) {
            val bundle = Bundle()
            bundle.putBinder(DhizukuVariables.PARAM_DHIZUKU_BINDER, binder)
            if (ctx != null) {
                val ownerComponent = DeviceOwnerManager.getAdminComponent(ctx)
                bundle.putParcelable(DhizukuVariables.PARAM_COMPONENT, ownerComponent)
            }
            bundle.putInt(EXTRA_DHIZUKU_SERVER_VERSION_CODE, DHIZUKU_SERVER_VERSION_CODE)
            bundle.putInt(EXTRA_SHEVERY_VERSION_CODE, binder.versionCode)
            bundle.putString(EXTRA_SHEVERY_VERSION_NAME, binder.versionName)
            return bundle
        }
        if ("getBinder" == method) {
            val bundle = Bundle()
            bundle.putBinder("binder", binder)
            return bundle
        }
        if ("get_owner_component" == method || "getOwnerComponent" == method) {
            if (ctx != null) {
                val bundle = Bundle()
                val ownerComponent = DeviceOwnerManager.getAdminComponent(ctx)
                bundle.putParcelable("component", ownerComponent)
                bundle.putParcelable("owner_component", ownerComponent)
                return bundle
            }
        }
        if ("is_permission_granted" == method || "isPermissionGranted" == method) {
            if (ctx != null) {
                val bundle = Bundle()
                val callingUid = Binder.getCallingUid()
                bundle.putBoolean("granted", DhizukuAuthManager.isGranted(ctx, callingUid))
                return bundle
            }
        }
        if ("get_version_code" == method || "getVersionCode" == method) {
            val bundle = Bundle()
            bundle.putInt("version_code", binder.versionCode)
            bundle.putInt(EXTRA_DHIZUKU_SERVER_VERSION_CODE, DHIZUKU_SERVER_VERSION_CODE)
            return bundle
        }
        if ("get_version_name" == method || "getVersionName" == method) {
            val bundle = Bundle()
            bundle.putString("version_name", binder.versionName)
            return bundle
        }
        return null
    }
}
