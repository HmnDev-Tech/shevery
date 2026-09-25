package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate

lateinit var application: ShizukuApplication

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    private fun init(context: Context?) {
        ShizukuSettings.initialize(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        // Value 3 is a legacy "follow system" constant once shipped in
        // night_mode_value; AppCompat only understands -1/1/2. Normalize once
        // at startup so devices that stored 3 get real FOLLOW_SYSTEM.
        val nightMode = when (ShizukuSettings.getNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> ShizukuSettings.getNightMode()
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
        moe.shizuku.manager.service.WatchdogManager.init(this)
        moe.shizuku.manager.service.WatchdogManager.reconcileService(this)
        moe.shizuku.manager.service.SheveryNotificationManager.setup(this)
        moe.shizuku.manager.service.StartupNotificationManager.setup(this)
        moe.shizuku.manager.worker.AdbNetworkObserver.register(this)
        moe.shizuku.manager.module.update.SheveryAutoUpdateWorker.maybeSchedule(this)
        registerActivityLifecycleCallbacks(moe.shizuku.manager.security.AuthManager)
    }

}
