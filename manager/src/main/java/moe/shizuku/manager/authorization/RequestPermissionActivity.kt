package moe.shizuku.manager.authorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
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

        val pm = packageManager
        val label = try {
            ai.loadLabel(pm)
        } catch (_: Exception) {
            ai.packageName
        }
        val appBitmap = try {
            ai.loadIcon(pm).toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }

        setContent {
            ShizukuExpressiveTheme {
                var secondsRemaining by remember { mutableIntStateOf(20) }

                LaunchedEffect(Unit) {
                    while (secondsRemaining > 0) {
                        delay(1000L)
                        secondsRemaining--
                    }
                    setResult(uid, pid, requestCode, allowed = false, onetime = true)
                    finish()
                }

                fun confirmPermission(onetime: Boolean) {
                    if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.PERMISSIONS)) {
                        AuthManager.authenticate(
                            activity = this@RequestPermissionActivity,
                            title = getString(R.string.security_auth_prompt_title),
                            subtitle = getString(R.string.security_action_permissions),
                            onResult = { authenticated ->
                                if (authenticated) {
                                    setResult(uid, pid, requestCode, allowed = true, onetime = onetime)
                                    finish()
                                }
                            }
                        )
                    } else {
                        setResult(uid, pid, requestCode, allowed = true, onetime = onetime)
                        finish()
                    }
                }

                AlertDialog(
                    onDismissRequest = {
                        setResult(uid, pid, requestCode, allowed = false, onetime = true)
                        finish()
                    },
                    icon = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Requesting app icon
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                                modifier = Modifier.size(54.dp)
                            ) {
                                if (appBitmap != null) {
                                    Image(
                                        bitmap = appBitmap,
                                        contentDescription = label.toString(),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_system_icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Chain / Link icon representing linking
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .size(34.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_link_24),
                                    contentDescription = "Link",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Shevery app icon
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.mipmap.ic_launcher),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            text = label.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            text = htmlToPlainText(
                                getString(
                                    R.string.permission_warning_template,
                                    label,
                                    getString(R.string.permission_group_description)
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { confirmPermission(onetime = false) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.grant_dialog_button_allow_always))
                            }
                            FilledTonalButton(
                                onClick = { confirmPermission(onetime = true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.grant_dialog_button_allow_once))
                            }
                            OutlinedButton(
                                onClick = {
                                    setResult(uid, pid, requestCode, allowed = false, onetime = true)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${stringResource(R.string.grant_dialog_button_deny)} (${secondsRemaining}s)")
                            }
                        }
                    },
                    dismissButton = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }
}
