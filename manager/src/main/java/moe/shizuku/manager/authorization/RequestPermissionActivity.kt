package moe.shizuku.manager.authorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.htmlToPlainText
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import rikka.shizuku.server.ktx.workerHandler

class RequestPermissionActivity : AppActivity() {

    private fun setResult(requestUid: Int, requestPid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    private fun checkSelfPermission(): Boolean {
        val permission = Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        if (permission) return true

        setContent {
            ShizukuExpressiveTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_icon),
                            contentDescription = null
                        )
                    },
                    title = {
                        Text("${stringResource(R.string.app_name)}: ${stringResource(R.string.app_management_dialog_adb_is_limited_title)}")
                    },
                    text = {
                        Text(
                            text = htmlToPlainText(
                                getString(
                                    R.string.app_management_dialog_adb_is_limited_message,
                                    Helps.ADB.get()
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    CustomTabsHelper.launchUrlOrCopy(
                                        this@RequestPermissionActivity,
                                        Helps.ADB.get()
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.home_adb_button_view_help))
                            }
                            Button(onClick = { finish() }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
        return false
    }

    private var binderListener: Shizuku.OnBinderReceivedListener? = null
    private val timeoutRunnable = Runnable {
        binderListener?.let {
            Shizuku.removeBinderReceivedListener(it)
            binderListener = null
        }
        if (!isFinishing && !isDestroyed) {
            LOGGER.w("Binder not received within timeout for permission request")
            finish()
        }
    }

    override fun onDestroy() {
        binderListener?.let {
            Shizuku.removeBinderReceivedListener(it)
            binderListener = null
        }
        window?.decorView?.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = intent.getParcelableExtra<ApplicationInfo>("applicationInfo")
        if (uid == -1 || pid == -1 || ai == null) {
            finish()
            return
        }

        if (Shizuku.pingBinder()) {
            initUi(uid, pid, requestCode, ai)
        } else {
            val listener = object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    binderListener?.let { Shizuku.removeBinderReceivedListener(it) }
                    binderListener = null
                    window?.decorView?.removeCallbacks(timeoutRunnable)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            initUi(uid, pid, requestCode, ai)
                        }
                    }
                }
            }
            binderListener = listener
            Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)
            window?.decorView?.postDelayed(timeoutRunnable, 5000)
        }
    }

    private fun initUi(uid: Int, pid: Int, requestCode: Int, ai: ApplicationInfo) {
        if (!checkSelfPermission()) {
            setResult(uid, pid, requestCode, allowed = false, onetime = true)
            return
        }

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        setContent {
            ShizukuExpressiveTheme {
                AlertDialog(
                    onDismissRequest = {},
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_icon),
                            contentDescription = null
                        )
                    },
                    title = {
                        Text(stringResource(R.string.app_name))
                    },
                    text = {
                        Text(
                            text = htmlToPlainText(
                                getString(
                                    R.string.permission_warning_template,
                                    label,
                                    getString(R.string.permission_group_description)
                                )
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                setResult(uid, pid, requestCode, allowed = true, onetime = false)
                                finish()
                            }
                        ) {
                            Text(stringResource(R.string.grant_dialog_button_allow_always))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                setResult(uid, pid, requestCode, allowed = false, onetime = true)
                                finish()
                            }
                        ) {
                            Text(stringResource(R.string.grant_dialog_button_deny))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }
}
