package moe.shizuku.manager.dhizuku

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import moe.shizuku.manager.utils.Logger.LOGGER
import java.util.concurrent.ConcurrentHashMap

/**
 * Isolated container service (`android:process=":dhizuku_user_service"`) that loads
 * and hosts third-party Dhizuku `UserService` classes so that crashes or memory issues
 * in client code do not terminate the main Shevery manager process.
 */
class DhizukuUserServiceHostService : Service() {

    private val activeServices = ConcurrentHashMap<ComponentName, IBinder>()

    private val executor = object : IDhizukuUserServiceExecutor.Stub() {

        private fun enforceSameApp() {
            val callingUid = Binder.getCallingUid()
            if (callingUid != Process.myUid()) {
                throw SecurityException("Unauthorized caller UID $callingUid for DhizukuUserServiceHostService")
            }
        }

        override fun getOrCreateService(component: ComponentName?): IBinder? {
            enforceSameApp()
            val comp = component ?: return null
            activeServices[comp]?.let { existing ->
                if (existing.isBinderAlive && existing.pingBinder()) {
                    return existing
                }
                activeServices.remove(comp, existing)
            }

            return synchronized(this) {
                activeServices[comp]?.let { existing ->
                    if (existing.isBinderAlive && existing.pingBinder()) {
                        return@synchronized existing
                    }
                    activeServices.remove(comp, existing)
                }

                val created = instantiateUserService(applicationContext, comp)
                if (created != null) {
                    sendLifecycleTransaction(created, IBinder.FIRST_CALL_TRANSACTION + 1)
                    activeServices[comp] = created
                }
                created
            }
        }

        override fun destroyService(component: ComponentName?) {
            enforceSameApp()
            val comp = component ?: return
            val removed = synchronized(this) {
                activeServices.remove(comp)
            }
            if (removed != null) {
                sendLifecycleTransaction(removed, IBinder.FIRST_CALL_TRANSACTION + 2)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = executor

    override fun onDestroy() {
        val entries = activeServices.keys.toList()
        for (comp in entries) {
            val binder = activeServices.remove(comp) ?: continue
            sendLifecycleTransaction(binder, IBinder.FIRST_CALL_TRANSACTION + 2)
        }
        super.onDestroy()
    }

    companion object {

        fun instantiateUserService(hostContext: Context, component: ComponentName): IBinder? {
            return try {
                val pkgContext = hostContext.createPackageContext(
                    component.packageName,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
                val clazz = pkgContext.classLoader.loadClass(component.className)
                val instance = try {
                    val ctorWithContext = clazz.getConstructor(Context::class.java)
                    ctorWithContext.isAccessible = true
                    ctorWithContext.newInstance(pkgContext)
                } catch (_: NoSuchMethodException) {
                    val defaultCtor = clazz.getDeclaredConstructor()
                    defaultCtor.isAccessible = true
                    defaultCtor.newInstance()
                }
                if (instance !is IBinder) {
                    LOGGER.w("DhizukuUserService: ${component.flattenToShortString()} does not implement IBinder")
                    return null
                }
                instance
            } catch (t: Throwable) {
                LOGGER.e(t, "DhizukuUserService: failed to instantiate ${component.flattenToShortString()}")
                null
            }
        }

        fun sendLifecycleTransaction(service: IBinder, transactionCode: Int) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                val descriptor = service.interfaceDescriptor ?: ""
                data.writeInterfaceToken(descriptor)
                service.transact(transactionCode, data, reply, IBinder.FLAG_ONEWAY)
            } catch (t: Throwable) {
                LOGGER.w(t, "DhizukuUserService: lifecycle transaction $transactionCode failed")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
