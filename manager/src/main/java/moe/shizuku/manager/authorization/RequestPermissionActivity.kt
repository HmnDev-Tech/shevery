package moe.shizuku.manager.authorization

import android.content.Intent
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
                AlertDialog(
                    onDismissRequest = { finish() },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = {
                        Text(
                            text = "${stringResource(R.string.app_name)}: ${stringResource(R.string.app_management_dialog_adb_is_limited_title)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    },
                    confirmButton = {
                        Button(onClick = { finish() }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
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

        val bundles = listOfNotNull(
            intent.extras,
            intent.getBundleExtra("bundle")
        )

        var uid = intent.getIntExtra("uid", -1)
        if (uid == -1) uid = intent.getIntExtra("client_uid", -1)
        if (uid == -1) uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        if (uid == -1) {
            val candidateKeys = listOf(
                DhizukuVariables.PARAM_CLIENT_UID,
                "client_uid",
                "clientUid",
                "uid",
                "EXTRA_CLIENT_UID",
                "extra_client_uid",
                Intent.EXTRA_UID
            )
            for (b in bundles) {
                for (key in candidateKeys) {
                    if (b.containsKey(key)) {
                        val v = b.get(key)
                        if (v is Int && v != -1) {
                            uid = v
                            break
                        }
                    }
                }
                if (uid != -1) break
            }
        }

        var callingPkg = intent.getStringExtra("packageName")
            ?: intent.getStringExtra("callingPackage")
            ?: intent.getStringExtra("client_package_name")
            ?: intent.getStringExtra("clientPackageName")
            ?: callingPackage

        if (callingPkg.isNullOrEmpty()) {
            val pkgKeys = listOf("packageName", "callingPackage", "client_package_name", "clientPackageName", "package_name")
            for (b in bundles) {
                for (key in pkgKeys) {
                    val p = b.getString(key)
                    if (!p.isNullOrEmpty()) {
                        callingPkg = p
                        break
                    }
                }
                if (!callingPkg.isNullOrEmpty()) break
            }
        }

        if (uid == -1) {
            uid = runCatching {
                android.app.Activity::class.java.getMethod("getLaunchedFromUid").invoke(this) as? Int
            }.getOrNull() ?: -1
        }
        if (callingPkg.isNullOrEmpty()) {
            callingPkg = runCatching {
                android.app.Activity::class.java.getMethod("getLaunchedFromPackage").invoke(this) as? String
            }.getOrNull()
        }

        if (callingPkg.isNullOrEmpty()) {
            callingPkg = runCatching {
                val ref = referrer
                if (ref != null && ref.scheme == "android-app") ref.authority else null
            }.getOrNull()
        }

        if (uid == -1 && !callingPkg.isNullOrEmpty()) {
            uid = runCatching { packageManager.getPackageUid(callingPkg, 0) }.getOrDefault(-1)
        }

        var dhizukuListener: IDhizukuRequestPermissionListener? = null
        val listenerKeys = listOf(
            DhizukuVariables.PARAM_CLIENT_REQUEST_PERMISSION_BINDER,
            "client_request_permission_binder",
            "request_permission_binder",
            "binder",
            "listener"
        )
        for (b in bundles) {
            for (key in listenerKeys) {
                val binder = b.getBinder(key)
                if (binder != null) {
                    dhizukuListener = kotlin.runCatching {
                        IDhizukuRequestPermissionListener.Stub.asInterface(binder)
                    }.getOrNull()
                    if (dhizukuListener != null) break
                }
            }
            if (dhizukuListener != null) break
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
        if (ai == null && !callingPkg.isNullOrEmpty()) {
            ai = kotlin.runCatching {
                packageManager.getApplicationInfo(callingPkg, 0)
            }.getOrNull()
        }
        if (ai == null) {
            val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull()
            if (pkg != null) {
                ai = kotlin.runCatching {
                    packageManager.getApplicationInfo(pkg, 0)
                }.getOrNull()
            }
        }

        if (ai == null) {
            ai = ApplicationInfo().apply {
                packageName = callingPkg ?: (packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid_$uid")
                this.uid = uid
                name = packageName
            }
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

        val isDeviceOwner = DeviceOwnerManager.isDeviceOwner(this)

        val pm = packageManager
        val label = try {
            ai.loadLabel(pm).takeIf { !it.isNullOrBlank() } ?: (ai.packageName ?: "UID $uid")
        } catch (_: Exception) {
            ai.packageName ?: "UID $uid"
        }
        val appBitmap = try {
            ai.loadIcon(pm).toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }

        val sheveryBitmap = try {
            packageManager.getApplicationIcon(packageName).toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }

        setContent {
            ShizukuExpressiveTheme {
                var secondsRemaining by remember { mutableIntStateOf(20) }
                var isAuthenticating by remember { mutableStateOf(false) }

                fun denyPermission() {
                    if (isDhizuku) {
                        DhizukuAuthManager.revoke(this@RequestPermissionActivity, uid)
                        if (isDeviceOwner) {
                            try {
                                DeviceOwnerManager.setDelegatedScopes(
                                    this@RequestPermissionActivity,
                                    ai.packageName,
                                    emptyList()
                                )
                            } catch (_: Throwable) {}
                        }
                        try {
                            dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_DENIED)
                        } catch (_: Throwable) {}
                    } else {
                        setShizukuResult(uid, pid, requestCode, allowed = false, onetime = true)
                    }
                    setResult(RESULT_CANCELED)
                    finish()
                }

                LaunchedEffect(isAuthenticating) {
                    if (!isAuthenticating) {
                        while (secondsRemaining > 0) {
                            delay(1000L)
                            secondsRemaining--
                        }
                        denyPermission()
                    }
                }

                fun confirmPermission(onetime: Boolean) {
                    fun proceed() {
                        if (isDhizuku) {
                            DhizukuAuthManager.grant(this@RequestPermissionActivity, uid, onetime = onetime)
                            if (isDeviceOwner) {
                                try {
                                    DeviceOwnerManager.setDelegatedScopes(
                                        this@RequestPermissionActivity,
                                        ai.packageName,
                                        DeviceOwnerManager.ALL_SCOPES.map { it.scopeName }
                                    )
                                } catch (e: Throwable) {
                                    LOGGER.w(e, "Failed to delegate scopes")
                                }
                            }
                            try {
                                dhizukuListener?.onRequestPermission(PackageManager.PERMISSION_GRANTED)
                            } catch (_: Throwable) {}
                        } else {
                            if (onetime) {
                                AuthorizationManager.markOneTime(uid, true)
                            } else {
                                AuthorizationManager.grant(ai.packageName, uid)
                            }
                            setShizukuResult(uid, pid, requestCode, allowed = true, onetime = onetime)
                        }
                        setResult(RESULT_OK)
                        finish()
                    }

                    if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.PERMISSIONS)) {
                        isAuthenticating = true
                        AuthManager.authenticate(
                            activity = this@RequestPermissionActivity,
                            title = getString(R.string.security_auth_prompt_title),
                            subtitle = getString(R.string.security_action_permissions),
                            onResult = { authenticated ->
                                isAuthenticating = false
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
                    if (!isAuthenticating) {
                        denyPermission()
                    }
                }

                AlertDialog(
                    onDismissRequest = {
                        if (!isAuthenticating) {
                            denyPermission()
                        }
                    },
                    icon = {
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
                                if (sheveryBitmap != null) {
                                    Image(
                                        bitmap = sheveryBitmap,
                                        contentDescription = stringResource(R.string.app_name),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_system_icon),
                                        contentDescription = stringResource(R.string.app_name),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    title = {
                        Text(
                            text = label.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
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
                                onClick = { denyPermission() },
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
