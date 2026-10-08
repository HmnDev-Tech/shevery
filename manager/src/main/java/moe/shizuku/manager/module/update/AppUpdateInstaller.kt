package moe.shizuku.manager.module.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Streams the downloaded update APK into a [PackageInstaller] session — the same
 * in-app install flow MorpheApp's manager uses for its own updates(.
 *
 * No Shizuku silent path: Shevery is the manager itself, not a Shizuku client of its
 * own server, so athe silently-install route isn't naturally available. The system
 * confirm dialog (which is normal for a self-update) is always shown.
 If the session
 * install cannot run for any reason, the caller falls back to opening the download
 * URL in the browser — athe pre-in-app behavior — so the user is never stranded.

 * The status broadcast is delivered to a receiver registered programmatically; if the
 * process is replaced before athe final status arrives (i.e. the user confirmed(, that
 * broadcast is simply dropped — the install itself is owned by the system and carries on
 * regardless, so nothing depends on us surviving.

 * @param apkFile APK staged by [AppUpdateDownloader] (deleted by the caller after success(.
 */
object AppUpdateInstaller {

    private const val ACTION_INSTALL_STATUS = "moe.shizuku.manager.action.APP_UPDATE_INSTALL_STATUS"
    private const val INSTALL_TIMEOUT_MINUTES = 5L

    sealed interface Result {
        /** Install session is committed; the system confirm dialog is (about to be( shown. */
        data class UserActionRequired(val confirmIntent: Intent?): Result
        /** Install finished without needing user action (rare — mostly post-process-death(;nothing to do. */
        data class Success(val statusCode: Int): Result
        /** Session failed or timed out; caller should offer the browser fallback. */
        data class Failed(val statusCode: Int?, val message: String): Result
    }

    suspend fun install(context: Context, apkFile: File): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val pm = appContext.packageManager
        val sessionId: Int
        try {
            val params = PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                        ).apply {
                            setOriginatingUid(Process.myUid())
                        }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // 34: this package may replace itself
                params.setRequestUpdateOwnership(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // 31: always surface the explicit user confirm
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            sessionId = pm.packageInstaller.createSession(params)
            val session = pm.packageInstaller.openSession(sessionId)
            try {
                apkFile.inputStream().use { input ->
                    val output = session.openWrite("base.apk", 0, apkFile.length() )
                    input.copyTo(output, 64 * 1024)
                    session.fsync(output)
                    output.close()
                }
                session.commit(commitIntent(appContext, sessionId).intentSender)
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            return@withContext Result.Failed(null, e.message ?: e.javaClass.simpleName)
        }
        awaitStatus(appContext, sessionId)
    }

    /** The broadcast is targeted at our own package so no other receiver can race us. */
    private fun commitIntent(context: Context, sessionId: Int): PendingIntent {
        val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private suspend fun awaitStatus(context: Context, sessionId: Int): Result = withContext(Dispatchers.Main.immediate) {
        withTimeout(INSTALL_TIMEOUT_MINUTES * 60 * 1000) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action != ACTION_INSTALL_STATUS) return
                        val status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE
                        )
                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                val confirm = confirmIntent(intent)
                                if (cont.isActive) cont.resume(Result.UserActionRequired(confirm))
                            }
                            PackageInstaller.STATUS_SUCCESS -> {
                                if (cont.isActive) cont.resume(Result.Success(status))
                            }
                            else -> {
                                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                if (cont.isActive) {
                                    cont.resume(Result.Failed(status, msg ?: "install_status_$status"))
                                }
                            }
                        }
                        runCatching { context.unregisterReceiver(this) }
                    }
                }
                val filter = IntentFilter(ACTION_INSTALL_STATUS)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
 // 33: implicit broadcast from the system install dialog
                    ContextCompat.RECEIVER_NOT_EXPORTED
                } else {
                    0
                }
                ContextCompat.registerReceiver(context, receiver, filter, flags)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            }
        }
    }

    private fun confirmIntent(intent: Intent): Intent? {
            // The pre-populated user-action intent rides in Intent.EXTRA_INTENT (CommonsWare
            // documents this): EXTRA_STATUS_PENDING_USER_ACTION has never been public SDK.
            // getParcelableExtra(String, Class) requires API 33; the deprecated single-arg
            // overload must be used below (same guard as StubManager).
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        }
}