package moe.shizuku.manager

import android.os.Binder
import android.os.Bundle
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

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
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
        if (extras == null) return null

        return when (method) {
            METHOD_SEND_BINDER -> {
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
