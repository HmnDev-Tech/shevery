package moe.shizuku.manager.starter

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.HtmlText
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()
private class DhizukuException(message: String, cause: Throwable? = null) : Exception(message, cause)

class StarterActivity : AppActivity() {

    private var waitingForService = false
    private var binderReceivedListener: Shizuku.OnBinderReceivedListener? = null

    private val viewModel by viewModels {
        ViewModel(
            this,
            intent.getBooleanExtra(EXTRA_IS_ROOT, true),
            intent.getBooleanExtra(EXTRA_IS_DHIZUKU, false),
            intent.getStringExtra(EXTRA_HOST),
            intent.getIntExtra(EXTRA_PORT, 0)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        binderReceivedListener?.let {
            Shizuku.removeBinderReceivedListener(it)
            binderReceivedListener = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startedWithRoot = intent.getBooleanExtra(EXTRA_IS_ROOT, true)
        val startedWithDhizuku = intent.getBooleanExtra(EXTRA_IS_DHIZUKU, false)

        viewModel.output.observe(this) {
            val output = it.data.orEmpty().trim()
            val dhizukuFinished = startedWithDhizuku && output.endsWith("✓ Shevery binder verified.")
            val finished = output.endsWith("info: shizuku_starter exit with 0")
            if (!waitingForService && dhizukuFinished) {
                waitingForService = true
                moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
                viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")
                window?.decorView?.postDelayed({
                    if (!isFinishing) finish()
                }, 3000)
            } else if (!waitingForService && finished) {
                waitingForService = true
                viewModel.appendOutput("")
                viewModel.appendOutput("Waiting for service...")

                val listener = object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        binderReceivedListener = null
                        runOnUiThread {
                            moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest()
                            viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")
                            window?.decorView?.postDelayed({
                                if (!isFinishing) finish()
                            }, 3000)
                        }
                    }
                }
                binderReceivedListener = listener
                Shizuku.addBinderReceivedListenerSticky(listener)
            } else if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    is SSLProtocolException -> {
                        message = R.string.adb_pair_required
                    }
                    is DhizukuException -> {
                        // Already logged in the output
                    }

                }

                if (message != 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }

        setContent {
            val outputResource by viewModel.output.observeAsState()
            val output = outputResource?.data.orEmpty()
            val failed = outputResource?.status == Status.ERROR

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.starter),
                    onNavigateUp = { finish() },
                    navigationIcon = R.drawable.ic_close_24,
                    navigationContentDescription = R.string.accessibility_close
                ) {
                    item {
                        val startedWithRoot = intent.getBooleanExtra(EXTRA_IS_ROOT, true)
                        val startedWithDhizuku = intent.getBooleanExtra(EXTRA_IS_DHIZUKU, false)
                        ExpressiveCard(
                            icon = when {
                                startedWithDhizuku -> R.drawable.ic_system_icon
                                startedWithRoot -> R.drawable.ic_root_24dp
                                else -> R.drawable.ic_adb_24dp
                            },
                            title = when {
                                startedWithDhizuku -> HtmlText(R.string.home_dhizuku_title)
                                startedWithRoot -> HtmlText(R.string.home_root_title)
                                else -> HtmlText(R.string.home_wireless_adb_title)
                            },
                            body = if (failed) {
                                stringResource(R.string.notification_service_start_failed)
                            } else {
                                stringResource(R.string.notification_service_starting)
                            },
                            danger = failed
                        )
                    }
                    item {
                        MonospaceLog(
                            text = output.ifBlank { stringResource(R.string.starting_root_shell) }
                        )
                    }
                }
            }
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_IS_DHIZUKU = "$EXTRA.IS_DHIZUKU"
        const val EXTRA_HOST = "$EXTRA.HOST"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

private class ViewModel(context: Context, root: Boolean, dhizuku: Boolean, host: String?, port: Int) : androidx.lifecycle.ViewModel() {

    private val appContext = context.applicationContext

    private val sb = StringBuilder()
    private val outputLock = Any()
    private val _output = MutableLiveData<Resource<String>>()

    val output = _output as LiveData<Resource<String>>

    init {
        try {
            when {
                dhizuku -> startDhizuku(context)
                root -> startRoot()
                else -> startAdb(host!!, port)
            }
        } catch (e: Throwable) {
            postResult(e)
        }
    }

    fun appendOutput(line: String) {
        synchronized(outputLock) {
            sb.appendLine(line)
        }
        postResult()
    }

    private fun appendRaw(value: String?) {
        synchronized(outputLock) {
            sb.append(value.orEmpty()).append('\n')
        }
        postResult()
    }

    private fun appendLine(value: String) {
        synchronized(outputLock) {
            sb.append(value).append('\n')
        }
        postResult()
    }

    private fun postResult(throwable: Throwable? = null) {
        val snapshot = synchronized(outputLock) {
            sb.toString()
        }
        if (throwable == null) {
            _output.postValue(Resource.success(snapshot))
        } else {
            _output.postValue(Resource.error(throwable, snapshot))
        }
    }

    private fun startRoot() {
        synchronized(outputLock) {
            sb.append("Starting with root...").append('\n').append('\n')
        }
        postResult()

        viewModelScope.launch(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                Shell.getCachedShell()?.close()
                appendLine("\nCan't open root shell, try again...")
                if (!Shell.getShell().isRoot) {
                    appendLine("\nStill not :(")
                    postResult(NotRootedException())
                    return@launch
                }
            }

            ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ROOT)

            Shell.cmd(Starter.internalCommand).to(object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    appendRaw(s)
                }
            }).submit {
                if (it.code != 0) {
                    appendLine("\nSend this to developer may help solve the problem.")
                }
            }
        }
    }

    private fun startAdb(host: String, port: Int) {
        synchronized(outputLock) {
            sb.append("Starting with wireless adb in port $port...").append('\n').append('\n')
        }
        postResult()

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                AdbStarter.start(host, port, context = appContext, listener = {
                    synchronized(outputLock) {
                        sb.append(String(it))
                    }
                    postResult()
                }, log = {
                    appendLine(it)
                })
            }.onFailure {
                it.printStackTrace()

                if (it is java.security.GeneralSecurityException) {
                    postResult(AdbKeyException(it))
                    return@onFailure
                }

                appendLine("\n${Log.getStackTraceString(it)}")
                postResult(it)
            }
        }
    }

    private suspend fun waitForShizukuBinder(timeoutMs: Long = 10_000L): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (Shizuku.pingBinder()) return true
            kotlinx.coroutines.delay(500)
        }
        return Shizuku.pingBinder()
    }

    private fun startDhizuku(context: Context) {
        synchronized(outputLock) {
            sb.append("Starting with Dhizuku (Device Owner)...").append('\n').append('\n')
        }
        postResult()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLine("Initializing Dhizuku...")
                val initResult = com.rosan.dhizuku.api.Dhizuku.init(context.applicationContext)
                if (!initResult) {
                    appendLine("✗ Dhizuku init failed. Is Dhizuku app installed and active?")
                    postResult(DhizukuException("Dhizuku init failed"))
                    return@launch
                }
                appendLine("✓ Dhizuku initialized\n")

                appendLine("Checking Dhizuku permission...")
                if (!com.rosan.dhizuku.api.Dhizuku.isPermissionGranted()) {
                    appendLine("Requesting Dhizuku permission...")
                    val permissionGranted = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                        com.rosan.dhizuku.api.Dhizuku.requestPermission(object : com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
                            override fun onRequestPermission(grantResult: Int) {
                                cont.resume(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {}
                            }
                        })
                    }
                    if (!permissionGranted) {
                        appendLine("✗ Dhizuku permission denied")
                        postResult(DhizukuException("Dhizuku permission denied"))
                        return@launch
                    }
                }
                appendLine("✓ Dhizuku permission granted\n")

                appendLine("Binding Dhizuku user service...")
                val userServiceArgs = com.rosan.dhizuku.api.DhizukuUserServiceArgs(
                    android.content.ComponentName(context.applicationContext, moe.shizuku.manager.dhizuku.DhizukuService::class.java)
                )
                var connection: android.content.ServiceConnection? = null
                try {
                    val serviceResult = kotlinx.coroutines.withTimeoutOrNull(10000) {
                        kotlinx.coroutines.suspendCancellableCoroutine<android.os.IBinder?> { cont ->
                            val conn = object : android.content.ServiceConnection {
                                override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                                    if (cont.isActive) cont.resumeWith(Result.success(service))
                                }
                                override fun onServiceDisconnected(name: android.content.ComponentName?) {}
                            }
                            connection = conn
                            val bound = com.rosan.dhizuku.api.Dhizuku.bindUserService(userServiceArgs, conn)
                            if (!bound && cont.isActive) {
                                cont.resumeWith(Result.success(null))
                            }
                        }
                    }

                    if (serviceResult == null) {
                        appendLine("✗ Dhizuku service binding failed or timed out.")
                        appendLine("  Make sure Dhizuku is set as Device Owner and is active.")
                        postResult(DhizukuException("Dhizuku service binding failed"))
                        return@launch
                    }
                    appendLine("✓ Dhizuku service connected\n")

                    val dhizukuService = moe.shizuku.manager.dhizuku.IDhizukuService.Stub.asInterface(serviceResult)

                    appendLine("Executing Shevery starter directly via Dhizuku Device Owner...")
                    dhizukuService.runCommand(moe.shizuku.manager.starter.Starter.internalCommand)
                    ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.DHIZUKU)

                    appendLine("✓ Starter command sent to Dhizuku shell.")
                    appendLine("Waiting for Shevery service to initialize...")
                    if (waitForShizukuBinder()) {
                        appendLine("✓ Shevery binder verified.")
                        postResult()
                    } else {
                        appendLine("✗ Starter command completed, but Shevery service did not become available.")
                        appendLine("  Direct Dhizuku startup can fail when the Device Owner context cannot provide the same shell/root environment as ADB or root.")
                        appendLine("  Try starting with Wireless ADB or root, then copy diagnostics if this repeats.")
                        postResult(DhizukuException("Dhizuku starter did not publish a Shevery binder"))
                    }
                } finally {
                    connection?.let { conn ->
                        try {
                            com.rosan.dhizuku.api.Dhizuku.unbindUserService(conn)
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) {
                appendLine("\n✗ Dhizuku error: ${e.message}")
                appendLine(Log.getStackTraceString(e))
                postResult(DhizukuException("Dhizuku failed: ${e.message}", e))
            }
        }
    }
}
