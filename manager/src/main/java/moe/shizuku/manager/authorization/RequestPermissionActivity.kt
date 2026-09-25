package moe.shizuku.manager.authorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.rosan.dhizuku.shared.DhizukuVariables
import kotlinx.coroutines.delay
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.dhizuku.DhizukuAuthManager
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

    private fun setShizukuResult(requestUid: Int, requestPid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        if (requestPid == -1 || requestCode == -1) return
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult failed", e)
        }
    }

    private fun checkSelfPermission(): Boolean {
        if (DeviceOwnerManager.isDeviceOwner(this)) {
            return true
        }
        if (!Shizuku.isPreV11()) {
            return true
        }

        val permission = try {
            Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        if (permission) return true

        setContent {
            ShizukuExpressiveTheme {
                BackHandler { finish() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_system_icon),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "${stringResource(R.string.app_name)}: ${stringResource(R.string.app_management_dialog_adb_is_limited_title)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = htmlToPlainText(
                                    getString(
                                        R.string.app_management_dialog_adb_is_limited_message,
                                        Helps.ADB.get()
                                    )
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
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
                        }
                    }
                }
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

        val bundles = listOfNotNull(
            intent.extras,
            intent.getBundleExtra("bundle")
        )

        var uid = intent.getIntExtra("uid", -1)
        if (uid == -1) {
            for (b in bundles) {
                if (b.containsKey(DhizukuVariables.PARAM_CLIENT_UID)) {
                    uid = b.getInt(DhizukuVariables.PARAM_CLIENT_UID, -1)
                    if (uid != -1) break
                }
            }
        }

        if (uid == -1) {
            val cp = callingPackage
            if (cp != null) {
                uid = runCatching { packageManager.getPackageUid(cp, 0) }.getOrDefault(-1)
            }
        }

        var dhizukuListener: IDhizukuRequestPermissionListener? = null
        for (b in bundles) {
            val binder = b.getBinder(DhizukuVariables.PARAM_CLIENT_REQUEST_PERMISSION_BINDER)
            if (binder != null) {
                dhizukuListener = kotlin.runCatching {
                    IDhizukuRequestPermissionListener.Stub.asInterface(binder)
                }.getOrNull()
                if (dhizukuListener != null) break
            }
        }

        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)

        if (uid == -1) {
            LOGGER.w("RequestPermissionActivity: No valid UID passed in intent, finishing")
            finish()
            return
        }

        @Suppress("DEPRECATION")
        var ai: ApplicationInfo? = intent.getParcelableExtra("applicationInfo")
        if (ai == null) {
            val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull()
            if (pkg != null) {
                ai = kotlin.runCatching {
                    packageManager.getApplicationInfo(pkg, 0)
                }.getOrNull()
            }
        }

        if (ai == null) {
            LOGGER.w("RequestPermissionActivity: Cannot resolve ApplicationInfo for UID $uid, finishing")
            dhizukuListener?.let {
                try {
                    it.onRequestPermission(PackageManager.PERMISSION_DENIED)
                } catch (_: Throwable) {}
            }
            finish()
            return
        }

        val isDhizuku = dhizukuListener != null || intent.action?.contains("dhizuku", ignoreCase = true) == true
        val isDeviceOwner = DeviceOwnerManager.isDeviceOwner(this)

        if (isDhizuku && DhizukuAuthManager.isGranted(this, uid)) {
            LOGGER.i("UID $uid already granted Dhizuku permission, confirming immediately")
            try {
                dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_GRANTED)
            } catch (_: Throwable) {}
            setResult(RESULT_OK)
            finish()
            return
        }

        if (isDhizuku) {
            if (DeviceOwnerManager.isDeviceOwnerWhitelistEnabled()) {
                val isGranted = DhizukuAuthManager.isGranted(this, uid)
                if (!isGranted) {
                    LOGGER.i("Strict whitelist mode: denying Dhizuku request from UID $uid")
                    try {
                        dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_DENIED)
                    } catch (_: Throwable) {}
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }
        } else {
            if (ShizukuSettings.isShizukuWhitelistEnabled()) {
                val isGranted = try {
                    AuthorizationManager.granted(ai.packageName, uid)
                } catch (_: Throwable) {
                    false
                }
                if (!isGranted) {
                    LOGGER.i("Strict whitelist mode: denying Shizuku request from ${ai.packageName} (UID $uid)")
                    setShizukuResult(uid, pid, requestCode, allowed = false, onetime = true)
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }
        }

        if (isDhizuku || isDeviceOwner) {
            initUi(uid, pid, requestCode, ai, dhizukuListener, isDhizuku)
        } else if (Shizuku.pingBinder()) {
            initUi(uid, pid, requestCode, ai, dhizukuListener, isDhizuku)
        } else {
            val listener = object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    binderListener?.let { Shizuku.removeBinderReceivedListener(it) }
                    binderListener = null
                    window?.decorView?.removeCallbacks(timeoutRunnable)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            initUi(uid, pid, requestCode, ai, dhizukuListener, isDhizuku)
                        }
                    }
                }
            }
            binderListener = listener
            Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)
            window?.decorView?.postDelayed(timeoutRunnable, 5000)
        }
    }

    private fun initUi(
        uid: Int,
        pid: Int,
        requestCode: Int,
        ai: ApplicationInfo,
        dhizukuListener: IDhizukuRequestPermissionListener?,
        isDhizuku: Boolean
    ) {
        if (!isDhizuku && !checkSelfPermission()) {
            setShizukuResult(uid, pid, requestCode, allowed = false, onetime = true)
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

                fun denyPermission() {
                    if (isDhizuku) {
                        DhizukuAuthManager.revoke(this@RequestPermissionActivity, uid)
                        try {
                            dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_DENIED)
                        } catch (_: Throwable) {}
                    }
                    setShizukuResult(uid, pid, requestCode, allowed = false, onetime = true)
                    setResult(RESULT_CANCELED)
                    finish()
                }

                LaunchedEffect(Unit) {
                    while (secondsRemaining > 0) {
                        delay(1000L)
                        secondsRemaining--
                    }
                    denyPermission()
                }

                fun confirmPermission(onetime: Boolean) {
                    fun proceed() {
                        if (isDhizuku) {
                            DhizukuAuthManager.grant(this@RequestPermissionActivity, uid, onetime = onetime)
                            try {
                                dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_GRANTED)
                            } catch (_: Throwable) {}
                        }
                        setShizukuResult(uid, pid, requestCode, allowed = true, onetime = onetime)
                        setResult(RESULT_OK)
                        finish()
                    }

                    if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.PERMISSIONS)) {
                        AuthManager.authenticate(
                            activity = this@RequestPermissionActivity,
                            title = getString(R.string.security_auth_prompt_title),
                            subtitle = getString(R.string.security_action_permissions),
                            onResult = { authenticated ->
                                if (authenticated) {
                                    proceed()
                                }
                            }
                        )
                    } else {
                        proceed()
                    }
                }

                BackHandler {
                    denyPermission()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { denyPermission() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // Consume click inside dialog
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Requesting app icon and Shevery icon linked together
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = label.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val scopeDesc = if (isDhizuku) {
                                stringResource(R.string.dhizuku_permission_group_description)
                            } else {
                                stringResource(R.string.permission_group_description)
                            }

                            Text(
                                text = htmlToPlainText(
                                    getString(
                                        R.string.permission_warning_template,
                                        label,
                                        scopeDesc
                                    )
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

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
                                    onClick = { denyPermission() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${stringResource(R.string.grant_dialog_button_deny)} (${secondsRemaining}s)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
