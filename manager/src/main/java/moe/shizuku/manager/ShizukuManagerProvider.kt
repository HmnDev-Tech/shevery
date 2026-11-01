package moe.shizuku.manager

import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.os.bundleOf
import com.hamondev.shevery.api.SheveryApiConstants
import com.hamondev.shevery.api.SheveryPrivilege
import moe.shizuku.api.BinderContainer
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuManagerProvider : ShizukuProvider() {

    companion object {
        private const val EXTRA_BINDER = SheveryApiConstants.LEGACY_EXTRA_BINDER
        private const val EXTRA_BINDER_SHEVERY = SheveryApiConstants.EXTRA_BINDER
        private const val METHOD_SEND_USER_SERVICE = SheveryApiConstants.METHOD_SEND_USER_SERVICE
        private const val METHOD_REQUEST_BINDER = SheveryApiConstants.METHOD_REQUEST_BINDER
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    /**
     * Both forwarder stubs (moe.shizuku.privileged.api and com.rosan.dhizuku) are built
     * from this repository and signed with the manager key. Only those signed stubs may
     * relay a client UID/package, because the forwarder itself is the launch caller.
     */
    private fun isTrustedForwarder(uid: Int, ctx: android.content.Context): Boolean {
        return try {
            val pkg = ctx.packageManager.getPackagesForUid(uid).orEmpty().firstOrNull {
                it == "moe.shizuku.privileged.api" || it == "com.rosan.dhizuku"
            } ?: return false
            val api28 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
            val flags = if (api28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            fun certs(info: android.content.pm.PackageInfo): Set<String> {
                val signatures = if (api28) {
                    info.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures
                }
                return signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            }
            val theirs = certs(ctx.packageManager.getPackageInfo(pkg, flags))
            val ours = certs(ctx.packageManager.getPackageInfo(ctx.packageName, flags))
            theirs.isNotEmpty() && theirs == ours
        } catch (_: Throwable) {
            false
        }
    }

    private fun isCallerTrusted(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid < Process.FIRST_APPLICATION_UID) return true

        val ctx = context ?: return false
        return try {
            if (isTrustedForwarder(uid, ctx)) return true
            if (ctx.checkCallingPermission("android.permission.INTERACT_ACROSS_USERS_FULL") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            if (ctx.checkCallingPermission("com.hamondev.shevery.permission.PRIVILEGED_API") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            val packages = ctx.packageManager.getPackagesForUid(uid).orEmpty()
            dpm != null && packages.any {
                dpm.isDeviceOwnerApp(it) || dpm.isProfileOwnerApp(it)
            }
        } catch (_: Throwable) {
            false
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // requestBinder is the documented client fast-path: any app may obtain the live
        // server interface, and the server enforces per-UID permissions on every call.
        if (method == METHOD_REQUEST_BINDER) {
            val activeBinder = SheveryPrivilege.getBinder() ?: Shizuku.getBinder()
            return if (activeBinder != null && activeBinder.pingBinder()) {
                val container = BinderContainer(activeBinder)
                Bundle().apply {
                    putParcelable(EXTRA_BINDER_SHEVERY, container)
                    putParcelable(EXTRA_BINDER, container)
                }
            } else {
                null
            }
        }

        // Injecting a server binder must never be possible from an ordinary app: that
        // would replace the privileged channel. User-service attach is deliberately open
        // because each token is bound to the service record created for its own package.
        if (extras == null) return null

        return when (method) {
            METHOD_SEND_BINDER -> {
                if (!isCallerTrusted()) {
                    LOGGER.w("ShizukuManagerProvider: rejected sendBinder from uid ${Binder.getCallingUid()}")
                    return null
                }
                try {
                    extras.classLoader = BinderContainer::class.java.classLoader
                    val container = extras.getParcelable<BinderContainer>(EXTRA_BINDER_SHEVERY)
                        ?: extras.getParcelable<BinderContainer>(EXTRA_BINDER)
                    val newBinder = container?.binder
                    if (newBinder != null) {
                        // Accept if no living binder OR if this is a new replacement binder from a restarted server
                        val ping = Shizuku.pingBinder()
                        val currentBinder = Shizuku.getBinder()
                        if (!ping || currentBinder != newBinder) {
                            Log.i("Shizuku", "ManagerProvider: received new/replacement server binder")
                            LOGGER.i("Received new/replacement Shizuku server binder in manager provider")
                            val pkg = context?.packageName ?: BuildConfig.APPLICATION_ID
                            Shizuku.onBinderReceived(newBinder, pkg)
                            SheveryPrivilege.onBinderReceived(newBinder, pkg)
                        } else {
                            LOGGER.d("sendBinder ignored: identical living binder already registered")
                        }
                    } else {
                        Log.e("Shizuku", "ManagerProvider: binder extracted from container is null")
                    }
                    Bundle()
                } catch (e: Throwable) {
                    Log.e("Shizuku", "ManagerProvider: exception in sendBinder", e)
                    LOGGER.e(e, "sendBinder")
                    super.call(method, arg, extras)
                }
            }
            METHOD_SEND_USER_SERVICE -> {
            try {
                extras.classLoader = BinderContainer::class.java.classLoader

                val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
                val binder = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder
                    ?: extras.getParcelable<BinderContainer>(EXTRA_BINDER_SHEVERY)?.binder
                    ?: return null

                // Fast path: attach immediately if Shizuku binder is already available
                if (Shizuku.pingBinder()) {
                    return try {
                        Shizuku.attachUserService(binder, bundleOf(
                            USER_SERVICE_ARG_TOKEN to token
                        ))
                        val container = BinderContainer(Shizuku.getBinder())
                        Bundle().apply {
                            putParcelable(EXTRA_BINDER, container)
                            putParcelable(EXTRA_BINDER_SHEVERY, container)
                        }
                    } catch (e: Throwable) {
                        LOGGER.e(e, "attachUserService fast-path $token")
                        null
                    }
                }

                val countDownLatch = CountDownLatch(1)
                var reply: Bundle? = Bundle()

                val listener = object : Shizuku.OnBinderReceivedListener {

                    override fun onBinderReceived() {
                        try {
                            Shizuku.attachUserService(binder, bundleOf(
                                USER_SERVICE_ARG_TOKEN to token
                            ))
                            val container = BinderContainer(Shizuku.getBinder())
                            reply!!.putParcelable(EXTRA_BINDER, container)
                            reply!!.putParcelable(EXTRA_BINDER_SHEVERY, container)
                        } catch (e: Throwable) {
                            LOGGER.e(e, "attachUserService $token")
                            reply = null
                        }

                        Shizuku.removeBinderReceivedListener(this)

                        countDownLatch.countDown()
                    }
                }

                Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)

                val completed = try {
                    countDownLatch.await(5, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    false
                }

                if (completed) {
                    reply
                } else {
                    LOGGER.e("Binder not received in 5s for sendUserService")
                    Shizuku.removeBinderReceivedListener(listener)
                    null
                }
            } catch (e: Throwable) {
                LOGGER.e(e, "sendUserService")
                null
            }
        }
        else -> super.call(method, arg, extras)
    }
}
}
