@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.shizuku.manager.starter

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.utils.ShizukuStateMachine
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

    private val viewModel by viewModels {
        ViewModel(
            this,
            intent.getBooleanExtra(EXTRA_IS_ROOT, true),
            intent.getBooleanExtra(EXTRA_IS_DHIZUKU, false),
            intent.getStringExtra(EXTRA_HOST),
            intent.getIntExtra(EXTRA_PORT, 0)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moe.shizuku.manager.service.WatchdogManager.isStarterActive = true

        val startedWithRoot = intent.getBooleanExtra(EXTRA_IS_ROOT, true)
        val startedWithDhizuku = intent.getBooleanExtra(EXTRA_IS_DHIZUKU, false)

        viewModel.output.observe(this) {
            val output = it.data.orEmpty().trim()
            val dhizukuFinished = startedWithDhizuku && output.endsWith("✓ Shevery binder verified.")
            val finished = output.endsWith("info: shizuku_starter exit with 0")
            if (!waitingForService && dhizukuFinished) {
                waitingForService = true
                moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@StarterActivity)
                viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")
                lifecycleScope.launch {
                    delay(3000L)
                    if (!isFinishing) finish()
                }
            } else if (!waitingForService && finished) {
                waitingForService = true
                viewModel.appendOutput("")
                viewModel.appendOutput("Waiting for service...")

                lifecycleScope.launch {
                    runCatching {
                        contentResolver.getType(Uri.parse("content://$packageName.shizuku"))
                    }
                    val running = ShizukuStateMachine.awaitRunning(12_000L)

                    if (running) {
                        moe.shizuku.manager.service.WatchdogManager.clearUserStopRequest(this@StarterActivity)
                        viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")
                        delay(3000L)
                        if (!isFinishing) finish()
                    } else {
                        viewModel.appendOutput("")
                        viewModel.appendOutput("✗ Timed out waiting for Shevery service to initialize.")
                        viewModel.appendOutput("  The starter process completed, but the server binder was not received.")
                        viewModel.appendOutput("  Please try starting again, or check background battery restrictions.")
                    }
                }
            }
        }

        setContent {
            val outputResource by viewModel.output.observeAsState()
            val output = outputResource?.data.orEmpty()
            val failed = outputResource?.status == Status.ERROR

            var errorDialogRes by rememberSaveable { mutableStateOf<Int?>(null) }

            LaunchedEffect(outputResource?.status, outputResource?.error) {
                if (outputResource?.status == Status.ERROR) {
                    val msg = when (outputResource?.error) {
                        is AdbKeyException -> R.string.adb_error_key_store
                        is NotRootedException -> R.string.start_with_root_failed
                        is ConnectException -> R.string.cannot_connect_port
                        is SSLProtocolException -> R.string.adb_pair_required
                        else -> null
                    }
                    if (msg != null) {
                        errorDialogRes = msg
                    }
                }
            }

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
                        val isServiceStarted = output.contains("Service started")
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
                            } else if (isServiceStarted) {
                                stringResource(R.string.home_status_service_is_running, stringResource(R.string.app_name))
                            } else {
                                ""
                            },
                            danger = failed
                        ) {
                            if (!failed && !isServiceStarted) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                    Text(
                                        text = stringResource(R.string.notification_service_starting),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item {
                        MonospaceLog(
                            text = output.ifBlank { stringResource(R.string.starting_root_shell) }
                        )
                    }
                }

                errorDialogRes?.let { messageRes ->
                    AlertDialog(
                        onDismissRequest = { errorDialogRes = null },
                        text = {
                            Text(
                                text = stringResource(messageRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(onClick = { errorDialogRes = null }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        moe.shizuku.manager.service.WatchdogManager.isStarterActive = false
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
        prewarmManagerProvider()
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

    private fun prewarmManagerProvider() {
        runCatching {
            val uri = Uri.parse("content://${appContext.packageName}.shizuku")
            appContext.contentResolver.getType(uri)
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
        return ShizukuStateMachine.awaitRunning(timeoutMs)
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
                        appendLine("✗ Starter command completed,but Shevery service did not become available.")
                        appendLine("  Per README: start Shevery first by PC/OTG or Wireless Debugging, then use Dhizuku — \"Do not start Shevery via Dhizuku first.\"")
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
