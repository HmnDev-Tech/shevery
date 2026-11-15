package com.rosan.dhizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

class ForwardDhizukuActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val forward = Intent().apply {
                component = ComponentName(SHEVERY_PACKAGE, SHEVERY_REQUEST_ACTIVITY)
                action = intent.action ?: "com.rosan.dhizuku.action.REQUEST_DHIZUKU_PERMISSION"
                intent.extras?.let { putExtras(it) }
            }

            var callingPackage = this.callingPackage
            var uid = -1
            try {
                val m = Activity::class.java.getMethod("getLaunchedFromUid")
                val res = m.invoke(this)
                if (res is Int) uid = res
            } catch (_: Throwable) {
            }
            if (callingPackage == null) {
                try {
                    val m = Activity::class.java.getMethod("getLaunchedFromPackage")
                    val res = m.invoke(this)
                    if (res is String) callingPackage = res
                } catch (_: Throwable) {
                }
            }
            if (callingPackage == null) {
                try {
                    val currentReferrer = this.referrer
                    if (currentReferrer != null && "android-app" == currentReferrer.scheme) {
                        callingPackage = currentReferrer.authority
                    }
                } catch (_: Throwable) {
                }
            }
            if (uid == -1 && callingPackage != null) {
                try {
                    uid = packageManager.getPackageUid(callingPackage, 0)
                } catch (_: Throwable) {
                }
            }

            if (callingPackage != null) {
                forward.putExtra("callingPackage", callingPackage)
                forward.putExtra("packageName", callingPackage)
                forward.putExtra("client_package_name", callingPackage)
            }
            if (uid != -1) {
                forward.putExtra("uid", uid)
                forward.putExtra("client_uid", uid)
            }

            if (callingActivity != null) {
                forward.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            } else {
                forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(forward)
            overridePendingTransition(0, 0)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val SHEVERY_PACKAGE = "com.hamondev.shevery"
        const val SHEVERY_REQUEST_ACTIVITY = "moe.shizuku.manager.authorization.RequestPermissionActivity"
    }
}
