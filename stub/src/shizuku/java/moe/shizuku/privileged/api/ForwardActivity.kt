package moe.shizuku.privileged.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ForwardActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val original = intent
            val forward = Intent(original).apply {
                setPackage(SHEVERY_PACKAGE)
                val action = original.action
                if (action != null && action.contains("REQUEST_AUTHORIZATION")) {
                    setClassName(SHEVERY_PACKAGE, LEGACY_ACTIVITY)
                } else {
                    setClassName(SHEVERY_PACKAGE, PERMISSION_ACTIVITY)
                }
                original.extras?.let { putExtras(it) }
                putExtra(EXTRA_PERMISSION_CHANNEL, "shizuku_stub")
                putExtra(EXTRA_REQUESTED_PERMISSION, LEGACY_API_V23)
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
        } catch (_: Throwable) {
        }

        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val SHEVERY_PACKAGE = "com.hamondev.shevery"
        const val PERMISSION_ACTIVITY = "moe.shizuku.manager.authorization.RequestPermissionActivity"
        const val LEGACY_ACTIVITY = "moe.shizuku.manager.legacy.LegacyIsNotSupportedActivity"
        const val EXTRA_PERMISSION_CHANNEL = "com.hamondev.shevery.intent.extra.PERMISSION_CHANNEL"
        const val EXTRA_REQUESTED_PERMISSION = "com.hamondev.shevery.intent.extra.REQUESTED_PERMISSION"
        const val LEGACY_API_V23 = "moe.shizuku.manager.permission.API_V23"
    }
}
